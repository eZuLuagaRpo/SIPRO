package com.bancolombia.sipro.validations.application.dto;

/**
 * Respuesta inicial de una validación asíncrona recién creada.
 */
public class ValidationJobStartResponse {

    private String jobId;
    private String status;
    private String phase;
    private String message;
    private Integer progressPercent;
    private boolean duplicateRequest;

    public ValidationJobStartResponse() {
    }

    public ValidationJobStartResponse(
            String jobId,
            String status,
            String phase,
            String message,
            Integer progressPercent,
            boolean duplicateRequest
    ) {
        this.jobId = jobId;
        this.status = status;
        this.phase = phase;
        this.message = message;
        this.progressPercent = progressPercent;
        this.duplicateRequest = duplicateRequest;
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

    public boolean isDuplicateRequest() {
        return duplicateRequest;
    }

    public void setDuplicateRequest(boolean duplicateRequest) {
        this.duplicateRequest = duplicateRequest;
    }
}