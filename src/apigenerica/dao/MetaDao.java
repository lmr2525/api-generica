package apigenerica.dao;

import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.TablaConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Operaciones CRUD para las tablas de metadatos almacenadas en MySQL
 * (`erp_meta_tablas` y `erp_meta_columnas`). Reemplaza la antigua
 * implementación en db4o.
 * 
 * @author Grupo1 
 */
public class MetaDao {

    /**
     * Guarda la configuración de una tabla en MySQL.
     * Si ya existe, elimina sus metadatos anteriores y los vuelve a insertar.
     * 
     * @param tabla Objeto TablaConfig
     */
    public void guardarConfiguracion(TablaConfig tabla) {
        // Eliminar configuración anterior si existe (por nombre_logico)
        eliminarConfiguracion(tabla.getNombreLogico());

        String sqlTabla = "INSERT INTO `erp_meta_tablas` (nombre_db, nombre_logico, nombre_amigable) VALUES (?, ?, ?)";
        String sqlColumna = "INSERT INTO `erp_meta_columnas` " +
            "(tabla_id, nombre, tipo, es_pk, nullable, es_contrasena, visible, autoincremental, unico, valor_defecto, referencia_tabla, referencia_col) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = ConexionMysql.getConexion("erp_sistema");
            conn.setAutoCommit(false); // Transacción para insertar todo junto

            long tablaId = 0;
            // 1. Insertar metadatos de la tabla
            try (PreparedStatement stmtTabla = conn.prepareStatement(sqlTabla, Statement.RETURN_GENERATED_KEYS)) {
                stmtTabla.setString(1, tabla.getNombreDb());
                stmtTabla.setString(2, tabla.getNombreLogico());
                stmtTabla.setString(3, tabla.getNombreAmigable());
                stmtTabla.executeUpdate();

                try (ResultSet rs = stmtTabla.getGeneratedKeys()) {
                    if (rs.next()) {
                        tablaId = rs.getLong(1);
                        tabla.setId(tablaId);
                    }
                }
            }

            // 2. Insertar metadatos de cada columna
            if (tabla.getColumnas() != null && tablaId > 0) {
                try (PreparedStatement stmtCol = conn.prepareStatement(sqlColumna)) {
                    for (ColumnaConfig col : tabla.getColumnas()) {
                        stmtCol.setLong(1, tablaId);
                        stmtCol.setString(2, col.getNombre());
                        stmtCol.setString(3, col.getTipo());
                        stmtCol.setBoolean(4, col.isPk());
                        stmtCol.setBoolean(5, col.isNullable());
                        stmtCol.setBoolean(6, col.isContrasena());
                        stmtCol.setBoolean(7, col.isVisible());
                        stmtCol.setBoolean(8, col.isAutoincremental());
                        stmtCol.setBoolean(9, col.isUnico());
                        
                        // Manejo de valor_defecto (String)
                        if (col.getValorDefecto() != null) {
                            stmtCol.setString(10, col.getValorDefecto().toString());
                        } else {
                            stmtCol.setNull(10, Types.VARCHAR);
                        }

                        // Relaciones
                        if (col.getReferenciaTabla() != null) {
                            stmtCol.setString(11, col.getReferenciaTabla());
                            stmtCol.setString(12, col.getReferenciaCol());
                        } else {
                            stmtCol.setNull(11, Types.VARCHAR);
                            stmtCol.setNull(12, Types.VARCHAR);
                        }

                        stmtCol.addBatch();
                    }
                    stmtCol.executeBatch();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new BaseDatosException("Error al guardar configuración de '" + tabla.getNombreLogico() + "'.", e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        }
    }

    /**
     * Recupera la configuración de una tabla a partir de su nombre lógico.
     * 
     * @param nombreLogico Nombre de la tabla
     * @return Configuración de la tabla o null si no existe
     */
    public TablaConfig getConfiguracion(String nombreLogico) {
        String sqlTabla = "SELECT * FROM `erp_meta_tablas` WHERE nombre_logico = ?";
        
        try (Connection conn = ConexionMysql.getConexion("erp_sistema");
             PreparedStatement stmtTabla = conn.prepareStatement(sqlTabla)) {
            
            stmtTabla.setString(1, nombreLogico);
            
            try (ResultSet rsTabla = stmtTabla.executeQuery()) {
                if (rsTabla.next()) {
                    TablaConfig tabla = new TablaConfig();
                    tabla.setId(rsTabla.getLong("id"));
                    tabla.setNombreDb(rsTabla.getString("nombre_db"));
                    tabla.setNombreLogico(rsTabla.getString("nombre_logico"));
                    tabla.setNombreAmigable(rsTabla.getString("nombre_amigable"));
                    
                    // Recuperar columnas
                    tabla.setColumnas(getColumnasPorTablaId(conn, tabla.getId()));
                    return tabla;
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al recuperar configuración de '" + nombreLogico + "'.", e);
        }
        return null;
    }

    /**
     * Recupera la lista de columnas de un ID de tabla específico.
     */
    private List<ColumnaConfig> getColumnasPorTablaId(Connection conn, long tablaId) throws SQLException {
        List<ColumnaConfig> columnas = new ArrayList<>();
        String sqlCols = "SELECT * FROM `erp_meta_columnas` WHERE tabla_id = ?";
        
        try (PreparedStatement stmtCol = conn.prepareStatement(sqlCols)) {
            stmtCol.setLong(1, tablaId);
            try (ResultSet rs = stmtCol.executeQuery()) {
                while (rs.next()) {
                    ColumnaConfig col = new ColumnaConfig();
                    col.setId(rs.getLong("id"));
                    col.setNombre(rs.getString("nombre"));
                    col.setTipo(rs.getString("tipo"));
                    col.setPk(rs.getBoolean("es_pk"));
                    col.setNullable(rs.getBoolean("nullable"));
                    col.setContrasena(rs.getBoolean("es_contrasena"));
                    col.setVisible(rs.getBoolean("visible"));
                    col.setAutoincremental(rs.getBoolean("autoincremental"));
                    col.setUnico(rs.getBoolean("unico"));
                    col.setValorDefecto(rs.getString("valor_defecto"));
                    col.setReferenciaTabla(rs.getString("referencia_tabla"));
                    col.setReferenciaCol(rs.getString("referencia_col"));
                    columnas.add(col);
                }
            }
        }
        return columnas;
    }

    /**
     * Comprueba si existe configuración para una tabla.
     */
    public boolean existeTabla(String nombreLogico) {
        return getConfiguracion(nombreLogico) != null;
    }

    /**
     * Elimina la configuración de una tabla (por CASCADE se eliminan las columnas).
     */
    public void eliminarConfiguracion(String nombreLogico) {
        String sql = "DELETE FROM `erp_meta_tablas` WHERE nombre_logico = ?";
        try (Connection conn = ConexionMysql.getConexion("erp_sistema");
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nombreLogico);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new BaseDatosException("Error al eliminar configuración de '" + nombreLogico + "'.", e);
        }
    }

    /**
     * Devuelve todas las tablas configuradas.
     */
    public List<TablaConfig> getTodas() {
        List<TablaConfig> tablas = new ArrayList<>();
        String sqlTabla = "SELECT * FROM `erp_meta_tablas`";
        
        try (Connection conn = ConexionMysql.getConexion("erp_sistema");
             PreparedStatement stmtTabla = conn.prepareStatement(sqlTabla);
             ResultSet rsTabla = stmtTabla.executeQuery()) {
            
            while (rsTabla.next()) {
                TablaConfig tabla = new TablaConfig();
                tabla.setId(rsTabla.getLong("id"));
                tabla.setNombreDb(rsTabla.getString("nombre_db"));
                tabla.setNombreLogico(rsTabla.getString("nombre_logico"));
                tabla.setNombreAmigable(rsTabla.getString("nombre_amigable"));
                tabla.setColumnas(getColumnasPorTablaId(conn, tabla.getId()));
                tablas.add(tabla);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al listar todas las tablas.", e);
        }
        return tablas;
    }
}