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
import io.javalin.Javalin;

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

        // Cerrar conexiones cuando se cierre la aplicación
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConexionMysql.cerrar();
            ConexionDb4o.cerrar();
        }));
    }
}
