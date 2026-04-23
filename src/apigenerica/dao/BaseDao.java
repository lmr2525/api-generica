/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.dao;

import apigenerica.EntidadMapper;
import apigenerica.model.ColumnaConfig;
import apigenerica.model.EntidadDinamica;
import apigenerica.model.RelacionConfig;
import java.sql.*;
import java.util.*;

/**
 * @author Grupo1 Consultas CRUD con las tablas
 */
public class BaseDao {

    private final EntidadMapper entidadMapper;

    public BaseDao(EntidadMapper entidadMapper) {
        this.entidadMapper = entidadMapper;
    }

    /**
     * Obtener todos los registros de una tabla
     *
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

        try (PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                resultado.add(entidadMapper.mapear(rs, campos, nombrePk));
            }
        }
        return resultado;
    }

    /**
     * Obtener un registro de una tabla a partir de una columna
     *
     * @param conn Conexión a MySQL
     * @param nombreTabla Nombre de la tabla en la que se buscará
     * @param campos
     * @param nombreCol
     * @param id
     * @return
     * @throws SQLException
     */
    public EntidadDinamica<Object> obtenerPorCol(Connection conn, String nombreTabla,
            List<ColumnaConfig> campos, String nombreCol, Object id) throws SQLException {

        String sql = "SELECT * FROM `" + nombreTabla + "` WHERE `" + nombreCol + "` = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return entidadMapper.mapear(rs, campos);
                }
            }
        }
        return null; // Retorna null si no encuentra el registro
    }

    /**
     * Construir y ejecutar una consulta INSERT en una base de datos SQL
     *
     * @param conn
     * @param nombreTabla
     * @param entidad
     * @return
     * @throws SQLException
     */
    public long insertar(Connection conn, String nombreTabla, EntidadDinamica<?> entidad) throws SQLException {
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
            String col = columnas.get(i);
            sql.append("`").append(columnas.get(i)).append("`"); // `Nombre de la columna`
            placeholders.append("?");
            valores.add(datos.get(col));

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
     *
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

    public Map<String, Long> insertarTransaccional(Connection conn, List<String> ordenTablas,
            Map<String, Map<String, Object>> datosPorTabla,
            Map<String, String> fkEntreTablas) throws SQLException {
        Map<String, Long> idsGenerados = new LinkedHashMap<>();
        for (String tabla : ordenTablas) {
            Map<String, Object> datos = datosPorTabla.get(tabla);
            if (datos == null) continue;
            if (fkEntreTablas.containsKey(tabla)) {
                String columnaFk = fkEntreTablas.get(tabla);
                String tablaAnterior = ordenTablas.get(ordenTablas.indexOf(tabla) - 1);
                datos.put(columnaFk, idsGenerados.get(tablaAnterior));
            }
            EntidadDinamica<Object> entidad = new EntidadDinamica<>("id");
            datos.forEach(entidad::set);
            idsGenerados.put(tabla, insertar(conn, tabla, entidad));
        }
        return idsGenerados;
    }

    public List<EntidadDinamica<Object>> seleccionarConIncludes(
            Connection conn,
            String tablaPrincipal,
            List<ColumnaConfig> columnasPrincipal,
            List<RelacionConfig> relaciones) throws SQLException {

        StringBuilder sql = new StringBuilder("SELECT t1.*");
        StringBuilder joins = new StringBuilder(" FROM `").append(tablaPrincipal).append("` t1");

        // Construir SELECT y JOINs para cada relación N:1
        int aliasCount = 2;
        for (RelacionConfig rel : relaciones) {
            if (rel.getCardinalidad().equals("N:1")) {
                String alias = "t" + aliasCount;

                // Añadimos campos de la tabla destino con prefijo para evitar colisiones
                for (String col : rel.getColumnasDestino()) {
                    sql.append(", ").append(alias).append(".`").append(col)
                       .append("` AS `").append(rel.getTablaDestino()).append("_").append(col).append("`");
                }

                joins.append(" LEFT JOIN `").append(rel.getTablaDestino()).append("` ").append(alias)
                        .append(" ON t1.`").append(rel.getFkColumna()).append("` = ")
                        .append(alias).append(".`id` "); // Siempre contra .id por tu regla de PK forzada

                aliasCount++;
            }
        }

        sql.append(joins);

        List<EntidadDinamica<Object>> resultados = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString()); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                // Aquí el ObjectMapper debe ser capaz de agrupar los campos con alias
                resultados.add(objectMapper.mapearConRelaciones(rs, columnasPrincipal, relaciones));
            }
        }
        return resultados;
    }

    public int actualizarTransaccional(Connection conn, List<String> ordenTablas,
            Map<String, Map<String, Object>> datosPorTabla,
            Map<String, String> columnasPk, Object valorPk) throws SQLException {
        int filasAfectadas = 0;
        for (String tabla : ordenTablas) {
            Map<String, Object> datos = datosPorTabla.get(tabla);
            if (datos == null) continue;
            filasAfectadas += actualizar(conn, tabla, datos, columnasPk.get(tabla), valorPk);
        }
        return filasAfectadas;
    }


    public int eliminarTransaccional(Connection conn, List<String> ordenTablas,
                Map<String, String> columnasPk, Object valorPk) throws SQLException {
            int filasAfectadas = 0;
            for (int i = ordenTablas.size() - 1; i >= 0; i--) {
                String tabla = ordenTablas.get(i);
                filasAfectadas += eliminar(conn, tabla, columnasPk.get(tabla), valorPk);
            }
            return filasAfectadas;
        }
}
