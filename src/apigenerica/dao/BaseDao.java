/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dao;

import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import java.sql.*;
import java.util.*;

/**
 * @author Grupo1
 * Consultas CRUD con las tablas
 */
public class BaseDao {
    /**
     * Obtener todos los registros de una tabla
     * @param conn
     * @param nombreTabla
     * @param campos
     * @param nombrePk
     * @return
     * @throws SQLException 
     */
    public List<EntidadDinamica<Object>> seleccionarTodo(Connection conn, String nombreTabla,
            List<ColumnaConfig> campos, String nombrePk) throws SQLException {
        String sql = "SELECT * FROM `" + nombreTabla + "`";
        List<EntidadDinamica<Object>> resultado = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                resultado.add(objectMapper.mapear(rs, campos, nombrePk));
            }
        }
        return resultado;
    }
    /**
     * Obtener un registro de una tabla a partir de su ID
     * @param conn Conexión a MySQL
     * @param nombreTabla Nombre de la tabla en la que se buscará
     * @param campos
     * @param nombrePk
     * @param id
     * @return
     * @throws SQLException 
     */
    public EntidadDinamica<Object> obtenerPorId(Connection conn, String nombreTabla, 
        List<ColumnaConfig> campos, String nombrePk, Object id) throws SQLException {
    
        String sql = "SELECT * FROM `" + nombreTabla + "` WHERE `" + nombrePk + "` = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return objectMapper.mapear(rs, campos, nombrePk);
                }
            }
        }
        return null; // Retorna null si no encuentra el registro
    }
    
    /** 
     * Construir y ejecutar una consulta INSERT en una base de datos SQL
     * @param conn
     * @param nombreTabla
     * @param entidad
     * @throws SQLException 
     */
    public void insertar(Connection conn, String nombreTabla, EntidadDinamica<?> entidad) throws SQLException {
        Map<String, Object> datos = entidad.getTodo();
        
        if (datos == null || datos.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para insertar");
        }

        List<String> columnas = new ArrayList<>(datos.keySet());
        
        // Construir sentencia de INSERT
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(nombreTabla).append("` (");
        StringBuilder placeholders = new StringBuilder("VALUES ("); // String con placeholders (?) de los valores
        
        List<Object> valores = new ArrayList<>();

        for (int i = 0; i < columnas.size(); i++) {
            sql.append("`").append(columnas.get(i)).append("`"); // `Nombre de la columna`
            placeholders.append("?");

            // Separar columnas y ? por comas
            if (i < columnas.size() - 1) {
                sql.append(", ");
                placeholders.append(", ");
            }
        }
        // Agregar placeholders al final de la sentencia
        sql.append(") ").append(placeholders).append(")");

        // RETURN_GENERATED_KEYS por si la inserción genera ID autoincremental
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS)) {
            // Preparar consulta enlazando los valores
            for (int j = 0; j < valores.size(); j++) {
                stmt.setObject(j + 1, valores.get(j));
            }
            stmt.executeUpdate();

            // Recuperar ID autoincremental si la tabla lo tiene
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0; // No tiene ID autoincremental
    }

    /**
     * Construir y ejecutar una sentencia UPDATE en una base de datos SQL
     * @param conn
     * @param nombreTabla
     * @param datos
     * @param columnaPk
     * @param valorPk
     * @return
     * @throws SQLException 
     */
    public int actualizar(Connection conn, String nombreTabla, Map<String, Object> datos, String columnaPk, Object valorPk) throws SQLException {
        // Construir sentencia UPDATE
        StringBuilder sql = new StringBuilder("UPDATE `").append(nombreTabla).append("` SET ");
        List<Object> valores = new ArrayList<>();
        int i = 0;

        for (Map.Entry<String, Object> entry : datos.entrySet()) {
            sql.append("`").append(entry.getKey()).append("` = ?"); // `Nombre de la columna` = ?
            valores.add(entry.getValue()); // Valores a insertar

            // Separar columnas por comas
            if (i < datos.size() - 1) {
                sql.append(", ");
            }
            i++;
        }

        sql.append(" WHERE `").append(columnaPk).append("` = ?");
        valores.add(valorPk);

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int j = 0; j < valores.size(); j++) {
                stmt.setObject(j + 1, valores.get(j));
            }
            return stmt.executeUpdate(); // Devuelve el número de filas afectadas
        }
    }


    public int eliminar(Connection conn, String nombreTabla, String columnaPk, Object valorPk) throws SQLException {
        String sql = "DELETE FROM `" + nombreTabla + "` WHERE `" + columnaPk + "` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, valorPk);
            return stmt.executeUpdate();
        }
    }
    
    public Map<String, Long> insertarVarias(Connection conn, List<String> ordenTablas, 
        Map<String, Map<String, Object>> datosPorTabla,
        Map<String, String> fkEntreTablas) throws SQLException {
        // fkEntreTablas: {"empleados": "Id_persona"} 
        // indica que empleados.Id_persona = ID generado por personas

        Map<String, Long> idsGenerados = new LinkedHashMap<>();

        for (String tabla : ordenTablas) {
            Map<String, Object> datos = datosPorTabla.get(tabla);
            if (datos == null) continue;

            // Si esta tabla tiene FK que depende del ID de otra tabla ya insertada
            if (fkEntreTablas.containsKey(tabla)) {
                String columnaFk = fkEntreTablas.get(tabla);
                // Buscar el ID generado por la tabla referenciada
                String tablaRef = buscarTablaRef(ordenTablas, idsGenerados, tabla);
                if (tablaRef != null) {
                    datos.put(columnaFk, idsGenerados.get(tablaRef));
                }
            }

            long idGenerado = insertar(conn, tabla, datos);
            idsGenerados.put(tabla, idGenerado);
        }
        return idsGenerados;
    }

    private String buscarTablaRef(List<String> orden, Map<String, Long> generados, String tablaActual) {
        int posActual = orden.indexOf(tablaActual);
        // Devuelve la última tabla anterior que generó un ID
        for (int i = posActual - 1; i >= 0; i--) {
            if (generados.containsKey(orden.get(i))) {
                return orden.get(i);
            }
        }
        return null;
    }

    public int actualizarVarias(Connection conn, List<String> ordenTablas,
            Map<String, Map<String, Object>> datosPorTabla,
            Map<String, String> columnasPk,
            Object valorPk) throws SQLException {
        int filasAfectadas = 0;
        for (String tabla : ordenTablas) {
            Map<String, Object> datos = datosPorTabla.get(tabla);
            if (datos == null) continue;
            String pk = columnasPk.get(tabla);
            filasAfectadas += actualizar(conn, tabla, datos, pk, valorPk);
        }
        return filasAfectadas;
    }

    public int eliminarVarias(Connection conn, List<String> ordenTablas,
            Map<String, String> columnasPk,
            Object valorPk) throws SQLException {
        int filasAfectadas = 0;
        // Eliminar en orden inverso para respetar FK
        for (int i = ordenTablas.size() - 1; i >= 0; i--) {
            String tabla = ordenTablas.get(i);
            String pk = columnasPk.get(tabla);
            filasAfectadas += eliminar(conn, tabla, pk, valorPk);
        }
        return filasAfectadas;
    }
}
