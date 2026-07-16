package com.bancolombia.sipro.validations.application.dto;

import com.bancolombia.sipro.validations.model.ValidationResult;

/**
 * Estado actual de una validación asíncrona, incluyendo progreso y resultado final.
 */
public class ValidationJobStatusResponse {

    private String jobId;
    private String status;
    private String phase;
    private String message;
    private Integer progressPercent;
    private Long processedRows;
    private Long totalRows;
    private Long updatedAtEpochMs;
    private boolean terminal;
    private ValidationResult result;

    public ValidationJobStatusResponse() {
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Long getProcessedRows() {
        return processedRows;
    }

    public void setProcessedRows(Long processedRows) {
        this.processedRows = processedRows;
    }

    public Long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Long totalRows) {
        this.totalRows = totalRows;
    }

    public Long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setUpdatedAtEpochMs(Long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    public ValidationResult getResult() {
        return result;
    }

    public void setResult(ValidationResult result) {
        this.result = result;
    }
}