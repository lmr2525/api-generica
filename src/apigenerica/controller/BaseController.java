package apigenerica.controller;

import apigenerica.ObjectMapper;
import apigenerica.TipoDatoMapper;
import apigenerica.config.ConexionMysql;
import apigenerica.dao.BaseDao;
import apigenerica.dao.MetaDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import apigenerica.model.MetaRequest;
import apigenerica.model.MetaRequest.Operacion;
import apigenerica.model.TablaConfig;
import apigenerica.service.MetaService;
import apigenerica.service.SqlService;
import apigenerica.service.ValidadorService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador genérico CRUD para cualquier tabla de la base de datos.
 * Usa los metadatos almacenados en MySQL para mapear los resultados.
 */
public class BaseController {

    private final SqlService sqlService;
    private final ValidadorService validador;
    private final MetaService metaService;
    private final BaseDao baseDao;
    private final MetaDao metaDao;
    private final ObjectMapper mapper;

    public BaseController(SqlService sqlService, ValidadorService validador,
                          MetaService metaService, BaseDao baseDao, MetaDao metaDao) {
        this.sqlService = sqlService;
        this.validador = validador;
        this.metaService = metaService;
        this.baseDao = baseDao;
        this.metaDao = metaDao;
        this.mapper = new ObjectMapper();
    }

    public void crearTabla(Context ctx) throws SQLException {
        MetaRequest request = ctx.bodyAsClass(MetaRequest.class);
        validador.validarMetadata(request);
        crearBaseDatos(request);

        int tablasCreadas = 0;
        if (request.getOperacion() == Operacion.CREATE_TABLE) {
            tablasCreadas = procesarFormulario(request);
        } else if (request.getOperacion() == Operacion.EXECUTE_SQL) {
            tablasCreadas = procesarScript(request);
        }
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Se han creado " + tablasCreadas + " tablas."));
    }

    public void fetchTodo(Context ctx) {
        String tabla = ctx.pathParam("tabla");
        validador.validarNombre(tabla);

        TablaConfig config = metaDao.getConfiguracion(tabla);
        String baseDatos = (config != null) ? config.getNombreDb() : "erp_sistema";
        List<ColumnaConfig> columnas = (config != null) ? config.getColumnas() : null;

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            String sql = "SELECT * FROM `" + tabla + "`";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                List<Object> resultados = new ArrayList<>();

                if (columnas != null && !columnas.isEmpty()) {
                    String pkName = findPkName(columnas);
                    while (rs.next()) {
                        resultados.add(mapper.mapear(rs, columnas, pkName));
                    }
                } else {
                    ResultSetMetaData meta = rs.getMetaData();
                    int numCols = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        for (int i = 1; i <= numCols; i++) {
                            fila.put(meta.getColumnName(i), rs.getObject(i));
                        }
                        resultados.add(fila);
                    }
                }
                ctx.json(ApiRespuesta.ok(resultados));
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al consultar tabla '" + tabla + "'.", e);
        }
    }

    public void fetchPorId(Context ctx) {
        String tabla = ctx.pathParam("tabla");
        String idStr = ctx.pathParam("id");
        validador.validarNombre(tabla);

        TablaConfig config = metaDao.getConfiguracion(tabla);
        String baseDatos = (config != null) ? config.getNombreDb() : "erp_sistema";
        List<ColumnaConfig> columnas = (config != null) ? config.getColumnas() : null;
        String pkName = (columnas != null) ? findPkName(columnas) : "id";

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            String sql = "SELECT * FROM `" + tabla + "` WHERE `" + pkName + "` = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, parseId(idStr));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        if (columnas != null && !columnas.isEmpty()) {
                            ctx.json(ApiRespuesta.ok(mapper.mapear(rs, columnas, pkName)));
                        } else {
                            ResultSetMetaData meta = rs.getMetaData();
                            Map<String, Object> fila = new LinkedHashMap<>();
                            for (int i = 1; i <= meta.getColumnCount(); i++) {
                                fila.put(meta.getColumnName(i), rs.getObject(i));
                            }
                            ctx.json(ApiRespuesta.ok(fila));
                        }
                    } else {
                        throw new RecursoNoEncontradoException("No se encontró registro.");
                    }
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al buscar.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void create(Context ctx) {
        String tabla = ctx.pathParam("tabla");
        validador.validarNombre(tabla);

        Map<String, Object> datos = ctx.bodyAsClass(Map.class);
        if (datos == null || datos.isEmpty()) {
            throw new ValidacionException("Cuerpo vacío.");
        }

        TablaConfig config = metaDao.getConfiguracion(tabla);
        String baseDatos = (config != null) ? config.getNombreDb() : "erp_sistema";
        if (config != null && config.getColumnas() != null) {
            datos = convertirTipos(datos, config.getColumnas());
        }

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            long id = baseDao.insertar(conn, tabla, datos);
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("id", id);
            ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok(respuesta));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al insertar.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void update(Context ctx) {
        String tabla = ctx.pathParam("tabla");
        String idStr = ctx.pathParam("id");
        validador.validarNombre(tabla);

        Map<String, Object> datos = ctx.bodyAsClass(Map.class);
        if (datos == null || datos.isEmpty()) {
            throw new ValidacionException("Cuerpo vacío.");
        }

        TablaConfig config = metaDao.getConfiguracion(tabla);
        String baseDatos = (config != null) ? config.getNombreDb() : "erp_sistema";
        List<ColumnaConfig> columnas = (config != null) ? config.getColumnas() : null;
        String pkName = (columnas != null) ? findPkName(columnas) : "id";

        if (columnas != null) {
            datos = convertirTipos(datos, columnas);
        }

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            int filas = baseDao.actualizar(conn, tabla, datos, pkName, parseId(idStr));
            if (filas == 0) {
                throw new RecursoNoEncontradoException("No se encontró registro.");
            }
            ctx.json(ApiRespuesta.ok("Actualizado."));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al actualizar.", e);
        }
    }

    public void delete(Context ctx) {
        String tabla = ctx.pathParam("tabla");
        String idStr = ctx.pathParam("id");
        validador.validarNombre(tabla);

        TablaConfig config = metaDao.getConfiguracion(tabla);
        String baseDatos = (config != null) ? config.getNombreDb() : "erp_sistema";
        List<ColumnaConfig> columnas = (config != null) ? config.getColumnas() : null;
        String pkName = (columnas != null) ? findPkName(columnas) : "id";

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            int filas = baseDao.eliminar(conn, tabla, pkName, parseId(idStr));
            if (filas == 0) {
                throw new RecursoNoEncontradoException("No se encontró registro.");
            }
            ctx.json(ApiRespuesta.ok("Eliminado."));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al eliminar.", e);
        }
    }

    private void crearBaseDatos(MetaRequest request) {
        validador.validarNombre(request.getBaseDatos());
        String sql = sqlService.generarCreateDbSql(request.getBaseDatos());
        sqlService.ejecutarSql(null, sql);
    }

    private int procesarFormulario(MetaRequest request) {
        int tablasCreadas = 0;
        for (TablaConfig t : request.getTabla()) {
            validador.validarNombre(t.getNombreLogico());
            String sql = sqlService.generarCreateSql(t.getNombreLogico(), t.getColumnas());
            sqlService.ejecutarSql(request.getBaseDatos(), sql);
            t.setNombreDb(request.getBaseDatos());
            metaService.guardarConfiguracion(t);
            tablasCreadas++;
        }
        return tablasCreadas;
    }

    private int procesarScript(MetaRequest request) throws SQLException {
        String[] sentencias = request.getSql().split(";");
        int tablasCreadas = 0;
        for (String sql : sentencias) {
            if (sql.trim().isEmpty()) continue;
            sqlService.ejecutarSql(request.getBaseDatos(), sql);
            TablaConfig t = metaService.guardarConfiguracion(request.getBaseDatos(), sql);
            if (t != null) tablasCreadas++;
        }
        return tablasCreadas;
    }

    private String findPkName(List<ColumnaConfig> columnas) {
        for (ColumnaConfig c : columnas) {
            if (c.isPk()) return c.getNombre();
        }
        return "id";
    }

    private Object parseId(String idStr) {
        try {
            return Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            return idStr;
        }
    }

    private Map<String, Object> convertirTipos(Map<String, Object> datos, List<ColumnaConfig> columnas) {
        Map<String, Object> convertidos = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : datos.entrySet()) {
            ColumnaConfig conf = null;
            for (ColumnaConfig c : columnas) {
                if (c.getNombre().equalsIgnoreCase(entry.getKey())) {
                    conf = c;
                    break;
                }
            }
            if (conf != null) {
                try {
                    convertidos.put(entry.getKey(), TipoDatoMapper.toJava(entry.getValue(), conf));
                } catch (Exception e) {
                    throw new ValidacionException(e.getMessage());
                }
            } else {
                convertidos.put(entry.getKey(), entry.getValue());
            }
        }
        return convertidos;
    }
}
