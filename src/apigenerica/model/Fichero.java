/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

import java.time.LocalDateTime;

/**
 *
 * @author Grupo1
 */
public class Fichero {

    private String uuid; // Identifica el fichero en MySQL
    private String nombreFichero;
    private String mimeType;
    private long tamano;
    private byte[] contenido;
    private String ruta; // Si el archivo excede de los 10MB, se guarda en el disco duro
    private LocalDateTime fechaSubida;

    public Fichero(String uuid, String nombreFichero, String mimeType, long tamano, 
            byte[] contenido, LocalDateTime fechaSubida) {
        this.uuid = uuid;
        this.nombreFichero = nombreFichero;
        this.tamano = tamano;
        this.mimeType = mimeType;
        this.contenido = contenido;
        this.fechaSubida = fechaSubida;
    }
    
    public Fichero(String uuid, String nombreFichero, String mimeType, long tamano, 
            String ruta, LocalDateTime fechaSubida) {
        this.uuid = uuid;
        this.nombreFichero = nombreFichero;
        this.tamano = tamano;
        this.mimeType = mimeType;
        this.ruta = ruta;
        this.fechaSubida = fechaSubida;
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

    public long getTamano() {
        return tamano;
    }

    public void setTamano(long tamano) {
        this.tamano = tamano;
    }

    public LocalDateTime getFechaSubida() {
        return fechaSubida;
    }

    public void setFechaSubida(LocalDateTime fechaSubida) {
        this.fechaSubida = fechaSubida;
    }
}