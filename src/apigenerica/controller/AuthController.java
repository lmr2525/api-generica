package apigenerica.controller;

import apigenerica.config.ConexionMysql;
import apigenerica.model.ApiRespuesta;
import apigenerica.dao.UsuarioDao;
import apigenerica.service.JwtService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Controlador para la autenticación de usuarios en el ERP.
 *
 * @author Grupo1
 */
public class AuthController {

    private final JwtService jwtService;
    private final UsuarioDao usuarioDao;

    public AuthController(JwtService jwtService, UsuarioDao authService) {
        this.jwtService = jwtService;
        this.usuarioDao = authService;
    }

    /**
     * Verifica las body de un empleado.
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void login(Context ctx) {
        Map<String, String> credenciales = ctx.bodyAsClass(Map.class);
        String email = credenciales.get("email");
        String password = credenciales.get("password");

        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            ctx.status(HttpCode.BAD_REQUEST).json(ApiRespuesta.error("Email y contraseña son requeridos."));
            return;
        }

        String hashGuardado = usuarioDao.obtenerHash();
        String sql = "SELECT id, contrasena, rol FROM `erp_users` WHERE `email` = ? AND `activo` = 1";

        try (Connection conn = ConexionMysql.getConexion("erp_sistema"); PreparedStatement stmt = conn.prepareStatement(sql)) {
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
                        // Generar JWT
                        String accessToken = jwtService.generarAccessToken(usuarioId, rol);
                        String refreshToken = jwtService.generarRefreshToken(usuarioId);
                        Map<String, String> respuesta = new HashMap<>();
                        respuesta.put("access_token", accessToken);
                        respuesta.put("refresh_token", refreshToken);
                        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(respuesta));
                    } else {
                        // Contraseña incorrecta
                        ctx.status(HttpCode.UNAUTHORIZED).json(ApiRespuesta.error("Credenciales incorrectas."));
                    }
                } else {
                    // Si el usuario (email) no se encuentra en la base de datos
                    ctx.status(HttpCode.UNAUTHORIZED).json(ApiRespuesta.error("Credenciales incorrectas."));
                }
            }
        } catch (SQLException e) {
            // Si la tabla no existe (no instalado)
            System.err.println("Error login: " + e.getMessage());
            ctx.status(HttpCode.INTERNAL_SERVER_ERROR).json(ApiRespuesta.error("Error interno al autenticar."));
        }
    }

    /**
     * Permite obtener un nuevo Access Token
     *
     * @param ctx Contexto de la petición HTTP
     */
    @SuppressWarnings("unchecked")
    public void refresh(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String refreshToken = body.get("refreshToken");

            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                ctx.status(HttpCode.BAD_REQUEST).json(ApiRespuesta.error("Refresh token requerido."));
                return;
            }
            // Verificar si el token es válido
            DecodedJWT jwt = jwtService.verificarToken(refreshToken);

            String tipo = jwt.getClaim("tipo").asString();
            if (!"refresh".equals(tipo)) {
                ctx.status(HttpCode.UNAUTHORIZED).json(ApiRespuesta.error("Token inválido."));
                return;
            }

            Long usuarioId = jwt.getClaim("id").asLong();

            // Buscar rol del usuario
            String rol = usuarioDao.obtenerRol(usuarioId);
            if (rol == null) {
                ctx.status(HttpCode.UNAUTHORIZED).json(ApiRespuesta.error("Usuario inválido o inactivo."));
                return;
            }

            // Generar nuevos JWT
            String nuevoAccess = jwtService.generarAccessToken(usuarioId, rol);
            String nuevoRefresh = jwtService.generarRefreshToken(usuarioId);

            Map<String, String> respuesta = new HashMap<>();
            respuesta.put("access_token", nuevoAccess);
            respuesta.put("refresh_token", nuevoRefresh);
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(respuesta));
        } catch (JWTVerificationException e) {
            ctx.status(401).json(ApiRespuesta.error("Refresh token expirado o inválido."));
        }
    }
}
