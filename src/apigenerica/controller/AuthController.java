package apigenerica.controller;

import apigenerica.model.ApiRespuesta;
import apigenerica.dao.UsuarioDao;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.EntidadDinamica;
import apigenerica.service.JwtService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;

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
     * Inicia sesión y genera JWT Access y Refresh
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

        EntidadDinamica login = usuarioDao.obtenerDatosLogin(email);

        // Comparar hash de la contraseña introducida con el de la base de datos
        if (login != null && BCrypt.checkpw(password, (String) login.get("hash"))) {
            // Contraseña correcta: Extraer los datos necesarios
            Long usuarioId = login.getId();
            String rol = (String) login.get("rol");
            // Generar JWT
            Map<String, String> respuesta = jwtService.insertarTokensRespuesta(usuarioId, rol);
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok(respuesta));
        } else {
            // Contraseña incorrecta o no se encontró el email en la base de datos
            ctx.status(HttpCode.UNAUTHORIZED).json(ApiRespuesta.error("Credenciales incorrectas."));
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

    /**
     * Registrar cuenta de usuario en la base de datos
     *
     * @param ctx
     */
    public void registrar(Context ctx) {
        Map<String, String> datosUsuario = ctx.bodyAsClass(Map.class);
        String email = datosUsuario.get("email");
        String password = datosUsuario.get("password");
        String rol = datosUsuario.get("rol");

        if (email == null || password == null || rol == null) {
            throw new ValidacionException("Todos los campos (email, password, rol) son obligatorios.");
        }

        // Encriptar la contraseña
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());

        // Guardar en DB
        long id = usuarioDao.crearUsuario(email, passwordHash, rol);

        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Usuario registrado con ID: " + id));
    }

    public void modificarUsuario(Context ctx) {
        Long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, String> datos = ctx.bodyAsClass(Map.class);

        // Buscar el usuario actual
        EntidadDinamica usuarioActual = usuarioDao.obtenerPorId(id);
        if (usuarioActual == null) {
            throw new RecursoNoEncontradoException("Usuario no encontrado.");
        }

        // Si el dato viene en el body, se utiliza; si no, se utiliza el de la db
        String email = datos.getOrDefault("email", (String) usuarioActual.get("email"));
        String rol = datos.getOrDefault("rol", (String) usuarioActual.get("rol"));
        String activoStr = datos.getOrDefault("activo", String.valueOf(usuarioActual.get("activo")));

        // Actualizar datos
        usuarioDao.actualizarUsuario(id, email, rol, Integer.parseInt(activoStr));
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Usuario actualizado."));
    }

    /**
     * Borrar cuenta de la base de datos
     *
     * @param ctx
     */
    public void eliminar(Context ctx) {
        Long id = ctx.pathParamAsClass("id", Long.class).get();

        usuarioDao.eliminarUsuario(id);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Usuario eliminado."));
    }
}
