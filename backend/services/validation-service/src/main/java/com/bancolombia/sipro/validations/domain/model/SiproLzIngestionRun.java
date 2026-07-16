package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Registro de cada ejecucion de ingesta LZ.
 * status: STARTED | SUCCESS | FAILED | INCOMPLETE
 */
@Entity
@Table(name = "sipro_lz_ingestion_run")
public class SiproLzIngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Long runId;

    @Column(name = "id_parametro", nullable = false)
    private Integer idParametro;

    @Column(name = "id_tabla", nullable = false)
    private Integer idTabla;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "parametro_version", nullable = false)
    private Integer parametroVersion;

    @Column(name = "parametro_estado", nullable = false)
    private String parametroEstado;

    @Column(name = "query_hash", nullable = false)
    private String queryHash;

    @Column(name = "lz_row_count")
    private Long lzRowCount;

    @Column(name = "pg_stg_row_count")
    private Long pgStgRowCount;

    @Column(name = "pg_final_row_count")
    private Long pgFinalRowCount;

    /** STARTED | SUCCESS | FAILED | INCOMPLETE */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "overwrite_allowed", nullable = false)
    private Boolean overwriteAllowed;

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = Instant.now();
        if (status == null) status = "STARTED";
        if (overwriteAllowed == null) overwriteAllowed = true;
    }

    // ── Factory ───────────────────────────────────────────────────────────

    public static SiproLzIngestionRun started(SiproParametroTablaLz param,
                                               int year, int month, String queryHash) {
        SiproLzIngestionRun run = new SiproLzIngestionRun();
        run.idParametro          = param.getIdParametro();
        run.idTabla              = param.getIdTabla();
        run.periodYear           = year;
        run.periodMonth          = month;
        run.parametroVersion     = param.getVersion();
        run.parametroEstado      = param.getEstado();
        run.queryHash            = queryHash;
        run.status               = "STARTED";
        run.overwriteAllowed     = true;
        run.startedAt            = Instant.now();

        if (run.idTabla == null) {
            throw new IllegalStateException(
                "El parametro id=" + param.getIdParametro() + " no tiene id_tabla asociado en catalogo.");
        }
        return run;
    }

    public void markSuccess(long lzRows, long stgRows, long finalRows) {
        this.status          = "SUCCESS";
        this.lzRowCount      = lzRows;
        this.pgStgRowCount   = stgRows;
        this.pgFinalRowCount = finalRows;
        this.endedAt         = Instant.now();
    }

    public void markFailed(String message) {
        this.status   = "FAILED";
        this.message  = message;
        this.endedAt  = Instant.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public Long getRunId() { return runId; }
    public Integer getIdParametro() { return idParametro; }
    public Integer getIdTabla() { return idTabla; }
    public Integer getPeriodYear() { return periodYear; }
    public Integer getPeriodMonth() { return periodMonth; }
    public Integer getParametroVersion() { return parametroVersion; }
    public String getParametroEstado() { return parametroEstado; }
    public String getQueryHash() { return queryHash; }
    public Long getLzRowCount() { return lzRowCount; }
    public void setLzRowCount(Long lzRowCount) { this.lzRowCount = lzRowCount; }
    public Long getPgStgRowCount() { return pgStgRowCount; }
    public void setPgStgRowCount(Long pgStgRowCount) { this.pgStgRowCount = pgStgRowCount; }
    public Long getPgFinalRowCount() { return pgFinalRowCount; }
    public void setPgFinalRowCount(Long pgFinalRowCount) { this.pgFinalRowCount = pgFinalRowCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Boolean getOverwriteAllowed() { return overwriteAllowed; }
}
