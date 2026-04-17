/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dao;

import apigenerica.CampoConfig;
import apigenerica.config.Conexion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Grupo1 
 * Operaciones CRUD para las tablas de metadatos:
 * sys_tablas y sys_campos
 */
public class MetaDao {

    public void guardarConfiguracion(String nombreLogico, String nombreAmigable, List<CampoConfig> conf) throws SQLException {
        try (Connection conn = Conexion.getConexion()) {
            conn.setAutoCommit(false); // Iniciar transacción
            try {
                // Insertar metadatos
                long tablaId = insertarTabla(conn, nombreLogico, nombreAmigable);
                // Insertar campos
                for (CampoConfig campo : conf) {
                    insertarCampo(conn, tablaId, campo);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private long insertarTabla(Connection conn, String nombreLogico, String nombreAmigable) throws SQLException {
        String sql = "INSERT INTO sys_tablas (nombre_logico, nombre_amigable) VALUES (?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nombreLogico);
            stmt.setString(2, nombreAmigable);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
            throw new SQLException("No se pudo obtener el ID de la nueva tabla.");
        }
    }
    
    private void insertarCampo(Connection conn, long tablaId, CampoConfig conf) throws SQLException {
        String sql = "INSERT INTO sys_campos (tabla_id, nombre_col, tipo_dato, "
                + "es_pk, es_nullable, es_unique, valor_defecto) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, tablaId);
            stmt.setString(2, conf.getNombre());
            stmt.setString(3, conf.getTipo());
            stmt.setBoolean(4, conf.isPk());
            stmt.setBoolean(5, conf.isNullable());
            stmt.setBoolean(6, conf.isUnico());
            stmt.setString(7, conf.getValorDefecto());
            stmt.setBoolean(8, conf.isContrasena());
            stmt.setBoolean(9, conf.isVisible());
            stmt.setBoolean(10, conf.isAutoincremental());
            stmt.setString(11, conf.getReferenciaTabla());
            stmt.setString(12, conf.getReferenciaCampo());
            stmt.executeUpdate();
        }
    }
    
    public List<CampoConfig> getConfiguracion(String nombreLogico) throws SQLException {
        List<CampoConfig> campos = new ArrayList<>();
        String sql = "SELECT c.* FROM sys_campos c " +
                    "JOIN sys_tablas t ON c.tabla_id = t.id " +
                    "WHERE t.nombre_logico = ?";

        try (Connection conn = Conexion.getConexion();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nombreLogico);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    CampoConfig campo = new CampoConfig();
                    campo.setNombre(rs.getString("nombre_col"));
                    campo.setTipo(rs.getString("tipo_dato"));
                    campo.setPk(rs.getBoolean("es_pk"));
                    campo.setNullable(rs.getBoolean("es_nullable"));
                    campo.setUnico(rs.getBoolean("es_unique"));
                    campo.setValorDefecto(rs.getString("valor_defecto"));
                    campo.setContrasena(rs.getBoolean("es_contrasena"));
                    campo.setVisible(rs.getBoolean("es_visible"));
                    campo.setAutoincremental(rs.getBoolean("es_autoincremental"));
                    campo.setReferenciaTabla(rs.getString("referencia_tabla"));
                    campo.setReferenciaCampo(rs.getString("referencia_campo"));
                    campos.add(campo);
                }
            }
        return campos;
    }
    
    public boolean existeTabla(String nombreLogico) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sys_tablas WHERE nombre_logico = ?";
        try (Connection conn = Conexion.getConexion();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, nombreLogico);
            ResultSet resultSet = stmt.executeQuery();
            // Existe si count es 1 o mayor
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
            // No existe
            return false;
        }
    }
    
    public void eliminarConfiguracion(String nombreLogico) throws SQLException {
        String sql = "DELETE t, c FROM sys_tablas t "
                + "JOIN sys_campos c ON c.tabla_id = t.id "
                + "WHERE t.nombre_logico = ?";
        try (Connection conn = Conexion.getConexion(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nombreLogico);
            stmt.executeUpdate();
        }
    }
}