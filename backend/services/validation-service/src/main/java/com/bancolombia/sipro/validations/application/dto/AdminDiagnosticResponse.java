package com.bancolombia.sipro.validations.application.dto;

import java.time.Instant;
import java.util.List;

public record AdminDiagnosticResponse(
        String instanceId,
        Instant inicioProceso,
        String version,
        Instant fechaBuild,
        String javaVersion,
        Instant capturadoEn,
        String alcance,
        Execution ejecucion,
        ExecutorStatus executor,
        List<ThreadStatus> hilos) {

    public record Execution(String executionId, String periodo, String estado, Instant solicitadoEn,
                            Instant iniciadoEn, Instant terminadoEn, String ultimaEtapa,
                            Instant ultimaActividad, long milisegundosSinActividad, Long threadId, String thread) {
    }

    public record ExecutorStatus(int activos, int poolSize, int corePoolSize, int maxPoolSize,
                                 int enCola, long completadas) {
    }

    public record ThreadStatus(long id, String nombre, String estado, String lock,
                               String propietarioLock, List<String> stack) {
    }
}
