/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package apigenerica.config;

import apigenerica.controller.BaseController;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.model.TablaConfig;
import io.javalin.Javalin;
import io.javalin.http.HandlerType;

/**
 * @author Grupo1
 */
public class ApiGenerica {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // Inicializar conexiones con la base de datos
        ConexionMysql.inicializar();
        ConexionDb4o.inicializar();
        
        // Lanzar servidor Jetty
        Javalin app = Javalin.create().start(7000);
        
        // Configurar endpoints
        app.post("/api/metadata", BaseController::crearTabla);
        app.get("/api/metadata", BaseController::);
        app.get("/api/{tabla}", BaseController::fetchTodo);
        app.get("/api/{tabla}/{id}", BaseController::fetchPorId);
        app.post("/api/{tabla}", BaseController::create);
        app.put("/api/{tabla}/{id}", BaseController::update);
        app.delete("/api/{tabla}/{id}", BaseController::delete);
        
        // Definir excepciones
        app.exception(ValidacionException.class, (e, ctx) ->
            ctx.status(400).json(ApiRespuesta.error(e.getMessage())));

        app.exception(RecursoNoEncontradoException.class, (e, ctx) ->
            ctx.status(404).json(ApiRespuesta.error(e.getMessage())));

        app.exception(BaseDatosException.class, (e, ctx) ->
            ctx.status(500).json(ApiRespuesta.error(e.getMessage())));

        app.exception(Exception.class, (e, ctx) ->
            ctx.status(500).json(ApiRespuesta.error("Error interno del servidor.")));

//        // Añade header a las peticiones GET antes de ser procesadas por la API
//        // private: cachear solo en navegador
//        // no-cache: pregunta si los datos han cambiado antes de enviarlos
//        app.before("/api/*", ctx -> {
//            if (ctx.method().equals(HandlerType.GET)) {
//                ctx.header("Cache-Control", "private, no-cache");
//            }
//        });
//        
//        // Etag para peticiones GET de metadatos
//        // Si el Etag de tabla es el mismo que el de la petición, no se reenvían los datos
//        app.get("/api/metadatos/{tabla}", ctx -> {
//            String nombreTabla = ctx.pathParam("tabla");
//            String etagCliente = ctx.header("If-None-Match");
//
//            String etagRam = metaService.getEtagDesdeRam(nombreTabla);
//            if (etagRam != null && etagRam.equals(etagCliente)) {
//                ctx.status(304);
//                return;
//            }
//            
//            TablaConfig tabla = metaService.getConfiguracion(nombreTabla);
//            
//            metaService.guardarEtagEnRam(nombreTabla, tabla.getEtag());
//
//            // Enviar datos y nuevo Etag
//            ctx.header("ETag", tabla.getEtag());
//            ctx.header("Cache-Control", "private, no-cache");
//            ctx.json(tabla);
//        });
//
//        // No cachear peticiones GET de las tablas
//        app.get("/api/datos/{tabla}", ctx -> {
//            ctx.header("Cache-Control", "no-store");
//        });
        
        // Cerrar conexiones cuando se cierre la aplicación
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConexionMysql.cerrar();
            ConexionDb4o.cerrar();
        }));
    }
}
