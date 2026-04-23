package apigenerica.config;

import apigenerica.controller.AuthController;
import apigenerica.controller.BaseController;
import apigenerica.controller.ConfigController;
import apigenerica.controller.ModuloController;
import apigenerica.dao.BaseDao;
import apigenerica.dao.MetaDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import apigenerica.service.MetaService;
import apigenerica.service.SqlService;
import apigenerica.service.ValidadorService;
import io.javalin.Javalin;

/**
 * Punto de entrada de la API genérica del ERP.
 * Inicializa conexiones, servicios, controladores y endpoints.
 *
 * @author Grupo1
 */
public class ApiGenerica {

    public static void main(String[] args) {
        // ── Inicializar conexión con MySQL ────────────────────────────
        ConexionMysql.inicializar();

        // ── Instanciar servicios (inyección de dependencias manual) ───
        ValidadorService validador = new ValidadorService();
        MetaDao metaDao = new MetaDao();
        MetaService metaService = new MetaService(metaDao, validador);
        SqlService sqlService = new SqlService(metaService, validador);

        // ── Instanciar controladores ─────────────────────────────────
        BaseDao baseDao = new BaseDao();
        BaseController baseCtrl = new BaseController(sqlService, validador, metaService, baseDao, metaDao);
        AuthController authCtrl = new AuthController(baseDao);
        ConfigController configCtrl = new ConfigController();
        ModuloController moduloCtrl = new ModuloController();

        // ── Crear servidor Javalin ───────────────────────────────────
        Javalin app = Javalin.create(config -> {
            // Habilitar CORS para que el frontend pueda consumir la API
            config.enableCorsForAllOrigins();
            // Logging de peticiones
            config.enableDevLogging();
        }).start(7000);

        // ── Endpoints de metadatos (creación de tablas) ──────────────
        app.post("/api/metadata", ctx -> baseCtrl.crearTabla(ctx));

        // ── Endpoints de autenticación ───────────────────────────────
        app.post("/api/auth/login", ctx -> authCtrl.login(ctx));

        // ── Endpoints de configuración ERP ───────────────────────────
        app.get("/api/erp/config", ctx -> configCtrl.getConfig(ctx));
        app.put("/api/erp/config", ctx -> configCtrl.updateConfig(ctx));

        // ── Endpoints de módulos ─────────────────────────────────────
        app.get("/api/erp/modulos", ctx -> moduloCtrl.getAll(ctx));
        app.post("/api/erp/modulos", ctx -> moduloCtrl.create(ctx));
        app.delete("/api/erp/modulos/{id}", ctx -> moduloCtrl.delete(ctx));

        // ── Endpoints CRUD genéricos (cualquier tabla) ───────────────
        // IMPORTANTE: Van al final para no interceptar las rutas específicas
        app.get("/api/{tabla}", ctx -> baseCtrl.fetchTodo(ctx));
        app.get("/api/{tabla}/{id}", ctx -> baseCtrl.fetchPorId(ctx));
        app.post("/api/{tabla}", ctx -> baseCtrl.create(ctx));
        app.put("/api/{tabla}/{id}", ctx -> baseCtrl.update(ctx));
        app.delete("/api/{tabla}/{id}", ctx -> baseCtrl.delete(ctx));

        // ── Manejo global de excepciones ─────────────────────────────
        app.exception(ValidacionException.class, (e, ctx) ->
            ctx.status(400).json(ApiRespuesta.error(e.getMessage())));

        app.exception(RecursoNoEncontradoException.class, (e, ctx) ->
            ctx.status(404).json(ApiRespuesta.error(e.getMessage())));

        app.exception(BaseDatosException.class, (e, ctx) ->
            ctx.status(500).json(ApiRespuesta.error(e.getMessage())));

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
}
