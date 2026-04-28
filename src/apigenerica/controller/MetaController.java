/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.controller;

import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.TablaConfig;
import apigenerica.service.MetaService;
import apigenerica.service.ValidadorService;
import io.javalin.http.Context;

/**
 * Controlador metadatos
 * @author Grupo1
 */
public class MetaController {

    private final MetaService metaService;
    private final ValidadorService validador;

    public MetaController(MetaService metaService, ValidadorService validador) {
        this.metaService = metaService;
        this.validador = validador;
    }

    /**
     * Obtener nombre de todas las tablas de una base de datos
     * GET /api/metadata/tablas?db=nombre_db
     * 
     * @param ctx Contexto de la petición HTTP
     */
    public void listarTablas(Context ctx) {
        String db = ctx.queryParam("db");
        if (db == null || db.isEmpty()) {
            throw new ValidacionException("El parámetro 'db' (base de datos) es obligatorio.");
        }
        
        ctx.json(ApiRespuesta.ok(metaService.listarTablas(db)));
    }

    /**
     * Obtener metadatos de una tabla
     * 
     * @param ctx Contexto de la petición HTTP
     */
    public void obtenerEstructuraTabla(Context ctx) {
        String nombreTabla = ctx.pathParam("nombreTabla");
        validador.validarNombre(nombreTabla); // Reutilizamos tu validador

        TablaConfig config = metaService.getConfiguracion(nombreTabla);
        if (config == null) {
            throw new RecursoNoEncontradoException("No existen metadatos para la tabla: " + nombreTabla);
        }

        ctx.json(ApiRespuesta.ok(config));
    }
}
