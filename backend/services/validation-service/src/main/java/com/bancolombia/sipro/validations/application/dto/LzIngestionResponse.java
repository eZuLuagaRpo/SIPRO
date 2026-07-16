package com.bancolombia.sipro.validations.application.dto;

/**
 * Resultado resumido de una ejecución de ingesta desde LZ hacia PostgreSQL.
 */
public class LzIngestionResponse {

    private Long   runId;
    private String status;
    private String message;
    private Long   lzRowCount;
    private Long   pgStgRowCount;
    private Long   pgFinalRowCount;
    private long   durationMs;

    // ── Factory ────────────────────────────────────────────────────────────

    /**
     * Crea una respuesta para una ejecución que se saltó por una regla de control.
     */
    public static LzIngestionResponse skipped(String reason) {
        LzIngestionResponse r = new LzIngestionResponse();
        r.status  = "SKIPPED";
        r.message = reason;
        return r;
    }

    /**
     * Crea una respuesta de ejecución exitosa con los conteos principales.
     */
    public static LzIngestionResponse success(Long runId, long lzRows, long stgRows,
                                               long finalRows, long durationMs) {
        LzIngestionResponse r = new LzIngestionResponse();
        r.runId         = runId;
        r.status        = "SUCCESS";
        r.lzRowCount    = lzRows;
        r.pgStgRowCount = stgRows;
        r.pgFinalRowCount = finalRows;
        r.durationMs    = durationMs;
        return r;
    }

    /**
     * Crea una respuesta de error con el mensaje principal del fallo.
     */
    public static LzIngestionResponse failed(Long runId, String message) {
        LzIngestionResponse r = new LzIngestionResponse();
        r.runId   = runId;
        r.status  = "FAILED";
        r.message = message;
        return r;
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public Long getRunId() { return runId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Long getLzRowCount() { return lzRowCount; }
    public Long getPgStgRowCount() { return pgStgRowCount; }
    public Long getPgFinalRowCount() { return pgFinalRowCount; }
    public long getDurationMs() { return durationMs; }
}
