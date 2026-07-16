package com.bancolombia.sipro.validations.infrastructure.storage;

import com.bancolombia.sipro.validations.domain.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Implementación de FileStorageService que usa el sistema de archivos local.
 * <p>
 * Diseñada para entornos de desarrollo donde la conexión a S3 (LocalStack en
 * WSL2/Docker) es inestable debido a problemas de port-forwarding de WSL2.
 * <p>
 * Activar con: {@code app.storage.type=local} (default en perfil dev).
 * Los archivos se almacenan en {@code app.storage.local.base-dir}.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local")
public class LocalFileStorageService implements FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);

    @Value("${app.storage.local.base-dir:C:/s3mock2/sipro-local-storage}")
    private String baseDir;

    private Path basePath;

    /**
     * Inicializa el directorio base donde se guardarán los archivos locales.
     */
    @PostConstruct
    public void init() {
        this.basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
            logger.info("[LocalStorage] Almacenamiento local activo en: {}", basePath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio base de almacenamiento: " + basePath, e);
        }
    }

    @Override
    public String store(MultipartFile file, String subDirectory) throws IOException {
        return store(file, subDirectory, null);
    }

    /**
     * Guarda un archivo en disco local usando un subdirectorio y nombre opcional.
     */
    @Override
    public String store(MultipartFile file, String subDirectory, String customFileName) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("No se puede guardar un archivo vacío.");
        }

        // Determinar nombre del archivo
        String filenameToUse = customFileName;
        if (filenameToUse == null || filenameToUse.isEmpty()) {
            filenameToUse = file.getOriginalFilename();
            if (filenameToUse == null || filenameToUse.isEmpty()) {
                filenameToUse = "archivo_" + System.currentTimeMillis() + ".xlsx";
            }
        }

        // Sanitizar
        String sanitizedFilename = filenameToUse.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Construir la key (ruta relativa)
        String key = sanitizedFilename;
        if (subDirectory != null && !subDirectory.isEmpty()) {
            key = subDirectory + "/" + sanitizedFilename;
        }

        // Verificar duplicados
        Path targetPath = resolveSecure(key);
        if (Files.exists(targetPath)) {
            String extension = "";
            String baseName = sanitizedFilename;
            int dotIndex = sanitizedFilename.lastIndexOf(".");
            if (dotIndex > 0) {
                extension = sanitizedFilename.substring(dotIndex);
                baseName = sanitizedFilename.substring(0, dotIndex);
            }
            sanitizedFilename = baseName + "_" + System.currentTimeMillis() + extension;
            key = (subDirectory != null && !subDirectory.isEmpty())
                    ? subDirectory + "/" + sanitizedFilename
                    : sanitizedFilename;
            targetPath = resolveSecure(key);
        }

        // Crear directorios padre y guardar
        Files.createDirectories(targetPath.getParent());
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        logger.info("[LocalStorage] Archivo guardado: {}", key);
        return key;
    }

    @Override
    public boolean delete(String path) {
        try {
            Path filePath = resolveSecure(path);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                logger.info("[LocalStorage] Archivo eliminado: {}", path);
            }
            return deleted;
        } catch (IOException e) {
            logger.error("[LocalStorage] Error al eliminar archivo '{}': {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * Mueve un archivo local a otra ruta lógica dentro del almacenamiento.
     */
    @Override
    public String move(String currentPath, String newSubDirectory) throws IOException {
        if (currentPath == null || currentPath.isEmpty()) {
            throw new IOException("La ruta actual del archivo no puede estar vacía.");
        }

        Path sourcePath = resolveSecure(currentPath);
        if (!Files.exists(sourcePath)) {
            throw new IOException("El archivo no existe: " + currentPath);
        }

        // Obtener nombre del archivo
        String fileName = currentPath.contains("/")
                ? currentPath.substring(currentPath.lastIndexOf("/") + 1)
                : currentPath;

        // Construir nueva key
        String newKey = (newSubDirectory != null && !newSubDirectory.isEmpty())
                ? newSubDirectory + "/" + fileName
                : fileName;

        Path targetPath = resolveSecure(newKey);

        // Si la key destino ya existe, agregar timestamp
        if (Files.exists(targetPath)) {
            String extension = "";
            String baseName = fileName;
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex > 0) {
                extension = fileName.substring(dotIndex);
                baseName = fileName.substring(0, dotIndex);
            }
            fileName = baseName + "_" + System.currentTimeMillis() + extension;
            newKey = (newSubDirectory != null && !newSubDirectory.isEmpty())
                    ? newSubDirectory + "/" + fileName
                    : fileName;
            targetPath = resolveSecure(newKey);
        }

        // Mover
        Files.createDirectories(targetPath.getParent());
        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("[LocalStorage] Archivo movido de {} a {}", currentPath, newKey);
        return newKey;
    }

    @Override
    public Path getAbsolutePath(String relativePath) {
        return resolveSecure(relativePath);
    }

    @Override
    public InputStream openStream(String relativePath) throws IOException {
        Path filePath = resolveSecure(relativePath);
        if (!Files.exists(filePath)) {
            throw new IOException("Archivo no encontrado: " + relativePath);
        }
        return Files.newInputStream(filePath, StandardOpenOption.READ);
    }

    @Override
    public byte[] getFileAsBytes(String relativePath) throws IOException {
        Path filePath = resolveSecure(relativePath);
        if (!Files.exists(filePath)) {
            throw new IOException("Archivo no encontrado: " + relativePath);
        }
        return Files.readAllBytes(filePath);
    }

    @Override
    public String storeBytes(byte[] content, String key, String contentType) throws IOException {
        Path targetPath = resolveSecure(key);
        Files.createDirectories(targetPath.getParent());
        Files.write(targetPath, content);
        logger.info("[LocalStorage] Bytes guardados: {} ({} bytes)", key, content.length);
        return key;
    }

    /**
     * Lista archivos guardados bajo un prefijo o coincidencia parcial de nombre.
     */
    @Override
    public List<String> listObjects(String prefix) {
        List<String> keys = new ArrayList<>();
        Path prefixPath = basePath.resolve(prefix.replace("/", basePath.getFileSystem().getSeparator()));

        if (!Files.exists(prefixPath)) {
            // Intentar como directorio padre
            Path parentDir = prefixPath.getParent();
            if (parentDir != null && Files.isDirectory(parentDir)) {
                String filePrefix = prefixPath.getFileName().toString();
                try (Stream<Path> stream = Files.walk(parentDir)) {
                    stream.filter(Files::isRegularFile)
                          .filter(p -> p.getFileName().toString().startsWith(filePrefix))
                          .forEach(p -> keys.add(toKey(p)));
                } catch (IOException e) {
                    logger.error("[LocalStorage] Error listando objetos con prefijo '{}': {}", prefix, e.getMessage());
                }
            }
            return keys;
        }

        if (Files.isDirectory(prefixPath)) {
            try (Stream<Path> stream = Files.walk(prefixPath)) {
                stream.filter(Files::isRegularFile)
                      .forEach(p -> keys.add(toKey(p)));
            } catch (IOException e) {
                logger.error("[LocalStorage] Error listando objetos con prefijo '{}': {}", prefix, e.getMessage());
            }
        } else if (Files.isRegularFile(prefixPath)) {
            keys.add(toKey(prefixPath));
        }

        logger.debug("[LocalStorage] Listados {} objetos con prefijo '{}'", keys.size(), prefix);
        return keys;
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Resuelve una key relativa contra el directorio base de forma segura
     * (previene path traversal).
     */
    private Path resolveSecure(String key) {
        // Normalizar separadores y resolver
        String normalizedKey = key.replace("\\", "/");
        Path resolved = basePath.resolve(normalizedKey).normalize();

        // Prevenir path traversal
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("Path traversal detectado: " + key);
        }
        return resolved;
    }

    /**
     * Convierte un Path absoluto a una key relativa usando "/" como separador.
     */
    private String toKey(Path absolutePath) {
        return basePath.relativize(absolutePath).toString().replace("\\", "/");
    }
}
