/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.controller;

import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRequest;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;
import apigenerica.service.MetaService;
import apigenerica.service.OrderService;
import apigenerica.service.SqlService;
import apigenerica.service.ValidadorService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador para el envío de metadatos a los clientes
 *
 * @author Grupo1
 */
public class MetaController {

    private final MetaService metaService;
    private final ValidadorService validador;
    private final OrderService orderService;
    private final SqlService sqlService;

    public MetaController(MetaService metaService, ValidadorService validador,
            OrderService orderService, SqlService sqlService) {
        this.metaService = metaService;
        this.validador = validador;
        this.orderService = orderService;
        this.sqlService = sqlService;
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
                    .collect(Collectors.toList()); // Meter nombres de las tablas en una lista
            List<String> orden = orderService.ordenarTablas(nombresTablas); // Ordenar nombres
            // Ordenar metadatos según índice que ocupa la tabla en la lista
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

            // Obtener las relaciones de la tabla
            List<RelacionConfig> relacionesTabla = t.getRelaciones() != null
                    ? t.getRelaciones()
                    : new ArrayList<>();

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
     * Obtener nombre de todas las tablas de una base de datos GET
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void listarTablas(Context ctx) {
        // Obtener nombre de la base de datos de la URL
        String db = ctx.queryParam("db");
        if (db == null || db.isEmpty()) {
            throw new ValidacionException("El parámetro 'db' (base de datos) es obligatorio.");
        }
        // Devolver la lista de nombres de las tablas
        ctx.json(ApiRespuesta.ok(metaService.listarTablas(db)));
    }

    /**
     * Obtener metadatos de una tabla
     *
     * @param ctx Contexto de la petición HTTP
     */
    public void obtenerEstructuraTabla(Context ctx) {
        // Obtener nombre de la tabla de la URL
        String nombreTabla = ctx.pathParam("tabla");
        validador.validarNombre(nombreTabla);

        // Obtener metadatos de la tabla
        TablaConfig config = metaService.getConfiguracion(nombreTabla);
        if (config == null) {
            throw new RecursoNoEncontradoException("No existen metadatos para la tabla: " + nombreTabla);
        }

        ctx.json(ApiRespuesta.ok(config));
    }

    /**
     * Agrega una columna a una tabla
     *
     * @param ctx
     */
    public void agregarColumna(Context ctx) {
        try {
            // Obtener nombre de la tabla de la URL
            String nombreTabla = ctx.pathParam("tabla");
            // Convertir JSON a objeto ColumnaConfig
            ColumnaConfig nuevaCol = ctx.bodyAsClass(ColumnaConfig.class);

            // Agregar columna
            metaService.agregarColumna(nombreTabla, nuevaCol);
            ctx.status(201).json(ApiRespuesta.ok("Columna agregada correctamente."));
        } catch (ValidacionException e) {
            ctx.status(400).json(ApiRespuesta.error(e.getMessage()));
        } catch (SQLException | BaseDatosException e) {
            ctx.status(500).json(ApiRespuesta.error("Error en la base de datos al agregar la columna: " + e.getMessage()));
        }
    }
    
    public void eliminarColumna(Context ctx) {
        try {
            String nombreTabla = ctx.pathParam("tabla");
            String nombreColumna = ctx.pathParam("columna");

            metaService.eliminarColumna(nombreTabla, nombreColumna);
            ctx.status(200).json(ApiRespuesta.ok("Columna eliminada correctamente."));
        } catch (ValidacionException e) {
            ctx.status(400).json(ApiRespuesta.error(e.getMessage()));
        } catch (SQLException | BaseDatosException e) {
            ctx.status(500).json(ApiRespuesta.error("Error en la base de datos al agregar la columna: " + e.getMessage()));
        }
    }
    
    public void modificarColumna(Context ctx) {
        try {
            String nombreTabla = ctx.pathParam("tabla");
            String nombreColumnaActual = ctx.pathParam("columna");
            ColumnaConfig colModificada = ctx.bodyAsClass(ColumnaConfig.class);

            // Verificar que el nombre de la URL coincida con el del body
            if (!nombreColumnaActual.equalsIgnoreCase(colModificada.getNombre())) {
                throw new ValidacionException("El nombre de la columna en la URL no coincide con los datos enviados.");
            }

            // Modificar columna
            metaService.modificarColumna(nombreTabla, colModificada);
            ctx.status(200).json(ApiRespuesta.ok("Columna modificada correctamente."));
        } catch (ValidacionException e) {
            ctx.status(400).json(ApiRespuesta.error(e.getMessage()));
        }
    }
    
    /**
     * Renombra una columna existente
     *
     * @param ctx
     */
    public void renombrarColumna(Context ctx) {
        try {
            String nombreTabla = ctx.pathParam("tabla");
            String nombreViejo = ctx.pathParam("columna");

            // Obtener nuevo nombre del body
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String nombreNuevo = body.get("nuevoNombre");

            if (nombreNuevo == null || nombreNuevo.trim().isEmpty()) {
                throw new ValidacionException("Debe proporcionar el campo 'nuevoNombre' en el JSON.");
            }

            // Renombrar la columna
            metaService.renombrarColumna(nombreTabla, nombreViejo, nombreNuevo);
            ctx.status(200).json(ApiRespuesta.ok("Columna renombrada de '" + nombreViejo + "' a '" + nombreNuevo + "' correctamente."));
        } catch (ValidacionException e) {
            ctx.status(400).json(ApiRespuesta.error(e.getMessage()));
        } catch (SQLException | BaseDatosException e) {
            ctx.status(500).json(ApiRespuesta.error("Error en la base de datos al renombrar la columna: " + e.getMessage()));
        }
    }
}
