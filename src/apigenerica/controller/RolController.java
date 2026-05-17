/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.controller;

import apigenerica.model.PermisoConfig;
import apigenerica.model.RolConfig;
import apigenerica.dao.RolDao;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ApiRespuesta;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import java.util.List;
import java.util.Map;

/**
 * Controlador para la gestión de roles y permisos del ERP. Solo accesible por
 * usuarios con rol 'admin'.
 *
 * Endpoints: GET /api/roles → listarRoles POST /api/roles → crearRol DELETE
 * /api/roles/{nombre} → eliminarRol GET /api/roles/{nombre}/permisos →
 * obtenerPermisos PUT /api/roles/{nombre}/permisos → guardarPermiso
 *
 * @author Grupo1
 */
public class RolController {

    private final RolDao rolDao;

    public RolController(RolDao rolDao) {
        this.rolDao = rolDao;
    }

    // ── GET /api/roles ────────────────────────────────────────────────────────
    /**
     * Lista todos los roles definidos en el sistema.
     *
     * @param ctx
     */
    public void listarRoles(Context ctx) {
        List<RolConfig> roles = rolDao.listarRoles();
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(roles));
    }

    // ── POST /api/roles ───────────────────────────────────────────────────────
    /**
     * Crea un nuevo rol. Body JSON: { "nombre": "rrhh", "descripcion":
     * "Recursos Humanos" }
     *
     * @param ctx
     */
    @SuppressWarnings("unchecked")
    public void crearRol(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String nombre = body.get("nombre");
        String descripcion = body.getOrDefault("descripcion", "");

        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ValidacionException("El campo 'nombre' es obligatorio.");
        }
        // Normalizar: minúsculas, sin espacios
        nombre = nombre.trim().toLowerCase().replaceAll("\\s+", "_");

        if (rolDao.existeRol(nombre)) {
            throw new ValidacionException("Ya existe un rol con el nombre '" + nombre + "'.");
        }

        rolDao.crearRol(nombre, descripcion.trim());
        ctx.status(HttpCode.CREATED).json(ApiRespuesta.ok("Rol '" + nombre + "' creado correctamente."));
    }

    // ── DELETE /api/roles/{nombre} ────────────────────────────────────────────
    /**
     * Elimina un rol y todos sus permisos asociados.
     *
     * @param ctx
     */
    public void eliminarRol(Context ctx) {
        String nombre = ctx.pathParam("nombre");

        if (!rolDao.existeRol(nombre)) {
            throw new RecursoNoEncontradoException("No existe el rol '" + nombre + "'.");
        }

        // Proteger el rol 'admin' de ser eliminado
        if ("admin".equalsIgnoreCase(nombre)) {
            throw new ValidacionException("El rol 'admin' no puede eliminarse.");
        }

        boolean eliminado = rolDao.eliminarRol(nombre);
        if (eliminado) {
            ctx.status(HttpCode.OK).json(ApiRespuesta.ok("Rol '" + nombre + "' eliminado correctamente."));
        } else {
            throw new RecursoNoEncontradoException("No se encontró el rol '" + nombre + "'.");
        }
    }

    // ── GET /api/roles/{nombre}/permisos ──────────────────────────────────────
    /**
     * Devuelve la lista de permisos de un rol sobre todas las tablas/secciones.
     *
     * @param ctx
     */
    public void obtenerPermisos(Context ctx) {
        int rolId = ctx.pathParamAsClass("id", Integer.class).get();

        List<PermisoConfig> permisos = rolDao.getTodosPermisos(rolId);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(permisos));
    }

    // ── PUT /api/roles/{nombre}/permisos ──────────────────────────────────────
    /**
     * Guarda o actualiza los permisos de un rol sobre una tabla/sección
     *
     * @param ctx
     */
    public void guardarPermiso(Context ctx) {
        int rolId = ctx.pathParamAsClass("id", Integer.class).get();

        PermisoConfig permiso = ctx.bodyAsClass(PermisoConfig.class);
        if (permiso.getTabla() == null || permiso.getTabla().trim().isEmpty()) {
            throw new ValidacionException("El campo 'tabla' es obligatorio.");
        }

        rolDao.guardarPermisos(rolId, permiso.getTabla(), permiso);
        ctx.status(HttpCode.OK).json(ApiRespuesta.ok(
                "Permisos actualizados para el rol ID " + rolId + " sobre la tabla '" + permiso.getTabla() + "'."
        ));
    }
}
