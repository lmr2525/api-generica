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
     * Agregar columnas a una tabla
     *
     * @param ctx
     */
    public void agregarColumna(Context ctx) {
        // Obtener nombre de la tabla de la URL
        String nombreTabla = ctx.pathParam("tabla");
        validador.validarNombre(nombreTabla);

        // Convertir JSON a objeto ApiRequest
        ApiRequest request = ctx.bodyAsClass(ApiRequest.class);

        metaService.agregarColumna(nombreTabla, request.getTabla().nuevaCol);
    }
    
    public void eliminarColumna(Context ctx) {
        // Obtener nombre de la tabla de la URL
        String nombreTabla = ctx.pathParam("tabla");
        
        // Ignorar eliminación de la primary key
        if (nombreTabla.equalsIgnoreCase("id")) {
            throw new ValidacionException("No se puede eliminar esa columna.");
        }
        
        metaService
}
