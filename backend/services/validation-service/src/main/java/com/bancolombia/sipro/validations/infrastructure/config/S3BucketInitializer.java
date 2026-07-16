package com.bancolombia.sipro.validations.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Componente que verifica y crea el bucket S3 al iniciar la aplicación.
 * Útil para entornos locales (LocalStack) donde el bucket puede no existir tras reiniciar Docker.
 *
 * Se ejecuta de forma asíncrona con reintentos automáticos para tolerar que
 * LocalStack (WSL/Docker) tarde más en estar listo que el propio Spring Boot.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3BucketInitializer {

    private static final Logger logger = LoggerFactory.getLogger(S3BucketInitializer.class);

    /** Máximo de reintentos antes de desistir. */
    private static final int MAX_RETRIES = 5;

    /** Delay base en ms; se duplica en cada intento (backoff exponencial). */
    private static final long BASE_DELAY_MS = 3_000;

    private final S3Client s3Client;
    private final String endpoint;

    @Value("${app.storage.s3.bucket}")
    private String bucketName;

    public S3BucketInitializer(S3Client s3Client,
                               @Qualifier("s3ResolvedEndpoint") String resolvedEndpoint) {
        this.s3Client = s3Client;
        this.endpoint = resolvedEndpoint;
    }

    /**
     * Se ejecuta de forma asíncrona tras ApplicationReadyEvent para no bloquear
     * el hilo principal. Reintenta con backoff exponencial hasta que LocalStack
     * esté accesible (o se agoten los reintentos).
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void initializeBucket() {
        logger.info("[S3] Verificando bucket '{}' con hasta {} reintentos...", bucketName, MAX_RETRIES);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                logger.info("[S3] Bucket '{}' existe y accesible (intento {}/{}).",
                        bucketName, attempt, MAX_RETRIES);
                bucketReady = true;
                return; // Éxito — salir del loop

            } catch (NoSuchBucketException e) {
                // El endpoint responde pero el bucket no existe → crearlo
                createBucket();
                bucketReady = true;
                return;

            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    createBucket();
                    bucketReady = true;
                    return;
                }
                logRetry(attempt, e.getMessage());

            } catch (Exception e) {
                logRetry(attempt, e.getMessage());
            }

            // Esperar antes de reintentar (backoff exponencial)
            if (attempt < MAX_RETRIES) {
                long delay = BASE_DELAY_MS * (1L << (attempt - 1)); // 3s, 6s, 12s, 24s
                logger.info("[S3] Reintentando en {} ms...", delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("[S3] Hilo interrumpido durante espera de reintento.");
                    return;
                }
            }
        }

        // Se agotaron los reintentos
        logger.warn("[S3] No se pudo conectar a S3 después de {} intentos. "
                + "Las operaciones de archivo fallarán hasta que el endpoint esté disponible. "
                + "Verifique que LocalStack esté corriendo en el endpoint configurado.", MAX_RETRIES);
    }

    private void createBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            logger.info("[S3] Bucket '{}' creado exitosamente.", bucketName);
        } catch (Exception e) {
            // Si ya existe (race condition o persistence), no es error crítico
            if (e.getMessage() != null && e.getMessage().contains("BucketAlreadyOwnedByYou")) {
                logger.info("[S3] Bucket '{}' ya existía (persistencia LocalStack).", bucketName);
            } else {
                logger.error("[S3] Error creando el bucket '{}': {}", bucketName, e.getMessage());
            }
        }
    }

    private void logRetry(int attempt, String errorMsg) {
        if (attempt < MAX_RETRIES) {
            logger.warn("[S3] Intento {}/{} falló ({}). Se reintentará...",
                    attempt, MAX_RETRIES, errorMsg);
        } else {
            logger.error("[S3] Intento {}/{} falló ({}). Sin más reintentos.",
                    attempt, MAX_RETRIES, errorMsg);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Keep-alive: evita que WSL2 auto-forward se desactive por inactividad
    // ─────────────────────────────────────────────────────────────────────

    /** Flag que se activa tras la primera conexión exitosa al bucket. */
    private volatile boolean bucketReady = false;

    /**
     * Keep-alive via TCP socket directo cada 15 s. Mantiene activo el port-forwarding
     * de WSL2 que se desactiva tras ~30 s de inactividad.
     *
     * IMPORTANTE: usa Socket directo (no s3Client.headBucket) porque:
     *  - El pool Apache HTTP puede reutilizar una conexión "stale" y no crear un TCP SYN nuevo.
     *  - Un SYN fresco a localhost:4566 es lo que activa/mantiene el auto-forward de WSL2.
     *  - initialDelay=10s: arranca antes de que WSL2 tenga oportunidad de caerse.
     *  - fixedDelay=15s: bien por debajo del timeout ~30 s de WSL2.
     */
    @Scheduled(fixedDelay = 15_000, initialDelay = 10_000)
    public void keepAlive() {
        if (!bucketReady) {
            return;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 4566;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2_000);
            }
            logger.trace("[S3] Keep-alive TCP OK ({}:{})", host, port);
        } catch (Exception e) {
            // Conexión rechazada = WSL2 acaba de caerse. El SYN lo despierta para el próximo intento.
            logger.debug("[S3] Keep-alive TCP falló — WSL2 forward reactivado para siguiente request: {}",
                    e.getMessage());
        }
    }
}
