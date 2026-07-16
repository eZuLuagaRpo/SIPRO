package com.bancolombia.sipro.validations.infrastructure.lz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Se ejecuta UNA SOLA VEZ al arrancar el backend.
 *
 * Valida que Secrets Manager (LocalStack en DEV, AWS en PDN) responda
 * y que el secreto lz/creds contenga user y password validos.
 *
 * NO conecta a Impala/LZ en este paso (eso requiere red corporativa).
 * Si la validacion falla, el backend sigue corriendo pero loggea WARNING claro.
 */
@Component
@Order(1)
public class LzStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LzStartupValidator.class);

    private final LzSecretsService secretsService;
    private final LzJdbcService    lzJdbcService;
    private final Environment      environment;

    @Value("${lz.ingestion.validate-on-startup:true}")
    private boolean validateOnStartup;

    public LzStartupValidator(LzSecretsService secretsService,
                               LzJdbcService lzJdbcService,
                               Environment environment) {
        this.secretsService = secretsService;
        this.lzJdbcService  = lzJdbcService;
        this.environment    = environment;
    }

    /**
     * Verifica al inicio que las credenciales y la configuración mínima de LZ estén disponibles.
     */
    @Override
    public void run(ApplicationArguments args) {
        String[] profiles = environment.getActiveProfiles();
        String perfil     = profiles.length > 0 ? String.join(",", profiles).toUpperCase() : "DEFAULT";
        String lzHost     = lzJdbcService.isEnabled() ? "10.8.85.237:21050" : "(no configurado)";

        log.info("");
        log.info("+============================================================+");
        log.info(String.format("|  SIPRO -- Perfil activo: %-30s    |", perfil));
        log.info(String.format("|  LZ Host  : %-43s|", lzHost));
        log.info("|  Ambiente : {} -- LZ {} |",
            perfil.equals("PDN") ? "PRODUCCION    " : "PREPRODUCTIVO (10.8.85.237)",
            perfil.equals("PDN") ? "productivo    " : "preproductivo");
        log.info("+============================================================+");
        log.info("");
        log.info("=== LZ Startup Validator ===");

        if (!validateOnStartup) {
            log.info("Validacion LZ deshabilitada (lz.ingestion.validate-on-startup=false). Skip.");
            return;
        }

        // 1. Validar Secrets Manager
        try {
            Map<String, String> creds = secretsService.getLzCredentials();
            String user = creds.get("user");
            log.info("[OK] Secrets Manager responde. Usuario LZ: {}", user);
        } catch (Exception e) {
            log.warn("================================================================");
            log.warn("[WARNING] Secrets Manager NO responde: {}", e.getMessage());
            log.warn("  DEV: Asegurate de que LocalStack este corriendo.");
            log.warn("  Comando: .\\run.ps1 (en Conectar a LZ)");
            log.warn("  Endpoint configurado: lz.secrets.endpoint");
            log.warn("================================================================");
            // No lanzamos excepcion — el backend levanta de todas formas
            return;
        }

        // 2. Avisar sobre estado de conexion Impala
        if (!lzJdbcService.isEnabled()) {
            log.warn("[WARNING] lz.host no configurado. La ingesta LZ no podra ejecutarse.");
        } else {
            log.info("[OK] LZ host configurado. Conexion dinamica habilitada.");
            log.info("[INFO] Para probar conexion manual: POST /api/lz/test-connection");
        }

        log.info("=== LZ Startup Validator completado ===");
    }
}
