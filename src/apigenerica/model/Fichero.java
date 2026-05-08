/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

/**
 *
 * @author Grupo1
 */
public class Fichero {

    private String uuid; // Identifica el fichero en MySQL
    private String tablaOrigen;
    private Long registroId;
    private String nombreFichero;
    private String mimeType;
    private byte[] contenido;

    public Fichero(String uuid, String tablaOrigen, Long registroId, String nombreFichero, String mimeType, byte[] contenido) {
        this.uuid = uuid;
        this.tablaOrigen = tablaOrigen;
        this.registroId = registroId;
        this.nombreFichero = nombreFichero;
        this.mimeType = mimeType;
        this.contenido = contenido;
    }

    // Constructor para búsquedas (Query by Example)
    public Fichero(String uuid) {
        this.uuid = uuid;
    }
    
    // Getters y setters
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getTablaOrigen() {
        return tablaOrigen;
    }

    public void setTablaOrigen(String tablaOrigen) {
        this.tablaOrigen = tablaOrigen;
    }

    public Long getRegistroId() {
        return registroId;
    }

    public void setRegistroId(Long registroId) {
        this.registroId = registroId;
    }

    public String getNombreFichero() {
        return nombreFichero;
    }

    public void setNombreFichero(String nombreFichero) {
        this.nombreFichero = nombreFichero;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public byte[] getContenido() {
        return contenido;
    }

    public void setContenido(byte[] contenido) {
        this.contenido = contenido;
    }
}