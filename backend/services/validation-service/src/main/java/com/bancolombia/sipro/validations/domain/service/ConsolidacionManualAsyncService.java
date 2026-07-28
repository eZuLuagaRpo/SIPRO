package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.ConsolidacionManualStatusResponse;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Orquesta la consolidación manual en segundo plano y expone su estado por periodo.
 */
@Service
public class ConsolidacionManualAsyncService {

    private static final Logger logger = LoggerFactory.getLogger(ConsolidacionManualAsyncService.class);

    private final ConsolidacionPlanillasService consolidacionPlanillasService;
    private final SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;
    private final Executor validationTaskExecutor;
    private final ArchivosBloqueadosFase2Service archivosBloqueadosFase2Service;
    private final Map<LocalDate, ConsolidacionManualStatusResponse> estadoTemporalPorPeriodo = new ConcurrentHashMap<>();

    public ConsolidacionManualAsyncService(
            ConsolidacionPlanillasService consolidacionPlanillasService,
            SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository,
            @Qualifier("validationTaskExecutor") Executor validationTaskExecutor,
            ArchivosBloqueadosFase2Service archivosBloqueadosFase2Service) {
        this.consolidacionPlanillasService = consolidacionPlanillasService;
        this.consolidacionRepository = consolidacionRepository;
        this.validationTaskExecutor = validationTaskExecutor;
        this.archivosBloqueadosFase2Service = archivosBloqueadosFase2Service;
    }

    /**
     * Inicia una consolidación manual asíncrona si el periodo cumple las validaciones previas.
     */
    public ConsolidacionManualStatusResponse iniciar(LocalDate periodoValoracion,
                                                     Long usuarioEjecutorId,
                                                     String observacion) {
        ConsolidacionPlanillasService.ConsolidacionManualPrecheck precheck =
                consolidacionPlanillasService.validarInicioConsolidacionManual(periodoValoracion);

        if (precheck.enCurso()) {
            return obtenerEstado(periodoValoracion)
                    .orElseGet(() -> construirRespuestaSimple(periodoValoracion,
                            "EN_PROCESO",
                            "Ya existe una consolidación en curso para el periodo " + periodoValoracion + ".",
                            false,
                            false));
        }

        if (!precheck.puedeIniciar()) {
            return construirRespuestaSimple(periodoValoracion,
                    "NO_EJECUTADA",
                    precheck.mensaje(),
                    true,
                    false);
        }

        ConsolidacionManualStatusResponse respuesta = construirRespuestaSimple(
                periodoValoracion,
                "SOLICITADA",
                "Solicitud enviada. La consolidación continuará en segundo plano aunque cierres la pantalla.",
                false,
                false);
        respuesta.setFechaHoraInicio(OffsetDateTime.now());
        estadoTemporalPorPeriodo.put(periodoValoracion, respuesta);

        validationTaskExecutor.execute(() -> ejecutarEnSegundoPlano(periodoValoracion, usuarioEjecutorId, observacion));
        return respuesta;
    }

    /**
     * Consulta el estado más reciente desde base de datos o desde el caché temporal en memoria.
     */
    public Optional<ConsolidacionManualStatusResponse> obtenerEstado(LocalDate periodoValoracion) {
        Optional<SiproDetalleConsolidacionesPlanillas> cabeceraOpt = consolidacionRepository
                .findFirstByPeriodoValoracionOrderByCreadoEnDesc(periodoValoracion);
        if (cabeceraOpt.isPresent()) {
            return Optional.of(mapearCabecera(cabeceraOpt.get()));
        }

        return Optional.ofNullable(estadoTemporalPorPeriodo.get(periodoValoracion));
    }

    private void ejecutarEnSegundoPlano(LocalDate periodoValoracion,
                                        Long usuarioEjecutorId,
                                        String observacion) {
        MDC.put(AdminLogBufferService.MDC_SCOPE_KEY, AdminLogBufferService.CONSOLIDACION_SCOPE);
        ConsolidacionManualStatusResponse estadoEnProceso = construirRespuestaSimple(
                periodoValoracion,
                "EN_PROCESO",
                "Consolidación en progreso. Puedes salir y volver; el estado se conservará por periodo.",
                false,
                false);
        estadoEnProceso.setFechaHoraInicio(OffsetDateTime.now());
        estadoTemporalPorPeriodo.put(periodoValoracion, estadoEnProceso);

        try {
            logger.info("Iniciando consolidación manual del periodo {} por usuario {}.", periodoValoracion, usuarioEjecutorId);
            boolean resultado = consolidacionPlanillasService.consolidarManual(periodoValoracion, usuarioEjecutorId, observacion);
            if (!resultado) {
                ConsolidacionManualStatusResponse respuesta = construirRespuestaSimple(
                        periodoValoracion,
                        "NO_EJECUTADA",
                        "No se consolidó el periodo " + periodoValoracion
                                + ". Verifique planillas aprobadas, configuración de ventana o una ejecución previa en curso.",
                        true,
                        false);
                respuesta.setFechaHoraFin(OffsetDateTime.now());
                estadoTemporalPorPeriodo.put(periodoValoracion, respuesta);
                return;
            }

            logger.info("Consolidación manual del periodo {} finalizada correctamente.", periodoValoracion);
            obtenerEstado(periodoValoracion).ifPresent(estado -> estadoTemporalPorPeriodo.put(periodoValoracion, estado));
        } catch (Exception e) {
            logger.error("Error ejecutando consolidación manual asíncrona para periodo {}: {}",
                    periodoValoracion, e.getMessage(), e);
            ConsolidacionManualStatusResponse respuesta = construirRespuestaSimple(
                    periodoValoracion,
                    "ERROR",
                    "La consolidación falló. Revisa el detalle y vuelve a intentar si aplica.",
                    true,
                    false);
            respuesta.setFechaHoraFin(OffsetDateTime.now());
            respuesta.setMensajeError(e.getMessage());
            estadoTemporalPorPeriodo.put(periodoValoracion, respuesta);
        } finally {
            MDC.remove(AdminLogBufferService.MDC_SCOPE_KEY);
        }
    }

    private ConsolidacionManualStatusResponse mapearCabecera(SiproDetalleConsolidacionesPlanillas cabecera) {
        ConsolidacionManualStatusResponse response = new ConsolidacionManualStatusResponse();
        response.setPeriodo(cabecera.getPeriodoValoracion() != null ? cabecera.getPeriodoValoracion().toString() : null);
        response.setEstado(cabecera.getEstadoConsolidacion());
        response.setFechaHoraInicio(cabecera.getFechaHoraInicio());
        response.setFechaHoraFin(cabecera.getFechaHoraFin());
        response.setCantidadArchivosConsolidados(cabecera.getCantidadArchivosConsolidados());
        response.setCantidadRegistrosConsolidados(cabecera.getCantidadRegistrosConsolidados());
        response.setMensajeError(cabecera.getMensajeError());

        String estado = cabecera.getEstadoConsolidacion();
        if ("COMPLETADO".equalsIgnoreCase(estado)) {
            response.setTerminal(true);
            response.setExito(true);
            response.setMensaje("Consolidación completada para el periodo " + response.getPeriodo() + ".");
            response.setFase2EnCurso(archivosBloqueadosFase2Service.estaEnCurso(cabecera.getPeriodoValoracion()));
        } else if ("COMPLETADO_CON_ADVERTENCIAS".equalsIgnoreCase(estado)) {
            response.setTerminal(true);
            response.setExito(true);
            response.setMensaje("Consolidación completada con advertencias para el periodo " + response.getPeriodo() + ".");
            response.setFase2EnCurso(archivosBloqueadosFase2Service.estaEnCurso(cabecera.getPeriodoValoracion()));
        } else if ("ERROR".equalsIgnoreCase(estado)) {
            response.setTerminal(true);
            response.setExito(false);
            response.setMensaje("La consolidación terminó con error para el periodo " + response.getPeriodo() + ".");
        } else {
            response.setTerminal(false);
            response.setExito(false);
            response.setMensaje("Consolidación en progreso para el periodo " + response.getPeriodo() + ".");
        }

        return response;
    }

    private ConsolidacionManualStatusResponse construirRespuestaSimple(
            LocalDate periodoValoracion,
            String estado,
            String mensaje,
            boolean terminal,
            boolean exito) {
        ConsolidacionManualStatusResponse response = new ConsolidacionManualStatusResponse();
        response.setPeriodo(periodoValoracion.toString());
        response.setEstado(estado);
        response.setMensaje(mensaje);
        response.setTerminal(terminal);
        response.setExito(exito);
        response.setCantidadArchivosConsolidados(0);
        response.setCantidadRegistrosConsolidados(0);
        return response;
    }
}