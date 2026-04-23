/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.model.ColumnaConfig;
import apigenerica.TipoDatoMapper;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.RelacionConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Grupo1 
 * Operaciones SQL genéricas (no CRUD)
 */
public class SqlService {

    private MetaService metaService;
    private final ValidadorService validador;

    public SqlService(MetaService metaService, ValidadorService validador) {
        this.metaService = metaService;
        this.validador = validador;
    }

    /**
     * Construye una sentencia CREATE TABLE
     *
     * @param nombreTabla Nombre de la tabla
     * @param campos Definición de los campos de la tabla
     * @return SQL listo para ejecutar
     */
    public String generarCreateSql(String nombreTabla, List<ColumnaConfig> campos) {
        try {
            validador.validarNombre(nombreTabla);
            validador.validarColumnasUnicas(campos);

            StringBuilder sql = new StringBuilder("CREATE TABLE " + nombreTabla + " (");
            // Recorrer lista de campos
            for (int i = 0; i < campos.size(); i++) {
                ColumnaConfig c = campos.get(i);
                // `Nombre de la tabla` + " " + tipo de dato
                sql.append("`").append(c.getNombre()).append("`").append(" ")
                        .append(TipoDatoMapper.toSql(c.getTipo()));

                // Si no puede ser nulo
                if (!c.isNullable()) {
                    sql.append(" NOT NULL");
                }
                // Si se especificó un valor por defecto y no es autoincremental
                if (c.getValorDefecto() != null && !c.isAutoincremental()) {
                    sql.append(" DEFAULT '").append(c.getValorDefecto()).append("'");
                }
                // Si es autoincremental (y tipo INT)
                if (c.isAutoincremental() && TipoDatoMapper.toSql(c.getTipo()).contains("INT")) {
                    sql.append(" AUTO_INCREMENT");
                }
                // Si es clave primaria
                if (c.isPk()) {
                    sql.append(" PRIMARY KEY");
                }
                // Si es único
                if (c.isUnico()) {
                    sql.append(" UNIQUE");
                }

                // Separar de la siguiente columna
                if (i < campos.size() - 1) {
                    sql.append(", ");
                }
            }

            // Añadir Foreign Keys
            for (ColumnaConfig c : campos) {
                if (c.getReferenciaTabla() != null) {
                    sql.append(", FOREIGN KEY (").append(c.getNombre())
                            .append(") REFERENCES `").append(c.getReferenciaTabla())
                            .append("`(`").append(c.getReferenciaCol()).append("`)");
                }
            }
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
            return sql.toString();
        } catch (Exception e) {
            throw new ValidacionException(e.getMessage());
        }
    }
    
    /**
     * Construye una sentencia CREATE DATABASE
     * @param nombreDb
     * @return 
     */
    public String generarCreateDbSql(String nombreDb) {
        return "CREATE DATABASE IF NOT EXISTS `" + nombreDb + "`";
    }

    /**
     * Construye una sentencia DROP table
     *
     * @param nombreTabla Nombre de la tabla a borrar
     * @return SQL listo para ejecutar
     */
    public String generarDropSql(String nombreTabla) {
        validador.validarNombre(nombreTabla);
        return "DROP TABLE IF EXISTS `" + nombreTabla + "`;";
    }
}
