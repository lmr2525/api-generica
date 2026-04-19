/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.controller;

import apigenerica.model.ApiRespuesta;
import apigenerica.model.MetaRequest;
import apigenerica.model.MetaRequest.Operacion;
import apigenerica.model.TablaConfig;
import apigenerica.service.MetaService;
import apigenerica.service.SqlService;
import apigenerica.service.ValidadorService;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;

/**
 * @author Grupo1
 */
public class BaseController {

    private final SqlService sqlService;
    private final ValidadorService validador;
    private final MetaService metaService;

    public BaseController(SqlService sqlService, ValidadorService validador, MetaService metaService) {
        this.sqlService = sqlService;
        this.validador = validador;
        this.metaService = metaService;
    }

    /**
    * Crea una base de datos y las tablas recibidas en el JSON.
    * Si la base de datos ya existe, solo crea las tablas.
    *
    * @param ctx Contexto de la petición HTTP
    */
    public void crearTabla(Context ctx) {
        // Convertir JSON a objeto MetaRequest
        MetaRequest request = ctx.bodyAsClass(MetaRequest.class);
        // Validaciones
        validador.validarMetadata(request);

        // Asegurar que la base de datos existe
        crearBaseDatos(request);
        
        int tablasCreadas = 0;
        if (request.getOperacion() == Operacion.CREATE_TABLE) {
            tablasCreadas = procesarFormulario(request);
        } else if (request.getOperacion() == Operacion.EXECUTE_SQL) {
            tablasCreadas = procesarScript(request);
        }
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Se han creado " + tablasCreadas + " tablas."));
    }
    
    /**
    * Crea la base de datos si no existe.
    *
    * @param request Datos de la petición
    */
    private void crearBaseDatos(MetaRequest request) {
        // Validaciones
        validador.validarNombre(request.getBaseDatos());
        // Crear base de datos
        String sql = sqlService.generarCreateDbSql(request.getBaseDatos());
        sqlService.ejecutarSql(null, sql);    
    }

    /**
    * Valida una lista de tablas recibidas desde el formulario,
    * genera el SQL de creación y persiste los metadatos en db4o.
    *
    * @param request Datos de la petición
    * @return Número de tablas creadas
    */
    private int procesarFormulario(MetaRequest request) {
        int tablasCreadas = 0;
        for (TablaConfig t : request.getTabla()) {
            // Validaciones
            validador.validarNombre(t.getNombreLogico());
            // Crear tabla
            String sql = sqlService.generarCreateSql(t.getNombreLogico(), t.getColumnas());
            sqlService.ejecutarSql(request.getBaseDatos(), sql);
            // Persistir metadatos
            t.setNombreDb(request.getBaseDatos());
            metaService.guardarConfiguracion(t);
            tablasCreadas++;
        }
        return tablasCreadas;
    }

    /**
    * Procesa un script SQL con una o varias sentencias DDL separadas por ";",
    * ejecuta cada una y persiste los metadatos de las tablas creadas en db4o.
    *
    * @param request Datos de la petición
    * @return Número de tablas procesadas correctamente
    */
    private int procesarScript(MetaRequest request) {
        // Separar el script en sentencias SQL por el delimitador ";"
        String[] sentencias = request.getSql().split(";");
        int tablasCreadas = 0;
        for (String sql : sentencias) {
            if (sql.trim().isEmpty()) { // Ignorar cadenas vacías
                continue;
            }
            // Crear tabla
            sqlService.ejecutarSql(request.getBaseDatos(), sql);
            // Persistir metadatos
            TablaConfig t = metaService.guardarConfiguracion(request.getBaseDatos(), sql);
            if (t != null) {
                tablasCreadas++;
            }
        }
        return tablasCreadas;
    }
}
