/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.dao.MetaDao;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.ApiRequest;
import apigenerica.model.TablaConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Grupo1 
 * Lógica de validaciones
 */
public class ValidadorService {

    private final MetaDao metaDao;

    public ValidadorService(MetaDao metaDao) {
        this.metaDao = metaDao;
    }
    
    /**
     * Comprueba que el nombre no esté vacío
     *
     * @param nombre Nombre lógico
     */
    public void validarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ValidacionException("El nombre es obligatorio.");
        }
        
        if (!nombre.matches("^[a-zA-Z0-9_]+$")) {
            throw new ValidacionException("El nombre contiene caracteres no válidos.");
        }
    }

    /**
     * Comprueba que una tabla no tenga columnas
     * con nombres duplicados
     *
     * @param cols Metadatos de las columnas
     */
    public void validarColumnasUnicas(List<ColumnaConfig> cols) {
        Set<String> nombres = new HashSet<>();
        for (ColumnaConfig c : cols) {
            if (!nombres.add(c.getNombre().toLowerCase())) {
                throw new ValidacionException("La columna '" + c.getNombre() + "' está duplicada.");
            }
        }
    }

   /**
    * Aplica reglas de validación sobre 
    * los metadatos recibidos en el JSON
    * 
    * @param request Request enviada por el cliente
    */
    public void validarMetadata(ApiRequest request) {
        if (request.getBaseDatos() == null || request.getBaseDatos().trim().isEmpty()) {
            throw new ValidacionException("La base de datos es obligatoria.");
        }
        if (request.getTabla() == null || request.getTabla().isEmpty()) {
            throw new ValidacionException("Debe proporcionar al menos una tabla.");
        }
        for (TablaConfig t : request.getTabla()) {
            validarNombre(t.getNombreLogico());
            if (t.getColumnas() == null || t.getColumnas().isEmpty()) {
                throw new ValidacionException("La tabla " + t.getNombreLogico() + " no tiene columnas.");
            }
            validarColumnasUnicas(t.getColumnas());
        }
        if (request.getRelaciones() != null) {
            validarRelaciones(request.getRelaciones(), request.getTabla());
        }
    }
    
    /**
     * Comprueba si la relación entre una lista de
     * tablas existe en los metadatos
     * 
     * @param tablas Lista de tablas 
     */
    public void validarRelacionesExisten(List<String> tablas) {
        for (String tabla : tablas) {
            TablaConfig config = metaDao.getConfiguracion(tabla);
            if (config == null) {
                throw new ValidacionException("No hay metadatos configurados para la tabla: " + tabla);
            }
        }
    }
}
