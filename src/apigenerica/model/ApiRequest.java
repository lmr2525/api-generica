/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

import java.util.List;

/**
 * @author Grupo1 
 * Entidad que representa el JSON recibido 
 * para la creación de las tablas
 */
public class ApiRequest {

    private String baseDatos; // Nombre lógico de la base de datos
    private List<TablaConfig> tabla; // Datos de la tabla

    // Getters y setters
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
}
