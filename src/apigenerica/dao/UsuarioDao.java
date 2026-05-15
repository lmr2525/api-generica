/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dao;

import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.model.EntidadDinamica;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Operaciones CRUD para la tabla de usuarios de la aplicación
 * erp_users, almacenada en MySQL
 * @author Grupo1
 */
public class UsuarioDao {

    /**
     * Consulta la base de datos para obtener rol de un usuario
     * de la aplicación a partir de su id
     *
     * @param id ID del usuario
     * @return Rol obtenido
     */
    public String obtenerRol(Long id) {
        String sql = "SELECT `rol` FROM `erp_users` WHERE `id` = ? AND `activo` = 1";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("rol");
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener el rol de la base de datos.", e);
        }
        return null;
    }

    /**
     * Consulta la base de datos para obtener el id, rol y hash de la contraseña
     * de un usuario de la aplicación a partir de su email
     * 
     * @param email Email del usuario
     * @return Id, rol y hash de la contraseña del usuario especificado
     */
    public EntidadDinamica obtenerDatosLogin(String email) {
        String sql = "SELECT id, rol, contrasena FROM `erp_users` WHERE `email` = ? AND `activo` = 1";
        
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); 
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    EntidadDinamica usuario = new EntidadDinamica();
                    usuario.setId(rs.getLong("id"));
                    usuario.set("rol", rs.getString("rol"));
                    usuario.set("hash", rs.getString("contrasena"));
                    return usuario;
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener el login de la base de datos.", e);
        }
        return null;
    }
    
    public EntidadDinamica obtenerPorId(Long id) {
        String sql = "SELECT id, email, rol, activo FROM `erp_users` WHERE id = ?";
        
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EntidadDinamica usuario = new EntidadDinamica();
                    usuario.setId(rs.getLong("id"));
                    usuario.set("email", rs.getString("email"));
                    usuario.set("rol", rs.getString("rol"));
                    usuario.set("activo", rs.getInt("activo"));
                    return usuario;
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener el usuario por ID.", e);
        }
        return null;
    }
    
    public long crearUsuario(String email, String hash, String rol) {
        String sql = "INSERT INTO `erp_users` (email, hash, rol) VALUES (?, ?, ?)";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, email);
            ps.setString(2, hash);
            ps.setString(3, rol);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
            return 0;
        } catch (SQLException e) {
            throw new BaseDatosException("Error al registrar usuario (posible email duplicado).", e);
        }
    }

    public void actualizarUsuario(Long id, String email, String rol, int activo) {
        String sql = "UPDATE `erp_users` SET email = ?, rol = ?, activo = ? WHERE id = ?";
        
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, email);
            ps.setString(2, rol);
            ps.setInt(3, activo);
            ps.setLong(4, id);
            
            if (ps.executeUpdate() == 0) {
                throw new RecursoNoEncontradoException("Usuario no encontrado.");
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al actualizar usuario.", e);
        }
    }

    public void eliminarUsuario(Long id) {
        String sql = "DELETE FROM `erp_users` WHERE id = ?";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, id);
            if (ps.executeUpdate() == 0) {
                throw new RecursoNoEncontradoException("Usuario no encontrado.");
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al eliminar usuario.", e);
        }
    }
}
