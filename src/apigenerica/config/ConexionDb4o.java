/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.config;

import com.db4o.Db4oEmbedded;
import com.db4o.ObjectContainer;

/**
 * @author Grupo1 
 * Conexión con la base de datos db4o
 */
public class ConexionDb4o {

    private static ObjectContainer db;

    public static void inicializar(String ruta) {
        db = Db4oEmbedded.openFile(
                Db4oEmbedded.newConfiguration(),
                ruta
        );
    }

    public static ObjectContainer getConexion(String ruta) {
        if (db == null || db.ext().isClosed()) {
            inicializar(ruta);
        }
        return db;
    }

    // Para cerrar las conexiones cuando se cierre la API
    public static void cerrar() {
        if (db != null && !db.ext().isClosed()) {
            db.close();
        }
    }
}
