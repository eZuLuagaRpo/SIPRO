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
 * Scheduler de ingesta LZ — arranque automatico + calendario mensual (dia 1 y ultimo dia).
 *
 * ── Flujo ─────────────────────────────────────────────────────────────────
 *  1. El backend arranca.
 *  2. Pasados ${lz.ingestion.startup-delay-ms} (default 5 min), se ejecuta
 *     una verificacion UNICA que respeta el guard (LZ_INGESTION_GUARD_DAYS):
 *     solo corre si no hubo SUCCESS reciente. No se repite periodicamente
 *     por si sola — el calendario mensual es quien la retoma despues.
 *  3. Todas las noches a la hora configurada (${lz.ingestion.mensual-cron},
 *     default 8pm) se ejecuta una verificacion que:
 *       → Si HOY es el dia 1 del mes o el ultimo dia del mes: fuerza la
 *         ingesta (forceOverwrite=true), sin importar el guard. Estas dos
 *         fechas son obligatorias y siempre deben correr, aunque la
 *         ejecucion anterior haya sido apenas ayer (ej: 31 de un mes y
 *         1 del siguiente, con solo 1 dia de diferencia).
 *       → Cualquier otro dia: respeta el guard como red de respaldo — si
 *         por alguna razon las fechas obligatorias fallaron (servidor
 *         caido, error), esta verificacion diaria eventualmente la
 *         recupera una vez pasan LZ_INGESTION_GUARD_DAYS dias sin exito.
 *  4. Para cada tabla activa en sipro_lz_catalogo_tablas, el guard interno
 *     de LzIngestionUseCase decide si procede o se salta (idempotente:
 *     ejecutarlo de mas nunca genera duplicados).
 *
 * ── Guard LZ_INGESTION_GUARD_DAYS ──────────────────────────────────────────
 *  Deja de ser "la cadencia real" (eso ahora lo deciden las fechas de
 *  calendario, dia 1 / ultimo dia, que se fuerzan sin preguntarle al guard).
 *  Pasa a ser solo la red de respaldo del paso 3. Debe quedar en un valor
 *  MAYOR al hueco mas largo posible entre el dia 1 y el ultimo dia del MISMO
 *  mes (hasta 30 dias, ej: enero 1 → enero 31), para que el respaldo se
 *  quede dormido en un mes sano y solo despierte si de verdad algo fallo
 *  por mas de un mes. Valor recomendado: 32.
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
     * Verificacion unica al arrancar: ${lz.ingestion.startup-delay-ms} despues
     * del arranque (default 5 min). Respeta el guard — no fuerza nada.
     * No se repite por si sola: el fixedDelay queda fijado a un valor enorme
     * para que Spring no la reprograme; el chequeo diario del calendario
     * mensual es quien retoma la cadencia despues de este primer intento.
     */
    @Scheduled(
        initialDelayString = "#{@parametroUnicoService.getString('LZ_INGESTION_STARTUP_DELAY_MS', '300000')}",
        fixedDelay = Long.MAX_VALUE
    )
    public void ejecutarIngestaAlArrancar() {
        log.info("=== [SCHEDULER] Verificacion de arranque (respeta guard) ===");
        ejecutarParaTablasActivas(false);
    }

    /**
     * Verificacion diaria a la hora configurada (${lz.ingestion.mensual-cron},
     * default 8pm todos los dias). Decide internamente:
     *  - Dia 1 o ultimo dia del mes → fuerza la ingesta (forceOverwrite=true).
     *  - Cualquier otro dia → respeta el guard (red de respaldo silenciosa).
     */
    @Scheduled(cron = "#{@parametroUnicoService.getString('LZ_INGESTION_MENSUAL_CRON', '0 0 20 * * *')}")
    public void ejecutarIngestaProgramada() {
        LocalDate hoy = LocalDate.now();
        boolean esPrimerDia = hoy.getDayOfMonth() == 1;
        boolean esUltimoDia = hoy.getDayOfMonth() == hoy.lengthOfMonth();
        boolean forzar = esPrimerDia || esUltimoDia;

        if (forzar) {
            log.info("=== [SCHEDULER] Fecha de calendario obligatoria ({}) — se fuerza la ingesta ===", hoy);
        } else {
            log.debug("=== [SCHEDULER] Chequeo diario de respaldo ({}) — respeta el guard ===", hoy);
        }
        ejecutarParaTablasActivas(forzar);
    }

    /**
     * Itera sobre TODAS las tablas activas en sipro_lz_catalogo_tablas y ejecuta
     * la ingesta para el MES ACTUAL. El guard interno de LzIngestionUseCase
     * garantiza que ejecutarlo de mas nunca genera duplicados.
     */
    private void ejecutarParaTablasActivas(boolean forceOverwrite) {
        LocalDate hoy    = LocalDate.now();
        int year  = hoy.getYear();
        int month = hoy.getMonthValue();

        List<SiproLzCatalogoTablas> tablasActivas = catalogoRepo.findByActivoTrue();

        if (tablasActivas.isEmpty()) {
            log.warn("=== [SCHEDULER] No hay tablas activas en sipro_lz_catalogo_tablas — nada que ingestar ===");
            return;
        }

        log.info("=== [SCHEDULER] Verificando ingesta LZ | mes_actual={}/{} tablas_activas={} forceOverwrite={} ===",
            year, month, tablasActivas.size(), forceOverwrite);

        for (SiproLzCatalogoTablas catalogo : tablasActivas) {
            String tablaOrigen = catalogo.getTablaOrigen();
            log.info("[SCHEDULER] Procesando tabla id={} origen='{}' ...",
                catalogo.getIdTabla(), tablaOrigen);

            try {
                LzIngestionRequest req = new LzIngestionRequest();
                req.setTablaOrigen(tablaOrigen);
                req.setPeriodYear(year);
                req.setPeriodMonth(month);
                req.setForceOverwrite(forceOverwrite);

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
