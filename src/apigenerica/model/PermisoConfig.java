/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.model;

/**
 * Representa los permisos CRUD de un rol sobre una tabla/sección concreta.
 *
 * @author Grupo1
 */
public class PermisoConfig {

    private String rol;
    private String tabla;
    private boolean puedeLeer; // SELECT
    private boolean puedeEscribir; // INSERT
    private boolean puedeEditar; // UPDATE
    private boolean puedeBorrar; // DELETE
    private boolean puedeConfigurar; // ALTER

    public PermisoConfig() {
    }

    public PermisoConfig(String rol, String tabla,
            boolean puedeVer, boolean puedeCrear,
            boolean puedeEditar, boolean puedeEliminar, boolean puedeConfigurar) {
        this.rol = rol;
        this.tabla = tabla;
        this.puedeLeer = puedeVer;
        this.puedeEscribir = puedeCrear;
        this.puedeEditar = puedeEditar;
        this.puedeBorrar = puedeEliminar;
        this.puedeConfigurar = puedeConfigurar;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getTabla() {
        return tabla;
    }

    public void setTabla(String tabla) {
        this.tabla = tabla;
    }

    public boolean isPuedeLeer() {
        return puedeLeer;
    }

    public void setPuedeLeer(boolean puedeLeer) {
        this.puedeLeer = puedeLeer;
    }

    public boolean isPuedeEscribir() {
        return puedeEscribir;
    }

    public void setPuedeEscribir(boolean puedeEscribir) {
        this.puedeEscribir = puedeEscribir;
    }

    public boolean isPuedeEditar() {
        return puedeEditar;
    }

    public void setPuedeEditar(boolean puedeEditar) {
        this.puedeEditar = puedeEditar;
    }

    public boolean isPuedeBorrar() {
        return puedeBorrar;
    }

    public void setPuedeBorrar(boolean puedeBorrar) {
        this.puedeBorrar = puedeBorrar;
    }

    public boolean isPuedeConfigurar() {
        return puedeConfigurar;
    }

    public void setPuedeConfigurar(boolean puedeConfigurar) {
        this.puedeConfigurar = puedeConfigurar;
    }
}
