/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dao;

import apigenerica.config.ConexionDb4o;
import apigenerica.model.ColumnaConfig;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.model.TablaConfig;
import com.sun.org.apache.xml.internal.security.signature.ObjectContainer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Grupo1 
 * Operaciones CRUD para las tablas de metadatos
 */
public class MetaDao {

    /**
     * Guarda la configuración de una tabla en db4o.
     * Si ya existe, la reemplaza.
     * 
     * @param tabla Objeto TablaConfig
     */
    public void guardarConfiguracion(TablaConfig tabla) {
        ObjectContainer db = ConexionDb4o.getConexion();
        try {
            // Eliminar configuración anterior si existe
            eliminarConfiguracion(tabla.getNombreLogico());
            // Guardar el objeto
            db.store(tabla);
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw new BaseDatosException("Error al guardar configuración de '" + tabla.getNombreLogico() + "'.", e);
        }
    }

    /**
     * Recupera la configuración de una tabla a partir de su nombre lógico.
     * 
     * @param nombreLogico Nombre de la tabla en db4o
     * @return Configuración de la tabla
     */
    public TablaConfig getConfiguracion(String nombreLogico) {
        ObjectContainer db = ConexionDb4o.getConexion();
        
        // Buscar objetos que coincidan con el nombre lógico
        Query query = db.query();
        query.constrain(TablaConfig.class);
        query.descend("nombreLogico").constrain(nombreLogico);
        ObjectSet<TablaConfig> resultado = db.queryByExample(ejemplo);
        if (resultado.hasNext()) {
            return resultado.next();
        }
        return null;
    }

    /**
     * Recupera la lista de columnas de una tabla.
     * 
     * @param nombreLogico Nombre de la tabla en db4o
     * @return Lista de objetos ColumnaConfig
     */
    public List<ColumnaConfig> getColumnas(String nombreLogico) {
        TablaConfig tabla = getConfiguracion(nombreLogico);
        if (tabla == null) {
            throw new RecursoNoEncontradoException("No existe configuración para la tabla '" + nombreLogico + "'.");
        }
        return tabla.getColumnas();
    }

    /**
     * Comprueba si existe configuración para una tabla.
     * 
     * @param nombreLogico Nombre de la tabla en db4o
     * @return true si se encontró la tabla; false, en caso contrario
     */
    public boolean existeTabla(String nombreLogico) {
        return getConfiguracion(nombreLogico) != null;
    }

    /**
     * Elimina la configuración de una tabla.
     * 
     * @param nombreLogico Nombre de la tabla en db4o
     */
    public void eliminarConfiguracion(String nombreLogico) {
        ObjectContainer db = ConexionDb4o.getConexion();
        try {
            Query query = db.query();
            query.constrain(TablaConfig.class);
            query.descend("nombreLogico").constrain(nombreLogico);

            ObjectSet<TablaConfig> resultado = db.queryByExample(ejemplo);
            while (resultado.hasNext()) {
                db.delete(resultado.next());
            }
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw new BaseDatosException("Error al eliminar configuración de '" + nombreLogico + "'.", e);
        }
    }

    /**
     * Devuelve todas las tablas.
     * 
     * @return Set de objetos TablaConfig
     */
    public List<TablaConfig> getTodas() {
        ObjectContainer db = ConexionDb4o.getConexion();
        ObjectSet<TablaConfig> resultado = db.queryByExample(TablaConfig.class);
        List<TablaConfig> tablas = new ArrayList<>();
        while (resultado.hasNext()) {
            tablas.add(resultado.next());
        }
        return tablas;
    }
}