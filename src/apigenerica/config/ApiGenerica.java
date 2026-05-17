package apigenerica.config;

import apigenerica.controller.AuthController;
import apigenerica.controller.BaseController;
import apigenerica.controller.ConfigController;
import apigenerica.controller.MetaController;
import apigenerica.controller.ModuloController;
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
import apigenerica.service.MetaService;
import apigenerica.service.OrderService;
import apigenerica.service.PermisoService;
import apigenerica.service.SqlService;
import apigenerica.service.ValidadorService;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;

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

        // ── Instanciar servicios (inyección de dependencias manual) ───
        MetaDao metaDao = new MetaDao();
        BaseDao baseDao = new BaseDao();
        ValidadorService validador = new ValidadorService(metaDao);
        SqlService sqlService = new SqlService(validador);
        FicheroService ficheroService = new FicheroService();
        MetaService metaService = new MetaService(metaDao, validador, sqlService, ficheroService);
        OrderService orderService = new OrderService(metaDao);
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

        // ── Crear servidor Javalin ───────────────────────────────────
        Javalin app = Javalin.create(config -> {
            // Habilitar CORS para que el frontend pueda consumir la API
            config.enableCorsForAllOrigins();
            // Logging de peticiones
            config.enableDevLogging();
        }).start(7000);

        // Verificar Token (excepto login/store)
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            if (path.startsWith("/api/auth/login") || path.startsWith("/api/store") || path.startsWith("/api/auth/signup")) {
                return;
            }

            // Verificar JWT y setear atributos
            DecodedJWT jwt = jwtService.verificarToken(getToken(ctx));
            ctx.attribute("usuarioId", jwt.getClaim("id").asLong());
            ctx.attribute("rol", jwt.getClaim("rol").asInt());
        });

        // Filtro de datos para tablas dinámicas
        app.before("/api/data/{tabla}*", ctx -> {
            Integer rol = ctx.attribute("rol");
            String tabla = ctx.pathParam("tabla").toLowerCase();
            String metodo = ctx.method();

            if (!permisoService.verificar(rol, tabla, metodo)) {
                throw new ForbiddenResponse("No tienes permiso para " + metodo + " en la tabla " + tabla);
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
        app.get("/api/auth/usuarios/{id}", ctx -> authCtrl.obtenerUsuario(ctx));
        app.put("/api/auth/usuarios/{id}", ctx -> authCtrl.modificarUsuario(ctx));
        app.delete("/api/auth/usuarios/{id}", ctx -> authCtrl.eliminar(ctx));

        // ── Endpoints de configuración ERP ───────────────────────────
        app.get("/api/metadata/config", ctx -> configCtrl.getConfig(ctx));
        app.put("/api/metadata/config", ctx -> configCtrl.updateConfig(ctx));

        // ── Endpoints de módulos ─────────────────────────────────────
        app.get("/api/metadata/modulos", ctx -> moduloCtrl.getAll(ctx));
        app.post("/api/metadata/modulos", ctx -> moduloCtrl.create(ctx));
        app.delete("/api/metadata/modulos/{id}", ctx -> moduloCtrl.delete(ctx));

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
    
    private static String getToken(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new io.javalin.http.UnauthorizedResponse("Token no proporcionado o formato inválido");
        }
        return header.substring(7); // Quitar "Bearer "
    }
}
