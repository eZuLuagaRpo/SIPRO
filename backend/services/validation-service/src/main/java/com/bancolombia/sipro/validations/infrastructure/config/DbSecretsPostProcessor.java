package com.bancolombia.sipro.validations.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lee las credenciales de la BD principal desde AWS Secrets Manager al arrancar
 * en ambientes cloud (profile=cloud). En local (db.secrets.name vacío) no hace nada
 * y Spring usa los defaults de application.yml (DB_USER / DB_PASS).
 *
 * Se registra en META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports
 * para que Spring Boot lo descubra antes de crear cualquier bean.
 */
public class DbSecretsPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DbSecretsPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String secretName = environment.getProperty("db.secrets.name", "").trim();
        if (secretName.isEmpty()) {
            return;
        }

        String region = environment.getProperty("db.secrets.region", "us-east-1").trim();
        log.info("[DB-SECRETS] Obteniendo credenciales de BD desde Secrets Manager: secreto={}, region={}", secretName, region);

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region))
                .build()) {

            GetSecretValueResponse response = client.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretName).build());

            String secretJson = response.secretString();
            String username = extractJsonField(secretJson, "username");
            String password = extractJsonField(secretJson, "password");

            if (username == null || username.isBlank()) {
                throw new IllegalStateException("El secreto '" + secretName + "' no contiene el campo 'username'.");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("El secreto '" + secretName + "' no contiene el campo 'password'.");
            }

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("spring.datasource.username", username);
            props.put("spring.datasource.password", password);
            environment.getPropertySources().addFirst(new MapPropertySource("dbSecretsManager", props));

            log.info("[DB-SECRETS] Credenciales de BD cargadas correctamente (usuario={}).", username);

        } catch (Exception e) {
            throw new RuntimeException(
                "[DB-SECRETS] No se pudieron obtener las credenciales de BD desde Secrets Manager: " + e.getMessage(), e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;
        StringBuilder val = new StringBuilder();
        for (int i = open + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                val.append(json.charAt(++i));
                continue;
            }
            if (c == '"') break;
            val.append(c);
        }
        return val.toString();
    }
}
