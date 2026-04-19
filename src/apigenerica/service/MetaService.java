/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.model.ColumnaConfig;
import apigenerica.TipoDatoMapper;
import apigenerica.config.ConexionMysql;
import apigenerica.dao.MetaDao;
import apigenerica.excepciones.BaseDatosException;
import apigenerica.model.TablaConfig;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Grupo1
 * Operaciones con los metadatos
 */
public class MetaService {

    private final MetaDao metaDao;
    private final ValidadorService validador;

    public MetaService(MetaDao metaDao, ValidadorService validador) {
        this.metaDao = metaDao;
        this.validador = validador;
    }

    /**
     * Guardar configuración si ya se tiene el objeto
     * TablaConfig (Formulario)
     * 
     * @param tabla 
     */
    public void guardarConfiguracion(TablaConfig tabla) {
        // Generar nombre amigable si no lo tiene
        if (tabla.getNombreAmigable() == null || tabla.getNombreAmigable().trim().isEmpty()) {
            tabla.setNombreAmigable(crearNombreAmigable(tabla.getNombreLogico()));
        }
        // Guardar objeto
        metaDao.guardarConfiguracion(tabla);
    }
    
    /**
     * Extrae el nombre de la tabla de una sentencia SQL, lee sus metadatos
     * desde MySQL y los persiste en db4o.
     * Solo procesa sentencias CREATE TABLE — para ALTER y DROP devuelve null.
     *
     * @param baseDatos Base de datos donde se creó la tabla
     * @param sql Sentencia SQL ejecutada
     * @return TablaConfig creado, o null si no era un CREATE TABLE
     */
    public TablaConfig guardarConfiguracion(String baseDatos, String sql) throws SQLException {
        // Extraer el nombre de la tabla de la sentencia SQL usando Regex
        String nombreLogico = extraerNombreTabla(sql);

        // Si no es un CREATE TABLE, devolver null
        if (nombreLogico == null) return null; 

        try {
            // Leer los metadatos desde MySQL
            List<ColumnaConfig> columnas = leerMetadatosSql(baseDatos, nombreLogico);
            // Construir el objeto TablaConfig
            TablaConfig tabla = new TablaConfig();
            tabla.setNombreDb(baseDatos);
            tabla.setNombreLogico(nombreLogico);
            tabla.setColumnas(columnas); 
            // Persistir metadatos
            guardarConfiguracion(tabla);
            return tabla;
        } catch(SQLException e) {
            throw new BaseDatosException("Error al guardar la configuración de '" + nombreLogico + "'.", e);
        }
    }
    
    /**
    * Extrae el nombre de la tabla de una sentencia CREATE TABLE.
    * Soporta nombres con y sin comillas invertidas.
    *
    * @param sql Sentencia SQL
    * @return Nombre de la tabla, o null si no es un CREATE TABLE
    */
    private String extraerNombreTabla(String sql) {
        // Busca "CREATE TABLE nombre" ignorando mayúsculas/minúsculas y comillas invertidas `
        Pattern pattern = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
  
    /**
     * Realiza una consulta de los metadatos de una tabla de MySQL.
     *
     * @param baseDatos Nombre de la base de datos en la que 
     * buscar la tabla
     * @param nombreLogico Nombre de la tabla en MySQL
     */
    private List<ColumnaConfig> leerMetadatosSql(String baseDatos, String nombreLogico) throws SQLException {
        try (Connection conn = ConexionMysql.getConexion()) {
            DatabaseMetaData meta = conn.getMetaData();
            
            // Obtener columnas, primary key y foreign keys
            try (ResultSet dbCols = meta.getColumns(baseDatos, null, nombreLogico, null);
             ResultSet dbPk = meta.getPrimaryKeys(baseDatos, null, nombreLogico);
             ResultSet dbFks = meta.getImportedKeys(baseDatos, null, nombreLogico)) {

                // Almacenar primary key
                String columnaPk = dbPk.next() ? dbPk.getString("COLUMN_NAME") : "";
                List<ColumnaConfig> cols = new ArrayList<>();
            
                // Guardar otros cols
                while (dbCols.next()) {
                    // visible=true y contrasena=false por defecto (definido en ColumnaConfig)
                    ColumnaConfig col = new ColumnaConfig();
                    // Nombre de la columna
                    String nombreCol = dbCols.getString("COLUMN_NAME");
                    col.setNombre(nombreCol);
                    // Es pk: true, no es pk: false
                    col.setPk(nombreCol.equals(columnaPk));
                    // Es nullable: true, no es nullable: false
                    col.setNullable(dbCols.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    // Tipo de dato
                    col.setTipo(TipoDatoMapper.toTexto(dbCols.getInt("DATA_TYPE")));
                    // Es autoincremental: true, no es autoincremental: false
                    col.setAutoincremental("YES".equalsIgnoreCase(dbCols.getString("IS_AUTOINCREMENT")));
                    cols.add(col);
                }
                
                // Almacenar foreign keys
                while (dbFks.next()) {
                    String columna = dbFks.getString("FKCOLUMN_NAME");
                    String tablaRef = dbFks.getString("PKTABLE_NAME");
                    String colRef = dbFks.getString("PKCOLUMN_NAME");
                    // Buscar columna y almacenar en ella los datos de fk
                    cols.stream()
                    .filter(c -> c.getNombre().equals(columna))
                    .findFirst()
                    .ifPresent(c -> {
                        c.setReferenciaTabla(tablaRef);
                        c.setReferenciaCol(colRef);
                    });
                }
                return cols;
            }
        }
    }
    
    /**
     * Borra la configuración de la tabla en db4o.
     *
     * @param nombreLogico Nombre de la tabla.
     */
    public void eliminarConfiguracion(String nombreLogico) {
        metaDao.eliminarConfiguracion(nombreLogico);
    }

    /**
     * Construye el nombre amigable a partir del nombre lógico. Ejemplo:
     * datos_clientes -- Datos clientes
     *
     * @param nombreLogico Nombre lógico de la tabla
     * @return Nombre amigable
     */
    public String crearNombreAmigable(String nombreLogico) {
        validador.validarNombre(nombreLogico);
        // Construir nombre amigable
        String nombreAmigable = nombreLogico.replace("_", " ");
        return nombreAmigable.substring(0, 1).toUpperCase() + nombreAmigable.substring(1);
    }
}
