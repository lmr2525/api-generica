package apigenerica.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pool de conexiones MySQL usando HikariCP.
 * La URL base NO incluye base de datos para permitir operaciones
 * a nivel de servidor (CREATE DATABASE).
 * 
 * @author Grupo1
 */
public class ConexionMysql {

    private static HikariDataSource ds;
    private static final String URL = "jdbc:mysql://localhost:3306";
    private static final String USUARIO = "root";
    private static final String PWD = "";

    /**
     * Inicializa el pool de conexiones y crea las tablas de metadatos
     * del ERP si no existen.
     */
    public static void inicializar() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        config.setUsername(USUARIO);
        config.setPassword(PWD);
        config.setMaximumPoolSize(10); // Máximo 10 conexiones simultáneas
        config.setMinimumIdle(2); // Mínimo 2 conexiones en espera
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        ds = new HikariDataSource(config);

        // Crear la BD y tablas de metadatos del ERP al arrancar
        crearEstructuraERP();
    }

    /**
     * Obtiene una conexión del pool SIN base de datos seleccionada.
     * Usa conn.setCatalog(nombreBd) para cambiar de BD.
     */
    public static Connection getConexion() throws SQLException {
        return ds.getConnection();
    }

    /**
     * Obtiene una conexión del pool CON una base de datos específica.
     * 
     * @param baseDatos Nombre de la base de datos
     */
    public static Connection getConexion(String baseDatos) throws SQLException {
        Connection conn = ds.getConnection();
        if (baseDatos != null && !baseDatos.trim().isEmpty()) {
            conn.setCatalog(baseDatos);
        }
        return conn;
    }

    /**
     * Cierra el pool de conexiones.
     */
    public static void cerrar() {
        if (ds != null) ds.close();
    }

    /**
     * Crea la base de datos 'erp_sistema' y las tablas de metadatos
     * necesarias para el funcionamiento del ERP.
     */
    private static void crearEstructuraERP() {
        try (Connection conn = getConexion(); Statement stmt = conn.createStatement()) {
            // Base de datos del sistema ERP
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `erp_sistema` " +
                "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            conn.setCatalog("erp_sistema");

            // Tabla de configuración global de la empresa
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_config` (" +
                "  `clave` VARCHAR(100) PRIMARY KEY," +
                "  `valor` TEXT," +
                "  `actualizado` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Tabla de módulos instalados
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_modulos` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `nombre` VARCHAR(100) NOT NULL," +
                "  `icono` VARCHAR(50) DEFAULT '📦'," +
                "  `icon_type` VARCHAR(20) DEFAULT 'emote'," +
                "  `habilitado` TINYINT(1) DEFAULT 1," +
                "  `orden` INT DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Tabla de metadatos de tablas
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_meta_tablas` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `nombre_db` VARCHAR(100) NOT NULL," +
                "  `nombre_logico` VARCHAR(100) NOT NULL," +
                "  `nombre_amigable` VARCHAR(200)," +
                "  UNIQUE KEY `uk_tabla` (`nombre_db`, `nombre_logico`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            // Tabla de metadatos de columnas
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_meta_columnas` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `tabla_id` INT NOT NULL," +
                "  `nombre` VARCHAR(100) NOT NULL," +
                "  `tipo` VARCHAR(50) NOT NULL," +
                "  `longitud` INT NOT NULL," +
                "  `nullable` TINYINT(1) DEFAULT 1," +
                "  `es_visible` TINYINT(1) DEFAULT 1," +
                "  `autoincremental` TINYINT(1) DEFAULT 0," +
                "  `unico` TINYINT(1) DEFAULT 0," +
                "  `valor_defecto` VARCHAR(255)," +
                "  FOREIGN KEY (`tabla_id`) REFERENCES `erp_meta_tablas`(`id`) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            
            // Tabla de metadatos de relaciones
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `erp_meta_relaciones` (" +
                "  `id` INT AUTO_INCREMENT PRIMARY KEY," +
                "  `nombre` VARCHAR(100) NOT NULL," +
                "  `tabla_origen` INT NOT NULL," +
                "  `fk_columna` VARCHAR(100) NOT NULL," +    
                "  `tabla_destino` INT NOT NULL," +
                "  `cardinalidad` VARCHAR(4) NOT NULL," +
                "  FOREIGN KEY (`tabla_origen`) REFERENCES `erp_meta_tablas`(`id`) ON DELETE CASCADE" +
                "  FOREIGN KEY (`fk_columna`) REFERENCES `erp_meta_columnas`(`id`) ON DELETE CASCADE" +
                "  FOREIGN KEY (`tabla_destino`) REFERENCES `erp_meta_tablas`(`id`) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            System.out.println("[API] Estructura ERP verificada en erp_sistema.");

        } catch (SQLException e) {
            System.err.println("[API] Error creando estructura ERP: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
