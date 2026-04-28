/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.model.ColumnaConfig;
import apigenerica.TipoDatoMapper;
import apigenerica.config.ConexionMysql;
import apigenerica.excepciones.ValidacionException;
import apigenerica.model.RelacionConfig;
import apigenerica.model.TablaConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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
     * @param tabla Metadatos de la tabla
     * @param relaciones Datos de relaciones de la tabla
     * @return SQL listo para ejecutar
     */
    public String generarCreateSql(TablaConfig tabla, List<RelacionConfig> relaciones) {
        try {
            String nombreTabla = tabla.getNombreLogico();
            List<ColumnaConfig> campos = tabla.getColumnas();
            validador.validarNombre(nombreTabla);
            validador.validarColumnasUnicas(campos);

            StringBuilder sql = new StringBuilder("CREATE TABLE " + nombreTabla + " (");
            sql.append("`id` BIGINT AUTO_INCREMENT PRIMARY KEY, ");
            // Recorrer lista de campos
            for (int i = 0; i < campos.size(); i++) {
                ColumnaConfig c = campos.get(i);

                // Si el usuario envió una columna llamada id, se ignora
                if (c.getNombre().equalsIgnoreCase("id")) {
                    continue;
                }

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

                // Si es único
                if (c.isUnico()) {
                    sql.append(" UNIQUE");
                }

                // Separar de la siguiente columna
                sql.append(", ");
            }

            // Añadir Foreign Keys
            if (relaciones != null && !relaciones.isEmpty()) {
                for (int i = 0; i < relaciones.size(); i++) {
                    RelacionConfig rel = relaciones.get(i);

                    sql.append("CONSTRAINT `fk_").append(nombreTabla).append("_").append(rel.getTablaDestino()).append("` ")
                            .append("FOREIGN KEY (`").append(rel.getFkColumna()).append("`) ")
                            .append("REFERENCES `").append(rel.getTablaDestino()).append("`(`id`) ")
                            .append("ON DELETE CASCADE ON UPDATE CASCADE");

                    if (i < relaciones.size() - 1) {
                        sql.append(", ");
                    }
                }
            } else {
                // Si no hay relaciones, quitar la última coma y espacio de las columnas
                if (sql.toString().endsWith(", ")) {
                    sql.setLength(sql.length() - 2);
                }
            }
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
            return sql.toString();
        } catch (Exception e) {
            throw new ValidacionException("Error al generar SQL para '" + tabla.getNombreLogico() + "': " + e.getMessage());
        }
    }

    /**
     * Construye una sentencia CREATE DATABASE
     *
     * @param nombreDb Nombre de la base de datos
     * @return SQL listo para ejecutar
     */
    public String generarCreateDbSql(String nombreDb) {
        return "CREATE DATABASE IF NOT EXISTS `" + nombreDb
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;";
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

    /**
     * Ejecutar un script SQL
     *
     * @param db Base de datos en la que se ejecutará la sentencia
     * @param sql Sentencia a ejecutar
     * @throws SQLException
     */
    public void ejecutarSql(String db, String sql) throws SQLException {
        try (Connection conn = ConexionMysql.getConexion(db); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error ejecutando: " + sql);
            throw e;
        }
    }
}
