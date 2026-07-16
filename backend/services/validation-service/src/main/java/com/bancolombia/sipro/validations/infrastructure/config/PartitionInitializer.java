package com.bancolombia.sipro.validations.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Se ejecuta al arrancar el backend ANTES de cualquier ingesta.
 *
 * 1. Asegura que sipro_lz_catalogo_tablas tenga las columnas
 *    tabla_destino_pg y tabla_staging_pg (ALTER TABLE ADD COLUMN IF NOT EXISTS).
 *    Auto-popula valores derivados por convencion para filas donde son NULL.
 *
 * 2. Lee TODAS las tablas activas del catalogo y para cada una verifica
 *    si la tabla destino/staging son particionadas → crea DEFAULT partition si falta.
 *
 * 3. Asegura la secuencia de run_id en sipro_lz_ingestion_run.
 *
 * Es completamente idempotente y dinamico: NUNCA tiene nombres de tablas
 * hardcodeados — todo se lee de sipro_lz_catalogo_tablas.
 */
@Component
@Order(1) // Ejecutar antes que LzStartupValidator y cualquier ingesta
public class PartitionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PartitionInitializer.class);

    private final JdbcTemplate pg;

    public PartitionInitializer(JdbcTemplate pg) {
        this.pg = pg;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Partition Initializer ===");

        try {
            // 0. Asegurar tablas y columnas auxiliares
            ensureParametrosUnicoTable();
            ensureUsuariosLocalesTables();
            ensurePlanillasAprobacionColumns();
            ensureArchivoControlColumn();
            ensureConsolidadoRegistrosColumnAlignment();
            ensureConsolidacionEstadoConstraint();

            // 1. Asegurar columnas tabla_destino_pg/tabla_staging_pg en catalogo
            ensureCatalogoColumns();

            // 2. Secuencia de run_id para sipro_lz_ingestion_run
            ensureDefaultPartition("sipro_lz_ingestion_run", "sipro_lz_ingestion_run_default");
            ensureRunIdSequence();

            // 3. Para cada tabla activa del catalogo, asegurar DEFAULT partitions
            List<Map<String, Object>> tablas = pg.queryForList(
                "SELECT tabla_destino_pg, tabla_staging_pg FROM sipro_lz_catalogo_tablas WHERE activo = true");

            for (Map<String, Object> row : tablas) {
                String destino = (String) row.get("tabla_destino_pg");
                String staging = (String) row.get("tabla_staging_pg");

                if (destino != null && !destino.isBlank()) {
                    if (tableExistsInPg(destino)) {
                        ensureDefaultPartition(destino, destino + "_default");
                    } else {
                        log.error("[ERROR] Tabla destino '{}' configurada en sipro_lz_catalogo_tablas "
                            + "NO existe en PostgreSQL. La ingesta fallara para esta tabla. "
                            + "Corrige sipro_lz_catalogo_tablas.tabla_destino_pg manualmente.", destino);
                    }
                }
                if (staging != null && !staging.isBlank()) {
                    if (tableExistsInPg(staging)) {
                        ensureDefaultPartition(staging, staging + "_default");
                    } else {
                        log.error("[ERROR] Tabla staging '{}' configurada en sipro_lz_catalogo_tablas "
                            + "NO existe en PostgreSQL. La ingesta fallara para esta tabla. "
                            + "Corrige sipro_lz_catalogo_tablas.tabla_staging_pg manualmente.", staging);
                    }
                }
            }

            log.info("=== Partition Initializer completado ===");

        } catch (Exception e) {
            log.error("=== Partition Initializer FALLO: {} ===", e.getMessage(), e);
            log.error("No se pudo completar la inicialización. Causa probable: el usuario "
                + "JDBC no es dueño de alguna tabla. Ejecute los ALTER manualmente como superusuario.");
        }
    }

    // ── Catalogo: asegurar columnas y datos ────────────────────────────────

    /**
     * Crea la tabla sipro_parametros_unico si no existe.
     */
    private void ensureParametrosUnicoTable() {
        pg.execute("CREATE TABLE IF NOT EXISTS sipro_parametros_unico ("
            + "id SERIAL PRIMARY KEY, "
            + "clave VARCHAR(100) NOT NULL UNIQUE, "
            + "valor VARCHAR(255) NOT NULL, "
            + "tipo VARCHAR(50) NOT NULL, "
            + "descripcion TEXT, "
            + "creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "modificado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        log.info("[OK] sipro_parametros_unico — tabla asegurada");
    }

    /**
     * Asegura que sipro_detalle_carga_planillas tenga las columnas de aprobación
     * que fueron agregadas por la migración 007 (id_usuario_aprobador, usuario_aprobador, fecha_aprobacion).
     */
    private void ensurePlanillasAprobacionColumns() {
        pg.execute("ALTER TABLE sipro_detalle_carga_planillas "
            + "ADD COLUMN IF NOT EXISTS id_usuario_aprobador BIGINT");
        pg.execute("ALTER TABLE sipro_detalle_carga_planillas "
            + "ADD COLUMN IF NOT EXISTS usuario_aprobador VARCHAR(255)");
        pg.execute("ALTER TABLE sipro_detalle_carga_planillas "
            + "ADD COLUMN IF NOT EXISTS fecha_aprobacion TIMESTAMP WITH TIME ZONE");

        // La constraint original (fecha_corte, nombre_archivo_fuente) impide que segmento 1
        // y segmento 2 del mismo producto coexistan activos para el mismo periodo (mismo
        // nombre de archivo base). Se reemplaza por (fecha_corte, id_producto) que es la
        // verdadera clave funcional: una planilla activa por producto por periodo.
        pg.execute("DROP INDEX IF EXISTS uq_planilla_activa_fecha_archivo");
        pg.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_planilla_activa_por_producto "
            + "ON sipro_detalle_carga_planillas (fecha_corte_informacion, id_producto) "
            + "WHERE activo = true");

        log.info("[OK] sipro_detalle_carga_planillas — columnas de aprobación aseguradas");
    }

    /**
     * Asegura que sipro_detalle_consolidado_registros tenga tipos amplios para los campos
     * numéricos que vienen del Excel y pueden exceder el rango de integer.
     *
     * Liquibase ya tiene la migración 011 para esto, pero en ambientes DEV heredados puede
     * quedar desalineado; este guardián lo corrige de forma idempotente al arranque.
     */
    private void ensureConsolidadoRegistrosColumnAlignment() {
        if (!tableExistsInPg("sipro_detalle_consolidado_registros")) {
            log.info("[SKIP] sipro_detalle_consolidado_registros no existe todavía — alineación omitida");
            return;
        }

        Integer columnasDesalineadas = pg.queryForObject(
            "SELECT COUNT(*)::int "
                + "FROM information_schema.columns "
                + "WHERE table_schema = 'public' "
                + "AND table_name = 'sipro_detalle_consolidado_registros' "
                + "AND ((column_name IN ('nit', 'oficina', 'documento', 'ctapuc') AND data_type <> 'bigint') "
                + "OR (column_name IN ('vlriniobl', 'saldo', 'sdootrctas', 'intereses', 'sdovencido', 'intctasord') "
                + "AND (data_type <> 'numeric' "
                + "OR numeric_precision IS DISTINCT FROM 18 "
                + "OR numeric_scale IS DISTINCT FROM 2)))",
            Integer.class);

        if (columnasDesalineadas == null || columnasDesalineadas == 0) {
            log.info("[OK] sipro_detalle_consolidado_registros — tipos numéricos ya alineados");
            return;
        }

        pg.execute("ALTER TABLE public.sipro_detalle_consolidado_registros "
            + "ALTER COLUMN nit TYPE BIGINT, "
            + "ALTER COLUMN oficina TYPE BIGINT, "
            + "ALTER COLUMN documento TYPE BIGINT, "
            + "ALTER COLUMN ctapuc TYPE BIGINT, "
            + "ALTER COLUMN vlriniobl TYPE NUMERIC(18,2), "
            + "ALTER COLUMN saldo TYPE NUMERIC(18,2), "
            + "ALTER COLUMN sdootrctas TYPE NUMERIC(18,2), "
            + "ALTER COLUMN intereses TYPE NUMERIC(18,2), "
            + "ALTER COLUMN sdovencido TYPE NUMERIC(18,2), "
            + "ALTER COLUMN intctasord TYPE NUMERIC(18,2)");

        log.info("[FIX] sipro_detalle_consolidado_registros — alineados BIGINT/NUMERIC para consolidación");
    }

    /**
     * Asegura que el CHECK constraint chk_consol_estado incluya 'COMPLETADO_CON_ADVERTENCIAS'.
     *
     * El estado COMPLETADO_CON_ADVERTENCIAS se emite cuando la consolidación fue exitosa
     * pero el envío de correo falló. La constraint original puede no incluirlo si la BD
     * fue creada antes de que se agregara ese estado al flujo.
     *
     * Es idempotente: solo actúa si el constraint existe sin ese valor, o si no existe.
     */
    private void ensureConsolidacionEstadoConstraint() {
        if (!tableExistsInPg("sipro_detalle_consolidaciones_planillas")) {
            log.info("[SKIP] sipro_detalle_consolidaciones_planillas no existe todavía — constraint omitido");
            return;
        }

        try {
            pg.execute("""
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_constraint
                        WHERE conrelid = 'public.sipro_detalle_consolidaciones_planillas'::regclass
                          AND conname = 'chk_consol_estado'
                          AND pg_get_constraintdef(oid) LIKE '%COMPLETADO_CON_ADVERTENCIAS%'
                    ) THEN
                        ALTER TABLE public.sipro_detalle_consolidaciones_planillas
                            DROP CONSTRAINT IF EXISTS chk_consol_estado;
                        ALTER TABLE public.sipro_detalle_consolidaciones_planillas
                            ADD CONSTRAINT chk_consol_estado
                            CHECK (estado_consolidacion IN (
                                'INICIADO', 'EN_PROCESO', 'COMPLETADO', 'ERROR',
                                'COMPLETADO_CON_ADVERTENCIAS'
                            ));
                        RAISE NOTICE 'chk_consol_estado actualizado con COMPLETADO_CON_ADVERTENCIAS';
                    END IF;
                END $$;
                """);
            log.info("[OK] sipro_detalle_consolidaciones_planillas — chk_consol_estado incluye COMPLETADO_CON_ADVERTENCIAS");
        } catch (Exception e) {
            log.warn("[WARN] No se pudo actualizar chk_consol_estado: {}. "
                + "Ejecute el ALTER manualmente como superusuario.", e.getMessage());
        }
    }

    /**
     * Asegura las tablas maestras de usuario para que soporten login solo-Entra.
     */
    private void ensureUsuariosLocalesTables() {
        pg.execute("CREATE SEQUENCE IF NOT EXISTS usuario_login_id_usuario_seq");

        pg.execute("CREATE TABLE IF NOT EXISTS usuario_login ("
            + "id_usuario BIGINT PRIMARY KEY DEFAULT nextval('usuario_login_id_usuario_seq'), "
            + "usuario TEXT NOT NULL UNIQUE, "
            + "clave TEXT)");

        pg.execute("CREATE TABLE IF NOT EXISTS usuario_persona ("
            + "id_usuario BIGINT PRIMARY KEY REFERENCES usuario_login(id_usuario), "
            + "nombres TEXT NOT NULL, "
            + "apellidos TEXT NOT NULL, "
            + "correo TEXT NOT NULL UNIQUE, "
            + "usuario TEXT NOT NULL UNIQUE)");

        pg.execute("CREATE TABLE IF NOT EXISTS usuario_area ("
            + "id_usuario BIGINT PRIMARY KEY REFERENCES usuario_login(id_usuario), "
            + "area_nombre TEXT NOT NULL, "
            + "rol_lider TEXT NOT NULL, "
            + "id_usuario_lider BIGINT NOT NULL REFERENCES usuario_persona(id_usuario))");

        pg.execute("ALTER TABLE usuario_login ALTER COLUMN id_usuario SET DEFAULT nextval('usuario_login_id_usuario_seq')");
        pg.execute("ALTER TABLE usuario_login ADD COLUMN IF NOT EXISTS clave TEXT");
        pg.execute("ALTER TABLE usuario_login ALTER COLUMN clave DROP NOT NULL");
        pg.execute("ALTER TABLE usuario_persona ADD COLUMN IF NOT EXISTS usuario TEXT");
        pg.execute("ALTER TABLE usuario_area ADD COLUMN IF NOT EXISTS rol_lider TEXT");
        pg.execute("ALTER TABLE usuario_area ADD COLUMN IF NOT EXISTS id_usuario_lider BIGINT");

        pg.execute("DO $$ "
            + "BEGIN "
            + "    IF EXISTS (SELECT 1 FROM information_schema.columns "
            + "               WHERE table_name = 'usuario_area' AND column_name = 'cargo_jefe') THEN "
            + "        UPDATE usuario_area "
            + "        SET rol_lider = COALESCE(NULLIF(TRIM(rol_lider), ''), cargo_jefe) "
            + "        WHERE rol_lider IS NULL OR TRIM(rol_lider) = ''; "
            + "    END IF; "
            + "    IF EXISTS (SELECT 1 FROM information_schema.columns "
            + "               WHERE table_name = 'usuario_area' AND column_name = 'id_jefe') THEN "
            + "        UPDATE usuario_area "
            + "        SET id_usuario_lider = COALESCE(id_usuario_lider, id_jefe) "
            + "        WHERE id_usuario_lider IS NULL; "
            + "    END IF; "
            + "END $$");

        pg.execute("DO $$ "
            + "BEGIN "
            + "    IF NOT EXISTS (SELECT 1 FROM pg_constraint "
            + "                   WHERE conname = 'fk_usuario_area_id_usuario_lider') THEN "
            + "        ALTER TABLE usuario_area "
            + "        ADD CONSTRAINT fk_usuario_area_id_usuario_lider "
            + "        FOREIGN KEY (id_usuario_lider) REFERENCES usuario_persona(id_usuario); "
            + "    END IF; "
            + "END $$");

        pg.update(
            "UPDATE usuario_persona p "
                + "SET usuario = l.usuario "
                + "FROM usuario_login l "
                + "WHERE p.id_usuario = l.id_usuario "
                + "AND (p.usuario IS NULL OR TRIM(p.usuario) = '')");

        pg.execute("SELECT setval('usuario_login_id_usuario_seq', "
            + "GREATEST(COALESCE((SELECT MAX(id_usuario) FROM usuario_login), 0) + 1, 1), false)");

        log.info("[OK] Tablas locales de usuario aseguradas para autenticación Entra");
    }

    /**
     * Verifica si una tabla (normal o particionada) existe en PostgreSQL.
     */
    private boolean tableExistsInPg(String tableName) {
        if (tableName == null || tableName.isBlank()) return false;
        Boolean exists = pg.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM pg_class WHERE relname = ? AND relkind IN ('p','r'))",
            Boolean.class, tableName.trim().toLowerCase());
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Agrega columnas tabla_destino_pg y tabla_staging_pg al catalogo si no existen.
     * Luego auto-popula valores por convencion donde estan NULL:
     *   tabla_destino_pg = sipro_lz_ + parte_despues_del_punto
     *   tabla_staging_pg = sipro_lz_ + parte_despues_del_punto + _stg
     */
    private void ensureCatalogoColumns() {
        pg.execute("ALTER TABLE sipro_lz_catalogo_tablas "
            + "ADD COLUMN IF NOT EXISTS tabla_destino_pg TEXT");
        pg.execute("ALTER TABLE sipro_lz_catalogo_tablas "
            + "ADD COLUMN IF NOT EXISTS tabla_staging_pg TEXT");

        // Auto-popular por convencion donde son NULL
        int updated = pg.update(
            "UPDATE sipro_lz_catalogo_tablas "
            + "SET tabla_destino_pg = 'sipro_lz_' || "
            + "    CASE WHEN POSITION('.' IN tabla_origen) > 0 "
            + "         THEN SUBSTRING(tabla_origen FROM POSITION('.' IN tabla_origen) + 1) "
            + "         ELSE tabla_origen END, "
            + "    tabla_staging_pg = 'sipro_lz_' || "
            + "    CASE WHEN POSITION('.' IN tabla_origen) > 0 "
            + "         THEN SUBSTRING(tabla_origen FROM POSITION('.' IN tabla_origen) + 1) "
            + "         ELSE tabla_origen END || '_stg' "
            + "WHERE tabla_destino_pg IS NULL OR tabla_staging_pg IS NULL");

        if (updated > 0) {
            log.info("[CATALOGO] Auto-populadas tabla_destino_pg/tabla_staging_pg para {} entradas", updated);
        }
        log.info("[OK] sipro_lz_catalogo_tablas — columnas destino/staging aseguradas");
    }

    // ── Particiones ────────────────────────────────────────────────────────

    /**
     * Crea DEFAULT partition si la tabla existe como particionada y no tiene ya una.
     * Si la tabla no esta particionada o no existe, no hace nada.
     */
    private void ensureDefaultPartition(String parentTable, String defaultPartitionName) {
        // Verificar si la tabla existe
        Boolean exists = pg.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM pg_class WHERE relname = ? AND relkind IN ('p','r'))",
            Boolean.class, parentTable);
        if (Boolean.FALSE.equals(exists)) {
            log.info("[SKIP] Tabla '{}' no existe — nada que hacer", parentTable);
            return;
        }

        // Verificar si esta particionada
        Boolean isPartitioned = pg.queryForObject(
            "SELECT EXISTS ("
            + "  SELECT 1 FROM pg_partitioned_table "
            + "  WHERE partrelid = ?::regclass"
            + ")",
            Boolean.class, parentTable);

        if (Boolean.FALSE.equals(isPartitioned)) {
            log.info("[SKIP] {} no es tabla particionada — nada que hacer", parentTable);
            return;
        }

        // Verificar si ya tiene DEFAULT partition
        Boolean hasDefault = pg.queryForObject(
            "SELECT EXISTS ("
            + "  SELECT 1 FROM pg_class c "
            + "  JOIN pg_inherits i ON c.oid = i.inhrelid "
            + "  WHERE i.inhparent = ?::regclass "
            + "    AND c.relname = ?"
            + ")",
            Boolean.class, parentTable, defaultPartitionName);

        if (Boolean.TRUE.equals(hasDefault)) {
            log.info("[OK] {} — DEFAULT partition '{}' ya existe", parentTable, defaultPartitionName);
            return;
        }

        // Crear DEFAULT partition
        String sql = "CREATE TABLE " + defaultPartitionName
            + " PARTITION OF " + parentTable + " DEFAULT";
        pg.execute(sql);
        log.info("[CREATED] DEFAULT partition '{}' para tabla '{}'",
            defaultPartitionName, parentTable);
    }

    // ── Secuencia run_id ───────────────────────────────────────────────────

    /**
     * Asegura auto-increment en sipro_lz_ingestion_run.run_id.
     */
    private void ensureRunIdSequence() {
        String seqName = "sipro_lz_ingestion_run_run_id_seq";
        pg.execute("CREATE SEQUENCE IF NOT EXISTS " + seqName);
        pg.execute(
            "SELECT setval('" + seqName + "', "
            + "GREATEST(COALESCE((SELECT MAX(run_id) FROM sipro_lz_ingestion_run), 0) + 1, 1), false)");
        pg.execute(
            "ALTER TABLE sipro_lz_ingestion_run "
            + "ALTER COLUMN run_id SET DEFAULT nextval('" + seqName + "')");
        log.info("[OK] sipro_lz_ingestion_run.run_id — secuencia '{}' configurada", seqName);
    }

    /**
     * Asegura que sipro_detalle_carga_planillas tenga la columna ruta_archivo_control
     * para el segmento Full IFRS (id_segmento=2), que requiere siempre un archivo xlsx
     * más un archivo de control txt (CTRL-NOMBRE.txt).
     *
     * Es idempotente: ADD COLUMN IF NOT EXISTS garantiza que no falla si ya existe.
     */
    private void ensureArchivoControlColumn() {
        pg.execute("ALTER TABLE sipro_detalle_carga_planillas "
            + "ADD COLUMN IF NOT EXISTS ruta_archivo_control VARCHAR(500)");
        log.info("[OK] sipro_detalle_carga_planillas — columna ruta_archivo_control asegurada (Full IFRS)");
    }
}
