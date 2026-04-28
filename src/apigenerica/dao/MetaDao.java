package apigenerica.dao;

import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Operaciones CRUD para las tablas de metadatos almacenadas en MySQL
 * (`erp_meta_tablas`, `erp_meta_columnas` y `erp_meta_relaciones`).
 *
 * @author Grupo1
 */
public class MetaDao {

    /**
     * Guarda la configuración de una tabla en MySQL. Si ya existe, elimina sus
     * metadatos anteriores y los vuelve a insertar.
     *
     * @param tabla Objeto TablaConfig
     */
    public void guardarConfiguracion(TablaConfig tabla) {
        // Eliminar configuración anterior si existe
        eliminarConfiguracion(tabla.getNombreLogico());

        String sqlTabla = "INSERT INTO `erp_meta_tablas` (nombre_db, nombre_logico, nombre_amigable) VALUES (?, ?, ?)";
        String sqlColumna = "INSERT INTO `erp_meta_columnas` "
                + "(tabla_id, nombre, tipo, nullable, es_contrasena, visible, autoincremental, unico, valor_defecto) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlRelacion = "INSERT INTO `erp_meta_relaciones` "
                + "(nombre, tabla_origen, fk_columna, tabla_destino, cardinalidad) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = ConexionMysql.getConexion("erp_sistema")) {
            try {
                conn.setAutoCommit(false); // Transacción para insertar todo junto
                long tablaId = 0;

                // Insertar metadatos de la tabla
                try (PreparedStatement stmtTabla = conn.prepareStatement(sqlTabla, Statement.RETURN_GENERATED_KEYS)) {
                    stmtTabla.setString(1, tabla.getNombreDb());
                    stmtTabla.setString(2, tabla.getNombreLogico());
                    stmtTabla.setString(3, tabla.getNombreAmigable());
                    stmtTabla.executeUpdate();

                    try (ResultSet rs = stmtTabla.getGeneratedKeys()) {
                        if (rs.next()) {
                            tablaId = rs.getLong(1);
                        }
                    }
                }

                // Insertar metadatos de cada columna           
                if (tabla.getColumnas() != null && tablaId > 0) {
                    try (PreparedStatement stmtCol = conn.prepareStatement(sqlColumna)) {
                        for (ColumnaConfig col : tabla.getColumnas()) {
                            stmtCol.setLong(1, tablaId);
                            stmtCol.setString(2, col.getNombre());
                            stmtCol.setString(3, col.getTipo());
                            stmtCol.setBoolean(4, col.isNullable());
                            stmtCol.setBoolean(5, col.isContrasena());
                            stmtCol.setBoolean(6, col.isVisible());
                            stmtCol.setBoolean(7, col.isAutoincremental());
                            stmtCol.setBoolean(8, col.isUnico());

                            // Manejo de valor_defecto (String)
                            if (col.getValorDefecto() != null) {
                                stmtCol.setString(9, col.getValorDefecto().toString());
                            } else {
                                stmtCol.setNull(9, Types.VARCHAR);
                            }
                            stmtCol.addBatch();
                        }
                        stmtCol.executeBatch();
                    }
                }

                // Insertar metadatos de cada relación
                if (tabla.getRelaciones() != null && tablaId > 0) {
                    try (PreparedStatement stmtRel = conn.prepareStatement(sqlRelacion)) {
                        for (RelacionConfig rel : tabla.getRelaciones()) {
                            stmtRel.setString(1, rel.getNombreRelacion());
                            stmtRel.setLong(2, tablaId);
                            stmtRel.setString(3, rel.getFkColumna());
                            stmtRel.setString(4, rel.getTablaDestino());
                            stmtRel.setString(5, rel.getCardinalidad());
                            stmtRel.addBatch();
                        }
                        stmtRel.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al guardar configuración de '" + tabla.getNombreLogico() + "'.", e);
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

        try (Connection conn = ConexionMysql.getConexion("erp_sistema"); PreparedStatement stmtTabla = conn.prepareStatement(sqlTabla)) {

            stmtTabla.setString(1, nombreLogico);

            try (ResultSet rsTabla = stmtTabla.executeQuery()) {
                if (rsTabla.next()) {
                    TablaConfig tabla = new TablaConfig();
                    tabla.setId(rsTabla.getLong("id"));
                    tabla.setNombreDb(rsTabla.getString("nombre_db"));
                    tabla.setNombreLogico(rsTabla.getString("nombre_logico"));
                    tabla.setNombreAmigable(rsTabla.getString("nombre_amigable"));

                    // Recuperar columnas y relaciones
                    tabla.setColumnas(getColumnasPorTablaId(conn, tabla.getId()));
                    tabla.setRelaciones(getRelacionesPorTablaId(conn, tabla.getId(), tabla.getNombreLogico()));
                    return tabla;
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al recuperar configuración de '" + nombreLogico + "'.", e);
        }
        return null;
    }

    /**
     * Recupera la lista de columnas a partir del ID de la tabla a la que
     * pertenecen
     *
     * @param conn Conexión con MySQL
     * @param tablaId ID de la tabla
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
                    col.setNullable(rs.getBoolean("nullable"));
                    col.setContrasena(rs.getBoolean("es_contrasena"));
                    col.setVisible(rs.getBoolean("visible"));
                    col.setAutoincremental(rs.getBoolean("autoincremental"));
                    col.setUnico(rs.getBoolean("unico"));
                    col.setValorDefecto(rs.getString("valor_defecto"));
                    columnas.add(col);
                }
            }
        }
        return columnas;
    }

    private List<RelacionConfig> getRelacionesPorTablaId(Connection conn, Long tablaId, String nombreTablaOrigen) throws SQLException {
        List<RelacionConfig> relaciones = new ArrayList<>();
        // JOIN para obtener el nombre de la tabla_origen
        String sql = "SELECT nombre, fk_columna, tabla_destino, cardinalidad FROM erp_meta_relaciones WHERE tabla_origen = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, tablaId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RelacionConfig r = new RelacionConfig();
                    r.setNombreRelacion(rs.getString("nombre"));
                    r.setFkColumna(rs.getString("fk_columna")); // Ya es el nombre "id_persona"
                    r.setTablaDestino(rs.getString("tabla_destino")); // Ya es "personas"
                    r.setCardinalidad(rs.getString("cardinalidad"));
                    r.setTablaOrigen(nombreTablaOrigen);
                    relaciones.add(r);
                }
            }
        }
        return relaciones;
    }

    /**
     * Busca qué tablas tienen una relación apuntando hacia la tabla
     * especificada. 
     * 
     * 
     * @param nombreTablaDestino
     * @return 
     */
    public List<RelacionConfig> getRelacionesHijas(String nombreTablaDestino) {
        List<RelacionConfig> relacionesHijas = new ArrayList<>();
        String sql = "SELECT r.*, t.nombre_logico as tabla_origen_nombre "
                + "FROM erp_meta_relaciones r "
                + "JOIN erp_meta_tablas t ON r.tabla_origen = t.id "
                + "WHERE r.tabla_destino = ?";

        try (Connection conn = ConexionMysql.getConexion("erp_sistema"); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nombreTablaDestino);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RelacionConfig r = new RelacionConfig();
                    r.setNombreRelacion(rs.getString("nombre"));
                    r.setTablaOrigen(rs.getString("tabla_origen_nombre"));
                    r.setFkColumna(rs.getString("fk_columna"));
                    r.setTablaDestino(nombreTablaDestino);
                    r.setCardinalidad(rs.getString("cardinalidad"));
                    relacionesHijas.add(r);
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al recuperar relaciones hijas de " + nombreTablaDestino, e);
        }
        return relacionesHijas;
    }

    /**
     * Comprueba si existe configuración para una tabla.
     *
     * @param nombreLogico Nombre de la tabla en MySQL
     * @return true si la configuración existe; false en caso contrario
     */
    public boolean existeTabla(String nombreLogico) {
        return getConfiguracion(nombreLogico) != null;
    }

    /**
     * Elimina la configuración de una tabla (por CASCADE se eliminan las
     * columnas y relaciones).
     *
     * @param nombreLogico Nombre de la tabla en MySQL
     */
    public void eliminarConfiguracion(String nombreLogico) {
        String sql = "DELETE FROM `erp_meta_tablas` WHERE nombre_logico = ?";
        try (Connection conn = ConexionMysql.getConexion("erp_sistema"); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nombreLogico);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new BaseDatosException("Error al eliminar configuración de '" + nombreLogico + "'.", e);
        }
    }

    /**
     * Devuelve todas las tablas configuradas.
     *
     * @return Lista con todas las tablas
     */
    public List<TablaConfig> getTodas() {
        List<TablaConfig> tablas = new ArrayList<>();
        String sqlTabla = "SELECT * FROM `erp_meta_tablas`";

        try (Connection conn = ConexionMysql.getConexion("erp_sistema"); PreparedStatement stmtTabla = conn.prepareStatement(sqlTabla); ResultSet rsTabla = stmtTabla.executeQuery()) {

            while (rsTabla.next()) {
                TablaConfig tabla = new TablaConfig();
                tabla.setId(rsTabla.getLong("id"));
                tabla.setNombreDb(rsTabla.getString("nombre_db"));
                tabla.setNombreLogico(rsTabla.getString("nombre_logico"));
                tabla.setNombreAmigable(rsTabla.getString("nombre_amigable"));
                tabla.setColumnas(getColumnasPorTablaId(conn, tabla.getId()));
                tabla.setRelaciones(getRelacionesPorTablaId(conn, tabla.getId(), tabla.getNombreLogico()));
                tablas.add(tabla);
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al listar todas las tablas.", e);
        }
        return tablas;
    }
}
