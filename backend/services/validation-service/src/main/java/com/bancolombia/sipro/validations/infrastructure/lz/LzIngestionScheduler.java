package com.bancolombia.sipro.validations.infrastructure.lz;

import com.bancolombia.sipro.validations.application.dto.LzIngestionRequest;
import com.bancolombia.sipro.validations.application.dto.LzIngestionResponse;
import com.bancolombia.sipro.validations.application.usecase.LzIngestionUseCase;
import com.bancolombia.sipro.validations.domain.model.SiproLzCatalogoTablas;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproLzCatalogoTablasRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduler de ingesta LZ — arranque automatico + reintento diario.
 *
 * ── Flujo ─────────────────────────────────────────────────────────────────
 *  1. El backend arranca.
 *  2. Pasados ${lz.ingestion.startup-delay-ms} (default 5 min), se ejecuta
 *     la primera verificacion.
 *  3. Para cada tabla activa en sipro_lz_catalogo_tablas, comprueba si ya
 *     existe un run SUCCESS en sipro_lz_ingestion_run para el MES ACTUAL.
 *       → Si existe   → SKIP (guard en LzIngestionUseCase).
 *       → Si no existe → extrae datos de LZ y los carga en PostgreSQL.
 *  4. Cada ${lz.ingestion.retry-interval-ms} (default 24 h) repite el paso 3.
 *     Como el guard es idempotente, ejecutarlo varias veces en el mismo mes
 *     no genera duplicados.
 *
 * ── Conflicto sipro_lz_ingestion_run vacio + datos en Final ───────────────
 *  Si sipro_lz_ingestion_run esta vacio pero sipro_lz_mdm_datos_generales_cliente
 *  tiene datos, el metodo cleanOrphanedData() de LzIngestionUseCase los detecta
 *  como huerfanos (run_id no existe) y los elimina antes de la nueva carga.
 *
 * ── DEV vs PDN ────────────────────────────────────────────────────────────
 *  DEV  : lz.ingestion.max-rows=100  → maximo 100 filas de LZ.
 *  PDN  : lz.ingestion.max-rows=0    → sin limite.
 *  El SQL completo de extracción se gestiona desde sipro_parametros_tablas_lz.query_sql.
 */
@Component
public class LzIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(LzIngestionScheduler.class);

    private final LzIngestionUseCase ingestionUseCase;
    private final SiproLzCatalogoTablasRepository catalogoRepo;

    public LzIngestionScheduler(LzIngestionUseCase ingestionUseCase,
                                SiproLzCatalogoTablasRepository catalogoRepo) {
        this.ingestionUseCase = ingestionUseCase;
        this.catalogoRepo = catalogoRepo;
    }

    /**
     * Se ejecuta automaticamente:
     *  - Primera vez: ${lz.ingestion.startup-delay-ms} despues del arranque (default 5 min).
     *  - Luego: cada ${lz.ingestion.retry-interval-ms} (default 24 h).
     *
     * Usa el MES ACTUAL como periodo. El guard mensual de LzIngestionUseCase
     * garantiza que si ya existe SUCCESS para este mes/tabla se hace SKIP.
     *
     * Itera sobre TODAS las tablas activas en sipro_lz_catalogo_tablas.
     */
    @Scheduled(
        initialDelayString = "#{@parametroUnicoService.getString('LZ_INGESTION_STARTUP_DELAY_MS', '300000')}",
        fixedDelayString   = "#{@parametroUnicoService.getString('LZ_INGESTION_RETRY_INTERVAL_MS', '86400000')}"
    )
    public void ejecutarIngesta() {
        LocalDate hoy    = LocalDate.now();
        int year  = hoy.getYear();
        int month = hoy.getMonthValue();

        List<SiproLzCatalogoTablas> tablasActivas = catalogoRepo.findByActivoTrue();

        if (tablasActivas.isEmpty()) {
            log.warn("=== [SCHEDULER] No hay tablas activas en sipro_lz_catalogo_tablas — nada que ingestar ===");
            return;
        }

        log.info("=== [SCHEDULER] Verificando ingesta LZ | mes_actual={}/{} tablas_activas={} ===",
            year, month, tablasActivas.size());

        for (SiproLzCatalogoTablas catalogo : tablasActivas) {
            String tablaOrigen = catalogo.getTablaOrigen();
            log.info("[SCHEDULER] Procesando tabla id={} origen='{}' ...",
                catalogo.getIdTabla(), tablaOrigen);

            try {
                LzIngestionRequest req = new LzIngestionRequest();
                req.setTablaOrigen(tablaOrigen);
                req.setPeriodYear(year);
                req.setPeriodMonth(month);
                req.setForceOverwrite(false);

                LzIngestionResponse resp = ingestionUseCase.execute(req);

                log.info("[SCHEDULER] Resultado tabla='{}' | status={} runId={} lzRows={} finalRows={}",
                    tablaOrigen, resp.getStatus(), resp.getRunId(),
                    resp.getLzRowCount(), resp.getPgFinalRowCount());

            } catch (Exception e) {
                log.error("[SCHEDULER] Error en tabla='{}': {} — continuando con la siguiente",
                    tablaOrigen, e.getMessage(), e);
            }
        }

        log.info("=== [SCHEDULER] Ciclo completado para {} tablas (mes {}/{}) ===",
            tablasActivas.size(), year, month);
    }
}
