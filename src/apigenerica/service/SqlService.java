/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.CampoConfig;
import apigenerica.TipoDatoMapper;
import apigenerica.config.Conexion;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set; 

/**
 * @author Grupo1 
 * Crea las sentencias create table
 */
public class SqlService {
    private final MetaService metaService = new MetaService();
    
    /**
     * Construye una sentencia de creación de tablas a partir de 
     * la estructura de campos
     * @param nombreTabla Nombre de la tabla
     * @param campos Definición de los campos de la tabla
     * @return Sentencia SQL
     */
    public String generarCreateSql(String nombreTabla, List<CampoConfig> campos) throws Exception {
        validarColumnasUnicas(campos);
        StringBuilder sql = new StringBuilder("CREATE TABLE " + nombreTabla + " (");

        // Recorrer lista de campos
        for (int i = 0; i < campos.size(); i++) {
            CampoConfig c = campos.get(i);
            // `Nombre de la tabla` + " " + tipo de dato
            sql.append("`").append(c.getNombre()).append("`").append(" ")
                    .append(TipoDatoMapper.toSql(c.getTipo()));
            
            // Si puede ser null
            if (!c.isNullable()) sql.append(" NOT NULL");
            // Si se especificó un valor por defecto y no es autoincremental
            if (c.getValorDefecto() != null && !c.isAutoincremental()) {
                sql.append(" DEFAULT '").append(c.getValorDefecto()).append("'");
            }
            // Si es clave primaria
            if (c.isPk()) sql.append(" PRIMARY KEY");
            // Si es autoincremental (y tipo INT)
            if (c.isAutoincremental() && TipoDatoMapper.toSql(c.getTipo()).contains("INT")) {
                sql.append(" AUTO_INCREMENT");
            }
            // Si es único
            if (c.isUnico()) sql.append(" UNIQUE");
            
            // Separar de la siguiente columna
            if (i < campos.size() - 1) sql.append(", ");
        }

        // Añadir Foreign Keys
        for (CampoConfig c : campos) {
            if (c.getReferenciaTabla() != null) {
                sql.append(", FOREIGN KEY (").append(c.getNombre())
                   .append(") REFERENCES `").append(c.getReferenciaTabla())
                   .append("`(`").append(c.getReferenciaCampo()).append("`)");
            }
        }

        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
        return sql.toString();
    }
    
    public String generarDropSql(String nombreTabla) {
        return "DROP TABLE IF EXISTS `" + nombreTabla + "`;";
    }
    
    
    /**
     * Recibe una consulta SQL y la limpia
     * @param sqlSinProc
     * @throws java.lang.Exception
     */
    private void limpiarSql(String sqlSinProc) throws Exception {
        String sqlLimpio = sqlSinProc.trim().toUpperCase().replaceAll("\\s+", " ");
        // Bloquear consultas a sys_tablas y sys_campos
        if (sqlLimpio.contains("SYS_TABLAS") || sqlLimpio.contains("SYS_CAMPOS")) {
            throw new Exception("No tienes permiso para modificar las tablas del sistema.");
        }

        // Permitir solo CREATE, ALTER y DROP
        if (!sqlLimpio.startsWith("CREATE TABLE") && !sqlLimpio.startsWith("ALTER TABLE") && !sqlLimpio.startsWith("DROP TABLE")) {
            throw new Exception("Solo se permiten sentencias DDL (CREATE, ALTER, DROP).");
        }
    }
    
    public void ejecutarSql(String sql, String nombreLogico) throws Exception {
        limpiarSql(sql);
        
        try (Connection conn = Conexion.getConexion();
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);

            String sqlUpper = sql.trim().toUpperCase();
            if (sqlUpper.startsWith("CREATE TABLE")|| sqlUpper.startsWith("ALTER TABLE")) {
                // Guardar configuración asociada a la tabla
                metaService.guardarConfiguracion(nombreLogico);
            } 
            else if (sqlUpper.startsWith("DROP TABLE")) {
                // Borrar configuración asociada a la tabla
                metaService.eliminarConfiguracion(nombreLogico);
            }
        }
    }
    
    private void validarColumnasUnicas(List<CampoConfig> campos) throws Exception {
        Set<String> nombres = new HashSet<>();
        for (CampoConfig c : campos) {
            if (!nombres.add(c.getNombre().toLowerCase())) {
                throw new Exception("La columna '" + c.getNombre() + "' está duplicada.");
            }
        }
    }
}
