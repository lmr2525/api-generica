/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dao;

import apigenerica.model.RolConfig;
import apigenerica.config.AppConfig;
import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.PermisoConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Grupo1
 */
public class RolDao {

    /**
     * Obtiene los permisos de un rol sobre una tabla
     *
     * @param rol Rol cuyos permisos se desean comprobar
     * @param tabla Nombre de la tabla en MySQL
     * @return
     */
    public PermisoConfig getPermisos(int rol, String tabla) {
        String sql = "SELECT p.puede_leer, p.puede_escribir, p.puede_editar, p.puede_borrar "
                + "FROM erp_permisos p "
                + "JOIN erp_meta_tablas t ON p.tabla_id = t.id "
                + "WHERE p.rol_id = ? AND t.nombre_logico = ?";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, rol);
            ps.setString(2, tabla);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PermisoConfig p = new PermisoConfig();
                    p.setRol(String.valueOf(rol));
                    p.setTabla(tabla);
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

    /**
     * Devuelve los permisos de un rol sobre todas las tablas/secciones.
     *
     * @param rol ID del rol
     * @return
     */
    public List<PermisoConfig> getTodosPermisos(int rol) {
        String sql = "SELECT t.nombre_logico, p.puede_leer, p.puede_escribir, p.puede_editar, p.puede_borrar "
                + "FROM erp_permisos p "
                + "JOIN erp_meta_tablas t ON p.tabla_id = t.id "
                + "WHERE p.rol_id = ?";
        List<PermisoConfig> permisos = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, rol); // <-- Cambiado a setInt

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PermisoConfig p = new PermisoConfig();
                    p.setRol(String.valueOf(rol));
                    p.setTabla(rs.getString("nombre_logico"));
                    p.setPuedeLeer(rs.getBoolean("puede_leer"));
                    p.setPuedeEscribir(rs.getBoolean("puede_escribir"));
                    p.setPuedeEditar(rs.getBoolean("puede_editar"));
                    p.setPuedeBorrar(rs.getBoolean("puede_borrar"));
                    permisos.add(p);
                }
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al obtener permisos del rol.", e);
        }
        return permisos;
    }

    /**
     * Devuelve la lista de todos los roles definidos.
     *
     * @return
     */
    public List<RolConfig> listarRoles() {
        String sql = "SELECT id, nombre, descripcion FROM erp_roles ORDER BY nombre";
        List<RolConfig> roles = new ArrayList<>();
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                roles.add(new RolConfig(
                        rs.getLong("id"),
                        rs.getString("nombre"),
                        rs.getString("descripcion")
                ));
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al listar roles.", e);
        }
        return roles;
    }

    /**
     * Crea un nuevo rol. Lanza excepción si ya existe.
     *
     * @param nombre
     * @param descripcion
     */
    public void crearRol(String nombre, String descripcion) {
        String sql = "INSERT INTO erp_roles (nombre, descripcion) VALUES (?, ?)";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nombre);
            stmt.setString(2, descripcion);
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // Duplicate key
                throw new BaseDatosException("Ya existe un rol con el nombre '" + nombre + "'.", e);
            }
            throw new BaseDatosException("Error al crear el rol.", e);
        }
    }

    /**
     * Elimina un rol y todos sus permisos asociados.
     *
     * @param nombre
     * @return
     */
    public boolean eliminarRol(String nombre) {
        String sql = "DELETE FROM erp_roles WHERE nombre = ?";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, nombre);
            int filas = stmt.executeUpdate();
            return filas > 0;

        } catch (SQLException e) {
            throw new BaseDatosException("Error al eliminar el rol.", e);
        }
    }

    /**
     * Comprueba si un rol existe por nombre.
     *
     * @param nombre
     * @return
     */
    public boolean existeRol(String nombre) {
        String sql = "SELECT 1 FROM erp_roles WHERE nombre = ?";
        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nombre);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new BaseDatosException("Error al verificar el rol.", e);
        }
    }

    /**
     * Guarda o actualiza los permisos de un rol sobre una tabla. Utiliza ON
     * DUPLICATE KEY UPDATE para sincronizar los metadatos.
     *
     * @param rolId ID numérico del rol
     * @param tabla Nombre lógico de la tabla (ej: "productos_test")
     * @param permiso Objeto con los booleanos de permisos
     */
    public void guardarPermisos(int rolId, String tabla, PermisoConfig permiso) {
        // La consulta busca el ID de la tabla internamente para no obligarte a pasarlo por parámetro
        String sql = "INSERT INTO erp_permisos (rol_id, tabla_id, puede_leer, puede_escribir, puede_editar, puede_borrar) "
                + "VALUES (?, (SELECT id FROM erp_meta_tablas WHERE nombre_logico = ?), ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "puede_leer = VALUES(puede_leer), "
                + "puede_escribir = VALUES(puede_escribir), "
                + "puede_editar = VALUES(puede_editar), "
                + "puede_borrar = VALUES(puede_borrar)";

        try (Connection conn = ConexionMysql.getConexion(AppConfig.DB_SISTEMA); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, rolId);
            stmt.setString(2, tabla);
            stmt.setBoolean(3, permiso.isPuedeLeer());
            stmt.setBoolean(4, permiso.isPuedeEscribir());
            stmt.setBoolean(5, permiso.isPuedeEditar());
            stmt.setBoolean(6, permiso.isPuedeBorrar());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new BaseDatosException("Error al guardar los permisos para la tabla: " + tabla, e);
        }
    }
}
