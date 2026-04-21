/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica;

import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Grupo1 
 * Convertir resultados de una consulta SQL en un objeto EntidadDinamica
 */
public class EntidadMapper {

    public EntidadDinamica<Object> mapear(ResultSet resultSet, List<ColumnaConfig> columnas, String nombrePk) throws SQLException {
        EntidadDinamica<Object> entidad = new EntidadDinamica<>(nombrePk);

        // Extraer nombres de las columnas de la tabla de ColumnaConfig
        for (ColumnaConfig col : columnas) {
            // Aplicar reglas de mapeo
            if (col != null) {
                // No incluir en el objeto resultante
                if (col.isContrasena()) {
                    continue;
                }
                if (!col.isVisible()) {
                    continue;
                }

                try {
                    // Recuperar valores de la base de datos. Los valores son 
                    // convertidos por JDBC a tipos Java según el tipo de la columna
                    Object valor = resultSet.getObject(col.getNombre());
                    entidad.set(col.getNombre(), valor);
                } catch (SQLException e) {
                    // Si el campo no existe en la db
                    throw e;
                }
            }
        }
        return entidad;
    }
}
