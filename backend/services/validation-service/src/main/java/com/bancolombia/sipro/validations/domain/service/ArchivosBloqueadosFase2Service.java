package com.bancolombia.sipro.validations.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Ejecuta y rastrea la Fase 2 de la consolidación: publicación de archivos bloqueados
 * en segundo plano, después de que el CREFFSOS y el estado COMPLETADO ya se persistieron.
 */
@Service
public class ArchivosBloqueadosFase2Service {

    private static final Logger logger = LoggerFactory.getLogger(ArchivosBloqueadosFase2Service.class);

    private final ConcurrentHashMap<LocalDate, String> estadoFase2PorPeriodo = new ConcurrentHashMap<>();
    private final CreffosConsolidationService creffosConsolidationService;
    private final Executor validationTaskExecutor;

    public ArchivosBloqueadosFase2Service(
            CreffosConsolidationService creffosConsolidationService,
            @Qualifier("validationTaskExecutor") Executor validationTaskExecutor) {
        this.creffosConsolidationService = creffosConsolidationService;
        this.validationTaskExecutor = validationTaskExecutor;
    }

    public boolean estaEnCurso(LocalDate fechaCorte) {
        return fechaCorte != null && "EN_PROCESO".equals(estadoFase2PorPeriodo.get(fechaCorte));
    }

    public void ejecutarFase2(LocalDate fechaCorte) {
        if (fechaCorte == null) {
            return;
        }
        estadoFase2PorPeriodo.put(fechaCorte, "EN_PROCESO");
        validationTaskExecutor.execute(() -> {
            try {
                logger.info("[Fase 2] Iniciando publicación de archivos bloqueados para periodo {}.", fechaCorte);
                creffosConsolidationService.procesarArchivosBloqueados(fechaCorte);
                estadoFase2PorPeriodo.put(fechaCorte, "COMPLETADO");
                logger.info("[Fase 2] Archivos bloqueados completados para periodo {}.", fechaCorte);
            } catch (Exception ex) {
                estadoFase2PorPeriodo.put(fechaCorte, "ERROR");
                logger.error("[Fase 2] Error procesando archivos bloqueados para periodo {}: {}",
                        fechaCorte, ex.getMessage(), ex);
            }
        });
    }
}
