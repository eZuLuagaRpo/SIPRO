package com.bancolombia.sipro.validations.infrastructure.lz;

import com.bancolombia.sipro.validations.infrastructure.config.LzProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Seed de la tabla MDM en LZ para perfiles DEV y QA.
 *
 * Al arrancar con perfil dev o qa:
 *   1. Elimina la tabla si ya existe (DROP TABLE IF EXISTS ... PURGE).
 *   2. La recrea con las columnas de fcr_mdm_datos_generales_clientes.
 *   3. Inserta 10 registros dummy con year/month/day de hoy (dinamico).
 *
 * La tabla que se usa viene de la config activa (LzProperties.getFullTableName("mdm")):
 *   DEV/QA : default.sipro_fcr_mdm_datos_generales_clientes
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

        LocalDate hoy = LocalDate.now();
        int y = hoy.getYear();
        int m = hoy.getMonthValue();
        int d = hoy.getDayOfMonth();

        log.info("[{}] Preparando tabla {} con datos dummy (year={}, month={}, day={})...",
            perfil, table, y, m, d);

        try {
            // 1. DROP TABLE IF EXISTS … PURGE
            log.info("[{}] Paso 1/3 — DROP TABLE IF EXISTS {} PURGE", perfil, table);
            lzJdbcService.execute("DROP TABLE IF EXISTS " + table + " PURGE");

            // 2. CREATE TABLE IF NOT EXISTS
            log.info("[{}] Paso 2/3 — CREATE TABLE {}", perfil, table);
            lzJdbcService.execute(buildCreateTableSql(table));

            // 3. INSERT 10 filas dummy con fechas de hoy
            log.info("[{}] Paso 3/3 — INSERT 10 filas en {}", perfil, table);
            lzJdbcService.execute(buildInsertSql(table, y, m, d));

            log.info("[{}] ✓ {} lista con 10 filas — conectividad LZ verificada OK", perfil, table);

        } catch (Exception e) {
            log.warn("[{}] No se pudo preparar {}: {}", perfil, table, e.getMessage());
            log.warn("[{}] Esperado si no hay VPN/red hacia LZ. El backend continua normalmente.", perfil);
        }
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

    /**
     * Construye el INSERT con 10 filas dummy.
     * year, month, day siempre son la fecha de HOY para mantener el filtro
     * WHERE year = YEAR(NOW()) AND month = MONTH(NOW()) AND day = DAY(NOW())
     * funcional en queries de prueba.
     */
    private String buildInsertSql(String table, int y, int m, int d) {
        // Un INSERT multi-fila VALUES es compatible con Impala 2.8+
        return String.format("""
            INSERT INTO %s VALUES
            (249912771688,'FS001','CEDULA DE CIUDADANIA',181793695824,181793695824,'PN','PERSONA NATURAL','8eba17ced58d7b1d31dc429a5f97d6d2','17c559','acdd532169','e658a','5ccea82c','ESTADO_02',%d,%d,%d,0,'2023-03-24'),
            (918670102069,'FS001','CEDULA DE CIUDADANIA',292710446464,292710446464,'PN','PERSONA NATURAL','98875d04ba58e03665b15b4f','890b7f6','6','b3e701ca','84ac7c2','ESTADO_02',%d,%d,%d,0,'2024-05-21'),
            (913927075089,'FS001','CEDULA DE CIUDADANIA',419203509826,419203509826,'PN','PERSONA NATURAL','32d7d7be6609159569311b3a9','39b8d7d','a1a7799','84debd284','6','ESTADO_02',%d,%d,%d,0,'2021-01-30'),
            (126348143341,'FS001','CEDULA DE CIUDADANIA',546791889439,546791889439,'PN','PERSONA NATURAL','31e1dd86f6d707d7fab14f233','f27239','27340','e658a','028c16','ESTADO_02',%d,%d,%d,0,'2023-04-16'),
            (223502983299,'FS001','CEDULA DE CIUDADANIA',807074011514,807074011514,'PN','PERSONA NATURAL','72822f066630d1bb59a39ad36bb655','de68','8a0fb8bd','7739a9f','77e9a7be','ESTADO_01',%d,%d,%d,0,'2024-05-07'),
            (592592824015,'FS001','CEDULA DE CIUDADANIA',527022437389,527022437389,'PN','PERSONA NATURAL','216d0bd6d1ad3c46bacd23677273b','ba344390','70f25b','7ca73','858b6ee','ESTADO_02',%d,%d,%d,0,'2024-11-05'),
            (519209296411,'FS004','TARJETA DE IDENTIDAD',478537226630,478537226630,'PN','PERSONA NATURAL','f551adc972f664beb46d8','b39e338a','6','663618','2a1a6','ESTADO_01',%d,%d,%d,0,'2026-03-06'),
            (136335245822,'FS001','CEDULA DE CIUDADANIA',318269707680,318269707680,'PN','PERSONA NATURAL','faf12cee62b043122793b3fa00119c5','98c0de','56300f0','1a6cf','dd414e593d','ESTADO_02',%d,%d,%d,0,'2020-05-03'),
            (610975974506,'FS001','CEDULA DE CIUDADANIA',567694302605,567694302605,'PN','PERSONA NATURAL','ec948b9823e0edb21f5df6b52207a','bafe70','f72c7b','273141f','1e9873f','ESTADO_02',%d,%d,%d,0,'2020-02-18'),
            (614923501560,'FS003','NIT',818662426696,818662426696,'PJ','PERSONA JURIDICA','50811a8e07edc1c7dbd211640b2',NULL,NULL,NULL,NULL,'ESTADO_02',%d,%d,%d,0,'2002-07-03')
            """,
            table,
            y, m, d,  // fila 1 — PN
            y, m, d,  // fila 2 — PN
            y, m, d,  // fila 3 — PN
            y, m, d,  // fila 4 — PN
            y, m, d,  // fila 5 — PN
            y, m, d,  // fila 6 — PN
            y, m, d,  // fila 7 — PN (TI)
            y, m, d,  // fila 8 — PN
            y, m, d,  // fila 9 — PN
            y, m, d   // fila 10 — PJ
        );
    }
}
