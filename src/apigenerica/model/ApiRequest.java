/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

import java.util.List;

/**
 * @author Grupo1 
 * Entidad que representa el JSON recibido para la creación de
 * las tablas
 */
public class ApiRequest {

    // Enumeración con los valores posibles de operación
    public enum Operacion {
        CREATE_TABLE, EXECUTE_SQL
    };
    
    private Operacion operacion;
    private String baseDatos;

    // Si se envió un formulario
    private List<TablaConfig> tabla;

    // Si se envió un script SQL
    private String sql;

    // Getters y setters
    public Operacion getOperacion() {
        return operacion;
    }

    public void setOperacion(Operacion operacion) {
        this.operacion = operacion;
    }

    public String getBaseDatos() {
        return baseDatos;
    }

    public void setBaseDatos(String baseDatos) {
        this.baseDatos = baseDatos;
    }

    public List<TablaConfig> getTabla() {
        return tabla;
    }

    public void setTabla(List<TablaConfig> tabla) {
        this.tabla = tabla;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }
}
