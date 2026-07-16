package com.bancolombia.sipro.validations.service;

import com.bancolombia.sipro.validations.application.dto.ValidationJobStartResponse;
import com.bancolombia.sipro.validations.application.dto.ValidationJobStatusResponse;
import com.bancolombia.sipro.validations.model.ValidationResult;

/**
 * Representa el estado en memoria de una validación asíncrona.
 */
public class ValidationJob {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private final String jobId;
    private final String fingerprint;
    private final long createdAtEpochMs;

    private String status;
    private String phase;
    private String message;
    private int progressPercent;
    private long processedRows;
    private long totalRows;
    private long updatedAtEpochMs;
    private ValidationResult result;

    public ValidationJob(String jobId, String fingerprint) {
        this.jobId = jobId;
        this.fingerprint = fingerprint;
        this.createdAtEpochMs = System.currentTimeMillis();
        this.updatedAtEpochMs = this.createdAtEpochMs;
        this.status = STATUS_QUEUED;
        this.phase = "RECEIVED";
        this.message = "Archivo recibido. Preparando validación...";
        this.progressPercent = 0;
    }

    public String getJobId() {
        return jobId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Actualiza la fase actual y el avance reportado por la validación.
     */
    public synchronized void updatePhase(String phase, String message, int progressPercent) {
        this.status = STATUS_RUNNING;
        this.phase = phase;
        this.message = message;
        this.progressPercent = Math.max(0, Math.min(progressPercent, 100));
        this.updatedAtEpochMs = System.currentTimeMillis();
    }

    public synchronized void updateRows(long processedRows) {
        this.processedRows = Math.max(processedRows, this.processedRows);
        if (this.totalRows > 0 && "VALIDATING".equals(this.phase)) {
            double completionRatio = Math.min(1d, (double) this.processedRows / (double) this.totalRows);
            int computedProgress = (int) Math.round(completionRatio * 90d);
            this.progressPercent = Math.max(this.progressPercent, Math.min(90, computedProgress));
        }
        this.updatedAtEpochMs = System.currentTimeMillis();
    }

    public synchronized void updateTotalRows(long totalRows) {
        this.totalRows = Math.max(totalRows, this.totalRows);
        this.updatedAtEpochMs = System.currentTimeMillis();
    }

    /**
     * Marca el job como completado y asocia el resultado final.
     */
    public synchronized void complete(ValidationResult result, String message) {
        this.status = STATUS_COMPLETED;
        this.phase = "DONE";
        this.message = message;
        this.progressPercent = 100;
        this.result = result;
        this.updatedAtEpochMs = System.currentTimeMillis();
    }

    /**
     * Marca el job como fallido y conserva el resultado de error a devolver.
     */
    public synchronized void fail(String message, ValidationResult result) {
        this.status = STATUS_FAILED;
        this.phase = "DONE";
        this.message = message;
        this.progressPercent = 100;
        this.result = result;
        this.updatedAtEpochMs = System.currentTimeMillis();
    }

    public synchronized boolean isTerminal() {
        return STATUS_COMPLETED.equals(status) || STATUS_FAILED.equals(status);
    }

    public synchronized ValidationJobStartResponse toStartResponse(boolean duplicateRequest) {
        return new ValidationJobStartResponse(
                jobId,
                status,
                phase,
                message,
                progressPercent,
                duplicateRequest
        );
    }

    /**
     * Convierte el estado interno del job al DTO consultado por el frontend.
     */
    public synchronized ValidationJobStatusResponse toStatusResponse() {
        ValidationJobStatusResponse response = new ValidationJobStatusResponse();
        response.setJobId(jobId);
        response.setStatus(status);
        response.setPhase(phase);
        response.setMessage(message);
        response.setProgressPercent(progressPercent);
        response.setProcessedRows(processedRows > 0 ? processedRows : null);
        response.setTotalRows(totalRows > 0 ? totalRows : null);
        response.setUpdatedAtEpochMs(updatedAtEpochMs);
        response.setTerminal(isTerminal());
        response.setResult(result);
        return response;
    }
}