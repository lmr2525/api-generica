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
    private byte[] contenido; // Si el archivo pesa menos de 5MB, se guarda completo en db4o
    private boolean comprimido;
    private String ruta; // Si el archivo excede de los 5MB, se guarda en el disco duro
    private LocalDateTime fechaSubida;

    // Archivos de menos de 5MB (guardar completo en db4o)
    public Fichero(String uuid, String nombreFichero, String mimeType, long tamano, 
            byte[] contenido, boolean comprimido, LocalDateTime fechaSubida) {
        this(uuid);
        this.nombreFichero = nombreFichero;
        this.tamano = tamano;
        this.mimeType = mimeType;
        this.contenido = contenido;
        this.comprimido = comprimido;
        this.fechaSubida = fechaSubida;
    }
    
    // Archivos de más de 5MB (guardar contenido en disco y ruta en db4o)
    public Fichero(String uuid, String nombreFichero, String mimeType, long tamano, 
            String ruta, LocalDateTime fechaSubida) {
        this(uuid);
        this.nombreFichero = nombreFichero;
        this.tamano = tamano;
        this.mimeType = mimeType;
        this.ruta = ruta;
        this.comprimido = false;
        this.fechaSubida = fechaSubida;
    }

    // Constructor para búsquedas (Query by Example)
    public Fichero(String uuid) {
        this.uuid = uuid;
    }
    
    public boolean isEnDisco() {
        return this.ruta != null && this.ruta.isEmpty();
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

    public boolean isComprimido() {
        return comprimido;
    }

    public void setComprimido(boolean comprimido) {
        this.comprimido = comprimido;
    }

    public String getRuta() {
        return ruta;
    }

    public void setRuta(String ruta) {
        this.ruta = ruta;
    }
}