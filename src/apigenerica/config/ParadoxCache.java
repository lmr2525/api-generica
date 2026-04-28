/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.model.TablaConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Grupo1
 */
public class ParadoxCache {

    private final Connection conexion;
    private final ReentrantLock lock = new ReentrantLock();

    public ParadoxCache(String ruta) throws SQLException {
        this.conexion = DriverManager.getConnection("jdbc:paradox:/" + ruta);
    }

    public Optional<TablaConfig> get(String nombreLogico) throws SQLException {
        lock.lock();
        try {
            String sql = "SELECT * FROM meta_cache WHERE nombre_logico = ?";
            try (PreparedStatement stmt = conexion.prepareStatement(sql)) {
                stmt.setString(1, nombreLogico);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return Optional.of(mapearTabla(rs));
                }
                return Optional.empty();
            }
        } finally {
            lock.unlock();
        }
    }

    public void imsertar(TablaConfig tabla) throws SQLException {
        lock.lock();
        try {
            String sql = "INSERT INTO meta_cache (nombre_logico, datos) VALUES (?, ?)";
            try (PreparedStatement stmt = conexion.prepareStatement(sql)) {
                stmt.setString(1, tabla.getNombreLogico());
                stmt.setString(2, serializarTabla(tabla));
                stmt.executeUpdate();
            }
        } finally {
            lock.unlock();
        }
    }

    public void borrar(String nombreLogico) throws SQLException {
        lock.lock();
        try {
            String sql = "DELETE FROM meta_cache WHERE nombre_logico = ?";
            try (PreparedStatement stmt = conexion.prepareStatement(sql)) {
                stmt.setString(1, nombreLogico);
                stmt.executeUpdate();
            }
        } finally {
            lock.unlock();
        }
    }
}
