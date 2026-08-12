package com.bancolombia.sipro.validations.infrastructure.config;

import com.bancolombia.sipro.validations.domain.service.IncumplimientoCargaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara periódicamente la verificación de incumplimientos de carga de planillas manuales.
 * Los tiempos se leen desde sipro_parametros_unico para poder ajustarlos sin redespliegue.
 */
@Component
public class IncumplimientoCargaScheduler {

    private static final Logger logger = LoggerFactory.getLogger(IncumplimientoCargaScheduler.class);

    private final IncumplimientoCargaService incumplimientoCargaService;

    public IncumplimientoCargaScheduler(IncumplimientoCargaService incumplimientoCargaService) {
        this.incumplimientoCargaService = incumplimientoCargaService;
    }

    @Scheduled(
            fixedDelayString = "#{@parametroUnicoService.getString('INCUMPLIMIENTO_SCHEDULER_FIXED_DELAY_MS', '3600000')}",
            initialDelayString = "#{@parametroUnicoService.getString('INCUMPLIMIENTO_SCHEDULER_INITIAL_DELAY_MS', '120000')}")
    public void ejecutarVerificacion() {
        logger.info("[IncumplimientoScheduler] Iniciando verificación de incumplimientos de carga");
        try {
            incumplimientoCargaService.ejecutarNotificacionIncumplimiento();
        } catch (Exception e) {
            logger.error("[IncumplimientoScheduler] Error ejecutando verificación de incumplimientos: {}", e.getMessage(), e);
        }
    }
}
