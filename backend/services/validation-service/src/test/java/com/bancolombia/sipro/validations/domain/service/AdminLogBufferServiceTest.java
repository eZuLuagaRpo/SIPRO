package com.bancolombia.sipro.validations.domain.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.bancolombia.sipro.validations.infrastructure.config.AdminPanelProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminLogBufferServiceTest {
    @Test
    void debeRetener2500DetectarPerdidasYConservarCorrelacionYCausasSinMensajesSensibles() {
        AdminDiagnosticService diagnostic = mock(AdminDiagnosticService.class);
        when(diagnostic.instanceId()).thenReturn("instancia");
        var service = new AdminLogBufferService(new AdminPanelProperties(), diagnostic);
        for (int index = 0; index < 2501; index++) {
            LoggingEvent event = new LoggingEvent();
            event.setLevel(Level.INFO);
            event.setLoggerName(ConsolidacionPeriodoExecutor.class.getName());
            event.setThreadName("validation-job-1");
            event.setTimeStamp(System.currentTimeMillis());
            event.setMessage("Periodo 2026-07-31: INICIO escaneo XLSX.");
            event.setMDCPropertyMap(Map.of(AdminLogBufferService.MDC_SCOPE_KEY, "CONSOLIDACION",
                    AdminDiagnosticService.EXECUTION_MDC_KEY, "ejecucion"));
            if (index == 2500) {
                event.setThrowableProxy(new ThrowableProxy(
                        new IllegalStateException("token=secreto", new java.io.IOException("NIT=123456789"))));
            }
            service.append(event);
        }
        var capture = service.obtenerLogs(0L, "ALL", 9999, "CONSOLIDACION");
        assertThat(capture.totalBuffered()).isEqualTo(2500);
        assertThat(capture.items()).hasSize(2500);
        assertThat(capture.oldestId()).isEqualTo(2);
        assertThat(capture.instanceId()).isEqualTo("instancia");
        var last = capture.items().get(2499);
        assertThat(last.executionId()).isEqualTo("ejecucion");
        assertThat(last.detalleError()).contains("IllegalStateException", "IOException")
                .doesNotContain("secreto", "123456789");
        assertThat(service.obtenerLogs(9999L, "ALL", 100, "ALL").entriesLost()).isTrue();
        service.append(eventoSimple());
        assertThat(service.obtenerLogs(1L, "ALL", 100, "ALL").entriesLost()).isTrue();
    }

    private LoggingEvent eventoSimple() {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setLoggerName("test");
        event.setThreadName("test");
        event.setTimeStamp(System.currentTimeMillis());
        event.setMessage("evento");
        event.setMDCPropertyMap(Map.of());
        return event;
    }
}
