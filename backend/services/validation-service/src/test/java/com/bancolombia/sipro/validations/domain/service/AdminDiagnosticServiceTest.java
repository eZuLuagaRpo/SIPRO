package com.bancolombia.sipro.validations.domain.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.bancolombia.sipro.validations.infrastructure.config.AdminLogAppender;
import com.bancolombia.sipro.validations.infrastructure.config.AdminPanelProperties;
import com.bancolombia.sipro.validations.infrastructure.config.ValidationAsyncConfiguration;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminDiagnosticServiceTest {
    @Test
    void debeEnlazarElExecutorYPublicarLasEtapasEnElBufferRealDelModal() {
        new ApplicationContextRunner()
                .withUserConfiguration(ValidationAsyncConfiguration.class)
                .withBean(AdminPanelProperties.class)
                .withBean(AdminDiagnosticService.class)
                .withBean(AdminLogBufferService.class)
                .withBean(AdminLogAppender.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var service = context.getBean(AdminDiagnosticService.class);
                    var buffer = context.getBean(AdminLogBufferService.class);
                    LocalDate periodo = LocalDate.of(2026, 7, 31);
                    String id = service.solicitar(periodo);
                    service.iniciar(id);
                    try (var scope = MDC.putCloseable(AdminLogBufferService.MDC_SCOPE_KEY, "CONSOLIDACION");
                         var execution = MDC.putCloseable(AdminDiagnosticService.EXECUTION_MDC_KEY, id)) {
                        LoggerFactory.getLogger(ConsolidacionPeriodoExecutor.class)
                                .info("Periodo {}: INICIO escaneo XLSX.", periodo);
                    }
                    var logs = buffer.obtenerLogs(null, "ALL", 10, "CONSOLIDACION");
                    assertThat(logs.items()).hasSize(1);
                    assertThat(logs.items().get(0).executionId()).isEqualTo(id);
                    assertThat(logs.instanceId()).isEqualTo(service.capturar(periodo).instanceId());
                    assertThat(service.capturar(periodo).ejecucion().ultimaEtapa()).contains("INICIO escaneo");
                    service.finalizar(id, "COMPLETADO");
                });
    }

    @Test
    void debeCapturarElHiloRealSinEjecutarOtraTareaNiPerderSuEstado() throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("diagnostic-test-");
        executor.initialize();
        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> build = mock(ObjectProvider.class);
        var service = new AdminDiagnosticService(executor, build);
        LocalDate periodo = LocalDate.of(2026, 7, 31);
        String id = service.solicitar(periodo);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                service.iniciar(id);
                service.registrarActividad(id, "Periodo 2026-07-31: INICIO escaneo XLSX.");
                started.countDown();
                try {
                    release.await();
                    service.finalizar(id, "COMPLETADO");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                } finally {
                    finished.countDown();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var capture = service.capturar(periodo);
            assertThat(capture.ejecucion().estado()).isEqualTo("EN_PROCESO");
            assertThat(capture.ejecucion().executionId()).isEqualTo(id);
            assertThat(capture.ejecucion().ultimaEtapa()).contains("INICIO escaneo");
            assertThat(capture.executor().activos()).isEqualTo(1);
            assertThat(capture.hilos()).anySatisfy(thread -> {
                assertThat(thread.id()).isEqualTo(capture.ejecucion().threadId());
                assertThat(thread.stack()).hasSizeLessThanOrEqualTo(30);
                assertThat(thread.nombre()).startsWith("diagnostic-test-");
            });
            assertThat(service.capturar(periodo.plusMonths(1)).ejecucion()).isNull();
            release.countDown();
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(service.capturar(periodo).ejecucion().estado()).isEqualTo("COMPLETADO");
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
