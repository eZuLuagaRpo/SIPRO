package com.bancolombia.sipro.validations.domain.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Contrato de almacenamiento para guardar, mover, leer y listar archivos del proceso.
 */
public interface FileStorageService {
    /**
     * Guarda un archivo y retorna la ruta o identificador para recuperarlo.
     * 
     * @param file         Archivo a guardar
     * @param subDirectory Subdirectorio opcional (puede ser null)
     * @return Ruta relativa o URL del archivo guardado
     * @throws IOException Si ocurre un error de E/S
     */
    String store(MultipartFile file, String subDirectory) throws IOException;

    /**
     * Guarda un archivo con un nombre específico.
     * 
     * @param file           Archivo a guardar
     * @param subDirectory   Subdirectorio opcional
     * @param customFileName Nombre específico para el archivo
     * @return Ruta relativa del archivo guardado
     * @throws IOException Si ocurre un error de E/S
     */
    String store(MultipartFile file, String subDirectory, String customFileName) throws IOException;

    /**
     * Elimina un archivo dado su path.
     * 
     * @param path Ruta del archivo
     * @return true si fue eliminado
     */
    boolean delete(String path);

    /**
     * Mueve un archivo de su ubicación actual a un nuevo subdirectorio.
     * 
     * @param currentPath     Ruta relativa actual del archivo
     * @param newSubDirectory Nuevo subdirectorio destino
     * @return Nueva ruta relativa del archivo
     * @throws IOException Si ocurre un error de E/S
     */
    String move(String currentPath, String newSubDirectory) throws IOException;

    /**
     * Genera la ruta de almacenamiento estándar para un archivo
     */
    default String generateKey(String directory, LocalDate date, String uid, String filename) {
        return String.format("%s/%s/%s__%s", directory, date.toString(), uid, filename);
    }

    /**
     * Obtiene la ruta absoluta de un archivo dado su path relativo.
     * 
     * @param relativePath Ruta relativa del archivo
     * @return Path absoluto del archivo
     */
    Path getAbsolutePath(String relativePath);

    /**
     * Abre un stream del archivo sin cargarlo completo en memoria.
     *
     * @param relativePath Ruta relativa del archivo
     * @return Stream de lectura del archivo
     * @throws IOException Si ocurre un error de E/S
     */
    InputStream openStream(String relativePath) throws IOException;

    /**
     * Obtiene el contenido del archivo como bytes.
     * 
     * @param relativePath Ruta relativa del archivo
     * @return Array de bytes del archivo
     * @throws IOException Si ocurre un error de E/S
     */
    byte[] getFileAsBytes(String relativePath) throws IOException;

    /**
     * Almacena contenido en bytes directamente en S3 con una key específica.
     * 
     * @param content     Bytes del contenido a almacenar
     * @param key         Key completa (ruta) en S3
     * @param contentType Tipo MIME del contenido
     * @return Key almacenada
     * @throws IOException Si ocurre un error de E/S
     */
    String storeBytes(byte[] content, String key, String contentType) throws IOException;

    /**
     * Lista las keys de objetos bajo un prefijo dado en S3.
     * 
     * @param prefix Prefijo para filtrar objetos
     * @return Lista de keys que coinciden con el prefijo
     */
    List<String> listObjects(String prefix);
}
