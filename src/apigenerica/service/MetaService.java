/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.CampoConfig;
import apigenerica.TipoDatoMapper;
import apigenerica.config.Conexion;
import apigenerica.dao.MetaDao;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Grupo1
 */
public class MetaService {

    MetaDao metaDao = new MetaDao();

    public void guardarConfiguracion(String nombreLogico) throws SQLException {
        // Si ya existe, eliminar la configuración anterior
        if (metaDao.existeTabla(nombreLogico)) {
            metaDao.eliminarConfiguracion(nombreLogico);
        }
        
        // Crear nombre amigable
        String nombreAmigable = nombreLogico.replace("_", " ");
        nombreAmigable = nombreAmigable.substring(0, 1).toUpperCase() + nombreAmigable.substring(1);

        try (Connection conn = Conexion.getConexion()) {
            DatabaseMetaData meta = conn.getMetaData();

            // Obtener columnas, primary key y foreign keys
            try (ResultSet dbCols = meta.getColumns(null, null, nombreLogico, null);
                ResultSet dbPk = meta.getPrimaryKeys(null, null, nombreLogico);
                ResultSet dbFks = meta.getImportedKeys(null, null, nombreLogico)) {
                List<CampoConfig> confCols = new ArrayList<>();

                // Almacenar primary key
                String columnaPk = "";
                if (dbPk.next()) {
                    columnaPk = dbPk.getString("COLUMN_NAME");
                }

                // Guardar otros campos
                while (dbCols.next()) {
                    CampoConfig campo = new CampoConfig();
                    String nombreCol = dbCols.getString("COLUMN_NAME");
                    campo.setNombre(nombreCol);
                    campo.setPk(nombreCol.equals(columnaPk)); // 0 o 1
                    campo.setNullable(dbCols.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    // Tipo de dato
                    int tipoSql = dbCols.getInt("DATA_TYPE");
                    campo.setTipo(TipoDatoMapper.toTexto(tipoSql));
                    // Es autoincremental
                    String isAuto = dbCols.getString("IS_AUTOINCREMENT");
                    campo.setAutoincremental("YES".equalsIgnoreCase(isAuto));

                    confCols.add(campo);
                }

                // Almacenar foreign keys
                while (dbFks.next()) {
                    String columna = dbFks.getString("FKCOLUMN_NAME");
                    String tablaRef = dbFks.getString("PKTABLE_NAME");
                    String campoRef = dbFks.getString("PKCOLUMN_NAME");
                    confCols.stream()
                    .filter(c -> c.getNombre().equals(columna))
                    .findFirst()
                    .ifPresent(c -> {
                        c.setReferenciaTabla(tablaRef);
                        c.setReferenciaCampo(campoRef);
                    });
                }

                // Guardar estructura en sys_tablas y sys_campos
                metaDao.guardarConfiguracion(nombreLogico, nombreAmigable, confCols);
            }
        }
    }
    
    public void eliminarConfiguracion(String nombreLogico) throws SQLException {
        metaDao.eliminarConfiguracion(nombreLogico);
    }
}
