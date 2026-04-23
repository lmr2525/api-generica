/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica;

import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import apigenerica.model.RelacionConfig;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Grupo1 
 * Convertir resultados de una consulta SQL en un objeto EntidadDinamica
 */
public class EntidadMapper {

    public EntidadDinamica mapear(ResultSet resultSet, List<ColumnaConfig> columnas) throws SQLException {
        EntidadDinamica entidad = new EntidadDinamica();

        // Mapear el ID (Columna "id")
        entidad.setId(resultSet.getLong("id"));
    
        // Extraer nombres de las columnas de la tabla de ColumnaConfig
        for (ColumnaConfig col : columnas) {
            // No incluir en el objeto resultante
            if (!col.getNombre().equalsIgnoreCase("id") && col.isVisible()) {
                // Mapear resto de columnas
                entidad.set(col.getNombre(), resultSet.getObject(col.getNombre()));
            }
        }
        return entidad;
    }
    
    public EntidadDinamica mapearIncludes(ResultSet resultSet, List<ColumnaConfig> colsPadre,
        List<RelacionConfig> relaciones, Map<String, List<ColumnaConfig>> colsHijas) throws SQLException {
        // Mapear entidad padre
        EntidadDinamica padre = mapear(resultSet, colsPadre);

        for (RelacionConfig rel : relaciones) {
            // Obtener alias
            String prefijo = rel.getNombreRelacion() + "_"; 

            Map<String, Object> hijo = new HashMap<>();
            // Obtenemos columnas de la tabla hija
            List<ColumnaConfig> columnasHijo = colsHijas.get(rel.getTablaDestino());
            // Mapear hijo
            for (ColumnaConfig col : columnasHijo) {
                // El nombre de la columna en ResultSet (prefijo_nombreCol)
                String nombreColumna = prefijo + col.getNombre();
                Object valor = resultSet.getObject(nombreColumna);
                hijo.put(col.getNombre(), valor);
            }
            // Guardar hijo dentro del padre
            padre.set(rel.getNombreRelacion(), hijo); 
        }
        return padre;
    }
}
