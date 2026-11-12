package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.AdminDiagnosticResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminDiagnosticService {
    public static final String EXECUTION_MDC_KEY = "siproExecutionId";
    private static final int MAX_COMPLETED_EXECUTIONS = 20;
    private static final int MAX_THREADS = 20;
    private static final int MAX_STACK_DEPTH = 30;

    private final String instanceId = UUID.randomUUID().toString();
    private final Instant inicioProceso = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
    private final ThreadPoolTaskExecutor executor;
    private final BuildProperties build;
    private final Map<String, Trace> ejecuciones = new LinkedHashMap<>();

    public AdminDiagnosticService(@Qualifier("validationTaskExecutor") ThreadPoolTaskExecutor executor,
                                  ObjectProvider<BuildProperties> buildProvider) {
        this.executor = executor;
        this.build = buildProvider.getIfAvailable();
    }

    public String instanceId() {
        return instanceId;
    }

    public synchronized String solicitar(LocalDate periodo) {
        String id = UUID.randomUUID().toString();
        ejecuciones.put(id, new Trace(id, periodo));
        return id;
    }

    public synchronized void iniciar(String id) {
        Trace trace = ejecuciones.get(id);
        trace.estado = "EN_PROCESO";
        trace.iniciadoEn = Instant.now();
        trace.ultimaActividad = trace.iniciadoEn;
        trace.threadId = Thread.currentThread().getId();
        trace.thread = Thread.currentThread().getName();
    }

    public synchronized void finalizar(String id, String estado) {
        Trace trace = ejecuciones.get(id);
        trace.estado = estado;
        trace.terminadoEn = Instant.now();
        trace.ultimaActividad = trace.terminadoEn;
        int completadas = (int) ejecuciones.values().stream().filter(value -> value.terminadoEn != null).count();
        var iterator = ejecuciones.values().iterator();
        while (completadas > MAX_COMPLETED_EXECUTIONS && iterator.hasNext()) {
            if (iterator.next().terminadoEn != null) {
                iterator.remove();
                completadas--;
            }
        }
    }

    public synchronized void registrarActividad(String id, String message) {
        Trace trace = ejecuciones.get(id);
        if (trace != null && trace.terminadoEn == null) {
            trace.ultimaActividad = Instant.now();
            if (message.startsWith("Periodo ") && (message.contains(": INICIO ") || message.contains(": FIN "))) {
                trace.ultimaEtapa = message.substring(0, Math.min(message.length(), 500));
            }
        }
    }

    public AdminDiagnosticResponse capturar(LocalDate periodo) {
        Instant ahora = Instant.now();
        AdminDiagnosticResponse.Execution ejecucion = obtenerEjecucion(periodo, ahora);
        var threads = ManagementFactory.getThreadMXBean();
        List<Long> ids = Arrays.stream(threads.getThreadInfo(threads.getAllThreadIds(), 0))
                .filter(info -> info != null && info.getThreadName().startsWith(executor.getThreadNamePrefix()))
                .map(ThreadInfo::getThreadId)
                .limit(MAX_THREADS)
                .toList();
        List<AdminDiagnosticResponse.ThreadStatus> hilos = Arrays.stream(
                        threads.getThreadInfo(ids.stream().mapToLong(Long::longValue).toArray(), MAX_STACK_DEPTH))
                .filter(info -> info != null)
                .map(info -> new AdminDiagnosticResponse.ThreadStatus(info.getThreadId(), info.getThreadName(),
                        info.getThreadState().name(), info.getLockName(), info.getLockOwnerName(),
                        Arrays.stream(info.getStackTrace()).map(StackTraceElement::toString).toList()))
                .toList();
        return new AdminDiagnosticResponse(instanceId, inicioProceso,
                build != null ? build.getVersion() : "No disponible (sin build-info)",
                build != null ? build.getTime() : null, System.getProperty("java.version"), ahora,
                "Solo esta instancia. Sin ejecucion local no implica que no exista en otra instancia. "
                        + "Captura puntual: hasta 20 hilos del executor compartido y 30 frames por hilo. "
                        + "No se leen variables ni contenido de archivos.",
                ejecucion, new AdminDiagnosticResponse.ExecutorStatus(executor.getActiveCount(), executor.getPoolSize(),
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueSize(),
                executor.getThreadPoolExecutor().getCompletedTaskCount()), hilos);
    }

    private synchronized AdminDiagnosticResponse.Execution obtenerEjecucion(LocalDate periodo, Instant ahora) {
        Trace selected = null;
        for (Trace trace : ejecuciones.values()) {
            if (periodo == null || periodo.equals(trace.periodo)) {
                selected = trace;
            }
        }
        if (selected == null) {
            return null;
        }
        return new AdminDiagnosticResponse.Execution(selected.id, selected.periodo.toString(), selected.estado,
                selected.solicitadoEn, selected.iniciadoEn, selected.terminadoEn, selected.ultimaEtapa,
                selected.ultimaActividad,
                Math.max(0, Duration.between(selected.ultimaActividad,
                        selected.terminadoEn != null ? selected.terminadoEn : ahora).toMillis()),
                selected.threadId, selected.thread);
    }

    private static final class Trace {
        private final String id;
        private final LocalDate periodo;
        private final Instant solicitadoEn = Instant.now();
        private String estado = "SOLICITADA";
        private Instant iniciadoEn;
        private Instant terminadoEn;
        private Instant ultimaActividad = solicitadoEn;
        private String ultimaEtapa;
        private Long threadId;
        private String thread;

        private Trace(String id, LocalDate periodo) {
            this.id = id;
            this.periodo = periodo;
        }
    }
}
