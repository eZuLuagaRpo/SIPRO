package com.bancolombia.sipro.validations.domain.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.bancolombia.sipro.validations.application.dto.AdminLogStreamResponse;
import com.bancolombia.sipro.validations.infrastructure.config.AdminPanelProperties;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffer en memoria de eventos de log para visualización operativa.
 */
@Service
public class AdminLogBufferService {

        public static final String CONSOLIDACION_SCOPE = "CONSOLIDACION";
        public static final String MDC_SCOPE_KEY = "siproLogScope";
        private static final Set<String> CONSOLIDACION_LOGGERS = Set.of(
                        "ConsolidacionManualAsyncService",
                        "ConsolidacionPlanillasService",
                        "ConsolidacionPeriodoExecutor",
                        "ConsolidacionConciliacionReportService",
                        "CreffosConsolidationService",
                        "CreffosParametricGenerator",
                        "CreffosColumnCalculator",
                        "CreffosComparisonService",
                        "VentanaCargaService",
                        "NotificacionConsolidacionService"
        );

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withLocale(new Locale("es", "CO"));

        private final AdminPanelProperties adminPanelProperties;
    private final AtomicLong sequence = new AtomicLong();
    private final Deque<BufferedLogItem> buffer = new ConcurrentLinkedDeque<>();

        public AdminLogBufferService(AdminPanelProperties adminPanelProperties) {
                this.adminPanelProperties = adminPanelProperties;
        }

    public void append(ILoggingEvent event) {
                if (!adminPanelProperties.getLogs().isStreamingEnabled()) {
                        return;
                }

        long id = sequence.incrementAndGet();
        buffer.addLast(new BufferedLogItem(
                id,
                FORMATTER.format(event.getInstant().atZone(ZoneId.systemDefault())),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                event.getMDCPropertyMap().getOrDefault(MDC_SCOPE_KEY, ""),
                event.getFormattedMessage()));

                while (buffer.size() > adminPanelProperties.getLogs().getEffectiveMaxBufferedEntries()) {
            buffer.pollFirst();
        }
    }

    public AdminLogStreamResponse obtenerLogs(Long afterId, String level, Integer limit, String scope) {
                if (!adminPanelProperties.getLogs().isStreamingEnabled()) {
                        return new AdminLogStreamResponse(sequence.get(), sequence.get(), 0, List.of());
                }

        long latestId = sequence.get();
        long lowerBound = afterId != null ? afterId : 0L;
        String normalizedLevel = level == null ? "ALL" : level.trim().toUpperCase(Locale.ROOT);
        String normalizedScope = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
                int maxItems = limit == null
                                ? adminPanelProperties.getLogs().getEffectiveDefaultQueryLimit()
                                : Math.max(1, Math.min(limit, adminPanelProperties.getLogs().getEffectiveMaxQueryLimit()));

        List<AdminLogStreamResponse.LogItem> items = buffer.stream()
                .filter(item -> item.id() > lowerBound)
                .filter(item -> "ALL".equals(normalizedLevel) || item.level().equalsIgnoreCase(normalizedLevel))
                .filter(item -> matchesScope(item, normalizedScope))
                .limit(maxItems)
                .map(item -> new AdminLogStreamResponse.LogItem(
                        item.id(),
                        item.timestamp(),
                        item.level(),
                        item.logger(),
                        item.thread(),
                        item.scope(),
                        item.message()))
                .toList();

        long cursorId = items.isEmpty()
                ? latestId
                : items.get(items.size() - 1).id();

        return new AdminLogStreamResponse(latestId, cursorId, buffer.size(), items);
    }

    private record BufferedLogItem(
            long id,
            String timestamp,
            String level,
            String logger,
            String thread,
                        String scope,
            String message) {
    }

        private boolean matchesScope(BufferedLogItem item, String normalizedScope) {
                if ("ALL".equals(normalizedScope)) {
                        return true;
                }

                if (CONSOLIDACION_SCOPE.equals(normalizedScope)) {
                        return CONSOLIDACION_SCOPE.equalsIgnoreCase(item.scope())
                                        && matchesConsolidacionLogger(item.logger());
                }

                return normalizedScope.equalsIgnoreCase(item.scope());
        }

        private boolean matchesConsolidacionLogger(String loggerName) {
                if (loggerName == null || loggerName.isBlank()) {
                        return false;
                }

                return CONSOLIDACION_LOGGERS.stream().anyMatch(loggerName::endsWith);
        }
}