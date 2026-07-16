package com.bancolombia.sipro.validations.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuración del cliente S3 con resolución inteligente de endpoint.
 * <p>
 * En entornos de desarrollo, LocalStack corre en Docker dentro de WSL2.
 * El port-forwarding automático de WSL2 hacia Windows (localhost) es inestable.
 * Esta configuración detecta esta situación y resuelve automáticamente la IP
 * real de WSL2 como fallback, garantizando conectividad transparente.
 *
 * Nota de mantenimiento 2026: comentarios funcionales agregados por
 * Junior Alexander Ortiz Arenas (junortiz), ANALITICO/A - EVC OTRAS FUNCIONES CORPORATIVAS.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3Config {

    private static final Logger logger = LoggerFactory.getLogger(S3Config.class);

    /** Timeout en ms para verificar si un endpoint es alcanzable (TCP connect). */
    private static final int REACHABILITY_TIMEOUT_MS = 3_000;

    /**
     * Intentos para verificar localhost antes de pasar a WSL2 fallback.
     * WSL2 auto-forward puede tardar 5-15s en activarse tras restart del container.
     */
    private static final int LOCALHOST_MAX_RETRIES = 5;

    /** Espera entre reintentos de localhost (ms). */
    private static final long LOCALHOST_RETRY_DELAY_MS = 3_000;

    @Value("${app.storage.s3.endpoint}")
    private String endpoint;

    @Value("${app.storage.s3.region}")
    private String region;

    @Value("${app.storage.s3.access-key}")
    private String accessKey;

    @Value("${app.storage.s3.secret-key}")
    private String secretKey;

    /**
     * Endpoint S3 efectivo tras la resolución inteligente.
     * Expuesto como bean para que otros componentes (ej. S3Presigner) lo usen.
     */
    @Bean("s3ResolvedEndpoint")
    public String s3ResolvedEndpoint() {
        return resolveEndpoint(endpoint);
    }

    @Bean
    public S3Client s3Client() {
        String effectiveEndpoint = s3ResolvedEndpoint();

        var credentials = AwsBasicCredentials.create(accessKey, secretKey);

        var serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        // Estos tiempos son cortos a propósito para fallar rápido en local cuando el
        // puente entre Windows y WSL2 está dormido o quedó apuntando a un socket muerto.
        var overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(10))
                .apiCallAttemptTimeout(Duration.ofSeconds(5))
                .build();

        // El pool se recicla rápido porque en desarrollo es más importante reabrir una
        // conexión sana que seguir reutilizando sockets viejos contra LocalStack.
        var httpClient = ApacheHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(3))
                .socketTimeout(Duration.ofSeconds(5))
                .connectionMaxIdleTime(Duration.ofSeconds(10))
                .connectionTimeToLive(Duration.ofSeconds(30))
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(effectiveEndpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(serviceConfiguration)
                .overrideConfiguration(overrideConfig)
                .httpClient(httpClient)
                .build();
    }

    // ── Resolución inteligente de endpoint ─────────────────────────────────

    /**
     * Si el endpoint configurado es localhost y NO es alcanzable, intenta
     * descubrir la IP de WSL2 (donde Docker/LocalStack realmente escucha)
     * y la usa como fallback automático.
     *
     * <p>En producción (endpoint no-localhost) se retorna tal cual, sin overhead.</p>
     */
    private String resolveEndpoint(String configuredEndpoint) {
        URI uri = URI.create(configuredEndpoint);
        String host = uri.getHost();
        int port = uri.getPort();

        // Solo aplica fallback para endpoints localhost/127.0.0.1
        if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
            logger.info("[S3Config] Endpoint no es localhost → usando directamente: {}", configuredEndpoint);
            return configuredEndpoint;
        }

        // Primero se agota localhost, porque es el camino más simple cuando el reenvío de
        // puertos sí está vivo. Solo si falla varias veces se intenta descubrir la IP real.
        for (int attempt = 1; attempt <= LOCALHOST_MAX_RETRIES; attempt++) {
            if (isReachable(host, port)) {
                logger.info("[S3Config] localhost:{} alcanzable (intento {}/{}) → usando: {}",
                        port, attempt, LOCALHOST_MAX_RETRIES, configuredEndpoint);
                return configuredEndpoint;
            }
            if (attempt < LOCALHOST_MAX_RETRIES) {
                logger.info("[S3Config] localhost:{} no alcanzable (intento {}/{}). Reintentando en {}ms...",
                        port, attempt, LOCALHOST_MAX_RETRIES, LOCALHOST_RETRY_DELAY_MS);
                try {
                    Thread.sleep(LOCALHOST_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        logger.warn("[S3Config] localhost:{} NO alcanzable tras {} intentos. "
                + "Intentando detectar IP de WSL2...", port, LOCALHOST_MAX_RETRIES);

        // Segundo camino: hablar directo con WSL2 para no depender del puente localhost.
        String wsl2Ip = detectWsl2Ip();
        if (wsl2Ip != null) {
            String wsl2Endpoint = uri.getScheme() + "://" + wsl2Ip + ":" + port;
            if (isReachable(wsl2Ip, port)) {
                logger.info("[S3Config] WSL2 IP {}:{} alcanzable → usando: {}",
                        wsl2Ip, port, wsl2Endpoint);
                return wsl2Endpoint;
            }
            logger.warn("[S3Config] WSL2 IP {}:{} tampoco alcanzable.", wsl2Ip, port);
        }

        // Si tampoco funciona, se devuelve el endpoint original para que el error final sea
        // claro y el equipo vea exactamente qué dirección intentó usar la aplicación.
        logger.error("[S3Config] No se pudo resolver un endpoint S3 alcanzable. "
                + "Usando configuración original: {}. "
                + "Verifique que LocalStack esté corriendo (docker ps en WSL2) "
                + "y que el port-proxy esté configurado (ejecute start-localstack.ps1 como Admin).",
                configuredEndpoint);
        return configuredEndpoint;
    }

    /**
     * Verifica si un host:port es alcanzable vía TCP connect.
     */
    private boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), REACHABILITY_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Detecta la IP de WSL2 ejecutando {@code wsl hostname -I}.
     * Retorna {@code null} si WSL no está disponible o falla el comando.
     */
    private String detectWsl2Ip() {
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl", "hostname", "-I");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes()).trim();
            boolean exited = proc.waitFor(5, TimeUnit.SECONDS);
            if (exited && proc.exitValue() == 0 && !output.isEmpty()) {
                // hostname -I puede devolver varias IPs. Se toma la primera porque suele ser
                // la IP útil del entorno Linux donde está corriendo Docker/LocalStack.
                String ip = output.split("\\s+")[0];
                logger.info("[S3Config] IP de WSL2 detectada: {}", ip);
                return ip;
            }
        } catch (Exception e) {
            logger.debug("[S3Config] No se pudo detectar IP de WSL2 (¿no hay WSL?): {}", e.getMessage());
        }
        return null;
    }
}
