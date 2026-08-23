package com.bancolombia.sipro.validations.infrastructure.lz;

import com.bancolombia.sipro.validations.infrastructure.config.LzProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seed de la tabla MDM en LZ para perfiles DEV y QA.
 *
 * Al arrancar con perfil dev o qa:
 *   1. Elimina la tabla si ya existe (DROP TABLE IF EXISTS ... PURGE).
 *   2. La recrea con las columnas de fcr_mdm_datos_generales_clientes.
 *   3. Lee dev/mdm-clientes-seed.csv (numero_id, tipo_id) y construye el INSERT.
 *      Las columnas restantes se rellenan con valores dummy coherentes.
 *
 * La tabla que se usa viene de la config activa (LzProperties.getFullTableName("mdm")):
 *   DEV/QA : proceso.sipro_fcr_mdm_datos_generales_clientes
 *   (PDN no corre este seed — excluido por @Profile)
 *
 * Usa LzDevSeedService solo si hay red/VPN hacia LZ (10.8.85.237:21050).
 * Si no hay red, el error se loggea como WARNING y el backend sigue normalmente.
 *
 * NO corre en PDN — excluido por @Profile({"dev", "qa"}).
 */
@Component
@Profile({"dev", "qa"})
@Order(2)
public class LzDevSeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LzDevSeedService.class);

    private static final String SEED_CSV = "dev/mdm-clientes-seed.csv";

    private static final int BATCH_SIZE = 100;

    // tipo_id → [desc_tipo_documento, tipo_persona, desc_tipo_persona]
    private static final Map<String, String[]> TIPO_ID_META = Map.of(
        "FS001", new String[]{"CEDULA DE CIUDADANIA",   "PN", "PERSONA NATURAL"},
        "FS002", new String[]{"CEDULA DE EXTRANJERIA",  "PN", "PERSONA NATURAL"},
        "FS003", new String[]{"NIT",                    "PJ", "PERSONA JURIDICA"},
        "FS004", new String[]{"TARJETA DE IDENTIDAD",   "PN", "PERSONA NATURAL"},
        "FS005", new String[]{"PASAPORTE",              "PN", "PERSONA NATURAL"},
        "FS006", new String[]{"REGISTRO CIVIL",         "PN", "PERSONA NATURAL"}
    );

    private final LzJdbcService lzJdbcService;
    private final LzProperties  lzProperties;
    private final Environment   environment;

    public LzDevSeedService(LzJdbcService lzJdbcService, LzProperties lzProperties, Environment environment) {
        this.lzJdbcService = lzJdbcService;
        this.lzProperties  = lzProperties;
        this.environment   = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] profiles = environment.getActiveProfiles();
        String perfil = profiles.length > 0 ? profiles[0].toUpperCase() : "DEV";

        String table = lzProperties.getFullTableName("mdm");

        if (!lzJdbcService.isEnabled()) {
            log.warn("[{}] lz.host no configurado — omitiendo seed de {}", perfil, table);
            return;
        }

        List<String[]> registros = leerCsv();
        if (registros.isEmpty()) {
            log.warn("[{}] {} sin datos — omitiendo seed de {}", perfil, SEED_CSV, table);
            return;
        }

        LocalDate hoy = LocalDate.now();
        int y = hoy.getYear();
        int m = hoy.getMonthValue();
        int d = hoy.getDayOfMonth();

        log.info("[{}] Preparando tabla {} con {} registros del CSV (year={}, month={}, day={})...",
            perfil, table, registros.size(), y, m, d);

        try {
            log.info("[{}] Paso 1/3 — DROP TABLE IF EXISTS {} PURGE", perfil, table);
            lzJdbcService.execute("DROP TABLE IF EXISTS " + table + " PURGE");

            log.info("[{}] Paso 2/3 — CREATE TABLE {}", perfil, table);
            lzJdbcService.execute(buildCreateTableSql(table));

            List<String> inserts = buildInsertSqls(table, registros, y, m, d);
            log.info("[{}] Paso 3/3 — INSERT {} filas en {} lotes", perfil, registros.size(), inserts.size());
            for (String sql : inserts) {
                lzJdbcService.execute(sql);
            }

            log.info("[{}] ✓ {} lista con {} filas — conectividad LZ verificada OK",
                perfil, table, registros.size());

        } catch (Exception e) {
            log.warn("[{}] No se pudo preparar {}: {}", perfil, table, e.getMessage());
            log.warn("[{}] Esperado si no hay VPN/red hacia LZ. El backend continua normalmente.", perfil);
        }
    }

    // ── CSV Reader ────────────────────────────────────────────────────────────

    private List<String[]> leerCsv() {
        List<String[]> result = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource(SEED_CSV);
            if (!resource.exists()) {
                log.warn("No se encontró el CSV de seed en classpath: {}", SEED_CSV);
                return result;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                String separador = null;
                boolean cabecera = true;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (cabecera) {
                        separador = line.contains(";") ? ";" : ",";
                        cabecera = false;
                        continue;
                    }
                    String[] partes = line.split(separador, 2);
                    if (partes.length == 2) {
                        String numeroId = partes[0].trim().replace("\"", "");
                        String tipoId   = partes[1].trim().replace("\"", "");
                        if (!numeroId.isEmpty() && !tipoId.isEmpty()) {
                            result.add(new String[]{numeroId, tipoId});
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error leyendo {}: {}", SEED_CSV, e.getMessage());
        }
        log.info("CSV seed cargado: {} registros", result.size());
        return result;
    }

    // ── DDL ──────────────────────────────────────────────────────────────────

    private String buildCreateTableSql(String table) {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                llave_mdm              BIGINT,
                tipo_id                STRING,
                desc_tipo_documento    STRING,
                numero_id              BIGINT,
                numeroid_externo       BIGINT,
                tipo_persona           STRING,
                desc_tipo_persona      STRING,
                nombre_o_razon_social  STRING,
                primer_nombre          STRING,
                segundo_nombre         STRING,
                primer_apellido        STRING,
                segundo_apellido       STRING,
                estado_cliente         STRING,
                year                   INT,
                month                  INT,
                day                    INT,
                version                INT,
                f_ult_actualizacion    STRING
            ) STORED AS PARQUET
            """.formatted(table);
    }

    // ── DML ──────────────────────────────────────────────────────────────────

    private List<String> buildInsertSqls(String table, List<String[]> registros, int y, int m, int d) {
        List<String> sqls = new ArrayList<>();
        for (int i = 0; i < registros.size(); i += BATCH_SIZE) {
            List<String[]> lote = registros.subList(i, Math.min(i + BATCH_SIZE, registros.size()));
            sqls.add(buildInsertBatch(table, lote, i, y, m, d));
        }
        return sqls;
    }

    private String buildInsertBatch(String table, List<String[]> lote, int offset, int y, int m, int d) {
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" VALUES\n");
        for (int i = 0; i < lote.size(); i++) {
            String[] row     = lote.get(i);
            long llaveMdm    = offset + i + 1L;
            String numeroId  = row[0];
            String tipoId    = row[1];
            String[] meta    = TIPO_ID_META.getOrDefault(tipoId,
                                    new String[]{"DOCUMENTO", "PN", "PERSONA NATURAL"});
            String desc      = meta[0];
            String tipoPers  = meta[1];
            String descPers  = meta[2];
            boolean esPj     = "PJ".equals(tipoPers);

            sb.append(String.format(
                "(%d,'%s','%s',%s,%s,'%s','%s','DUMMY',%s,NULL,%s,NULL,'ESTADO_02',%d,%d,%d,0,'2024-01-01')",
                llaveMdm,
                tipoId, desc,
                numeroId, numeroId,
                tipoPers, descPers,
                esPj ? "NULL" : "'DUMMY'",
                esPj ? "NULL" : "'DUMMY'",
                y, m, d
            ));
            if (i < lote.size() - 1) sb.append(",\n");
        }
        return sb.toString();
    }
}