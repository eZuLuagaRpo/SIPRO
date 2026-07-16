package com.bancolombia.sipro.validations.api;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 * Publica chequeos rápidos del servicio y del almacenamiento configurado.
 */
@RestController
public class HealthController {

    /** Puede ser null cuando app.storage.type=local (S3 no está activo). */
    @Autowired(required = false)
    private S3Client s3Client;

    @Value("${app.storage.type:s3}")
    private String storageType;

    @Value("${app.storage.s3.bucket:sipro-bucket}")
    private String bucketName;

    @Value("${app.storage.local.base-dir:C:/s3mock2/sipro-local-storage}")
    private String localBaseDir;

    /**
     * Informa si el backend está arriba y qué tipo de almacenamiento está activo.
     */
    @GetMapping("/health")
	public Map<String, Object> health() {
		return Map.of("status", "UP", "storage", storageType);
	}

    /**
     * Revisa si S3 responde o si el perfil actual está trabajando con storage local.
     */
    @GetMapping("/health/s3")
    public Map<String, Object> checkS3() {
        Map<String, Object> status = new HashMap<>();
        status.put("storageType", storageType);

        if (!"s3".equalsIgnoreCase(storageType)) {
            status.put("connection", "DISABLED");
            status.put("message", "Almacenamiento local activo. S3 deshabilitado en perfil dev.");
            status.put("localDir", localBaseDir);
            return status;
        }

        status.put("bucket", bucketName);
        try {
            // 1. Verificar si bucket existe
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            status.put("exists", true);
            
            // 2. Listar archivos (prueba de lectura)
            ListObjectsV2Response list = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(5)
                    .build());
            
            status.put("connection", "OK");
            status.put("objectCount", list.keyCount());
            status.put("sampleObjects", list.contents().stream()
                    .map(o -> o.key())
                    .toList());
                    
        } catch (Exception e) {
            status.put("exists", false);
            status.put("connection", "ERROR");
            status.put("error", e.getMessage());
            status.put("errorType", e.getClass().getSimpleName());
        }
        
        return status;
    }
}

