package com.bancolombia.sipro.validations.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conserva los jobs activos y evita validaciones duplicadas sobre el mismo archivo.
 */
@Component
public class ValidationJobStore {

    private final Map<String, ValidationJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, String> activeJobsByFingerprint = new ConcurrentHashMap<>();

    public void save(ValidationJob job) {
        jobs.put(job.getJobId(), job);
        activeJobsByFingerprint.put(job.getFingerprint(), job.getJobId());
    }

    public Optional<ValidationJob> get(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * Busca un job activo por huella para reutilizarlo si la misma solicitud se repite.
     */
    public Optional<ValidationJob> findActiveByFingerprint(String fingerprint) {
        String jobId = activeJobsByFingerprint.get(fingerprint);
        if (jobId == null) {
            return Optional.empty();
        }

        ValidationJob job = jobs.get(jobId);
        if (job == null || job.isTerminal()) {
            activeJobsByFingerprint.remove(fingerprint, jobId);
            return Optional.empty();
        }

        return Optional.of(job);
    }

    /**
     * Libera la huella del job cuando deja de ser elegible para deduplicación.
     */
    public void release(ValidationJob job) {
        activeJobsByFingerprint.remove(job.getFingerprint(), job.getJobId());
    }
}