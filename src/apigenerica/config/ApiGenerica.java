/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package apigenerica.config;

import io.javalin.Javalin;

/**
 *
 * @author 2025-2026-DAM1
 */
public class ApiGenerica {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // Inicializar conexiones con la base de datos
        Conexion.inicializar();
        
        // Lanzar servidor Jetty
        Javalin app = Javalin.create().start(7000);
        
        // Configurar endpoints
        app.get("/api/{tabla}", BaseController::fetchTodo);
        app.get("/api/{tabla}/{id}", BaseController::fetchPorId);
        app.post("/api/{tabla}", BaseController::create);
        app.put("/api/{tabla}/{id}", BaseController::update);
        app.delete("/api/{tabla}/{id}", BaseController::delete);
        
        // Cerrar conexiones cuando se cierre la aplicación
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Conexion.cerrar();
        }));
    }
    }
    
}
