package com.bancolombia.sipro.validations.infrastructure.storage;

import com.bancolombia.sipro.validations.domain.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementa el almacenamiento de archivos sobre S3 o LocalStack con tolerancia a fallos de conexión.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3FileStorageService implements FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3FileStorageService.class);

    /** Reintentos a nivel aplicación para operaciones S3 críticas (store/move). */
    private static final int APP_RETRY_MAX = 3;

    /** Delay base entre reintentos (ms). Suficiente para que WSL2 reactive forward. */
    private static final long APP_RETRY_DELAY_MS = 3_000;

    private final S3Client s3Client;

    /** Endpoint resuelto (puede ser WSL2 IP si localhost no era alcanzable). */
    private final String endpoint;

    @Value("${app.storage.s3.bucket}")
    private String bucketName;

    @Value("${app.storage.s3.region}")
    private String region;

    public S3FileStorageService(S3Client s3Client,
                                @Qualifier("s3ResolvedEndpoint") String resolvedEndpoint) {
        this.s3Client = s3Client;
        this.endpoint = resolvedEndpoint;
    }

    // La verificación/creación del bucket se delega a S3BucketInitializer
    // que corre en ApplicationReadyEvent con reintentos automáticos.

    @Override
    public String store(MultipartFile file, String subDirectory) throws IOException {
        return store(file, subDirectory, null);
    }

    /**
     * Sube un archivo a S3 aplicando sanitización, control de colisiones y reintentos.
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

        // Sanitizar el nombre (remover caracteres problemáticos)
        String sanitizedFilename = filenameToUse.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Construir la key (ruta en S3)
        String key = sanitizedFilename;
        if (subDirectory != null && !subDirectory.isEmpty()) {
            key = subDirectory + "/" + sanitizedFilename;
        }

        // Verificar si ya existe (solo si no es un nombre custom con UID que garantice
        // unicidad)
        // Pero por seguridad siempre verificamos
        if (objectExists(key)) {
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
        }

        // Subir archivo a S3 con retry a nivel aplicación.
        // WSL2 auto-forward puede caerse por idle; el warm-up previo + retry lo reactiva.
        warmUpConnection(); // pre-calentamiento: activa WSL2 forward ANTES del primer intento
        SdkClientException lastConnectionError = null;
        for (int attempt = 1; attempt <= APP_RETRY_MAX; attempt++) {
            try {
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(file.getContentType())
                        .build();

                s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

                logger.info("Archivo subido a S3: s3://{}/{}", bucketName, key);
                return key;
            } catch (S3Exception e) {
                logger.error("Error al subir archivo a S3: {}", e.getMessage());
                throw new IOException("Error al guardar archivo en S3: " + e.getMessage(), e);
            } catch (SdkClientException e) {
                lastConnectionError = e;
                if (attempt < APP_RETRY_MAX) {
                    logger.warn("[S3] Conexión fallida al subir archivo (intento {}/{}). "
                            + "Reactivando WSL2 forward y reintentando en {}ms... Error: {}",
                            attempt, APP_RETRY_MAX, APP_RETRY_DELAY_MS, e.getMessage());
                    warmUpConnection();
                    try { Thread.sleep(APP_RETRY_DELAY_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        // Agotados los reintentos
        logger.error("[S3] No se pudo conectar a S3 tras {} intentos para subir archivo. "
                + "Verifique que LocalStack esté corriendo. Endpoint: {}. Error: {}",
                APP_RETRY_MAX, endpoint,
                lastConnectionError != null ? lastConnectionError.getMessage() : "unknown");
        throw new IOException("Error de conexión con S3 (endpoint: " + endpoint + "): "
                + (lastConnectionError != null ? lastConnectionError.getMessage() : "unknown"),
                lastConnectionError);
    }

    @Override
    public boolean delete(String path) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();

            s3Client.deleteObject(deleteRequest);
            logger.info("Archivo eliminado de S3: s3://{}/{}", bucketName, path);
            return true;
        } catch (S3Exception e) {
            logger.error("Error al eliminar archivo de S3: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Reubica un objeto dentro del bucket copiándolo al nuevo destino y borrando el original.
     */
    @Override
    public String move(String currentPath, String newSubDirectory) throws IOException {
        if (currentPath == null || currentPath.isEmpty()) {
            throw new IOException("La ruta actual del archivo no puede estar vacía.");
        }

        // Verificar que el archivo existe
        if (!objectExists(currentPath)) {
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

        // Si la key destino ya existe, agregar timestamp
        if (objectExists(newKey)) {
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
        }

        try {
            // Copiar objeto a nueva ubicación (con retry para WSL2)
            SdkClientException lastErr = null;
            boolean copied = false;
            for (int attempt = 1; attempt <= APP_RETRY_MAX; attempt++) {
                try {
                    CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                            .sourceBucket(bucketName)
                            .sourceKey(currentPath)
                            .destinationBucket(bucketName)
                            .destinationKey(newKey)
                            .build();
                    s3Client.copyObject(copyRequest);
                    copied = true;
                    break;
                } catch (SdkClientException ce) {
                    lastErr = ce;
                    if (attempt < APP_RETRY_MAX) {
                        logger.warn("[S3] Conexión fallida al copiar objeto (intento {}/{}). Reactivando...",
                                attempt, APP_RETRY_MAX);
                        warmUpConnection();
                        try { Thread.sleep(APP_RETRY_DELAY_MS); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            if (!copied) {
                throw new IOException("Error de conexión con S3 al mover archivo: "
                        + (lastErr != null ? lastErr.getMessage() : "unknown"), lastErr);
            }

            // Eliminar archivo original
            delete(currentPath);

            logger.info("Archivo movido de {} a {} en S3", currentPath, newKey);
            return newKey;
        } catch (S3Exception e) {
            logger.error("Error al mover archivo en S3: {}", e.getMessage());
            throw new IOException("Error al mover archivo en S3: " + e.getMessage(), e);
        }
    }

    @Override
    public Path getAbsolutePath(String relativePath) {
        // Para S3, retornamos un Path virtual que representa la estructura
        // El path real es la URL de S3
        return Paths.get("s3://" + bucketName + "/" + relativePath);
    }

    /**
     * Genera una URL pre-firmada para descargar el archivo
     */
    public String getPresignedUrl(String key, Duration expiration) {
        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build()) {

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getRequest)
                    .build();

            return presigner.presignGetObject(presignRequest).url().toString();
        }
    }

    private boolean objectExists(String key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            logger.warn("Error checking if object exists: {}", e.getMessage());
            return false;
        } catch (SdkClientException e) {
            // Conexión caída — warm-up y reintentar UNA vez
            logger.warn("[S3] Conexión inestable al verificar '{}'. Reactivando forward...", key);
            warmUpConnection();
            try { Thread.sleep(2_000); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            try {
                s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
                return true;
            } catch (NoSuchKeyException retry) {
                return false;
            } catch (Exception retry) {
                logger.warn("[S3] No se pudo verificar existencia de '{}' tras reintento: {}",
                        key, retry.getMessage());
                return false;
            }
        }
    }

    /**
     * "Despierta" el port-forwarding de WSL2 haciendo un TCP connect a localhost:4566.
     * WSL2 auto-forward se desactiva tras ~30s de inactividad; un intento de conexión
     * TCP lo reactiva. Incluso si falla, el intento en sí activa el forwarding para
     * la siguiente conexión real.
     */
    private void warmUpConnection() {
        URI uri = URI.create(endpoint);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port <= 0) port = 4566;

        // Reintenta por ~10s con intervalos cortos para dar tiempo a que WSL2
        // reactive su auto-forward hacia Windows.
        for (int i = 1; i <= 20; i++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1_000);
                logger.debug("[S3] Warm-up TCP connect a {}:{} exitoso (intento {}).", host, port, i);
                return;
            } catch (Exception e) {
                // fallback explícito a IPv4 loopback cuando el host es localhost
                if ("localhost".equalsIgnoreCase(host)) {
                    try (Socket ipv4Socket = new Socket()) {
                        ipv4Socket.connect(new InetSocketAddress("127.0.0.1", port), 1_000);
                        logger.debug("[S3] Warm-up TCP connect a 127.0.0.1:{} exitoso (intento {}).", port, i);
                        return;
                    } catch (Exception ignored) {
                        // continuar reintentando
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        logger.warn("[S3] Warm-up TCP no logró abrir {}:{} tras múltiples intentos.", host, port);
    }

    /**
     * Descarga un objeto completo y lo devuelve como arreglo de bytes.
     */
    @Override
    public byte[] getFileAsBytes(String relativePath) throws IOException {
        warmUpConnection();
        SdkClientException lastConnectionError = null;
        for (int attempt = 1; attempt <= APP_RETRY_MAX; attempt++) {
            try {
                GetObjectRequest getRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(relativePath)
                        .build();

                return s3Client.getObjectAsBytes(getRequest).asByteArray();
            } catch (NoSuchKeyException e) {
                throw new IOException("Archivo no encontrado en S3: " + relativePath, e);
            } catch (S3Exception e) {
                logger.error("Error al descargar archivo de S3: {}", e.getMessage());
                throw new IOException("Error al descargar archivo de S3: " + e.getMessage(), e);
            } catch (SdkClientException e) {
                lastConnectionError = e;
                if (attempt < APP_RETRY_MAX) {
                    logger.warn("[S3] Conexión fallida al descargar '{}' (intento {}/{}). Reactivando...",
                            relativePath, attempt, APP_RETRY_MAX);
                    warmUpConnection();
                    try { Thread.sleep(APP_RETRY_DELAY_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new IOException("Error de conexión con S3 al descargar " + relativePath + " (endpoint: " + endpoint
                + "): " + (lastConnectionError != null ? lastConnectionError.getMessage() : "unknown"),
                lastConnectionError);
    }

    /**
     * Abre un stream de lectura directa sobre un objeto almacenado en S3.
     */
    @Override
    public InputStream openStream(String relativePath) throws IOException {
        warmUpConnection();
        SdkClientException lastConnectionError = null;
        for (int attempt = 1; attempt <= APP_RETRY_MAX; attempt++) {
            try {
                GetObjectRequest getRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(relativePath)
                        .build();

                ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(getRequest);
                logger.debug("[S3] Stream abierto para {}", relativePath);
                return stream;
            } catch (NoSuchKeyException e) {
                throw new IOException("Archivo no encontrado en S3: " + relativePath, e);
            } catch (S3Exception e) {
                logger.error("Error al abrir stream de S3: {}", e.getMessage());
                throw new IOException("Error al abrir stream de S3: " + e.getMessage(), e);
            } catch (SdkClientException e) {
                lastConnectionError = e;
                if (attempt < APP_RETRY_MAX) {
                    logger.warn("[S3] Conexión fallida al abrir stream de '{}' (intento {}/{}). Reactivando...",
                            relativePath, attempt, APP_RETRY_MAX);
                    warmUpConnection();
                    try { Thread.sleep(APP_RETRY_DELAY_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new IOException("Error de conexión con S3 al abrir stream de " + relativePath + " (endpoint: " + endpoint
                + "): " + (lastConnectionError != null ? lastConnectionError.getMessage() : "unknown"),
                lastConnectionError);
    }

    /**
     * Guarda contenido binario ya generado en la clave indicada del bucket.
     */
    @Override
    public String storeBytes(byte[] content, String key, String contentType) throws IOException {
        warmUpConnection();
        SdkClientException lastConnectionError = null;
        for (int attempt = 1; attempt <= APP_RETRY_MAX; attempt++) {
            try {
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType != null ? contentType
                                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .build();

                s3Client.putObject(putRequest, RequestBody.fromBytes(content));
                logger.info("Archivo (bytes) subido a S3: s3://{}/{}", bucketName, key);
                return key;
            } catch (S3Exception e) {
                logger.error("Error al subir archivo (bytes) a S3: {}", e.getMessage());
                throw new IOException("Error al guardar archivo en S3: " + e.getMessage(), e);
            } catch (SdkClientException e) {
                lastConnectionError = e;
                if (attempt < APP_RETRY_MAX) {
                    logger.warn("[S3] Conexión fallida al subir bytes (intento {}/{}). "
                            + "Reactivando WSL2 forward y reintentando en {}ms...",
                            attempt, APP_RETRY_MAX, APP_RETRY_DELAY_MS);
                    warmUpConnection();
                    try { Thread.sleep(APP_RETRY_DELAY_MS); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        logger.error("[S3] No se pudo conectar a S3 tras {} intentos para subir bytes. Endpoint: {}",
                APP_RETRY_MAX, endpoint);
        throw new IOException("Error de conexión con S3 (endpoint: " + endpoint + "): "
                + (lastConnectionError != null ? lastConnectionError.getMessage() : "unknown"),
                lastConnectionError);
    }

    /**
     * Lista las claves almacenadas bajo un prefijo del bucket.
     */
    @Override
    public List<String> listObjects(String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response response;
            do {
                response = s3Client.listObjectsV2(listRequest);
                keys.addAll(response.contents().stream()
                        .map(S3Object::key)
                        .collect(Collectors.toList()));

                listRequest = listRequest.toBuilder()
                        .continuationToken(response.nextContinuationToken())
                        .build();
            } while (response.isTruncated());

            logger.debug("Listados {} objetos con prefijo '{}'", keys.size(), prefix);
        } catch (S3Exception e) {
            logger.error("Error al listar objetos en S3 con prefijo '{}': {}", prefix, e.getMessage());
        } catch (SdkClientException e) {
            logger.error("[S3] No se pudo conectar a S3 para listar objetos. Endpoint: {}. Error: {}",
                    endpoint, e.getMessage());
        }
        return keys;
    }
}
