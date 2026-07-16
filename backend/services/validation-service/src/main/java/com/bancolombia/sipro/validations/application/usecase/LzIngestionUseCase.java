package com.bancolombia.sipro.validations.application.usecase;

import com.bancolombia.sipro.validations.application.dto.LzIngestionRequest;
import com.bancolombia.sipro.validations.application.dto.LzIngestionResponse;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.domain.model.SiproLzIngestionRun;
import com.bancolombia.sipro.validations.domain.model.SiproParametroTablaLz;
import com.bancolombia.sipro.validations.infrastructure.lz.LzJdbcService;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproLzIngestionRunRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproParametroTablaLzRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orquesta el flujo completo de ingesta desde LZ hacia PostgreSQL.
 *
 * Flujo robusto con manejo de interrupciones y datos huerfanos:
 *
 *  0. RECOVERY: marcar runs STARTED viejos (> timeout) como FAILED.
 *  1. Obtener config ACTIVA (max version) por tabla del catalogo.
 *  2. ORPHAN CLEANUP: limpiar datos STG/Final cuyo ingestion_run_id
 *     no existe en sipro_lz_ingestion_run o cuyo run esta FAILED.
 *  3. Guard: si ya existe SUCCESS en el periodo → SKIP (salvo forceOverwrite).
 *  4. Guard concurrencia: si hay STARTED activo → SKIP.
 *  5. Registrar RUN STARTED.
 *  6. COUNT(*) sobre query_sql → log total filas.
 *  7. Ejecutar query_sql en LZ (con LIMIT configurable en DEV).
 *  8. Limpiar STG para este run_id.
 *  9. INSERT batch a STG.
 * 10. Validar conteos LZ vs STG.
 * 11. PROMOTE transaccional: DELETE periodo en Final + INSERT FROM STG WHERE run_id.
 * 12. Limpiar STG (datos ya estan en Final).
 * 13. Marcar SUCCESS.
 *
 * Si falla en cualquier paso:
 *  - Marca run como FAILED.
 *  - Limpia STG para este run_id (datos parciales).
 *
 * NOTA DE VINCULACION LOGICA:
 *  Las tablas estan vinculadas por la columna [ingestion_run_id] (en datos)
 *  que referencia a [run_id] (en sipro_lz_ingestion_run).
 *
 *  - Tabla de datos (mdm, stg): PARTITION BY LIST (ingestion_run_id)
 *    Optimiza borrado/carga masiva por ejecucion. En PDN se pueden crear
 *    particiones dedicadas por run_id para DROP PARTITION instantaneo.
 *    En DEV usa DEFAULT partition.
 *
 *  - Tabla de control (ingestion_run): PARTITION BY RANGE (started_at)
 *    Gestiona historico y purgado por periodos de tiempo.
 *
 * Nota de mantenimiento 2026: comentarios funcionales agregados por
 * Junior Alexander Ortiz Arenas (junortiz), ANALITICO/A - EVC OTRAS FUNCIONES CORPORATIVAS.
 */
@Service
public class LzIngestionUseCase {

    private static final Logger log = LoggerFactory.getLogger(LzIngestionUseCase.class);

    private final SiproParametroTablaLzRepository  paramRepo;
    private final SiproLzIngestionRunRepository    runRepo;
    private final LzJdbcService                    lzJdbc;
    private final JdbcTemplate                     pg;
    private final ParametroUnicoService            parametroUnicoService;

    private static final int DEFAULT_LZ_INGESTION_MAX_ROWS = 0;
    private static final int DEFAULT_LZ_INGESTION_STALE_RUN_TIMEOUT_MINUTES = 60;
    private static final long DEFAULT_MINIMUM_EXPECTED_ROWS = 100;
    private static final Set<String> REQUIRED_LZ_COLUMNS = Set.of(
        "llave_mdm", "year", "month", "day"
    );
    private static final Set<String> SYSTEM_COLUMNS = Set.of(
        "period_year", "period_month", "ingestion_run_id", "inserted_at"
    );

    public LzIngestionUseCase(SiproParametroTablaLzRepository paramRepo,
                               SiproLzIngestionRunRepository   runRepo,
                               LzJdbcService                   lzJdbc,
                               JdbcTemplate                    pg,
                               ParametroUnicoService           parametroUnicoService) {
        this.paramRepo = paramRepo;
        this.runRepo   = runRepo;
        this.lzJdbc    = lzJdbc;
        this.pg        = pg;
        this.parametroUnicoService = parametroUnicoService;
    }

    // ── Punto de entrada ────────────────────────────────────────────────────

    public LzIngestionResponse execute(LzIngestionRequest req) {
        long t0 = System.currentTimeMillis();
        int maxRows = parametroUnicoService.getInt("LZ_INGESTION_MAX_ROWS", DEFAULT_LZ_INGESTION_MAX_ROWS);
        long minimumExpectedRows = parametroUnicoService.getLong("MINIMUM_EXPECTED_ROWS", DEFAULT_MINIMUM_EXPECTED_ROWS);

        String tablaOrigen = req.getTablaOrigen();
        int    year        = req.getPeriodYear();
        int    month       = req.getPeriodMonth();

        // 1. Obtener configuracion activa
        SiproParametroTablaLz param = paramRepo.findActivaMaxVersion(tablaOrigen)
            .orElseThrow(() -> new IllegalArgumentException(
                "No existe parametro ACTIVO para tabla_origen: " + tablaOrigen));
        Integer idTabla = param.getIdTabla();
        String tablaCatalogo = Optional.ofNullable(param.getTablaOrigen()).orElse(tablaOrigen);
        if (idTabla == null) {
            throw new IllegalStateException(
                "El parametro id=" + param.getIdParametro() + " no tiene id_tabla asociado en sipro_lz_catalogo_tablas.");
        }

        // 1b. Validar que las tablas destino/staging existen en PostgreSQL.
        //     Si no existen (ej: nombre derivado incorrecto en catalogo), se aborta
        //     con mensaje claro. El scheduler reintenta en 24h; el admin corrige
        //     sipro_lz_catalogo_tablas.tabla_destino_pg / tabla_staging_pg.
        String stgTable   = param.getTablaStagingPg();
        String finalTable = param.getTablaDestinoPg();
        if (finalTable == null || finalTable.isBlank()) {
            String msg = "El catalogo no resolvio tabla_destino_pg para tabla_origen='" + tablaCatalogo
                + "' (id_tabla=" + idTabla + ").";
            log.error(msg);
            return LzIngestionResponse.failed(null, msg);
        }
        if (stgTable == null || stgTable.isBlank()) {
            String msg = "El catalogo no resolvio tabla_staging_pg para tabla_origen='" + tablaCatalogo
                + "' (id_tabla=" + idTabla + ").";
            log.error(msg);
            return LzIngestionResponse.failed(null, msg);
        }
        if (!tableExistsInPg(finalTable)) {
            String msg = "Tabla destino '" + finalTable + "' NO existe en PostgreSQL. "
                + "Verifica sipro_lz_catalogo_tablas.tabla_destino_pg para tabla_origen='" + tablaCatalogo + "'.";
            log.error(msg);
            return LzIngestionResponse.failed(null, msg);
        }
        if (!tableExistsInPg(stgTable)) {
            String msg = "Tabla staging '" + stgTable + "' NO existe en PostgreSQL. "
                + "Verifica sipro_lz_catalogo_tablas.tabla_staging_pg para tabla_origen='" + tablaCatalogo + "'.";
            log.error(msg);
            return LzIngestionResponse.failed(null, msg);
        }

        // 0. RECOVERY: marcar runs STARTED que llevan demasiado tiempo como FAILED.
        //    Cubre: servidor crasheó, bootRun matado, etc.
        recoverStaleRuns(idTabla, tablaCatalogo);

        // 2. ORPHAN CLEANUP: datos en STG/Final cuyo ingestion_run_id no existe
        //    en sipro_lz_ingestion_run, o cuyo run esta FAILED.
        //    Cubre: usuario limpio manualmente sipro_lz_ingestion_run.
        cleanOrphanedData(param);

        // 3. Guard mensual
        if (!req.isForceOverwrite()) {
            Optional<SiproLzIngestionRun> existing = runRepo.findSuccess(idTabla, year, month);
            if (existing.isPresent()) {
                log.info("Guard: ya existe SUCCESS run_id={} para {}/{}-{}. SKIP.",
                    existing.get().getRunId(), tablaCatalogo, year, month);
                return LzIngestionResponse.skipped(
                    "Ya existe ejecucion exitosa run_id=" + existing.get().getRunId()
                    + " para " + tablaCatalogo + " periodo " + year + "/" + month
                    + ". Usa forceOverwrite=true para repetir.");
            }
        }

        // No se deja correr el mismo periodo en paralelo porque dos procesos podrían
        // limpiarse o promoverse entre sí y dejar resultados mezclados.
        String lockKey = tablaCatalogo + "(id_tabla=" + idTabla + "):" + year + ":" + month;
        Integer startedCount = pg.queryForObject(
            "SELECT COUNT(*) FROM sipro_lz_ingestion_run "
            + "WHERE id_tabla = ? AND period_year = ? AND period_month = ? "
            + "AND status = 'STARTED'",
            Integer.class, idTabla, year, month);
        if (startedCount != null && startedCount > 0) {
            log.warn("Guard concurrencia: ya hay {} run(s) STARTED para {}", startedCount, lockKey);
            return LzIngestionResponse.skipped(
                "Ya hay " + startedCount + " ejecucion(es) STARTED para " + lockKey
                + ". Espera a que termine o marcala FAILED manualmente.");
        }
        log.info("No hay runs STARTED activos para {} — procediendo", lockKey);

        // 5. Registrar RUN STARTED
        String sql = buildLzQuery(param, maxRows);
        String queryHash = Integer.toHexString(sql.hashCode());
        SiproLzIngestionRun run = SiproLzIngestionRun.started(param, year, month, queryHash);
        run = runRepo.save(run);
        log.info("RUN STARTED id={} tabla={} idTabla={} idParametro={} periodo={}/{} maxRows={}",
            run.getRunId(), tablaCatalogo, idTabla, param.getIdParametro(), year, month, maxRows);

        try {
            // 6. Contar filas en LZ (rapido, sin transferir datos)
            String countSql = buildLzCountQuery(param);
            log.info("Contando filas en LZ: {}", countSql);
            long totalLzCount = lzJdbc.executeScalar(countSql);
            log.info("Total filas en LZ para periodo {}/{}: {}", year, month, totalLzCount);

            // Es mejor fallar temprano que marcar una ingesta como exitosa con un lote casi vacío.
            // Este umbral protege cuando LZ todavía no cargó el periodo o publicó datos incompletos.
            if (totalLzCount < minimumExpectedRows) {
                String msg = String.format(
                    "LZ devolvio %d filas (minimo esperado: %d). " +
                    "Los datos del periodo %d/%d aun no estan disponibles en el Data Lake.",
                    totalLzCount, minimumExpectedRows, year, month);
                log.warn(msg);
                run.markFailed(msg);
                runRepo.save(run);
                return LzIngestionResponse.failed(run.getRunId(), msg);
            }

            if (maxRows > 0) {
                log.info("maxRows={} configurado — se extraeran max {} de {} filas totales",
                    maxRows, maxRows, totalLzCount);
            }

            // 7. Extraer desde LZ (con LIMIT si maxRows > 0)
            log.info("Ejecutando SELECT en LZ: {}", sql);
            List<Map<String, Object>> lzRows = lzJdbc.executeQuery(sql);
            List<String> businessColumns = resolveBusinessColumns(param, stgTable, lzRows);
            long lzCount = lzRows.size();
            run.setLzRowCount(lzCount);
            runRepo.save(run);

            // 8. Limpiar STG para este run_id antes de insertar
            cleanStagingForRun(param, run.getRunId());

            // 9. Cargar a staging
            long stgCount = loadToStaging(param, lzRows, businessColumns, run.getRunId(), year, month);
            run.setPgStgRowCount(stgCount);
            runRepo.save(run);

            // 10. Validar conteos
            if (lzCount != stgCount) {
                throw new IllegalStateException(
                    "Mismatch conteos: LZ=" + lzCount + " vs STG=" + stgCount);
            }

            // Aquí se reemplaza el periodo completo en la tabla final con lo que acaba de
            // entrar a STG. Si este paso falla, el periodo anterior no se pierde por rollback.
            long finalCount = promoteToFinal(param, businessColumns, run.getRunId(), year, month);
            run.setPgFinalRowCount(finalCount);

            // 12. Limpiar STG tras promocion exitosa (datos ya estan en Final)
            cleanStagingForRun(param, run.getRunId());

            // 13. Marcar SUCCESS
            run.markSuccess(lzCount, stgCount, finalCount);
            runRepo.save(run);

            long duration = System.currentTimeMillis() - t0;
            log.info("RUN SUCCESS id={} lz={} stg={} final={} {}ms",
                run.getRunId(), lzCount, stgCount, finalCount, duration);
            return LzIngestionResponse.success(run.getRunId(), lzCount, stgCount, finalCount, duration);

        } catch (Exception e) {
            log.error("RUN FAILED id={}: {}", run.getRunId(), e.getMessage(), e);
            run.markFailed(e.getMessage());
            runRepo.save(run);

            // Limpiar datos parciales de STG para este run
            try {
                cleanStagingForRun(param, run.getRunId());
            } catch (Exception cleanupErr) {
                log.warn("No se pudo limpiar STG tras fallo del run {}: {}",
                    run.getRunId(), cleanupErr.getMessage());
            }

            return LzIngestionResponse.failed(run.getRunId(), e.getMessage());
        }
    }

    // ── Recovery y limpieza ────────────────────────────────────────────────

    /**
     * Marca como FAILED los runs que llevan mas de staleRunTimeoutMinutes en STARTED.
     * Cubre interrupciones del servidor: crash, bootRun matado, OOM kill, etc.
     */
    private void recoverStaleRuns(Integer idTabla, String tablaOrigen) {
        int staleRunTimeoutMinutes = parametroUnicoService.getInt(
                "LZ_INGESTION_STALE_RUN_TIMEOUT_MINUTES",
                DEFAULT_LZ_INGESTION_STALE_RUN_TIMEOUT_MINUTES);
        int updated = pg.update(
            "UPDATE sipro_lz_ingestion_run "
            + "SET status = 'FAILED', "
            + "    message = 'Auto-recuperado: STARTED > ' || ? || ' minutos (servidor interrumpido)', "
            + "    ended_at = now() "
            + "WHERE id_tabla = ? AND status = 'STARTED' "
            + "AND started_at < now() - make_interval(mins => ?)",
            staleRunTimeoutMinutes, idTabla, staleRunTimeoutMinutes);
        if (updated > 0) {
            log.warn("RECOVERY: {} run(s) STARTED antiguos marcados como FAILED para tabla={} id_tabla={} (timeout {} min)",
                updated, tablaOrigen, idTabla, staleRunTimeoutMinutes);
        }
    }

    /**
     * Limpia datos huerfanos en STG y Final.
     *
     * Caso 1: ingestion_run_id NO existe en sipro_lz_ingestion_run.
     *   → El usuario borro manualmente el run. Los datos quedaron sueltos.
     *
     * Caso 2: ingestion_run_id refiere a un run con status FAILED.
     *   → El run fallo pero los datos parciales de STG no se limpiaron (ej: OOM).
     *
     * Las tablas de datos son LIST-particionadas por ingestion_run_id (DEFAULT partition).
     * DELETE WHERE ingestion_run_id = ? opera sobre la DEFAULT partition.
     * La tabla de control (ingestion_run) esta RANGE-particionada por started_at.
     */
    private void cleanOrphanedData(SiproParametroTablaLz param) {
        String stgTable   = param.getTablaStagingPg();
        String finalTable = param.getTablaDestinoPg();

        // 1. Datos en STG cuyo run_id no existe en run table
        if (tableExistsInPg(stgTable)) {
            int stgOrphans = pg.update(
                "DELETE FROM " + stgTable + " d "
                + "WHERE NOT EXISTS ("
                + "  SELECT 1 FROM sipro_lz_ingestion_run r "
                + "  WHERE r.run_id = d.ingestion_run_id)");
            if (stgOrphans > 0) {
                log.warn("CLEANUP: eliminadas {} filas huerfanas de {} (run_id inexistente)",
                    stgOrphans, stgTable);
            }

            // 2. Datos en STG cuyo run esta en FAILED (parciales, nunca promovidos)
            int stgFailed = pg.update(
                "DELETE FROM " + stgTable + " d "
                + "USING sipro_lz_ingestion_run r "
                + "WHERE d.ingestion_run_id = r.run_id AND r.status = 'FAILED'");
            if (stgFailed > 0) {
                log.warn("CLEANUP: eliminadas {} filas de {} de runs FAILED", stgFailed, stgTable);
            }
        } else {
            log.warn("CLEANUP: tabla STG '{}' no existe en PG — saltando limpieza STG", stgTable);
        }

        // 3. Datos en Final cuyo run_id no existe en run table
        if (tableExistsInPg(finalTable)) {
            int finalOrphans = pg.update(
                "DELETE FROM " + finalTable + " d "
                + "WHERE NOT EXISTS ("
                + "  SELECT 1 FROM sipro_lz_ingestion_run r "
                + "  WHERE r.run_id = d.ingestion_run_id)");
            if (finalOrphans > 0) {
                log.warn("CLEANUP: eliminadas {} filas huerfanas de {} (run_id inexistente)",
                    finalOrphans, finalTable);
            }
        } else {
            log.warn("CLEANUP: tabla Final '{}' no existe en PG — saltando limpieza Final", finalTable);
        }

        // Nota: NO eliminamos datos de Final cuyo run esta en FAILED.
        // promoteToFinal es transaccional: si el run fallo ANTES de promover,
        // no hay datos en Final; si fallo DESPUES del commit, los datos son validos.
    }

    /**
     * Limpia STG para un run_id especifico.
     * Se usa:  (a) antes de INSERT al STG (idempotencia),
     *          (b) tras un fallo (datos parciales),
     *          (c) tras promocion exitosa (datos ya estan en Final).
     */
    private void cleanStagingForRun(SiproParametroTablaLz param, Long runId) {
        String stgTable = param.getTablaStagingPg();
        int deleted = pg.update(
            "DELETE FROM " + stgTable + " WHERE ingestion_run_id = ?", runId);
        if (deleted > 0) {
            log.info("STG cleanup: eliminadas {} filas de {} para run_id={}",
                deleted, stgTable, runId);
        }
    }

    // ── Validacion de existencia de tablas ──────────────────────────────────

    /**
     * Verifica si una tabla (o tabla particionada) existe en PostgreSQL.
     * Consulta pg_class con relkind IN ('r','p') → tabla normal o particionada.
     */
    private boolean tableExistsInPg(String tableName) {
        if (tableName == null || tableName.isBlank()) return false;
        Boolean exists = pg.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM pg_class WHERE relname = ? AND relkind IN ('p','r'))",
            Boolean.class, tableName.trim().toLowerCase());
        return Boolean.TRUE.equals(exists);
    }

    // ── Helpers SQL ──────────────────────────────────────────────────────────

    private String normalizeBaseQuery(SiproParametroTablaLz param) {
        String querySql = param.getQuerySql();
        if (querySql == null || querySql.isBlank()) {
            throw new IllegalStateException(
                "El parametro id=" + param.getIdParametro() + " no tiene query_sql configurado.");
        }

        String normalized = querySql.strip();
        if (normalized.startsWith("\"")) {
            normalized = normalized.substring(1).strip();
        }
        if (normalized.endsWith("\"")) {
            normalized = normalized.substring(0, normalized.length() - 1).strip();
        }
        normalized = normalized.replaceFirst(";+\\s*$", "");

        if (normalized.isBlank()) {
            throw new IllegalStateException(
                "El query_sql del parametro id=" + param.getIdParametro() + " quedo vacio tras normalizarlo.");
        }
        return adaptPostgresDateExpressionsForImpala(normalized);
    }

    static String adaptPostgresDateExpressionsForImpala(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }

        String translated = sql;
        translated = replaceExtractNowMinusInterval(translated, "YEAR", "year");
        translated = replaceExtractNowMinusInterval(translated, "MONTH", "month");
        translated = replaceExtractNowMinusInterval(translated, "DAY", "day");
        translated = replaceExtractNow(translated, "YEAR", "year");
        translated = replaceExtractNow(translated, "MONTH", "month");
        translated = replaceExtractNow(translated, "DAY", "day");
        return translated;
    }

    private static String replaceExtractNowMinusInterval(String sql, String extractPart, String impalaFunction) {
        return sql.replaceAll(
            "(?i)EXTRACT\\(\\s*" + extractPart + "\\s+FROM\\s+NOW\\(\\)\\s*-\\s*INTERVAL\\s*'([0-9]+)\\s+days?'\\s*\\)",
            impalaFunction + "(date_sub(now(), $1))");
    }

    private static String replaceExtractNow(String sql, String extractPart, String impalaFunction) {
        return sql.replaceAll(
            "(?i)EXTRACT\\(\\s*" + extractPart + "\\s+FROM\\s+NOW\\(\\)\\s*\\)",
            impalaFunction + "(now())");
    }

    private String buildLzCountQuery(SiproParametroTablaLz param) {
        return "SELECT COUNT(*) FROM (" + normalizeBaseQuery(param) + ") sipro_lz_count";
    }

    private String buildLzQuery(SiproParametroTablaLz param, int limit) {
        String baseQuery = normalizeBaseQuery(param);
        if (limit <= 0) {
            return baseQuery;
        }
        return "SELECT * FROM (" + baseQuery + ") sipro_lz_query LIMIT " + limit;
    }

    private List<String> resolveBusinessColumns(SiproParametroTablaLz param,
                                                String stgTable,
                                                List<Map<String, Object>> lzRows) {
        List<String> targetColumns = getBusinessColumnsFromPg(stgTable);
        if (targetColumns.isEmpty()) {
            throw new IllegalStateException(
                "La tabla staging '" + stgTable + "' no expone columnas de negocio para cargar el parametro id="
                    + param.getIdParametro() + ".");
        }

        List<String> missingRequired = REQUIRED_LZ_COLUMNS.stream()
            .filter(required -> !targetColumns.contains(required))
            .sorted()
            .collect(Collectors.toList());
        if (!missingRequired.isEmpty()) {
            throw new IllegalStateException(
                "La tabla staging '" + stgTable + "' no incluye columnas obligatorias para LZ: " + missingRequired);
        }

        if (!lzRows.isEmpty()) {
            Set<String> queryColumns = lzRows.get(0).keySet().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));

            List<String> missingInQuery = targetColumns.stream()
                .filter(column -> !queryColumns.contains(column))
                .collect(Collectors.toList());
            if (!missingInQuery.isEmpty()) {
                throw new IllegalStateException(
                    "El query_sql del parametro id=" + param.getIdParametro()
                        + " no devuelve columnas requeridas por " + stgTable + ": " + missingInQuery);
            }
        }

        return targetColumns;
    }

    private List<String> getBusinessColumnsFromPg(String tableName) {
        PgTableRef tableRef = PgTableRef.parse(tableName);
        List<String> allColumns = pg.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
            String.class,
            tableRef.schema(),
            tableRef.table());

        return allColumns.stream()
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(column -> !SYSTEM_COLUMNS.contains(column))
            .collect(Collectors.toList());
    }

    // ── Carga STG ────────────────────────────────────────────────────────────

    /**
     * Carga las filas en staging en batch.
     * El STG para este run_id ya fue limpiado por cleanStagingForRun() antes.
     *
    * Las columnas de staging son: period_year, period_month, ingestion_run_id
    * + columnas negocio resueltas desde la tabla staging en PostgreSQL.
     *
     * La tabla STG esta LIST-particionada por ingestion_run_id:
     * los datos van a la DEFAULT partition. Si existiera una partition
     * dedicada para este run_id, iria ahi.
     */
    private long loadToStaging(SiproParametroTablaLz param,
                               List<Map<String, Object>> lzRows,
                               List<String> colsNegocio,
                               Long runId, int year, int month) {

        if (lzRows.isEmpty()) return 0L;

        String stgTable = param.getTablaStagingPg();

        // Columnas completas de la INSERT
        List<String> insertCols = new ArrayList<>();
        insertCols.add("period_year");
        insertCols.add("period_month");
        insertCols.add("ingestion_run_id");
        insertCols.addAll(colsNegocio);

        String colList    = String.join(", ", insertCols);
        String placemarks = insertCols.stream().map(c -> "?").collect(Collectors.joining(", "));
        String insertSql  = "INSERT INTO " + stgTable + " (" + colList + ") VALUES (" + placemarks + ")";

        // Batch insert
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, Object> row : lzRows) {
            Object[] args = new Object[insertCols.size()];
            args[0] = year;
            args[1] = month;
            args[2] = runId;
            for (int i = 0; i < colsNegocio.size(); i++) {
                args[3 + i] = row.get(colsNegocio.get(i));
            }
            batch.add(args);
        }

        int[] counts = pg.batchUpdate(insertSql, batch);
        long inserted = Arrays.stream(counts).asLongStream().sum();
        log.info("Staging: insertadas {} filas en {} para run_id={}", inserted, stgTable, runId);
        return inserted;
    }

    // ── Promocion STG → Final ────────────────────────────────────────────────

    /**
     * Promueve staging → tabla final dentro de una transaccion.
     *
     * Transaccional: si falla INSERT, el DELETE se revierte automaticamente.
     * Filtra por ingestion_run_id para no mezclar datos de otros runs.
     *
     * DELETE: elimina datos del periodo actual en Final (de runs anteriores).
     * INSERT: copia SOLO los datos del run actual desde STG a Final.
     *
     * Columnas derivadas dinamicamente desde la tabla staging/final en PostgreSQL
     * + columnas de sistema (period_year, period_month, ingestion_run_id, inserted_at).
     *
     * Tablas de datos LIST-particionadas por ingestion_run_id (DEFAULT partition).
     * Tabla de control RANGE-particionada por started_at.
     */
    @Transactional
    public long promoteToFinal(SiproParametroTablaLz param,
                               List<String> colsNegocio,
                               Long runId,
                               int year,
                               int month) {
        String stgTable   = param.getTablaStagingPg();
        String finalTable = param.getTablaDestinoPg();

        // Columnas derivadas dinamicamente de la tabla staging/final + columnas de sistema.
        List<String> allCols = new ArrayList<>();
        allCols.add("period_year");
        allCols.add("period_month");
        allCols.addAll(colsNegocio);
        allCols.add("ingestion_run_id");
        allCols.add("inserted_at");
        String columns = String.join(", ", allCols);

        // Se borra primero y se inserta después dentro de la misma transacción para que el
        // periodo quede siempre en un estado consistente: viejo completo o nuevo completo.
        int prevDeleted = pg.update(
            "DELETE FROM " + finalTable + " WHERE period_year = ? AND period_month = ?",
            year, month);
        log.info("Final: borradas {} filas previas del periodo {}/{}", prevDeleted, year, month);

        // INSERT desde staging SOLO para ESTE run_id, con columnas explicitas
        String sql = "INSERT INTO " + finalTable + " (" + columns + ") "
            + "SELECT " + columns + " FROM " + stgTable
            + " WHERE ingestion_run_id = ? AND period_year = ? AND period_month = ?";
        int inserted = pg.update(sql, runId, year, month);
        log.info("Final: insertadas {} filas en {} run_id={} periodo {}/{}",
            inserted, finalTable, runId, year, month);
        return (long) inserted;
    }

    private record PgTableRef(String schema, String table) {
        private static PgTableRef parse(String qualifiedName) {
            if (qualifiedName == null || qualifiedName.isBlank()) {
                throw new IllegalArgumentException("El nombre de tabla PostgreSQL es obligatorio.");
            }
            String normalized = qualifiedName.trim().toLowerCase();
            int dot = normalized.indexOf('.');
            if (dot < 0) {
                return new PgTableRef("public", normalized);
            }
            return new PgTableRef(normalized.substring(0, dot), normalized.substring(dot + 1));
        }
    }
}
