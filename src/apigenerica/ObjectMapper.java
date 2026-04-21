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
 * Convertir resultados de una consulta SQL en un Objeto
 */
public class ObjectMapper {

    public EntidadDinamica<Object> mapear(ResultSet resultSet, List<ColumnaConfig> configuracion, String nombrePk) throws SQLException {
        EntidadDinamica<Object> entidad = new EntidadDinamica<>(nombrePk);

        for (ColumnaConfig confCampo : configuracion) {
            // Aplicar reglas de mapeo
            if (confCampo != null) {
                // No incluir en el objeto resultante
                if (confCampo.isContrasena()) {
                    continue;
                }
                if (!confCampo.isVisible()) {
                    continue;
                }

                try {
                    // Recuperar valores de la base de datos. Los valores son 
                    // convertidos por JDBC a tipos Java según el tipo de la columna
                    Object valor = resultSet.getObject(confCampo.getNombre());
                    entidad.set(confCampo.getNombre(), valor);
                } catch (SQLException e) {
                    // Si el campo no existe en la db
                    e.printStackTrace();
                }
            }
        }
        return entidad;
    }
}
