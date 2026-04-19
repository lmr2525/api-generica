/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.excepciones.ValidacionException;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.MetaRequest;
import apigenerica.model.TablaConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Grupo1 
 * Lógica de validaciones
 */
public class ValidadorService {

    /**
     * Comprueba que la sentencia a ejecutar 
     * corresponda a una operación permitida (DDL)
     *
     * @param sql SQL a validar
     */
    public void validarSql(String sql) {
        String sqlUpper = sql.toUpperCase();
        // Permitir solo CREATE, ALTER y DROP
        if (!sqlUpper.startsWith("CREATE TABLE") && 
            !sqlUpper.startsWith("ALTER TABLE") && !sqlUpper.startsWith("DROP TABLE")) {
            throw new ValidacionException("Solo se permiten sentencias DDL (CREATE, ALTER, DROP).");
        }
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
     * Comprueba si la tabla tiene una clave primaria
     *
     * @param cols Metadatos de las columnas
     */
    public void validarTienePk(List<ColumnaConfig> cols) {
        boolean tienePk = cols.stream().anyMatch(ColumnaConfig::isPk);
        if (!tienePk) {
            throw new ValidacionException("La tabla debe tener al menos una clave primaria.");
        }
    }
    
    /**
    * Comprueba que la tabla no tenga más de una clave primaria
    * @param cols Metadatos de las columnas
    */
   public void validarPkUnica(List<ColumnaConfig> cols) {
       long numPks = cols.stream().filter(ColumnaConfig::isPk).count();
       if (numPks > 1) {
           throw new ValidacionException("La tabla solo puede tener una clave primaria.");
       }
   }

   /**
    * Aplica reglas de validación sobre los metadatos recibidos
    * en el JSON
    * @param request 
    */
    public void validarMetadata(MetaRequest request) {
        if (request.getOperacion() == null) {
            throw new ValidacionException("La operación es obligatoria.");
        }
        if (request.getBaseDatos() == null || request.getBaseDatos().trim().isEmpty()) {
            throw new ValidacionException("La base de datos es obligatoria.");
        }
        if (request.getOperacion() == MetaRequest.Operacion.CREATE_TABLE) {
            if (request.getTabla() == null || request.getTabla().isEmpty()) {
                throw new ValidacionException("Debe proporcionar al menos una tabla.");
            }
            for (TablaConfig t : request.getTabla()) {
                validarNombre(t.getNombreLogico());
                if (t.getColumnas() == null || t.getColumnas().isEmpty()) {
                    throw new ValidacionException("La tabla " + t.getNombreLogico() + " no tiene columnas.");
                }
                validarColumnasUnicas(t.getColumnas());
                validarTienePk(t.getColumnas());
                validarPkUnica(t.getColumnas());
            }
        } else if (request.getOperacion() == MetaRequest.Operacion.EXECUTE_SQL) {
            if (request.getSql() == null || request.getSql().trim().isEmpty()) {
                throw new ValidacionException("El SQL no puede estar vacío.");
            }
        }
    }
}
