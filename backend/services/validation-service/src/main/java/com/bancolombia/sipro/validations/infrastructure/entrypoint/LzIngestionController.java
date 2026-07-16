package com.bancolombia.sipro.validations.infrastructure.entrypoint;

import com.bancolombia.sipro.validations.application.dto.LzIngestionRequest;
import com.bancolombia.sipro.validations.application.dto.LzIngestionResponse;
import com.bancolombia.sipro.validations.application.usecase.LzIngestionUseCase;
import com.bancolombia.sipro.validations.infrastructure.lz.LzJdbcService;
import com.bancolombia.sipro.validations.infrastructure.lz.LzSecretsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint para disparar la ingesta LZ de forma manual o desde un scheduler.
 *
 * POST /api/lz/ingest
 * {
 *   "tablaOrigen": "mdm_zona_mdm_datos_generales_clientes",
 *   "periodYear": 2026,
 *   "periodMonth": 2,
 *   "forceOverwrite": false
 * }
 */
@RestController
@RequestMapping("/api/lz")
public class LzIngestionController {

    private static final Logger log = LoggerFactory.getLogger(LzIngestionController.class);

    private final LzIngestionUseCase ingestionUseCase;
    private final LzSecretsService   secretsService;
    private final LzJdbcService      lzJdbcService;

    public LzIngestionController(LzIngestionUseCase ingestionUseCase,
                                  LzSecretsService   secretsService,
                                  LzJdbcService      lzJdbcService) {
        this.ingestionUseCase = ingestionUseCase;
        this.secretsService   = secretsService;
        this.lzJdbcService    = lzJdbcService;
    }

    /**
     * Lanza una ingesta manual de LZ para la tabla y periodo solicitados.
     */
    @PostMapping("/ingest")
    public ResponseEntity<LzIngestionResponse> ingest(@Valid @RequestBody LzIngestionRequest req) {
        log.info("Solicitud ingesta LZ: tabla={} periodo={}/{}",
            req.getTablaOrigen(), req.getPeriodYear(), req.getPeriodMonth());

        LzIngestionResponse resp = ingestionUseCase.execute(req);

        return switch (resp.getStatus()) {
            case "SUCCESS"  -> ResponseEntity.ok(resp);
            case "SKIPPED"  -> ResponseEntity.ok(resp);
            case "FAILED"   -> ResponseEntity.internalServerError().body(resp);
            default         -> ResponseEntity.accepted().body(resp);
        };
    }

    /**
     * Valida primero Secrets Manager y luego ejecuta "SELECT 1" en LZ.
     * Util para confirmar conectividad antes de lanzar una ingesta real.
     *
     * GET /api/lz/test-connection
     */
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. Test Secrets Manager
        try {
            java.util.Map<String, String> creds = secretsService.getLzCredentials();
            result.put("secrets_status", "OK");
            result.put("secrets_user",   creds.get("user"));
        } catch (Exception e) {
            result.put("secrets_status", "FAIL");
            result.put("secrets_error",  e.getMessage());
            result.put("lz_status",      "SKIP - secrets fallaron");
            return ResponseEntity.status(503).body(result);
        }

        // 2. Test conexion Impala con SELECT 1
        try {
            List<Map<String, Object>> rows = lzJdbcService.executeQuery("SELECT 1 AS ping");
            result.put("lz_status", "OK");
            result.put("lz_ping",   rows.isEmpty() ? "sin filas" : rows.get(0).toString());
        } catch (Exception e) {
            result.put("lz_status", "FAIL");
            result.put("lz_error",  e.getMessage());
            // Secrets OK pero LZ falla (sin red corporativa en DEV es normal)
            return ResponseEntity.status(503).body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Estado de la ultima ingesta para tabla+periodo — consultar sipro_lz_ingestion_run.
     */
    @GetMapping("/status")
    public ResponseEntity<String> status(
            @RequestParam String tablaOrigen,
            @RequestParam int    periodYear,
            @RequestParam int    periodMonth) {
        return ResponseEntity.ok(
            "Consulta estado para: " + tablaOrigen + " " + periodYear + "/" + periodMonth +
            " — ver tabla sipro_lz_ingestion_run en Postgres.");
    }
}
