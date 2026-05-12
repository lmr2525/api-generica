/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package apigenerica.service;

import apigenerica.config.ConexionDb4o;
import apigenerica.model.Fichero;
import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import io.javalin.http.UploadedFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;

/**
 *
 * @author Grupo1
 */
public class FicheroService {

    private static final String RUTA = "ficheros.db4o";
    private final ObjectContainer db = ConexionDb4o.getConexion(RUTA);
    // Guardar ficheros muy pesados en el disco
    private static final String CARPETA_FICHEROS = "storage/ficheros/";
    private static final long TAMANO_MAX = 5 * 1024 * 1024; // 5MB

    public void guardar(String uuid, String tabla, UploadedFile file) {
        try (InputStream is = file.getContent()) {
            Fichero f;

            if (file.getSize() > TAMANO_MAX) {
                String ruta = guardarEnDisco(uuid, tabla, is, file.getFilename());
                f = new Fichero(
                        uuid,
                        file.getFilename(),
                        file.getContentType(),
                        file.getSize(),
                        ruta,
                        LocalDateTime.now()
                );
            } else {
                // Obtener todos los bytes del archivo
                byte[] bytes = inputStreamToByteArray(is);
                boolean comprimido = false;

                if (file.getSize() > 1024 && esComprimible(file.getContentType())) {
                    // Comprimir si pesa más de 1KB y es comprimible
                    bytes = GzipUtil.comprimir(bytes);
                    comprimido = true;
                }

                f = new Fichero(
                        uuid,
                        file.getFilename(),
                        file.getContentType(),
                        file.getSize(),
                        bytes,
                        comprimido,
                        LocalDateTime.now()
                );
            }
            db.store(f);
            db.commit();
        } catch (IOException e) {
            throw new RuntimeException("Error al procesar el fichero", e);
        }
    }

    private String guardarEnDisco(String uuid, String tabla, InputStream is, String nombreOriginal) throws IOException {
        Path directorio = Paths.get(CARPETA_FICHEROS, tabla);
        Files.createDirectories(directorio);
        String nombreFisico = uuid + "_" + nombreOriginal;
        Path rutaArchivo = directorio.resolve(nombreFisico);
        Files.copy(is, rutaArchivo, StandardCopyOption.REPLACE_EXISTING);
        return rutaArchivo.toString();
    }

    /**
     * Comprueba si el archivo es comprimible a partir de su Mime Type
     *
     * @param mime Mime Type del archivo
     * @return true si el archivo es comprimible; false, en caso contrario
     */
    private boolean esComprimible(String mime) {
        if (mime == null) {
            return false;
        }
        String m = mime.toLowerCase();
        return m.contains("text") || m.contains("pdf") || m.contains("json") || m.contains("xml");
    }

    /**
     * Leer bytes
     *
     * @param is
     * @return
     * @throws IOException
     */
    private byte[] inputStreamToByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384]; // Buffer de 16KB
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Eliminar un fichero de db4o
     *
     * @param uuid UUID del archivo
     */
    public void eliminar(String uuid) {
        Fichero f = obtener(uuid);
        if (f != null) {
            if (f.isEnDisco()) {
                try { // Eliminar el archivo del disco
                    Files.deleteIfExists(Paths.get(f.getRuta()));
                } catch (IOException e) {
                    System.err.println("No se pudo eliminar el archivo físico.");
                }
            }
            db.delete(f); // Eliminar registro de db4o
            db.commit();
        }
    }

    /**
     * Recuperar el archivo de db4o
     *
     * @param uuid UUID del archivo
     * @return Objeto Fichero
     */
    public Fichero obtener(String uuid) {
        ObjectSet<Fichero> result = db.queryByExample(new Fichero(uuid));
        if (!result.hasNext()) {
            return null;
        }

        Fichero file = result.next();
        
        // Cargar desde el disco
        if (file.isEnDisco()) {
            try {
                file.setContenido(Files.readAllBytes(Paths.get(file.getRuta())));
            } catch (IOException e) {
                System.err.println("Error: No se ha encontrado el archivo.");
            }
        } else if (file.isComprimido()) { // Cargar descomprimido desde db4o
            try {
                file.setContenido(GzipUtil.descomprimir(file.getContenido()));
            } catch (Exception e) {
                System.err.println("Error al descomprimir fichero.");
            }
        }
        return file;
    }
}
