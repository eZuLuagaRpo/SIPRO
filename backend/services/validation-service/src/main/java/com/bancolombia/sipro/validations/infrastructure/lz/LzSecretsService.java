package com.bancolombia.sipro.validations.infrastructure.lz;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Obtiene credenciales desde AWS Secrets Manager (o LocalStack en DEV).
 * En DEV: SECRETS_ENDPOINT = http://localhost:4567
 * En PDN: dejar vacio y configurar credenciales AWS estandar.
 *
 * Nota de mantenimiento 2026: comentarios funcionales agregados por
 * Junior Alexander Ortiz Arenas (junortiz), ANALITICO/A - EVC OTRAS FUNCIONES CORPORATIVAS.
 */
@Service
public class LzSecretsService {

    private static final Logger log = LoggerFactory.getLogger(LzSecretsService.class);

    @Value("${lz.secrets.endpoint:http://localhost:4567}")
    private String secretsEndpoint;

    @Value("${lz.secrets.region:us-east-1}")
    private String secretsRegion;

    @Value("${lz.secrets.name:lz/creds}")
    private String secretsName;

    /**
     * DEV ONLY: credenciales directas sin pasar por Secrets Manager.
     * Se configuran en application-dev.yml. En PDN deben estar vacias.
     */
    @Value("${lz.dev.user:}")
    private String devUser;

    @Value("${lz.dev.password:}")
    private String devPassword;

    /**
     * Indica si las credenciales LZ van por bypass DEV (lz.dev.user Y lz.dev.password configurados).
     * Retorna false si falta cualquiera de los dos, o en QA/PDN donde deben estar vacios.
     */
    public boolean isDevBypass() {
        return devUser != null && !devUser.isBlank()
            && devPassword != null && !devPassword.isBlank();
    }

    /**
     * Loguea el modo de autenticacion LZ al arrancar el backend.
     * Visible en logs de arranque para confirmar la configuracion activa.
     */
    @PostConstruct
    void logConfiguration() {
        if (isDevBypass()) {
            log.info("╔══════════════════════════════════════════════════════╗");
            log.info("║  [LZ-AUTH] MODO BYPASS DEV                          ║");
            log.info("║  Variable: LZ_DEV_USER / LZ_DEV_PASSWORD            ║");
            log.info("║  Usuario : {}",  padRight(devUser, 38) + "║");
            log.info("║  Password: [configurado]                            ║");
            log.warn("║  ATENCION: modo bypass NO debe activarse en QA/PDN  ║");
            log.info("╚══════════════════════════════════════════════════════╝");
        } else if (devUser != null && !devUser.isBlank()) {
            // user configurado pero password vacio — el bypass NO se activa
            log.warn("╔══════════════════════════════════════════════════════╗");
            log.warn("║  [LZ-AUTH] BYPASS INCOMPLETO — sin LZ_DEV_PASSWORD  ║");
            log.warn("║  Usuario : {}",  padRight(devUser, 38) + "║");
            log.warn("║  Password: [VACIO] → define LZ_DEV_PASSWORD         ║");
            log.warn("║  Fallback : Secrets Manager                         ║");
            log.warn("╚══════════════════════════════════════════════════════╝");
        } else {
            log.info("╔══════════════════════════════════════════════════════╗");
            log.info("║  [LZ-AUTH] MODO Secrets Manager                     ║");
            log.info("║  Endpoint : {}",  padRight(secretsEndpoint.isBlank() ? "(AWS SDK nativo)" : secretsEndpoint, 38) + "║");
            log.info("║  Region   : {}",  padRight(secretsRegion, 38) + "║");
            log.info("║  Secreto  : {}",  padRight(secretsName, 38) + "║");
            log.info("╚══════════════════════════════════════════════════════╝");
        }
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length());
    }

    /**
     * Retorna mapa con "user" y "password" leidos desde Secrets Manager.
     */
    public Map<String, String> getLzCredentials() {
        // Bypass DEV activo solo si AMBOS user y password estan configurados.
        // Si solo hay user pero falta password, cae a Secrets Manager en lugar de
        // intentar autenticar con password vacio (que causaria Authentication failed).
        if (devUser != null && !devUser.isBlank()) {
            if (devPassword == null || devPassword.isBlank()) {
                log.warn("[DEV] lz.dev.user configurado pero lz.dev.password esta vacio. " +
                         "Usando Secrets Manager como fallback. " +
                         "Define LZ_DEV_PASSWORD para activar bypass directo.");
                // IMPORTANTE: NO retornar aqui — cae al flujo de Secrets Manager
            } else {
                log.warn("[DEV] Usando credenciales LZ directas (lz.dev.user/lz.dev.password). NO usar en PDN.");
                Map<String, String> creds = new LinkedHashMap<>();
                creds.put("user", devUser);
                creds.put("password", devPassword);
                return creds;
            }
        }
        log.info("Obteniendo credenciales LZ desde Secrets Manager [{}]", secretsEndpoint);
        try {
            String rawJson = callSecretsManager(secretsEndpoint, secretsRegion, secretsName);
            String user = extractJsonField(rawJson, "user");
            String pwd  = extractJsonField(rawJson, "password");
            if (user == null || user.isBlank() || pwd == null || pwd.isBlank()) {
                throw new IllegalStateException(
                    "Secreto '" + secretsName + "' no contiene 'user' o 'password' validos.");
            }
            Map<String, String> creds = new LinkedHashMap<>();
            creds.put("user", user);
            creds.put("password", pwd);
            return creds;
        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo credenciales LZ: " + e.getMessage(), e);
        }
    }

    // ── Internos ────────────────────────────────────────────────────────────

    private String callSecretsManager(String endpoint, String region, String secretName)
            throws Exception {

        // Se usa una llamada HTTP simple porque este helper también debe funcionar contra
        // LocalStack en DEV sin meter más complejidad de la necesaria en esta integración.

        HttpURLConnection con = (HttpURLConnection) new URL(endpoint).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(8_000);
        con.setReadTimeout(8_000);
        con.setRequestProperty("Content-Type", "application/x-amz-json-1.1");
        con.setRequestProperty("X-Amz-Target", "secretsmanager.GetSecretValue");

        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        if (accessKey == null) accessKey = "test";
        if (secretKey == null) secretKey = "test";
        con.setRequestProperty("Authorization",
            "AWS4-HMAC-SHA256 Credential=" + accessKey + "/20260101/" + region
            + "/secretsmanager/aws4_request, SignedHeaders=content-type;host;x-amz-target,"
            + " Signature=dummy");

        try (OutputStream os = con.getOutputStream()) {
            os.write(("{\"SecretId\":\"" + secretName + "\"}").getBytes(StandardCharsets.UTF_8));
        }

        int status = con.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? con.getInputStream() : con.getErrorStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }

        if (status < 200 || status >= 300) {
            throw new RuntimeException("Secrets Manager HTTP " + status + ": " + sb);
        }

        String secretString = extractJsonField(sb.toString(), "SecretString");
        if (secretString == null || secretString.isBlank()) {
            throw new RuntimeException("SecretString vacio. Respuesta: " + sb);
        }
        return secretString;
    }

    private String extractJsonField(String json, String field) {
        // El JSON esperado aquí es pequeño y muy estable. Por eso se usa una extracción simple
        // en vez de agregar otra dependencia solo para leer dos campos puntuales.
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;
        StringBuilder val = new StringBuilder();
        for (int i = open + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) { val.append(json.charAt(++i)); continue; }
            if (c == '"') break;
            val.append(c);
        }
        return val.toString();
    }
}
