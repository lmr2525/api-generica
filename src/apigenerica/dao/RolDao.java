/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dao;

import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.PermisoTabla;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author Grupo1
 */
public class RolDao {

    public PermisoTabla obtenerPermisos(int rol, String tabla) {
        String sql = "SELECT p.puede_leer, p.puede_escribir, p.puede_editar, p.puede_borrar "
                + "FROM erp_permisos p "
                + "JOIN erp_meta_tablas t ON p.tabla_id = t.id "
                + "WHERE p.rol_id = ? AND t.nombre_logico = ?";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, rol);
            ps.setString(2, tabla);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PermisoTabla p = new PermisoTabla();
                    p.setPuedeLeer(rs.getBoolean("puede_leer"));
                    p.setPuedeEscribir(rs.getBoolean("puede_escribir"));
                    p.setPuedeEditar(rs.getBoolean("puede_editar"));
                    p.setPuedeBorrar(rs.getBoolean("puede_borrar"));
                    return p;
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al consultar permisos", e);
        }
        return null; // Si se encuentra, no hay permiso
    }
}
