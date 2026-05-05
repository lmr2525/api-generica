package apigenerica.controller;

import apigenerica.config.ConexionMysql;
import apigenerica.model.ApiRespuesta;
import apigenerica.service.JwtService;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Controlador para la autenticación de usuarios en el ERP.
 * @author Grupo1
 */
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
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

        String sql = "SELECT id, contrasena, rol FROM `erp_users` WHERE `email` = ? AND `activo` = 1";
        
        try (Connection conn = ConexionMysql.getConexion("erp_sistema");
            PreparedStatement stmt = conn.prepareStatement(sql)) { 
            stmt.setString(1, email);  
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Obtener hash de la contraseña de la base de datos
                    String hashGuardado = rs.getString("contrasena");
                    // Comparar hash de la contraseña introducida con el de la base de datos
                    if (BCrypt.checkpw(password, hashGuardado)) {
                        // Contraseña correcta: Extraer los datos necesarios
                        Long usuarioId = rs.getLong("id");
                        String rol = rs.getString("rol");

                        // Generar el JWT
                        String token = jwtService.generarToken(usuarioId, rol);
                        Map<String, String> respuesta = new HashMap<>();
                        respuesta.put("token", token);
                        ctx.status(200).json(ApiRespuesta.ok(respuesta));
                    } else {
                        // Contraseña incorrecta
                        ctx.status(401).json(ApiRespuesta.error("Credenciales incorrectas."));
                    }
                } else {
                    // Si el usuario (email) no se encuentra en la base de datos
                    ctx.status(401).json(ApiRespuesta.error("Credenciales incorrectas."));
                }
            }
        } catch (SQLException e) {
            // Si la tabla no existe (no instalado)
            System.err.println("Error login: " + e.getMessage());
            ctx.status(500).json(ApiRespuesta.error("Error interno al autenticar."));
        }
    }
}
