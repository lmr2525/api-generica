/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica;

/**
 * @author Grupo1
 * Metadatos de los campos de la base de datos
 */
public class CampoConfig {
    Long id;
    String nombre;
    String tipo;
    boolean pk;
    boolean nullable;
    boolean contrasena;
    boolean visible; // Para el DTO
    boolean autoincremental;
    boolean unico;
    String valorDefecto; // Validar según tipo de dato
    
    // Relaciones (foreign key)
    String referenciaTabla;
    String referenciaCampo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public boolean isPk() {
        return pk;
    }

    public void setPk(boolean pk) {
        this.pk = pk;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isContrasena() {
        return contrasena;
    }

    public void setContrasena(boolean contrasena) {
        this.contrasena = contrasena;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isAutoincremental() {
        return autoincremental;
    }

    public void setAutoincremental(boolean autoincremental) {
        this.autoincremental = autoincremental;
    }

    public boolean isUnico() {
        return unico;
    }

    public void setUnico(boolean unico) {
        this.unico = unico;
    }

    public String getValorDefecto() {
        return valorDefecto;
    }

    public void setValorDefecto(String valorDefecto) {
        this.valorDefecto = valorDefecto;
    }

    public String getReferenciaTabla() {
        return referenciaTabla;
    }

    public void setReferenciaTabla(String referenciaTabla) {
        this.referenciaTabla = referenciaTabla;
    }

    public String getReferenciaCampo() {
        return referenciaCampo;
    }

    public void setReferenciaCampo(String referenciaCampo) {
        this.referenciaCampo = referenciaCampo;
    }
}
