/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Grupo1
 */
public class ConexionParadox {
    private final Connection conexion;
    private final ReentrantLock lock = new ReentrantLock();

    public ConexionParadox(String ruta) throws SQLException {
        this.conexion = DriverManager.getConnection("jdbc:paradox:/" + ruta);
    }

    public <T> T ejecutar(ConParadox<T> operacion) throws SQLException {
        lock.lock(); // Bloquea la conexión
        try {
            return operacion.ejecutar(conexion);
        } finally {
            lock.unlock(); // Liberar conexión
        }
    }
}
