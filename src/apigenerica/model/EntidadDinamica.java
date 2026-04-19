/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Grupo1
 * 
 * Representa una fila de una tabla
 * como un mapa de clave-valor
 * @param <ID> Tipo del dato de la clave primaria
 */
public class EntidadDinamica<ID> {
    // Nombre de la colummna y valor
    private Map<String, Object> valores = new HashMap<>();
    // Nombre de la columna de la PrimaryKey
    private String campoId;

   public EntidadDinamica(String campoId) {
        this.campoId = campoId;
    }

    // Devuelve el ID de la colección
    @SuppressWarnings("unchecked")
    public ID getId() {
        return (ID) valores.get(campoId);
    }
    
    // Guarda el ID dentro de la colección
    public void setId(ID id) {
        valores.put(campoId, id);
    }

    // Guarda un dato (NO ID) en la colección
    public void set(String columna, Object valor) {
        valores.put(columna, valor);
    }

    // Devuelve un dato (NO ID) de la colección
    public Object get(String columna) {
        return valores.get(columna);
    }

    public Map<String, Object> getTodo() {
        return valores;
    }
}