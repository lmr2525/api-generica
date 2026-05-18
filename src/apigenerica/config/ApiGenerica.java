package apigenerica.config;

import static apigenerica.config.ApiGenerica.TipoAcceso.CONFIGURAR;
import static apigenerica.config.ApiGenerica.TipoAcceso.DATO;
import static apigenerica.config.ApiGenerica.TipoAcceso.SISTEMA;
import apigenerica.controller.*;
import apigenerica.dao.*;
import apigenerica.excepciones.*;
import apigenerica.model.ApiRespuesta;
import apigenerica.service.*;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.Javalin;
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
        CifradoService cifrado = new CifradoService();
        JwtService jwtService = new JwtService();
        UsuarioDao authService = new UsuarioDao();
        RolDao rolDao = new RolDao();
        PermisoService permisoService = new PermisoService(rolDao);

        // ── Instanciar controladores ─────────────────────────────────
        BaseController baseCtrl = new BaseController(validador, metaService, baseDao, orderService, ficheroService);
        AuthController authCtrl = new AuthController(jwtService, authService);
        ConfigController configCtrl = new ConfigController();
        ModuloController moduloCtrl = new ModuloController();
        MetaController metaCtrl = new MetaController(metaService, validador, metaDao, orderService, sqlService);
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

        // Middleware: interceptar petición antes de ejecutarse
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            String method = ctx.method();

            // Si el método es OPTIONS, permitir el paso sin realizar comprobaciones
            if ("OPTIONS".equalsIgnoreCase(method)) {
                return;
            }

            // Rutas públicas
            if (path.equals("/api/auth/login")
                    || path.equals("/api/auth/signup")
                    || path.equals("/api/auth/refresh")
                    || path.startsWith("/api/store")
                    || path.equals("/api/test")) {
                return; // Permitir paso sin realizar comprobaciones
            }

            // Validar token 
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new NoAutorizadoException("Token no proporcionado.", null);
            }

            int rolId;
            try {
                DecodedJWT jwt = jwtService.verificarToken(authHeader.replace("Bearer ", ""));
                rolId = jwt.getClaim("rol").asInt();
                ctx.attribute("usuarioId", jwt.getClaim("id").asLong());
                ctx.attribute("rolId", rolId);
            } catch (Exception e) {
                throw new NoAutorizadoException("Token inválido o expirado.", e);
            }

            // Clasificar la ruta 
            RecursoAcceso recurso = clasificarRuta(path, method);

            if (recurso == null) {
                throw new NoAutorizadoException("Ruta no reconocida.", null);
            }

            // Aplicar política según tipo
            switch (recurso.getTipo()) {
                case SISTEMA:
                    // Solo rol 1 (creado en el instalador) puede escribir en recursos de sistema
                    if (!method.equalsIgnoreCase("GET") && rolId != 1) {
                        throw new ForbiddenResponse("Solo el administrador puede modificar la configuración del sistema.");
                    }
                    break;

                case CONFIGURAR:
                    if (rolId == 1) {
                        break; // Si es admin, permitir paso sin comprobar permisos
                    }
                    // Para relaciones por ID, el MetaController comprueba internamente
                    if (recurso.getTabla().equals("__relacion_por_id__")) {
                        break;
                    }
                    // Comprobar permisos para el resto
                    if (!permisoService.puedeConfigurar(rolId, recurso.getTabla())) {
                        throw new ForbiddenResponse(
                                "No tienes permiso para modificar la estructura de '" + recurso.getTabla() + "'.");
                    }
                    break;

                case DATO:
                    // Comprobar permisos sobre una tabla creada dinámicamente
                    if (!permisoService.verificar(rolId, recurso.getTabla(), method)) {
                        throw new ForbiddenResponse(
                                "No tienes permiso para '" + method + "' en '" + recurso.getTabla() + "'.");
                    }
                    break;
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

        // ── Endpoints de roles ─────────────────────────────────────
        app.get("/api/metadata/roles", ctx -> rolCtrl.listarRoles(ctx));
        app.post("/api/metadata/roles", ctx -> rolCtrl.crearRol(ctx));
        app.put("/api/metadata/roles/{rol}", ctx -> rolCtrl.);
        app.delete("/api/metadata/roles/{rol}", ctx -> rolCtrl.eliminarRol(ctx));

        // ── Endpoints de relaciones ──────────────
        // Añadir una relación a una tabla existente
        app.post("/api/metadata/tablas/{tabla}/relaciones", ctx -> metaCtrl.agregarRelacion(ctx));
        // Eliminar una relación
        app.delete("/api/metadata/relaciones/{id}", ctx -> metaCtrl.eliminarRelacion(ctx));
        // Modificar una relación
        app.put("/api/metadata/relaciones/{id}", ctx -> metaCtrl.modificarRelacion(ctx));

        // ── Endpoints de ficheros ─────────────────────────────────
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

    // Tipo de recurso de la base de datos
    // SISTEMA: Corresponde a una tabla del sistema
    // CONFIGURAR: Acceso a las tablas erp_meta_, para poder configurar las 
    // tablas creadas por el usuario
    // DATO: Corresponde a una tabla dinámica creada por el usuario
    enum TipoAcceso {
        SISTEMA, CONFIGURAR, DATO
    }

    // Clase interna para gestionar el acceso a los recursos
    static class RecursoAcceso {

        private final TipoAcceso tipo;
        private final String tabla;

        public RecursoAcceso(TipoAcceso tipo, String tabla) {
            this.tipo = tipo;
            this.tabla = tabla;
        }

        public TipoAcceso getTipo() {
            return tipo;
        }

        public String getTabla() {
            return tabla;
        }
    }

    private static RecursoAcceso clasificarRuta(String path, String method) {

        // Rutas de tablas dinámicas
        if (path.startsWith("/api/data/")) {
            // Obtener nombre de la tabla
            String[] p = path.split("/");
            String tabla = p.length >= 4 ? p[3].toLowerCase() : null;
            if (tabla == null) {
                return null;
            }
            return new RecursoAcceso(TipoAcceso.DATO, tabla);
        }

        // Rutas de metadatos 
        if (path.startsWith("/api/metadata/tablas/")) {
            String[] p = path.split("/");
            // p[0]="" p[1]="api" p[2]="metadata" p[3]="tablas" p[4]="{tabla}" p[5]=segmento
            if (p.length >= 6) {
                String tabla = p[4].toLowerCase();
                String segmento = p[5].toLowerCase();
                if (segmento.equals("columnas") || segmento.equals("relaciones")) {
                    // GET de estructura → SISTEMA (solo lectura, rol 1 o con acceso)
                    // PUT/POST/DELETE sobre columnas o relaciones → CONFIGURAR
                    if (method.equalsIgnoreCase("GET")) {
                        return new RecursoAcceso(TipoAcceso.SISTEMA, tabla);
                    }
                    return new RecursoAcceso(TipoAcceso.CONFIGURAR, tabla);
                }
            }
            // /api/metadata/tablas (listar) o /api/metadata/tablas/{tabla} (obtener/eliminar tabla entera)
            return new RecursoAcceso(TipoAcceso.SISTEMA, "erp_meta_tablas");
        }

        // Relaciones por ID (put/delete /api/metadata/relaciones/{id})
        // No tenemos el nombre de tabla en el path; el MetaController deberá
        // validar internamente que el rol tiene puede_configurar sobre ella.
        // Aquí solo comprobamos que el rol no sea anónimo.
        if (path.startsWith("/api/metadata/relaciones/")) {
            return new RecursoAcceso(TipoAcceso.CONFIGURAR, "__relacion_por_id__");
        }

        // Resto de rutas
        if (path.startsWith("/api/auth/usuarios")) {
            return new RecursoAcceso(TipoAcceso.SISTEMA, "erp_usuarios");
        }
        if (path.startsWith("/api/metadata/config")) {
            return new RecursoAcceso(TipoAcceso.SISTEMA, "erp_config");
        }
        if (path.startsWith("/api/metadata/modulos")) {
            return new RecursoAcceso(TipoAcceso.SISTEMA, "erp_modulos");
        }
        if (path.contains("/roles")) {
            return new RecursoAcceso(TipoAcceso.SISTEMA, "erp_roles");
        }
        if (path.startsWith("/api/ficheros")) {
            return new RecursoAcceso(TipoAcceso.DATO, "erp_ficheros");
        }

        return null;
    }
}
