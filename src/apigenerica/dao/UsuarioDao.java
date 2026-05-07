/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dao;

import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author Grupo1
 */
public class UsuarioDao {

    /**
     * Obtener rol del usuario especificado
     *
     * @param id ID del usuario
     * @return Rol obtenido
     */
    public String obtenerRol(Long id) {
        String sql = "SELECT rol FROM erp_users WHERE id = ? AND activo = 1";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("rol");
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener el rol de usuario.", e);
        }
        return null;
    }

    /**
     *
     */
    public void obtenerHash(String email) {
        String sql = "SELECT id, contrasena, rol FROM `erp_users` WHERE `email` = ? AND `activo` = 1";
        
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Obtener hash de la contraseña de la base de datos
                    String hashGuardado = rs.getString("contrasena");
                }
            } catch (SQLException e) {
                throw new BaseDatosException("Error al obtener el rol de usuario.", e);
            }
            return null;
        }
