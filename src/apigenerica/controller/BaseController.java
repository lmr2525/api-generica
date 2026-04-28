package apigenerica.controller;

import apigenerica.TipoDatoMapper;
import apigenerica.config.ConexionMysql;
import apigenerica.dao.BaseDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRequest;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;
import apigenerica.service.MetaService;
import apigenerica.service.OrderService;
import apigenerica.service.SqlService;
import apigenerica.service.ValidadorService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador genérico CRUD para cualquier tabla de la base de datos. Usa los
 * metadatos almacenados en MySQL para mapear los resultados.
 *
 * @author Grupo1
 */
public class BaseController {

    private final SqlService sqlService;
    private final ValidadorService validador;
    private final MetaService metaService;
    private final BaseDao baseDao;
    private final OrderService orderService;

    public BaseController(SqlService sqlService, ValidadorService validador,
            MetaService metaService, BaseDao baseDao, OrderService orderService) {
        this.sqlService = sqlService;
        this.validador = validador;
        this.metaService = metaService;
        this.baseDao = baseDao;
        this.orderService = orderService;
    }

    /**
     * Crea una tabla en MySQL
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void crearTabla(Context ctx) {
        try {
            // Convertir JSON a objeto ApiRequest
            ApiRequest request = ctx.bodyAsClass(ApiRequest.class);
            // Validaciones
            validador.validarMetadata(request);

            // Ordenar tablas para evitar errores por foreign keys
            List<String> nombresTablas = request.getTabla().stream()
                    .map(TablaConfig::getNombreLogico)
                    .collect(Collectors.toList());
            List<String> orden = orderService.ordenarTablas(nombresTablas);
            request.getTabla().sort(Comparator.comparingInt(t -> orden.indexOf(t.getNombreLogico())));

            // Asegurar que la base de datos existe. Crearla si no
            crearBaseDatos(request);

            int tablasCreadas = procesarFormulario(request);
            ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Se han creado " + tablasCreadas + " tablas."));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al crear tablas.", e);
        }
    }

    /**
     * Crea la base de datos si no existe.
     *
     * @param request Datos de la petición
     * @throws SQLException
     */
    private void crearBaseDatos(ApiRequest request) throws SQLException {
        // Validaciones
        validador.validarNombre(request.getBaseDatos());
        // Crear base de datos
        String sql = sqlService.generarCreateDbSql(request.getBaseDatos());
        sqlService.ejecutarSql(null, sql);
    }

    /*
    * Valida una lista de tablas recibidas desde el formulario,
    * genera el SQL de creación y persiste los metadatos en db4o.
    *
    * @param request Datos de la petición
    * @return Número de tablas creadas
     */
    private int procesarFormulario(ApiRequest request) throws SQLException {
        int tablasCreadas = 0;
        for (TablaConfig t : request.getTabla()) {
            // Limpieza y validación
            validador.validarNombre(t.getNombreLogico());
            t.setNombreDb(request.getBaseDatos());

            // Buscar relaciones en las que la tabla actual es la origen (tiene la fk)
            List<RelacionConfig> relacionesTabla = new ArrayList<>();
            if (request.getRelaciones() != null) {
                relacionesTabla = request.getRelaciones().stream()
                        .filter(r -> r.getTablaOrigen().equalsIgnoreCase(t.getNombreLogico()))
                        .collect(Collectors.toList());
            }
            t.setRelaciones(relacionesTabla);

            // Crear tabla
            String sql = sqlService.generarCreateSql(t, relacionesTabla);
            sqlService.ejecutarSql(request.getBaseDatos(), sql);

            // Persistencia de metadatos
            metaService.guardarConfiguracion(t);
            tablasCreadas++;
        }
        return tablasCreadas;
    }

    /**
     * Obtener datos de una tabla
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void fetchTodo(Context ctx) {
        // Obtener nombre de la tabla de la URL
        String tabla = ctx.pathParam("tabla");
        validador.validarNombre(tabla);

        // Filtros
        Map<String, String> filtros = new HashMap<>();
        // Ignorar los siguientes parámetros
        List<String> controlParams = Arrays.asList("limit", "offset", "sort", "order", "include");
        ctx.queryParamMap().forEach((key, values) -> {
            if (!controlParams.contains(key.toLowerCase())) {
                filtros.put(key, values.get(0));
            }
        });

        // Parámetros de paginación y orden
        String includes = ctx.queryParam("include"); // Ejemplo: ?include=cliente,empresa
        int limite = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        String sort = ctx.queryParam("sort");
        String order = ctx.queryParam("order");

        // Buscar metadatos de la tabla de MySQL
        TablaConfig config = metaService.getConfiguracion(tabla);
        String baseDatos = config.getNombreDb();
        List<ColumnaConfig> columnas = config.getColumnas() != null
                ? config.getColumnas() : new ArrayList<>();

        // Includes (Relaciones)
        List<RelacionConfig> relaciones = metaService.getRelaciones(tabla, includes);
        Map<String, List<ColumnaConfig>> colsHijas = obtenerColumnasHijas(relaciones);

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            long totalRegistros = baseDao.contarRegistros(conn, tabla, filtros);
            int totalPaginas = (int) Math.ceil((double) totalRegistros / limite);

            // Añadir número de registros y páginas totales + página actual al header
            ctx.header("X-Total-Count", String.valueOf(totalRegistros));
            ctx.header("X-Total-Pages", String.valueOf(totalPaginas));
            ctx.header("X-Current-Page", String.valueOf((offset / limite) + 1));
            // Exponer los headers para que CORS pueda leerlos
            ctx.header("Access-Control-Expose-Headers", "X-Total-Count, X-Total-Pages, X-Current-Page");

            List<EntidadDinamica> resultados;

            // Si hay relaciones, SELECT con includes
            if (!relaciones.isEmpty()) {
                resultados = baseDao.buscarConIncludes(
                        conn, tabla, columnas, relaciones, colsHijas,
                        filtros, sort, order, limite, offset
                );
            } // Si no hay relaciones pero sí metadatos de columnas, SELECT normal
            else if (!columnas.isEmpty()) {
                resultados = baseDao.buscarTodo(conn, tabla, columnas, filtros, sort, order, limite, offset);
            } else {
                throw new BaseDatosException("La tabla '" + tabla + "' no tiene columnas configuradas.", null);
            }
            aplicarFiltroPrivacidadLista(resultados, columnas);
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(resultados));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al consultar tabla '" + tabla + "'.", e);
        }
    }

    private Map<String, List<ColumnaConfig>> obtenerColumnasHijas(List<RelacionConfig> relaciones) {
        Map<String, List<ColumnaConfig>> colsHijas = new HashMap<>();
        for (RelacionConfig rel : relaciones) {
            // Buscar configuración de la tabla destino (hija)
            TablaConfig configHija = metaService.getConfiguracion(rel.getTablaDestino());

            // Si tiene columnas, se guardan. Si no, guardar una lista vacía
            List<ColumnaConfig> colHija = configHija.getColumnas() != null
                    ? configHija.getColumnas() : new ArrayList<>();

            colsHijas.put(rel.getTablaDestino(), colHija);
        }
        return colsHijas;
    }

    /**
     * Obtener datos de un registro de una tabla
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void fetchPorId(Context ctx) {
        // Obtener parámetros de la URL
        String tabla = ctx.pathParam("tabla");
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        String includes = ctx.queryParam("include");
        validador.validarNombre(tabla);

        // Buscar metadatos de la tabla de MySQL
        TablaConfig config = metaService.getConfiguracion(tabla);
        String baseDatos = config.getNombreDb();
        List<ColumnaConfig> columnas = config.getColumnas() != null
                ? config.getColumnas() : new ArrayList<>();

        // Includes (Relaciones)
        List<RelacionConfig> relaciones = metaService.getRelaciones(tabla, includes);
        Map<String, List<ColumnaConfig>> colsHijas = obtenerColumnasHijas(relaciones);

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            Object resultado;

            // Si hay relaciones, SELECT con includes
            if (!relaciones.isEmpty()) {
                resultado = baseDao.buscarPorIdConIncludes(conn, tabla, id, columnas, relaciones, colsHijas);
            } // Si no hay relaciones pero sí columnas, SELECT normal
            else if (!columnas.isEmpty()) {
                resultado = baseDao.buscarPorId(conn, tabla, columnas, id);
            } // Si no hay metadatos
            else {
                throw new BaseDatosException("La tabla '" + tabla + "' no tiene columnas configuradas.", null);
            }

            // Si no existe el registro en la DB
            if (resultado == null) {
                throw new RecursoNoEncontradoException("No se encontró registro.");
            }

            aplicarFiltroPrivacidadEntidad((EntidadDinamica) resultado, columnas);
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(resultado));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al buscar el registro por ID.", e);
        }
    }

    /**
     * Inserta un registro en la base de datos
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void insert(Context ctx) {
        // Obtener parámetros de la URL
        String tabla = ctx.pathParam("tabla");
        validador.validarNombre(tabla);

        // Convertir body del JSON en EntidadDinamica
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null || body.isEmpty()) {
            throw new ValidacionException("Cuerpo vacío.");
        }
        EntidadDinamica datos = new EntidadDinamica();
        body.forEach(datos::set); // Mapear cada línea a EntidadDinamica

        // Buscar metadatos de la tabla de MySQL
        TablaConfig config = metaService.getConfiguracion(tabla);
        String baseDatos = config.getNombreDb();
        if (config.getColumnas() != null) {
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

    /**
     * Inserta registros en varias tablas a modo de transacción
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void insertTransaccional(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);

        Map<String, Object> datosRaw = (Map<String, Object>) body.get("datos");
        if (datosRaw == null) {
            throw new ValidacionException("El campo 'datos' es obligatorio.");
        }

        // Ordenar tablas según fk
        List<String> orden = orderService.ordenarTablas(new ArrayList<>(datosRaw.keySet()));

        Map<String, EntidadDinamica> datosPorTabla = new LinkedHashMap<>();
        String baseDatos = null;

        for (String tabla : orden) {
            Map<String, Object> mapaDatos = (Map<String, Object>) datosRaw.get(tabla);
            if (mapaDatos == null) {
                continue;
            }

            // Convertir Map en EntidadDinamica
            EntidadDinamica entidad = new EntidadDinamica();
            mapaDatos.forEach(entidad::set);

            TablaConfig config = metaService.getConfiguracion(tabla);
            if (config != null) {
                if (baseDatos == null) {
                    baseDatos = config.getNombreDb();
                }
                if (config.getColumnas() != null) {
                    entidad = convertirTipos(entidad, config.getColumnas());
                }
            }
            datosPorTabla.put(tabla, entidad);
        }

        if (baseDatos == null) {
            throw new RecursoNoEncontradoException("No se encontró configuración para ninguna tabla.");
        }

        List<RelacionConfig> relaciones = metaService.getRelacionesEntreTablas(orden);

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            conn.setAutoCommit(false);
            try {
                Map<String, Long> idsGenerados = baseDao.insertarTransaccional(conn, orden, datosPorTabla, relaciones);
                conn.commit();
                // Devolver todos los IDs generados por si el cliente los necesita
                ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok(idsGenerados));
            } catch (Exception e) {
                conn.rollback();
                throw new BaseDatosException("Error al insertar. Se aplicó rollback.", e);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error de conexión.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void update(Context ctx) {
        // Obtener parámetros de la URL
        String tabla = ctx.pathParam("tabla");
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        validador.validarNombre(tabla);

        // Convertir body del JSON en EntidadDinamica
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null || body.isEmpty()) {
            throw new ValidacionException("Cuerpo vacío.");
        }
        EntidadDinamica datos = new EntidadDinamica();
        body.forEach(datos::set);

        // Buscar metadatos de la tabla de MySQL
        TablaConfig config = metaService.getConfiguracion(tabla);
        String baseDatos = config.getNombreDb();
        if (config.getColumnas() != null) {
            datos = convertirTipos(datos, config.getColumnas());
        }

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            int filas = baseDao.actualizar(conn, tabla, datos, id);
            if (filas == 0) {
                throw new RecursoNoEncontradoException("No se encontró registro.");
            }
            ctx.json(ApiRespuesta.ok("Actualizado."));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al actualizar.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public void updateTransaccional(Context ctx) {
        // Convertir body del JSON en Map
        Map<String, Object> body = ctx.bodyAsClass(Map.class);

        Number idRaw = (Number) body.get("id");
        if (idRaw == null) {
            throw new ValidacionException("El campo 'id' es obligatorio.");
        }
        Long id = idRaw.longValue();

        Map<String, Object> datosRaw = (Map<String, Object>) body.get("datos");
        if (datosRaw == null) {
            throw new ValidacionException("El campo 'datos' es obligatorio.");
        }

        // Ordenar tablas según fk
        List<String> orden = orderService.ordenarTablas(new ArrayList<>(datosRaw.keySet()));

        Map<String, EntidadDinamica> datosPorTabla = new LinkedHashMap<>();
        String baseDatos = null;

        for (String tabla : orden) {
            Map<String, Object> mapaDatos = (Map<String, Object>) datosRaw.get(tabla); // ✅ declarado
            if (mapaDatos == null) {
                continue;
            }

            // Convertir map en EntidadDinamica
            EntidadDinamica entidad = new EntidadDinamica();
            mapaDatos.forEach(entidad::set);

            TablaConfig config = metaService.getConfiguracion(tabla);
            if (config != null) {
                if (baseDatos == null) {
                    baseDatos = config.getNombreDb(); // ✅ solo la primera vez
                }
                if (config.getColumnas() != null) {
                    entidad = convertirTipos(entidad, config.getColumnas());
                }
            }
            datosPorTabla.put(tabla, entidad);
        }

        if (baseDatos == null) {
            throw new RecursoNoEncontradoException("No se encontró configuración para ninguna tabla.");
        }

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            conn.setAutoCommit(false);
            try {
                int filasAfectadas = baseDao.actualizarTransaccional(conn, orden, datosPorTabla, id);
                if (filasAfectadas == 0) {
                    throw new RecursoNoEncontradoException("No se encontraron registros para actualizar.");
                }
                conn.commit();
                ctx.json(ApiRespuesta.ok("Se actualizaron " + filasAfectadas + " registros."));
            } catch (Exception e) {
                conn.rollback();
                throw new BaseDatosException("Error al actualizar registros. Se aplicó rollback.", e);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error de conexión.", e);
        }
    }

    public void delete(Context ctx) {
        // Obtener parámetros de la URL
        String tabla = ctx.pathParam("tabla");
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        validador.validarNombre(tabla);

        // Buscar metadatos de la tabla de MySQL
        TablaConfig config = metaService.getConfiguracion(tabla);
        String baseDatos = config.getNombreDb();

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            int filas = baseDao.eliminar(conn, tabla, id); // Intentar eliminar registro
            if (filas == 0) {
                throw new RecursoNoEncontradoException("No se encontró registro.");
            }
            ctx.json(ApiRespuesta.ok("Eliminado."));
        } catch (SQLException e) {
            throw new BaseDatosException("Error al eliminar.", e);
        }
    }

    public void deleteTransaccional(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);

        Number idRaw = (Number) body.get("id");
        if (idRaw == null) {
            throw new ValidacionException("El campo 'id' es obligatorio.");
        }
        Long id = idRaw.longValue();

        List<String> tablas = (List<String>) body.get("tablas");
        if (tablas == null || tablas.isEmpty()) {
            throw new ValidacionException("El campo 'tablas' es obligatorio.");
        }

        // Ordenar y luego invertir para respetar FK en el DELETE
        List<String> orden = orderService.ordenarTablas(tablas);

        String baseDatos = null;
        for (String tabla : orden) {
            TablaConfig config = metaService.getConfiguracion(tabla);
            if (config != null && baseDatos == null) {
                baseDatos = config.getNombreDb();
                break;
            }
        }
        if (baseDatos == null) {
            throw new RecursoNoEncontradoException("No se encontró configuración.");
        }

        try (Connection conn = ConexionMysql.getConexion(baseDatos)) {
            conn.setAutoCommit(false);
            try {
                int filas = baseDao.eliminarTransaccional(conn, orden, id);
                if (filas == 0) {
                    throw new RecursoNoEncontradoException("No se encontraron registros.");
                }
                conn.commit();
                ctx.json(ApiRespuesta.ok("Se eliminaron registros en " + orden.size() + " tablas."));
            } catch (Exception e) {
                conn.rollback();
                throw new BaseDatosException("Error al eliminar. Se aplicó rollback.", e);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error de conexión.", e);
        }
    }

    private EntidadDinamica convertirTipos(EntidadDinamica datos, List<ColumnaConfig> columnas) {
        EntidadDinamica convertidos = new EntidadDinamica();

        for (Map.Entry<String, Object> entry : datos.getTodo().entrySet()) {
            ColumnaConfig conf = null;
            for (ColumnaConfig c : columnas) {
                if (c.getNombre().equalsIgnoreCase(entry.getKey())) {
                    conf = c;
                    break;
                }
            }

            if (conf != null) {
                try {
                    convertidos.set(entry.getKey(), TipoDatoMapper.toJava(entry.getValue(), conf));
                } catch (Exception e) {
                    throw new ValidacionException(e.getMessage());
                }
            } else {
                convertidos.set(entry.getKey(), entry.getValue());
            }
        }
        return convertidos;
    }

    /**
     * Elimina columnas que no deben ser visibles al usuario y contraseñas
     */
    private void aplicarFiltroPrivacidadEntidad(EntidadDinamica entidad, List<ColumnaConfig> configs) {
        if (entidad == null || configs == null) {
            return;
        }

        configs.stream()
                .filter(c -> c.isContrasena() || !c.isVisible())
                .forEach(c -> entidad.getTodo().remove(c.getNombre()));
    }

    /**
     * Filtra una lista de entidades
     */
    private void aplicarFiltroPrivacidadLista(List<EntidadDinamica> entidades, List<ColumnaConfig> configs) {
        if (entidades == null) {
            return;
        }
        entidades.forEach(e -> aplicarFiltroPrivacidadEntidad(e, configs));
    }
}
