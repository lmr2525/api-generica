/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.dao.MetaDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.excepciones.RecursoNoEncontradoException;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Grupo1
 * Operaciones con los metadatos
 */
public class MetaService {

    private final MetaDao metaDao;
    private final ValidadorService validador;

    public MetaService(MetaDao metaDao, ValidadorService validador) {
        this.metaDao = metaDao;
        this.validador = validador;
    }

    /**
     * Guardar configuración si ya se tiene el objeto
     * TablaConfig (Formulario)
     * 
     * @param tabla 
     */
    public void guardarConfiguracion(TablaConfig tabla) {
        // Generar nombre amigable si no lo tiene
        if (tabla.getNombreAmigable() == null || tabla.getNombreAmigable().trim().isEmpty()) {
            tabla.setNombreAmigable(crearNombreAmigable(tabla.getNombreLogico()));
        }
        
        // Marcar columnas de contraseña automáticamente
        if (tabla.getColumnas() != null) {
            for (ColumnaConfig col : tabla.getColumnas()) {
                if ("CONTRASENA".equalsIgnoreCase(col.getTipo())) {
                    col.setContrasena(true);
                    col.setVisible(false);
                }
            }
        }
    
        try {
            // Guardar objeto
            metaDao.guardarConfiguracion(tabla);
        } catch (Exception e) {
            throw new BaseDatosException("Error al guardar configuración de '" + tabla.getNombreLogico() + "'.", e);
        }
    }
    
    /**
     * Borra la configuración de la tabla en db4o.
     *
     * @param nombreLogico Nombre de la tabla.
     */
    public void eliminarConfiguracion(String nombreLogico) {
        metaDao.eliminarConfiguracion(nombreLogico);
    }

    /**
     * Construye el nombre amigable a partir del nombre lógico. Ejemplo:
     * datos_clientes -- Datos clientes
     *
     * @param nombreLogico Nombre lógico de la tabla
     * @return Nombre amigable
     */
    public String crearNombreAmigable(String nombreLogico) {
        validador.validarNombre(nombreLogico);
        // Construir nombre amigable
        String nombreAmigable = nombreLogico.replace("_", " ");
        return nombreAmigable.substring(0, 1).toUpperCase() + nombreAmigable.substring(1);
    }
    
    /**
     * Obtiene las relaciones entre una tabla principal y una o más tablas
     * secundarias.
     * Busca la configuración de la tabla principal (metadatos) y con ella su
     * lista de relaciones.
     * Procesa una lista de includes, separando cada elemento en un array, 
     * 
     * @param tablaPrincipal Tabla padre
     * @param includes Cadena de texto con las tablas secundarias
     * @return Lista de relaciones
     */
    public List<RelacionConfig> getRelaciones(String tablaPrincipal, String includes) {
        if (includes == null || includes.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Dividir los includes
        String[] tablasSolicitadas = includes.replace(" ", "").split(",");
        List<String> listaSolicitadas = Arrays.asList(tablasSolicitadas);

        List<RelacionConfig> relacionesFinales = new ArrayList<>();

        // Obtener la configuración principal (Padres de la tabla)
        TablaConfig config = metaDao.getConfiguracion(tablaPrincipal);
        if (config != null && config.getRelaciones() != null) {
            for (RelacionConfig rel : config.getRelaciones()) {
                // Seleccionar relaciones con la tablas secundarias especificadas
                if (listaSolicitadas.contains(rel.getTablaDestino())) {
                    relacionesFinales.add(rel);
                }
            }
        }

        // Obtener las Hijas (Tablas que apuntan a la principal)
        List<RelacionConfig> relacionesHijas = metaDao.getRelacionesHijas(tablaPrincipal);
        if (relacionesHijas != null) {
            for (RelacionConfig rel : relacionesHijas) {
                // Seleccionar relaciones con la tabla principal
                if (listaSolicitadas.contains(rel.getTablaOrigen())) {
                    relacionesFinales.add(rel);
                }
            }
        }

        return relacionesFinales;
    }
    
    /**
     * Obtiene todas las relaciones internas que existen entre una lista de tablas.
     * Ideal para operaciones transaccionales donde necesitamos inyectar FKs.
     * 
     * @param tablas Lista de tablas involucradas en la transacción
     * @return Lista de relaciones pertinentes entre estas tablas
     */
    public List<RelacionConfig> getRelacionesEntreTablas(List<String> tablas) {
        List<RelacionConfig> relacionesFinales = new ArrayList<>();

        if (tablas == null || tablas.isEmpty()) {
            return relacionesFinales;
        }

        // Revisar cada tabla de la lista
        for (String tablaOrigen : tablas) {
            TablaConfig config = metaDao.getConfiguracion(tablaOrigen);
            
            if (config != null && config.getRelaciones() != null) {
                for (RelacionConfig rel : config.getRelaciones()) {
                    // Si la tabla a la que apunta la FK TAMBIÉN está en nuestra lista de inserción
                    if (tablas.contains(rel.getTablaDestino())) {
                        relacionesFinales.add(rel);
                    }
                }
            }
        }

        return relacionesFinales;
    }
    
    public Map<String, Object> obtenerGuiaUsuario(String nombreTabla) {
        TablaConfig tabla = metaDao.getConfiguracion(nombreTabla);
        if (tabla == null) throw new RecursoNoEncontradoException("Tabla no configurada");

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("tabla", tabla.getNombreLogico());
        respuesta.put("nombre_amigable", tabla.getNombreAmigable());
        respuesta.put("columnas", tabla.getColumnas());

        // Estructura de relaciones bidireccional
        Map<String, Object> relaciones = new HashMap<>();

        // Padres: Lo que la tabla ya tiene dentro (sus FKs salientes)
        relaciones.put("padres", tabla.getRelaciones()); 

        // Hijas: Quién apunta a ella (usando el método que creamos antes)
        relaciones.put("hijas", metaDao.getRelacionesHijas(nombreTabla));

        respuesta.put("relaciones", relaciones);
        return respuesta;
    }
    
    public TablaConfig getConfiguracion(String nombreLogico) {
        TablaConfig config = metaDao.getConfiguracion(nombreLogico);
        if (config == null) {
            throw new RecursoNoEncontradoException("Tabla no registrada: " + nombreLogico);
        }
        return config;
    }
}
