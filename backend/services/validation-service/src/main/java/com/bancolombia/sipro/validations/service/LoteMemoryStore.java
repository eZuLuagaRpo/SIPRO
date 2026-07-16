package com.bancolombia.sipro.validations.service;

import com.bancolombia.sipro.validations.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mantiene en memoria resultados de validación y archivos temporales pendientes de aprobación.
 */
@Component
public class LoteMemoryStore {
    private static final Logger logger = LoggerFactory.getLogger(LoteMemoryStore.class);

    private final Map<String, ValidationResult> lotes = new ConcurrentHashMap<>();
    private final Map<String, byte[]> erroresFiles = new ConcurrentHashMap<>();
    private final Map<String, CachedValidatedUpload> validatedUploads = new ConcurrentHashMap<>();
    private final Duration validatedUploadTtl;

    public LoteMemoryStore(
            @Value("${app.validation.cached-upload-ttl-minutes:120}") long validatedUploadTtlMinutes) {
        this.validatedUploadTtl = Duration.ofMinutes(Math.max(1L, validatedUploadTtlMinutes));
    }
    
    public void save(ValidationResult result) {
        lotes.put(result.getLoteId(), result);
    }
    
    public ValidationResult get(String loteId) {
        return lotes.get(loteId);
    }
    
    public void saveErroresFile(String loteId, byte[] content) {
        erroresFiles.put(loteId, content);
    }
    
    public byte[] getErroresFile(String loteId) {
        return erroresFiles.get(loteId);
    }

    /**
     * Registra un archivo ya validado para reutilizarlo luego en la aprobación.
     */
    public void saveValidatedUpload(
            String loteId,
            Path filePath,
            String originalFilename,
            long size,
            String sha256,
            String usuario
    ) {
        cleanupExpiredValidatedUploads();
        releaseValidatedUpload(loteId);
        validatedUploads.put(loteId, new CachedValidatedUpload(
                filePath,
                originalFilename,
                size,
                sha256,
                normalizeUser(usuario),
                System.currentTimeMillis() + validatedUploadTtl.toMillis()));
    }

    /**
     * Recupera un archivo validado solo si sigue vigente y pertenece al usuario indicado.
     */
    public Optional<CachedValidatedUpload> getValidatedUpload(String loteId, String usuario) {
        cleanupExpiredValidatedUploads();
        CachedValidatedUpload cachedUpload = validatedUploads.get(loteId);
        if (cachedUpload == null) {
            return Optional.empty();
        }

        if (!cachedUpload.belongsTo(usuario)) {
            return Optional.empty();
        }

        return Optional.of(cachedUpload);
    }

    /**
     * Libera el archivo temporal asociado a un lote cuando ya no se necesita.
     */
    public void releaseValidatedUpload(String loteId) {
        CachedValidatedUpload cachedUpload = validatedUploads.remove(loteId);
        if (cachedUpload != null) {
            deleteQuietly(cachedUpload.filePath());
        }
    }

    private void cleanupExpiredValidatedUploads() {
        long now = System.currentTimeMillis();
        validatedUploads.entrySet().removeIf(entry -> {
            CachedValidatedUpload cachedUpload = entry.getValue();
            if (!cachedUpload.isExpired(now)) {
                return false;
            }

            deleteQuietly(cachedUpload.filePath());
            return true;
        });
    }

    private void deleteQuietly(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.warn("No fue posible eliminar archivo temporal cacheado {}: {}", filePath, e.getMessage());
        }
    }

    private String normalizeUser(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Contiene el archivo temporal validado y los metadatos necesarios para su reutilización.
     */
    public record CachedValidatedUpload(
            Path filePath,
            String originalFilename,
            long size,
            String sha256,
            String usuario,
            long expiresAtEpochMs
    ) {
        boolean isExpired(long now) {
            return now >= expiresAtEpochMs;
        }

        boolean belongsTo(String usuario) {
            String normalizedUser = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
            return this.usuario.equals(normalizedUser);
        }
    }
}
