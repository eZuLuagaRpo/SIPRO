package com.bancolombia.sipro.validations.infrastructure.lz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.*;

/**
 * Servicio que abre una conexion JDBC a LZ (Impala) y ejecuta un SELECT.
 * La conexion se crea por-peticion (no pool) para evitar overhead de
 * mantener conexiones permanentes a LZ desde el backend.
 *
 * Requiere ImpalaJDBC42.jar en libs/ del servicio.
 *
 * La URL JDBC se construye dinamicamente a partir de lz.host y lz.port.
 * Configuracion probada y funcional (2026-02-24):
 *   jdbc:impala://host:port;AuthMech=3;SSL=1;AllowSelfSignedCerts=1;
 *   ConnectTimeout=30;SocketTimeout=120;UID=user;PWD=pass
 *
 * IMPORTANTE: NO usar UseSASL=1, KrbHostFQDN ni KrbServiceName con AuthMech=3.
 * AuthMech=3 = LDAP (usuario/password). Esos parametros fuerzan negociacion
 * SASL/Kerberos que causa "Authentication failed" o cuelgue indefinido.
 *
 * Nota de mantenimiento 2026: comentarios funcionales agregados por
 * Junior Alexander Ortiz Arenas (junortiz), ANALITICO/A - EVC OTRAS FUNCIONES CORPORATIVAS.
 */
@Service
public class LzJdbcService {

    private static final Logger log = LoggerFactory.getLogger(LzJdbcService.class);
    private static final String DRIVER = "com.cloudera.impala.jdbc.Driver";
    private static final Object LZ_SSL_TRUSTSTORE_LOCK = new Object();

    @Value("${lz.host:}")
    private String lzHost;

    @Value("${lz.port:21050}")
    private int lzPort;

    @Value("${lz.ssl.truststore-path:}")
    private String truststorePath;

    @Value("${lz.ssl.truststore-password:changeit}")
    private String truststorePassword;

    @Value("${lz.ssl.truststore-type:${LZ_TRUSTSTORE_TYPE:JKS}}")
    private String truststoreType;

    private final LzSecretsService secretsService;
    private Path resolvedTruststorePath;

    public LzJdbcService(LzSecretsService secretsService) {
        this.secretsService = secretsService;
    }

    /**
     * Resuelve el truststore de LZ sin registrarlo como truststore global.
     *
     * El truststore se aplica solo durante el handshake JDBC de Impala para no
     * afectar otras integraciones HTTPS del proceso, como la descarga de JWKs
     * de Entra ID para validar JWT.
     *
     * Intenta en orden:
     *   1. Ruta explicita configurada en lz.ssl.truststore-path
     *   2. classpath:certificates/impala-truststore.jks (empaquetado en el JAR)
     */
    @PostConstruct
    void prepareTruststore() {
        String globalTruststore = System.getProperty("javax.net.ssl.trustStore");
        if (globalTruststore != null
                && !globalTruststore.isBlank()
                && !"NONE".equalsIgnoreCase(globalTruststore)) {
            log.warn("Existe truststore SSL global configurado via JVM arg: {}. "
                    + "Eso puede romper llamadas HTTPS ajenas a LZ, por ejemplo la validacion de tokens Entra.",
                globalTruststore);
        }

        // 1. Ruta explicita desde application-*.yml
        if (truststorePath != null && !truststorePath.isBlank()) {
            Path p = Path.of(truststorePath);
            if (Files.exists(p)) {
                resolvedTruststorePath = p.toAbsolutePath();
                log.info("Truststore SSL de LZ preparado desde lz.ssl.truststore-path: {}", resolvedTruststorePath);
                logStartup();
                return;
            }
            log.warn("lz.ssl.truststore-path={} no existe. Intentando classpath...", truststorePath);
        }

        // 2. Classpath (empaquetado en src/main/resources/certificates/)
        try (InputStream is = getClass().getResourceAsStream("/certificates/impala-truststore.jks")) {
            if (is != null) {
                // El driver espera una ruta física de archivo. Por eso el recurso del JAR
                // se copia a un temporal y luego se usa solo al abrir conexiones JDBC.
                Path temp = Files.createTempFile("impala-truststore-", ".jks");
                Files.copy(is, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                temp.toFile().deleteOnExit();
                resolvedTruststorePath = temp.toAbsolutePath();
                log.info("Truststore SSL de LZ cargado desde classpath → {}", resolvedTruststorePath);                logStartup();                return;
            }
        } catch (Exception e) {
            log.warn("No se pudo cargar truststore desde classpath: {}", e.getMessage());
        }

        log.warn("No se encontro truststore SSL para LZ. "
            + "Si la conexion falla con 'PKIX path building failed', coloca "
            + "impala-truststore.jks en src/main/resources/certificates/ "
            + "o configura lz.ssl.truststore-path en application-dev.yml");

        logStartup();
    }

    /**
     * Imprime en log la configuracion activa de LZ al arrancar.
     * Permite confirmar en DEV/QA/PDN que el host, puerto y modo de
     * autenticacion son los correctos sin depurar el codigo.
     */
    private void logStartup() {
        String hostInfo = (lzHost != null && !lzHost.isBlank())
                ? lzHost + ":" + lzPort
                : "(no configurado - LZ deshabilitado)";
        String authMode = secretsService.isDevBypass()
                ? "BYPASS DEV  [env: LZ_DEV_USER / LZ_DEV_PASSWORD]"
                : "Secrets Manager  [env: LZ_SECRETS_ENDPOINT / LZ_SECRETS_NAME]";
        String tsInfo = resolvedTruststorePath != null
                ? resolvedTruststorePath.toString()
                : "(no encontrado - conexion SSL puede fallar)";

        log.info("┌─────────────────────────────────────────────────────┐");
        log.info("│  [LZ-JDBC] Configuracion al arranque                │");
        log.info("│  Host/Puerto : {}",  padRight(hostInfo, 37) + "│");
        log.info("│  Auth        : {}",  padRight(authMode, 37) + "│");
        log.info("│  Truststore  : {}",  padRight(tsInfo.length() > 37 ? "..." + tsInfo.substring(tsInfo.length() - 34) : tsInfo, 37) + "│");
        log.info("└─────────────────────────────────────────────────────┘");
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length());
    }

    /**
     * Construye la URL JDBC dinamicamente.
     *
     * Configuracion PROBADA y FUNCIONAL (2026-02-24):
     *   AuthMech=3          → LDAP (usuario + password)
     *   SSL=1               → Conexion cifrada
     *   AllowSelfSignedCerts=1 → Acepta cert autofirmado del lado del driver
     *   ConnectTimeout=30   → Max 30s para establecer socket
     *   SocketTimeout=120   → Max 120s para operaciones de lectura
     *
     * NO incluye UseSASL, KrbHostFQDN, KrbServiceName porque causan
     * negociacion SASL/Kerberos incompatible con AuthMech=3 LDAP.
     */
    private String buildJdbcUrl(String user, String password) {
        return String.format(
            "jdbc:impala://%s:%d;"
            + "AuthMech=3;"
            + "SSL=1;AllowSelfSignedCerts=1;"
            + "ConnectTimeout=30;SocketTimeout=120;"
            + "UID=%s;PWD=%s",
            lzHost, lzPort, user, password
        );
    }

    /**
     * Indica si la conexion LZ esta habilitada (host configurado).
     */
    public boolean isEnabled() {
        return lzHost != null && !lzHost.isBlank();
    }

    /**
     * Ejecuta la query en LZ y devuelve las filas como Lista de Mapas.
     * Cada mapa: { columnName -> value }.
     *
     * @param sql  Query SQL completa (ya construida por el use case)
     * @return lista de filas
     */
    public List<Map<String, Object>> executeQuery(String sql) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                "LZ no configurado: lz.host esta vacio. "
                + "Configura lz.host=impala.bancolombia.corp en application-dev.yml "
                + "y asegurate de tener red corporativa.");
        }

        Map<String, String> creds = secretsService.getLzCredentials();
        String user = creds.get("user");
        String pwd  = creds.get("password");

        String fullUrl = buildJdbcUrl(user, pwd);

        log.info("Conectando a LZ: [{}:{}] usuario={}", lzHost, lzPort, user);
        log.debug("SQL: {}", sql);

        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "Driver Impala no encontrado. Verifica que ImpalaJDBC42.jar esta en libs/.", e);
        }

        // Segundo cinturón de seguridad: si la red corporativa no responde o el driver
        // se queda esperando más de la cuenta, este timeout evita que el intento quede colgado.
        DriverManager.setLoginTimeout(35);

        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection conn = openConnection(fullUrl)) {
            log.info("Conexion LZ establecida OK ({}:{})", lzHost, lzPort);

            try (Statement stmt = conn.createStatement()) {
                // El fetch size ayuda a que el driver vaya trayendo filas por bloques y no
                // haga una carga demasiado agresiva desde Impala en una sola lectura.
                stmt.setFetchSize(1000);
                log.info("Ejecutando query en Impala...");

                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    List<String> colNames = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) {
                        colNames.add(meta.getColumnName(i).toLowerCase());
                    }

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= cols; i++) {
                            row.put(colNames.get(i - 1), rs.getObject(i));
                        }
                        rows.add(row);
                        if (rows.size() % 10_000 == 0) {
                            log.info("  ... {} filas leidas de LZ", rows.size());
                        }
                    }
                }
            }

            log.info("LZ devolvio {} filas en total", rows.size());
            return rows;

        } catch (SQLException e) {
            throw new RuntimeException("Error ejecutando query en LZ: " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta una query que retorna un unico valor escalar (e.g., COUNT(*)).
     * Abre conexion per-request como executeQuery.
     */
    public long executeScalar(String sql) {
        if (!isEnabled()) {
            throw new IllegalStateException("LZ no configurado: lz.host esta vacio.");
        }

        Map<String, String> creds = secretsService.getLzCredentials();
        String fullUrl = buildJdbcUrl(creds.get("user"), creds.get("password"));

        log.info("Ejecutando query escalar en LZ...");
        log.debug("SQL escalar: {}", sql);

        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Driver Impala no encontrado.", e);
        }

        DriverManager.setLoginTimeout(35);

        try (Connection conn = openConnection(fullUrl);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(sql)) {

            log.info("Conexion LZ (escalar) establecida OK");
            if (rs.next()) {
                long val = rs.getLong(1);
                log.info("Resultado escalar: {}", val);
                return val;
            }
            return 0L;

        } catch (SQLException e) {
            throw new RuntimeException("Error ejecutando query escalar en LZ: " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta una sentencia DDL o DML en LZ (Impala) sin retornar filas.
     * Uso exclusivo para seeds de DEV/QA (LzDevSeedService).
     * No disponible para operaciones de negocio normales (usar executeQuery).
     *
     * @param sql Sentencia DDL o DML completa (DROP, CREATE, INSERT, etc.)
     */
    public void execute(String sql) {
        if (!isEnabled()) {
            throw new IllegalStateException("LZ no configurado: lz.host esta vacio.");
        }

        Map<String, String> creds = secretsService.getLzCredentials();
        String fullUrl = buildJdbcUrl(creds.get("user"), creds.get("password"));

        String logSnippet = sql.replaceAll("\\s+", " ").substring(0, Math.min(sql.length(), 100));
        log.info("Ejecutando DDL/DML en LZ: {}...", logSnippet);

        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Driver Impala no encontrado.", e);
        }

        DriverManager.setLoginTimeout(35);

        try (Connection conn = openConnection(fullUrl);
             Statement  stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("DDL/DML ejecutado OK en LZ");
        } catch (SQLException e) {
            throw new RuntimeException("Error ejecutando DDL/DML en LZ: " + e.getMessage(), e);
        }
    }

    private Connection openConnection(String fullUrl) throws SQLException {
        return withLzTruststore(() -> DriverManager.getConnection(fullUrl));
    }

    private <T> T withLzTruststore(SqlSupplier<T> action) throws SQLException {
        if (resolvedTruststorePath == null) {
            return action.get();
        }

        synchronized (LZ_SSL_TRUSTSTORE_LOCK) {
            String previousTruststore = System.getProperty("javax.net.ssl.trustStore");
            String previousTruststorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
            String previousTruststoreType = System.getProperty("javax.net.ssl.trustStoreType");

            try {
                System.setProperty("javax.net.ssl.trustStore", resolvedTruststorePath.toString());
                System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);
                System.setProperty("javax.net.ssl.trustStoreType", resolveTruststoreType());
                return action.get();
            } finally {
                restoreSystemProperty("javax.net.ssl.trustStore", previousTruststore);
                restoreSystemProperty("javax.net.ssl.trustStorePassword", previousTruststorePassword);
                restoreSystemProperty("javax.net.ssl.trustStoreType", previousTruststoreType);
                restoreJvmHttpsDefaults();
            }
        }
    }

    /**
     * JSSE puede cachear el SSLContext default la primera vez que se usa HTTPS.
     * Si LZ abrió una conexión mientras el truststore temporal de Impala estaba activo,
     * futuras llamadas HTTPS (por ejemplo JWKs de Entra ID) pueden seguir usando ese
     * contexto aun después de restaurar las system properties. Aquí se fuerza un
     * nuevo contexto basado en la configuración actual de la JVM.
     */
    private void restoreJvmHttpsDefaults() {
        try {
            SSLContext restoredContext = SSLContext.getInstance("TLS");
            restoredContext.init(null, null, null);
            SSLContext.setDefault(restoredContext);
            HttpsURLConnection.setDefaultSSLSocketFactory(restoredContext.getSocketFactory());
        } catch (GeneralSecurityException e) {
            log.warn("No se pudieron restaurar los defaults HTTPS de la JVM tras conexión LZ: {}", e.getMessage());
        }
    }

    private String resolveTruststoreType() {
        if (truststoreType != null && !truststoreType.isBlank()) {
            return truststoreType.trim();
        }

        String fileName = resolvedTruststorePath == null ? "" : resolvedTruststorePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".p12") || fileName.endsWith(".pfx")) {
            return "PKCS12";
        }
        return "JKS";
    }

    private void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
