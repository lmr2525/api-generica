package apigenerica.config;

import apigenerica.controller.AuthController;
import apigenerica.controller.BaseController;
import apigenerica.controller.ConfigController;
import apigenerica.controller.FicheroController;
import apigenerica.controller.LogController;
import apigenerica.controller.MetaController;
import apigenerica.controller.ModuloController;
import apigenerica.controller.RolController;
import apigenerica.dao.BaseDao;
import apigenerica.dao.MetaDao;
import apigenerica.dao.RolDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.NoAutorizadoException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.dao.UsuarioDao;
import apigenerica.service.FicheroService;
import apigenerica.service.JwtService;
import apigenerica.service.LogService;
import apigenerica.service.MetaService;
import apigenerica.service.OrderService;
import apigenerica.service.PermisoService;
import apigenerica.service.ServicioCifrado;
import apigenerica.service.SqlService;
import apigenerica.service.ValidadorService;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UploadedFile;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Punto de entrada de la API genérica del ERP. Inicializa conexiones,
 * servicios, controladores y endpoints.
 *
 * @author Grupo1
 */
public class ApiGenerica {

    public static void main(String[] args) {
        // ── Inicializar conexión con MySQL ────────────────────────────
        ConexionMysql.inicializar();

        // ── Inicializar conexión con Paradox ──────────────────────────
        ConexionParadox.inicializar();

        // ── Inicializar motor de logs ─────────────────────────────────
        LogService.inicializar();

        // ── Instanciar servicios (inyección de dependencias manual) ───
        MetaDao metaDao = new MetaDao();
        BaseDao baseDao = new BaseDao();
        ValidadorService validador = new ValidadorService(metaDao);
        SqlService sqlService = new SqlService(validador);
        FicheroService ficheroService = new FicheroService();
        MetaService metaService = new MetaService(metaDao, validador, sqlService, ficheroService);
        OrderService orderService = new OrderService(metaDao);
        ServicioCifrado cifrado = new ServicioCifrado();
        JwtService jwtService = new JwtService();
        UsuarioDao authService = new UsuarioDao();
        RolDao rolDao = new RolDao();
        PermisoService permisoService = new PermisoService(rolDao);

        // ── Instanciar controladores ─────────────────────────────────
        BaseController baseCtrl = new BaseController(validador, metaService, baseDao, orderService, ficheroService);
        AuthController authCtrl = new AuthController(jwtService, authService);
        ConfigController configCtrl = new ConfigController();
        ModuloController moduloCtrl = new ModuloController();
        MetaController metaCtrl = new MetaController(metaService, validador, orderService, sqlService);
        RolController rolCtrl = new RolController(new RolDao());
        LogController logCtrl = new LogController();

        // Variable effectively-final necesaria para usar ficheroService dentro
        // de lambdas. Java exige que las variables capturadas en lambdas no se reasignen,
        // y ficheroService puede ser null si db4o falla, por eso se copia aquí.
        final FicheroService fs = ficheroService;
        final FicheroController ficheroCtrl = (fs != null) ? new FicheroController(fs) : null;

        // ── Crear servidor Javalin ───────────────────────────────────
        Javalin app = Javalin.create(config -> {
            // Habilitar CORS para que el frontend pueda consumir la API
            config.enableCorsForAllOrigins();
            // Logging de peticiones
            config.enableDevLogging();
            // Aumentar el límite de subida a 500 MB para soportar ficheros grandes
            config.maxRequestSize = 500_000_000L;
        }).start(7000);

        // Verificar Token
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            String method = ctx.method();

            // Permitir CORS (OPTIONS) y Rutas Públicas
            if ("OPTIONS".equalsIgnoreCase(method)) {
                return;
            }

            if (path.equals("/api/auth/login")
                    || path.equals("/api/auth/signup")
                    || path.equals("/api/auth/refresh")
                    || path.startsWith("/api/store")
                    || path.equals("/api/test")) {
                return; // Pasan directo
            }

            // 2. Extraer y Validar el Token
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new NoAutorizadoException("Token no proporcionado.", null);
            }

            String token = authHeader.replace("Bearer ", "");
            Integer rolId;
            try {
                DecodedJWT jwt = jwtService.verificarToken(token);
                rolId = jwt.getClaim("rol").asInt();
                ctx.attribute("usuarioId", jwt.getClaim("id").asLong());
                ctx.attribute("rolId", rolId);
            } catch (Exception e) {
                throw new NoAutorizadoException("Token inválido o expirado.", e);
            }

            // Mapear la URL a tabla
            String recursoLogico = mapearRutaARecurso(path);

            if (recursoLogico == null) {
                throw new NoAutorizadoException("Ruta no reconocida en el mapa de permisos.", null);
            }

            // Comprobar permisos
            if (!permisoService.verificar(rolId, recursoLogico, method)) {
                throw new ForbiddenResponse("No tienes permiso para ejecutar la acción '" + method + "' en el recurso '" + recursoLogico + "'.");
            }
        });

        // ── Endpoints de metadatos ──────────────
        // Crear tablas
        app.post("/api/metadata/tablas", ctx -> metaCtrl.crearTabla(ctx));
        // Obtener metadatos (lista de nombres) de todas las tablas
        app.get("/api/metadata/tablas", ctx -> metaCtrl.listarTablas(ctx));
        // Obtener los metadatos de una tabla
        app.get("/api/metadata/tablas/{tabla}", ctx -> metaCtrl.obtenerEstructuraTabla(ctx));
        // Eliminar una tabla
        app.delete("/api/metadata/tablas/{tabla}", ctx -> metaCtrl.eliminarTabla(ctx));
        // Añadir columnas a una tabla
        app.post("/api/metadata/tablas/{tabla}/columnas", ctx -> metaCtrl.agregarColumna(ctx));
        // Modificar las columnas de una tabla
        app.put("/api/metadata/tablas/{tabla}/columnas/{columna}", ctx -> metaCtrl.modificarColumna(ctx));
        // Renombrar una columna
        app.put("/api/metadata/tablas/{tabla}/columnas/{columna}/nombre", ctx -> metaCtrl.renombrarColumna(ctx));
        // Eliminar columnas de una tabla
        app.delete("/api/metadata/tablas/{tabla}/columnas/{columna}", ctx -> metaCtrl.eliminarColumna(ctx));

        // ── Endpoints de autenticación ───────────────────────────────
        app.post("/api/auth/login", ctx -> authCtrl.login(ctx));
        app.post("/api/auth/refresh", ctx -> authCtrl.refresh(ctx));
        app.post("/api/auth/signup", ctx -> authCtrl.registrar(ctx));
        app.get("/api/metadata/usuarios/{id}", ctx -> authCtrl.obtenerUsuario(ctx));
        app.put("/api/metadata/usuarios/{id}", ctx -> authCtrl.modificarUsuario(ctx));
        app.delete("/api/metadata/usuarios/{id}", ctx -> authCtrl.eliminar(ctx));

        // ── Endpoints de configuración ERP ───────────────────────────
        app.get("/api/metadata/config", ctx -> configCtrl.getConfig(ctx));
        app.put("/api/metadata/config", ctx -> configCtrl.updateConfig(ctx));

        // ── Endpoints de módulos ─────────────────────────────────────
        app.get("/api/metadata/modulos", ctx -> moduloCtrl.getAll(ctx));
        app.post("/api/metadata/modulos", ctx -> moduloCtrl.create(ctx));
        app.delete("/api/metadata/modulos/{id}", ctx -> moduloCtrl.delete(ctx));

        // ── Endpoints de roles ─────────────────────────────────────
        app.get("/api/metadata/roles", ctx -> moduloCtrl.getAll(ctx));
        app.post("/api/metadata/modulos", ctx -> moduloCtrl.create(ctx));
        app.delete("/api/metadata/modulos/{id}", ctx -> moduloCtrl.delete(ctx));

        // Endpoints de ficheros ─────────────────────────────────
        app.get("/test", ctx -> {
            java.nio.file.Path ruta = java.nio.file.Paths.get("test.html");
            if (java.nio.file.Files.exists(ruta)) {
                ctx.contentType("text/html; charset=UTF-8");
                ctx.result(new String(java.nio.file.Files.readAllBytes(ruta), java.nio.charset.StandardCharsets.UTF_8));
            } else {
                ctx.status(404).result("test.html no encontrado. Colocalo en la raiz del proyecto.");
            }
        });

        if (ficheroCtrl != null) {
            app.post("/api/ficheros/{tabla}", ctx -> ficheroCtrl.subir(ctx));
            app.get("/api/ficheros", ctx -> ficheroCtrl.listar(ctx));
            app.get("/api/ficheros/{uuid}/info", ctx -> ficheroCtrl.obtenerInfo(ctx));
            app.get("/api/ficheros/{uuid}/descargar", ctx -> ficheroCtrl.descargar(ctx));
            app.delete("/api/ficheros/{uuid}", ctx -> ficheroCtrl.eliminar(ctx));
        } else {
            // Si db4o falló al inicio y el servicio es nulo, devolvemos 503 Service Unavailable en todas sus rutas
            io.javalin.http.Handler fallback = ctx -> ctx.status(503).json(ApiRespuesta.error("Servicio de ficheros no disponible."));
            app.post("/api/ficheros/{tabla}", fallback);
            app.get("/api/ficheros", fallback);
            app.get("/api/ficheros/{uuid}/info", fallback);
            app.get("/api/ficheros/{uuid}/descargar", fallback);
            app.delete("/api/ficheros/{uuid}", fallback);
        }
        
        // ── Endpoints CRUD transaccionales ─────────────────────────────────────
        app.post("/api/data/batch/insert", ctx -> baseCtrl.insertTransaccional(ctx));
        app.put("/api/data/batch/update", ctx -> baseCtrl.updateTransaccional(ctx));
        app.delete("/api/data/batch/delete", ctx -> baseCtrl.deleteTransaccional(ctx));

        // ── Endpoints CRUD genéricos (cualquier tabla) ───────────────
        // IMPORTANTE: Van al final para no interceptar las rutas específicas
        app.get("/api/data/{tabla}", ctx -> baseCtrl.fetchTodo(ctx));
        app.get("/api/data/{tabla}/{id}", ctx -> baseCtrl.fetchPorId(ctx));
        app.post("/api/data/{tabla}", ctx -> baseCtrl.insert(ctx));
        app.put("/api/data/{tabla}/{id}", ctx -> baseCtrl.update(ctx));
        app.delete("/api/data/{tabla}/{id}", ctx -> baseCtrl.delete(ctx));

        // Catálogo público 
        app.get("/api/store", ctx -> baseCtrl.fetchTodo(ctx));

        // ── Manejo global de excepciones ─────────────────────────────
        app.exception(ValidacionException.class, (e, ctx)
                -> ctx.status(400).json(ApiRespuesta.error(e.getMessage())));

        app.exception(RecursoNoEncontradoException.class, (e, ctx)
                -> ctx.status(404).json(ApiRespuesta.error(e.getMessage())));

        app.exception(NoAutorizadoException.class, (e, ctx)
                -> ctx.status(401).json(ApiRespuesta.error(e.getMessage())));

        app.exception(BaseDatosException.class, (e, ctx)
                -> ctx.status(500).json(ApiRespuesta.error(e.getMessage())));

        app.exception(Exception.class, (e, ctx) -> {
            System.err.println("Error no controlado: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).json(ApiRespuesta.error("Error interno del servidor."));
        });

        // ── Shutdown hook ────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConexionMysql.cerrar();
            System.out.println("[API] Conexiones cerradas correctamente.");
        }));

        System.out.println("===========================================");
        System.out.println("  API ERP Genérica arrancada en :7000");
        System.out.println("===========================================");
    }

    private static String mapearRutaARecurso(String path) {
        // Si es una tabla dinámica
        if (path.startsWith("/api/data/")) {
            String[] partes = path.split("/");
            // Extraer el nombre de la tabla
            return partes.length >= 4 ? partes[3].toLowerCase() : null;
        }

        // Si es una tabla del sistema
        if (path.startsWith("/api/metadata/tablas") || path.contains("/columnas")) {
            return "erp_meta_tablas";
        }
        if (path.startsWith("/api/auth/usuarios")) {
            return "erp_usuarios";
        }
        if (path.startsWith("/api/metadata/config")) {
            return "erp_config";
        }
        if (path.startsWith("/api/metadata/modulos")) {
            return "erp_modulos";
        }
        if (path.contains("/roles")) {
            return "erp_roles";
        }

        return null; // Ruta desconocida
    }
}
