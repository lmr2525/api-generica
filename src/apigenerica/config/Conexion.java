/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Grupo1
 * Conexión con la base de datos
 */
public class Conexion {
    // Las conexiones se crean al inicio y siempre están abiertas
    private static HikariDataSource ds;
    private static final String URL = "jdbc:mysql://localhost:3306/prueba"; // BBDD
    private static final String USUARIO = "root"; // Usuario MySQL
    private static final String PWD = ""; // Contraseña MySQL

    public static void inicializar() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USUARIO);
        config.setPassword(PWD);
        config.setMaximumPoolSize(10); // Máximo 10 conexiones simultáneas
        config.setMinimumIdle(2); // Mínimo 2 conexiones en espera
        config.setConnectionTimeout(30000);
        ds = new HikariDataSource(config);
    }

    public static Connection getConexion() throws SQLException {
        return ds.getConnection();
    }

    // Para cerrar las conexiones cuando se cierre la API
    public static void cerrar() {
        if (ds != null) ds.close();
    }
}
