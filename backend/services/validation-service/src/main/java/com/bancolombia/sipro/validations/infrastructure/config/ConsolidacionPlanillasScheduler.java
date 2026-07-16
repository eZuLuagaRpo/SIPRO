package com.bancolombia.sipro.validations.infrastructure.config;

import com.bancolombia.sipro.validations.domain.service.ConsolidacionPlanillasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Programa el barrido automático que revisa qué periodos ya pueden consolidarse.
 */
@Component
public class ConsolidacionPlanillasScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ConsolidacionPlanillasScheduler.class);

    private final ConsolidacionPlanillasService consolidacionPlanillasService;

    public ConsolidacionPlanillasScheduler(ConsolidacionPlanillasService consolidacionPlanillasService) {
        this.consolidacionPlanillasService = consolidacionPlanillasService;
    }

    /**
     * Ejecuta el barrido periódico usando los tiempos configurados en parámetros.
     */
    @Scheduled(
            fixedDelayString = "#{@parametroUnicoService.getString('APP_CONSOLIDACION_FIXED_DELAY_MS', '3600000')}",
            initialDelayString = "#{@parametroUnicoService.getString('APP_CONSOLIDACION_INITIAL_DELAY_MS', '60000')}")
    public void ejecutarBarrido() {
        logger.info("Iniciando barrido automático de consolidaciones pendientes");
        try {
            consolidacionPlanillasService.ejecutarBarridoConsolidacionesPendientes();
        } catch (Exception e) {
            logger.error("Error ejecutando el barrido automático de consolidaciones pendientes: {}", e.getMessage(), e);
        }
    }
}