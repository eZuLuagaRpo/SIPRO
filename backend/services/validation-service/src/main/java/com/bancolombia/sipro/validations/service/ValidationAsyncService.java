package com.bancolombia.sipro.validations.service;

import com.bancolombia.sipro.validations.application.dto.ValidationJobStartResponse;
import com.bancolombia.sipro.validations.application.dto.ValidationJobStatusResponse;
import com.bancolombia.sipro.validations.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Orquesta la validación asíncrona de archivos y expone el estado de cada job.
 */
@Service
public class ValidationAsyncService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationAsyncService.class);

    private final FileValidationService fileValidationService;
    private final ValidationJobStore jobStore;
    private final LoteMemoryStore loteMemoryStore;
    private final Executor validationTaskExecutor;

    public ValidationAsyncService(
            FileValidationService fileValidationService,
            ValidationJobStore jobStore,
            LoteMemoryStore loteMemoryStore,
            @Qualifier("validationTaskExecutor") Executor validationTaskExecutor
    ) {
        this.fileValidationService = fileValidationService;
        this.jobStore = jobStore;
        this.loteMemoryStore = loteMemoryStore;
        this.validationTaskExecutor = validationTaskExecutor;
    }

    /**
     * Crea un job de validación, evita duplicados activos y lanza el procesamiento en segundo plano.
     *
     * @param archivoControl archivo control .txt (null si no aplica o no fue enviado)
     */
    public ValidationJobStartResponse startValidationJob(
            Long idProducto,
            Long idSegmento,
            String producto,
            String fechaCorte,
            String descripcion,
            String usuarioAdmin,
            MultipartFile archivo,
            MultipartFile archivoControl
    ) throws IOException {
        PreparedUpload preparedUpload = prepareUpload(archivo);
        PreparedUpload preparedControl = archivoControl != null ? prepareUpload(archivoControl) : null;
        String fingerprint = buildFingerprint(preparedUpload.sha256(), idProducto, idSegmento, fechaCorte, usuarioAdmin);

        ValidationJob existingJob = jobStore.findActiveByFingerprint(fingerprint).orElse(null);
        if (existingJob != null) {
            deleteQuietly(preparedUpload.tempFile());
            if (preparedControl != null) deleteQuietly(preparedControl.tempFile());
            return existingJob.toStartResponse(true);
        }

        ValidationJob job = new ValidationJob(UUID.randomUUID().toString(), fingerprint);
        job.updatePhase("RECEIVED", "Archivo recibido. Iniciando validación...", 0);
        jobStore.save(job);

        validationTaskExecutor.execute(() -> processValidationJob(
                job,
                idProducto,
            idSegmento,
                producto,
                fechaCorte,
                descripcion,
                usuarioAdmin,
                preparedUpload,
                preparedControl
        ));

        return job.toStartResponse(false);
    }

    public ValidationJobStatusResponse getValidationJobStatus(String jobId) {
        ValidationJob job = jobStore.get(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el job de validación"));
        return job.toStatusResponse();
    }

    /**
     * Ejecuta la validación real del job y actualiza su progreso y resultado.
     */
    private void processValidationJob(
            ValidationJob job,
            Long idProducto,
            Long idSegmento,
            String producto,
            String fechaCorte,
            String descripcion,
            String usuarioAdmin,
            PreparedUpload preparedUpload,
            PreparedUpload preparedControl
    ) {
        boolean cachedForApproval = false;
        try {
            ValidationResult result = fileValidationService.validarArchivoAsync(
                    idProducto,
                    idSegmento,
                    producto,
                    fechaCorte,
                    descripcion,
                    preparedUpload.tempFile(),
                    preparedUpload.originalFilename(),
                    preparedUpload.fileSize(),
                    preparedUpload.sha256(),
                    preparedControl != null ? preparedControl.tempFile() : null,
                    preparedControl != null ? preparedControl.originalFilename() : null,
                    preparedControl != null ? preparedControl.fileSize() : 0L,
                    usuarioAdmin,
                    new ValidationProgressListener() {
                        @Override
                        public void onPhase(String phase, String message, int progressPercent) {
                            job.updatePhase(phase, message, progressPercent);
                        }

                        @Override
                        public void onTotalRowsEstimated(long totalRows) {
                            job.updateTotalRows(totalRows);
                        }

                        @Override
                        public void onRowsProcessed(long processedRows) {
                            job.updateRows(processedRows);
                        }
                    }
            );

                    if ("OK".equalsIgnoreCase(result.getStatus())
                        && (result.getErrores() == null || result.getErrores().isEmpty())) {
                    loteMemoryStore.saveValidatedUpload(
                        result.getLoteId(),
                        preparedUpload.tempFile(),
                        preparedUpload.originalFilename(),
                        preparedUpload.fileSize(),
                        preparedUpload.sha256(),
                        usuarioAdmin);
                    result.setArchivoTemporalDisponible(true);
                    cachedForApproval = true;
                    }

            String completionMessage = "Validación completada.";
            if (result.getErrores() != null && !result.getErrores().isEmpty()) {
                completionMessage = "Validación completada con errores de negocio.";
            }
            job.complete(result, completionMessage);
        } catch (Exception e) {
            logger.error("Error en job de validación {}: {}", job.getJobId(), e.getMessage(), e);

            ValidationResult errorResult = new ValidationResult();
            errorResult.setLoteId(job.getJobId());
            errorResult.setStatus("ERROR");
            errorResult.setArchivoTemporalDisponible(false);
            errorResult.getErrores().add("Error procesando archivo: " + e.getMessage());
            job.fail("La validación falló por un error interno.", errorResult);
        } finally {
            jobStore.release(job);
            if (!cachedForApproval) {
                deleteQuietly(preparedUpload.tempFile());
            }
            if (preparedControl != null) {
                deleteQuietly(preparedControl.tempFile());
            }
        }
    }

    /**
     * Copia el archivo a un temporal y calcula su huella SHA-256 para deduplicación.
     */
    private PreparedUpload prepareUpload(MultipartFile archivo) throws IOException {
        String originalFilename = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "archivo.xlsx";
        String suffix = extractSuffix(originalFilename);
        Path tempFile = Files.createTempFile("sipro-validation-", suffix);

        MessageDigest digest = sha256Digest();

        try (
                InputStream rawInput = archivo.getInputStream();
                DigestInputStream digestInput = new DigestInputStream(rawInput, digest);
                OutputStream outputStream = Files.newOutputStream(tempFile)
        ) {
            digestInput.transferTo(outputStream);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw e;
        }

        String sha256 = HexFormat.of().formatHex(digest.digest());
        return new PreparedUpload(tempFile, originalFilename, archivo.getSize(), sha256);
    }

    private String buildFingerprint(String sha256, Long idProducto, Long idSegmento, String fechaCorte, String usuarioAdmin) {
        String normalizedUser = usuarioAdmin == null ? "" : usuarioAdmin.trim().toLowerCase(Locale.ROOT);
        String productPart = idProducto == null ? "" : idProducto.toString();
        String segmentPart = idSegmento == null ? "" : idSegmento.toString();
        String datePart = fechaCorte == null ? "" : fechaCorte.trim();
        return String.join("|", sha256, productPart, segmentPart, datePart, normalizedUser);
    }

    private String extractSuffix(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0) {
            return ".tmp";
        }
        return filename.substring(lastDot);
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en la JVM", e);
        }
    }

    private void deleteQuietly(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            logger.warn("No fue posible eliminar archivo temporal {}: {}", tempFile, e.getMessage());
        }
    }

    /**
     * Representa el archivo temporal preparado para una validación asíncrona.
     */
    private record PreparedUpload(Path tempFile, String originalFilename, long fileSize, String sha256) {
    }
}