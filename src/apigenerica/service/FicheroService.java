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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Grupo1
 */
public class FicheroService {

    private static final String RUTA = "ficheros.db4o";
    private final ObjectContainer db = ConexionDb4o.getConexion(RUTA);

    // Tamaño máximo permitido por fichero al subir a db4o
    private static final long TAMANO_MAX = 20L * 1024 * 1024; // 20MB
    // Ruta del disco en la que se almacenan los archivos que exceden el límite
    private static final String CARPETA_FICHEROS = "storage/ficheros/";

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
                    bytes = comprimir(bytes);
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
     * Comprime los bytes de un fichero antes de almacenarlos en db4o
     *
     * @param datos Cadena de bytes del fichero a comprimir
     * @return Cadena de bytes resultante de la compresión
     * @throws IOException
     */
    public byte[] comprimir(byte[] datos) throws IOException {
        if (datos == null || datos.length == 0) {
            return datos;
        }
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(obj)) {
            gzip.write(datos);
        }
        return obj.toByteArray();
    }

    /**
     * Descomprime los bytes de un fichero almacenado en db4o
     *
     * @param datosComprimidos Cadena de bytes a descomprimir
     * @return Cadena de bytes resultante de la descompresión
     * @throws IOException
     */
    public byte[] descomprimir(byte[] datosComprimidos) throws IOException {
        if (datosComprimidos == null || datosComprimidos.length == 0) {
            return datosComprimidos;
        }
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(datosComprimidos))) {
            return inputStreamToByteArray(gis);
        } catch (IOException e) {
            // Si no estaba comprimido, devolvemos los datos tal cual
            return datosComprimidos;
        }
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
        Fichero f = obtenerMetadatos(uuid); // Buscar fichero
        if (f != null) {
            if (f.isEnDisco()) {
                try { // Eliminar el archivo del disco antes de borrar el objeto de db4o
                    Files.deleteIfExists(Paths.get(f.getRuta()));
                } catch (IOException e) {
                    System.err.println("No se pudo eliminar el archivo del disco duro.");
                }
            }
            db.delete(f); // Eliminar objeto de db4o
            db.commit();
        }
    }

    /**
     * Recuperar contenido de un archivo a partir de su UUID
     * 
     * @param uuid
     * @return
     * @throws IOException 
     */
    public InputStream obtenerStream(String uuid) throws IOException {
        Fichero file = obtenerMetadatos(uuid); // Recuperar objeto de db4o
        if (file == null) {
            return null;
        }

        if (file.isEnDisco()) {
            // Devolver un stream directo al archivo físico
            return Files.newInputStream(Paths.get(file.getRuta()));
        } else if (file.isComprimido()) {
            // Descomprir
            byte[] descompr = descomprimir(file.getContenido());
            return new ByteArrayInputStream(descompr);
        } else {
            // Archivo sin comprimir
            return new ByteArrayInputStream(file.getContenido());
        }
    }

    /**
     * Recuperar metadatos de un archivo a partir de su UUID
     *
     * @param uuid UUID del archivo
     * @return Objeto Fichero o null si no se encontró
     */
    public Fichero obtenerMetadatos(String uuid) {
        ObjectSet<Fichero> result = db.queryByExample(new Fichero(uuid));
        return result.hasNext() ? result.next() : null;
    }
}
