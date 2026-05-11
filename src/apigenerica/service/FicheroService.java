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
import java.time.LocalDateTime;

/**
 *
 * @author Grupo1
 */
public class FicheroService {

    private static final String RUTA = "ficheros.db4o";
    private final ObjectContainer db = ConexionDb4o.getConexion(RUTA);

    public void guardar(String uuid, String tabla, Long id, UploadedFile file) {
        try (InputStream is = file.getContent()) {
            // Obtener todos los bytes del archivo
            byte[] bytes = inputStreamToByteArray(is);

            Fichero obj = new Fichero(
                    uuid,
                    file.getFilename(),
                    file.getContentType(),
                    file.getSize(),
                    bytes,
                    LocalDateTime.now()
            );
            db.store(obj);
            db.commit();
        } catch (IOException e) {
            throw new RuntimeException("Error al leer los bytes del fichero", e);
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
        ObjectSet<Fichero> result = db.queryByExample(new Fichero(uuid));
        if (result.hasNext()) {
            db.delete(result.next());
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
        return result.hasNext() ? result.next() : null;
    }
}
