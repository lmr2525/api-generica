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
    private static final String RUTA = "metadatos.db4o"; // BBDD

    public static void inicializar() {
        db = Db4oEmbedded.openFile(
            Db4oEmbedded.newConfiguration(), 
            RUTA
        );
    }

    public static ObjectContainer getConexion() {
        if (db == null || db.ext().isClosed()) {
            inicializar();
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
