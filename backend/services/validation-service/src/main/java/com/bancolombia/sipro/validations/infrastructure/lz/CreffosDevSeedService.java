package com.bancolombia.sipro.validations.infrastructure.lz;

import com.bancolombia.sipro.validations.infrastructure.config.LzProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Seed de la tabla CENIE (bvnc_visionry_cenie) en LZ para perfil DEV.
 *
 * Al arrancar con perfil dev:
 *   1. Elimina la tabla si ya existe (DROP TABLE IF EXISTS ... PURGE).
 *   2. La recrea con las columnas mínimas que usa CreffosParametricGenerator.
 *   3. Lee dev/cenie-seed.csv (una columna: ceac21) y construye el INSERT.
 *      Todos los registros cumplen los filtros del query de producción:
 *        cetr21 = 1, ceap21 = 'C', cest21 NOT IN ('01', '02')
 *      Valores dummy de negocio:
 *        cein21 = 'N1'  →  campo 44 (CLASEGTIA) devolverá 'N1' si cruza
 *        ceca21 = 'B'   →  campo 48 (CALIFICPUC) devolverá 'B' si cruza
 *        (los documentos que NO estén en esta tabla recibirán el default: campo44='N', campo48='A')
 *
 * La tabla se crea en el schema configurado en app.lz.schema (proceso en DEV).
 * NO corre en PDN — excluido por @Profile("dev").
 *
 * IMPORTANTE: también debes ejecutar este UPDATE en tu BD de dev (una sola vez):
 *   UPDATE sipro_parametros_columnas_creffsos
 *   SET parametros_json = jsonb_set(parametros_json, '{lookupSchema}', '"proceso"')
 *   WHERE nombre_columna IN ('CLASEGTIA', 'CALIFICPUC');
 */
@Component
@Profile("dev")
@Order(3)
public class CreffosDevSeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CreffosDevSeedService.class);

    private static final String SEED_CSV   = "dev/cenie-seed.csv";
    private static final int    BATCH_SIZE = 100;

    // Valores dummy que satisfacen los filtros del query de producción
    private static final String CEIN21_DUMMY = "N1";   // campo 44 CLASEGTIA
    private static final String CECA21_DUMMY = "B";    // campo 48 CALIFICPUC
    private static final int    CETR21_VALUE = 1;      // filtro: cetr21 = 1
    private static final String CEAP21_VALUE = "C";    // filtro: ceap21 = 'C'
    private static final String CEST21_VALUE = "03";   // filtro: NOT IN ('01','02')

    private final LzJdbcService lzJdbcService;
    private final LzProperties  lzProperties;

    public CreffosDevSeedService(LzJdbcService lzJdbcService, LzProperties lzProperties) {
        this.lzJdbcService = lzJdbcService;
        this.lzProperties  = lzProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!lzJdbcService.isEnabled()) {
            log.warn("[DEV-CENIE] lz.host no configurado — omitiendo seed de CENIE");
            return;
        }

        String table = lzProperties.getSchema() + ".bvnc_visionry_cenie";

        List<String> ceac21List = leerCsv();
        if (ceac21List.isEmpty()) {
            log.warn("[DEV-CENIE] {} sin datos — omitiendo seed de {}", SEED_CSV, table);
            return;
        }

        log.info("[DEV-CENIE] Preparando tabla {} con {} documentos del CSV...", table, ceac21List.size());

        try {
            log.info("[DEV-CENIE] Paso 1/3 — DROP TABLE IF EXISTS {} PURGE", table);
            lzJdbcService.execute("DROP TABLE IF EXISTS " + table + " PURGE");

            log.info("[DEV-CENIE] Paso 2/3 — CREATE TABLE {}", table);
            lzJdbcService.execute(buildCreateTableSql(table));

            List<String> inserts = buildInsertSqls(table, ceac21List);
            log.info("[DEV-CENIE] Paso 3/3 — INSERT {} filas en {} lotes", ceac21List.size(), inserts.size());
            for (String sql : inserts) {
                lzJdbcService.execute(sql);
            }

            log.info("[DEV-CENIE] ✓ {} lista con {} filas (campo44=cein21='{}', campo48=ceca21='{}')",
                table, ceac21List.size(), CEIN21_DUMMY, CECA21_DUMMY);

        } catch (Exception e) {
            log.warn("[DEV-CENIE] No se pudo preparar {}: {}", table, e.getMessage());
            log.warn("[DEV-CENIE] Esperado si no hay VPN/red hacia LZ. El backend continua normalmente.");
        }
    }

    // ── CSV Reader ────────────────────────────────────────────────────────────

    private List<String> leerCsv() {
        List<String> result = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource(SEED_CSV);
            if (!resource.exists()) {
                log.warn("[DEV-CENIE] No se encontró el CSV en classpath: {}", SEED_CSV);
                return result;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean cabecera = true;
                while ((line = reader.readLine()) != null) {
                    line = line.trim().replace("\"", "");
                    if (line.isEmpty()) continue;
                    if (cabecera) { cabecera = false; continue; }
                    if (!line.isEmpty()) result.add(line);
                }
            }
        } catch (Exception e) {
            log.warn("[DEV-CENIE] Error leyendo {}: {}", SEED_CSV, e.getMessage());
        }
        log.info("[DEV-CENIE] CSV seed cargado: {} documentos", result.size());
        return result;
    }

    // ── DDL ──────────────────────────────────────────────────────────────────

    private String buildCreateTableSql(String table) {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                ceac21   STRING,
                cein21   STRING,
                ceca21   STRING,
                cetr21   INT,
                ceap21   STRING,
                cest21   STRING,
                period_year        INT,
                period_month       INT,
                ingestion_run_id   BIGINT
            ) STORED AS PARQUET
            """.formatted(table);
        }

    // ── DML ──────────────────────────────────────────────────────────────────

    private List<String> buildInsertSqls(String table, List<String> ceac21List) {
        List<String> sqls = new ArrayList<>();
        for (int i = 0; i < ceac21List.size(); i += BATCH_SIZE) {
            List<String> lote = ceac21List.subList(i, Math.min(i + BATCH_SIZE, ceac21List.size()));
            sqls.add(buildInsertBatch(table, lote));
        }
        return sqls;
    }

    private String buildInsertBatch(String table, List<String> lote) {
        int year  = java.time.LocalDate.now().getYear();
        int month = java.time.LocalDate.now().getMonthValue();

        StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" VALUES\n");
        for (int i = 0; i < lote.size(); i++) {
            String ceac21 = lote.get(i);
            sb.append(String.format(
                "('%s','%s','%s',%d,'%s','%s',%d,%d,1)",
                ceac21, CEIN21_DUMMY, CECA21_DUMMY,
                CETR21_VALUE, CEAP21_VALUE, CEST21_VALUE,
                year, month
            ));
            if (i < lote.size() - 1) sb.append(",\n");
        }
        return sb.toString();
    }
}