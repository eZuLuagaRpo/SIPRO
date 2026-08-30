package com.bancolombia.sipro.validations.infrastructure.config;

import co.com.bancolombia.secretsmanager.api.GenericManager;
import co.com.bancolombia.secretsmanager.api.exceptions.SecretException;
import co.com.bancolombia.secretsmanager.connector.AWSSecretManagerConnector;
import com.google.gson.annotations.SerializedName;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lee las credenciales y URL de la BD principal desde AWS Secrets Manager al arrancar
 * en ambientes cloud (cuando db.secrets.name está configurado). En local no hace nada.
 *
 * Usa la misma librería Bancolombia (aws-secrets-manager-sync) que el proyecto ABA.
 * El secreto debe tener los campos: host, port, dbname, username, password.
 */
public class DbSecretsPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String secretName = environment.getProperty("db.secrets.name", "").trim();
        if (secretName.isEmpty()) {
            return;
        }

        String region = environment.getProperty("db.secrets.region", "us-east-1").trim();
        syslog("INFO", "[DB-SECRETS] Obteniendo credenciales de BD — secreto=" + secretName + ", region=" + region);

        try {
            GenericManager connector = new AWSSecretManagerConnector(region);
            DbSecretFields secret = connector.getSecret(secretName, DbSecretFields.class);

            if (secret.getUsername() == null || secret.getUsername().isBlank()) {
                throw new IllegalStateException("El secreto '" + secretName + "' no contiene el campo 'username'.");
            }
            if (secret.getPassword() == null || secret.getPassword().isBlank()) {
                throw new IllegalStateException("El secreto '" + secretName + "' no contiene el campo 'password'.");
            }
            if (secret.getHost() == null || secret.getHost().isBlank()) {
                throw new IllegalStateException("El secreto '" + secretName + "' no contiene el campo 'host'.");
            }

            String jdbcUrl = "jdbc:postgresql://" + secret.getHost().trim()
                    + ":" + secret.getPort().trim()
                    + "/" + secret.getDbname().trim();

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", secret.getUsername());
            props.put("spring.datasource.password", secret.getPassword());
            environment.getPropertySources().addFirst(new MapPropertySource("dbSecretsManager", props));

            syslog("INFO", "[DB-SECRETS] Credenciales de BD cargadas OK — usuario=" + secret.getUsername() + ", host=" + secret.getHost());

        } catch (SecretException e) {
            throw new RuntimeException(
                    "[DB-SECRETS] No se pudieron obtener las credenciales de BD desde Secrets Manager: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "[DB-SECRETS] Error procesando credenciales de BD: " + e.getMessage(), e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** Imita el formato de logback para logs que ocurren antes de que el sistema de logging arranque. */
    private static void syslog(String level, String msg) {
        System.out.printf("%s %-5s [main] DbSecretsPostProcessor - %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")),
                level, msg);
    }

    private static class DbSecretFields {
        @SerializedName("host")     private String host;
        @SerializedName("port")     private String port;
        @SerializedName("dbname")   private String dbname;
        @SerializedName("username") private String username;
        @SerializedName("password") private String password;

        public String getHost()     { return host; }
        public String getPort()     { return port; }
        public String getDbname()   { return dbname; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }
}
