/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.config;

/**
 *
 * @author Grupo1
 */
public class AppConfig {

    public static final String DB_SISTEMA = "erp_sistema";

    // Estas variables se llenan al iniciar la API (desde un .env, properties o el instalador)
    // public static String DB_CLIENTE = System.getenv("DB_CLIENTE"); 
    public static final String DB_CLIENTE = "prueba";
    // private static final String SECRET_KEY = System.getenv("SECRET_KEY");
    private static final String SECRET_KEY = "clave_secreta_de_prueba"; // Firma y verifica los tokens
    
    // Getter
    public static String getSecretKey() {
        return SECRET_KEY;
    }
}
