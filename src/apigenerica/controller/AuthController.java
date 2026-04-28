package apigenerica.controller;

import apigenerica.config.ConexionMysql;
import apigenerica.dao.BaseDao;
import apigenerica.model.ApiRespuesta;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador para la autenticación de usuarios en el ERP.
 * @author Grupo1
 */
public class AuthController {

    private final BaseDao baseDao;

    public AuthController(BaseDao baseDao) {
        this.baseDao = baseDao;
    }

    /**
     * Verifica las credenciales de un empleado.
     * 
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void login(Context ctx) {
        Map<String, String> credenciales = ctx.bodyAsClass(Map.class);
        String email = credenciales.get("email");
        String password = credenciales.get("password");

        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            ctx.status(400).json(ApiRespuesta.error("Email y contraseña son requeridos."));
            return;
        }

        String sql = "SELECT * FROM `erp_users` WHERE `Email` = ? AND `Contrasena` = ?";
        
        try (Connection conn = ConexionMysql.getConexion("prueba"); // Asumiendo que empleados está en 'prueba'
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            stmt.setString(2, password);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> empleado = new LinkedHashMap<>();
                    empleado.put("id", rs.getInt("ID"));
                    empleado.put("nombre", rs.getString("Nombre"));
                    empleado.put("apellidos", rs.getString("Apellidos"));
                    empleado.put("email", rs.getString("Email"));
                    empleado.put("telefono", rs.getString("Telefono"));
                    
                    ctx.json(ApiRespuesta.ok(empleado));
                } else {
                    ctx.status(401).json(ApiRespuesta.error("Credenciales incorrectas."));
                }
            }
        } catch (SQLException e) {
            // Si la tabla no existe (no instalado), retornamos 401 simulando error
            System.err.println("Error login: " + e.getMessage());
            ctx.status(401).json(ApiRespuesta.error("Error al autenticar o tabla no existe."));
        }
    }
}
