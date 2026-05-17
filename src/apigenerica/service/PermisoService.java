/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.dao.RolDao;
import apigenerica.model.PermisoConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestión de roles y permisos
 * @author Grupo1
 */
public class PermisoService {
    RolDao rolDao = new RolDao();
    
    public PermisoService(RolDao rolDao) {
        this.rolDao = rolDao;
    }
    
    // La clave es "ROL:TABLA"
    // El valor es el objeto con los booleanos de permisos
    private final Map<String, PermisoConfig> cachePermisos = new ConcurrentHashMap<>();

    /**
     * Comprueba si un rol tiene permisos para una tabla
     * 
     * @param rol Rol a comprobar
     * @param tabla Tabla sobre la que se desean comprobar los permisos
     * @param metodo Método HTTP correspondiente al permiso
     * @return true si el rol tiene permisos; false, en caso contrario
     */
    public boolean verificar(int rol, String tabla, String metodo) {
        String clave = rol + ":" + tabla.toLowerCase();

        // Intentar obtener de la caché. Si no está, buscar en DB y almacenar
        PermisoConfig permisos = cachePermisos.computeIfAbsent(clave, k -> obtenerRolPorTabla(rol, tabla));

        if (permisos == null) return false; // Si no hay permisos configurados, no dar permiso

        // Mapear método HTTP a permiso
        switch (metodo.toUpperCase()) {
            case "GET":    return permisos.isPuedeLeer();
            case "POST":   return permisos.isPuedeEscribir();
            case "PUT":    
            case "PATCH":  return permisos.isPuedeEditar();
            case "DELETE": return permisos.isPuedeBorrar();
            default:       return false;
        }
    }

    /**
     * Realiza una consulta a la tabla de roles del sistema
     * para obtener los permisos correspondientes al rol
     * 
     * @param rol
     * @param tabla
     * @return 
     */
    private PermisoConfig obtenerRolPorTabla(int rol, String tabla) {
        return rolDao.getPermisos(rol, tabla);
    }

    /**
     * Borrar la caché si cambian los permisos
     */
    public void limpiarCache() {
        cachePermisos.clear();
    }
}
