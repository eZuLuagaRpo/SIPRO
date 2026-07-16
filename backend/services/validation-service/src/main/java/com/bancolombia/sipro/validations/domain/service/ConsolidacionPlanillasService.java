package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Coordina cuándo debe intentarse una consolidación automática o manual de planillas.
 */
@Service
public class ConsolidacionPlanillasService {

    private static final Logger logger = LoggerFactory.getLogger(ConsolidacionPlanillasService.class);
    private static final Long SISTEMA_USUARIO_ID = 1L;
    private static final Long SEGMENTO_COLGAAP_MODIFICADO_ID = 1L;
    private static final List<String> ESTADOS_EN_PROCESO = List.of("INICIADO", "EN_PROCESO");
    private static final List<String> ESTADOS_NO_DUPLICABLES = List.of("COMPLETADO", "COMPLETADO_CON_ADVERTENCIAS", "INICIADO", "EN_PROCESO");
    private static final List<String> ESTADOS_COMPLETADOS = List.of("COMPLETADO", "COMPLETADO_CON_ADVERTENCIAS");
    private static final long DEFAULT_POST_CLOSE_DELAY_HOURS = 1L;
    private static final long DEFAULT_MAX_POST_CLOSE_DAYS = 5L;
    private static final String ADMIN_CONSOLIDACION_BYPASS_WINDOW_KEY = "APP_ADMIN_CONSOLIDACION_BYPASS_WINDOW";
        private static final DateTimeFormatter FECHA_CONSOLIDACION_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-CO"));

    private final SiproDetalleCargaPlanillasRepository planillaRepository;
    private final SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;
    private final VentanaCargaService ventanaCargaService;
    private final ConsolidacionPeriodoExecutor consolidacionPeriodoExecutor;
    private final ParametroUnicoService parametroUnicoService;
    private final Environment environment;

    public ConsolidacionPlanillasService(SiproDetalleCargaPlanillasRepository planillaRepository,
                                         SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository,
                                         VentanaCargaService ventanaCargaService,
                                         ConsolidacionPeriodoExecutor consolidacionPeriodoExecutor,
                                         ParametroUnicoService parametroUnicoService,
                                         Environment environment) {
        this.planillaRepository = planillaRepository;
        this.consolidacionRepository = consolidacionRepository;
        this.ventanaCargaService = ventanaCargaService;
        this.consolidacionPeriodoExecutor = consolidacionPeriodoExecutor;
        this.parametroUnicoService = parametroUnicoService;
        this.environment = environment;
    }

    /**
     * Revisa si después de una aprobación ya corresponde consolidar el periodo afectado.
     */
    public void evaluarPeriodoPostAprobacion(LocalDate periodoValoracion, Long usuarioEjecutorId) {
        try {
            ejecutarBarridoHastaPeriodo(
                    periodoValoracion,
                    usuarioEjecutorId != null ? usuarioEjecutorId : SISTEMA_USUARIO_ID,
                    "Evaluación automática posterior a aprobación");
        } catch (Exception e) {
            logger.error("Error evaluando consolidación post-aprobación para periodo {}: {}",
                    periodoValoracion, e.getMessage(), e);
        }
    }

    public void evaluarPeriodoPostAprobacion(LocalDate periodoValoracion) {
        evaluarPeriodoPostAprobacion(periodoValoracion, SISTEMA_USUARIO_ID);
    }

    /**
     * Recorre periodos aprobados y trata de consolidar los que estén pendientes.
     */
    public void ejecutarBarridoConsolidacionesPendientes() {
        ejecutarBarridoHastaPeriodo(
                null,
                SISTEMA_USUARIO_ID,
                "Barrido automático de consolidaciones pendientes");
    }

    /**
     * Ejecuta consolidación manual de un periodo específico.
     * En DEV puede omitir temporalmente la ventana si el flag está activo.
     */
    public boolean consolidarManual(LocalDate periodoValoracion, Long usuarioEjecutorId, String observacion) {
        try {
            return consolidacionPeriodoExecutor.consolidarPeriodoForzado(
                    periodoValoracion,
                    usuarioEjecutorId != null ? usuarioEjecutorId : SISTEMA_USUARIO_ID,
                    observacion != null && !observacion.isBlank() ? observacion : "Consolidación manual forzada");
        } catch (Exception e) {
            logger.error("Error en consolidación manual para periodo {}: {}", periodoValoracion, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Valida si una consolidación manual puede arrancar sin chocar con reglas o ejecuciones en curso.
     */
    public ConsolidacionManualPrecheck validarInicioConsolidacionManual(LocalDate periodoValoracion) {
        Optional<ConsolidacionRangoOperativo> rangoOpt = obtenerRangoConsolidacion(periodoValoracion);
        if (rangoOpt.isEmpty()) {
            return new ConsolidacionManualPrecheck(false, false,
                    "No existe configuración de ventana para el periodo " + periodoValoracion + ".",
                    false,
                false,
                    null);
        }

        ConsolidacionRangoOperativo rango = rangoOpt.get();
        OffsetDateTime ahora = OffsetDateTime.now();
        boolean bypassVentana = isBypassVentanaManualActivo();
        boolean antesDelRango = ahora.isBefore(rango.inicioRangoConsolidacion());
        boolean despuesDelRango = ahora.isAfter(rango.finRangoConsolidacion());

        if (antesDelRango && !bypassVentana) {
            return new ConsolidacionManualPrecheck(false, false,
                    "La ventana de carga aún no ha cerrado para este periodo. El cierre está programado para "
                            + rango.cierreVentana() + ".",
                    false,
                false,
                    rango);
        }

        if (despuesDelRango && !bypassVentana) {
            return new ConsolidacionManualPrecheck(false, false,
                    "El rango de consolidación para este periodo expiró el "
                            + rango.finRangoConsolidacion()
                            + ". Para consolidar fuera de rango, primero cree una excepción de ventana en la sección de parámetros para extender la fecha de cierre.",
                    false,
                false,
                    rango);
        }

        if (consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(periodoValoracion, ESTADOS_EN_PROCESO)) {
            return new ConsolidacionManualPrecheck(false, true,
                    "Ya existe una consolidación en curso para el periodo " + periodoValoracion + ".",
                    false,
                bypassVentana,
                    rango);
        }

        ResumenPlanillasPeriodo resumenPlanillas = construirResumenPlanillasPeriodo(periodoValoracion);

        if (resumenPlanillas.aprobadasConsolidables() == 0) {
            return new ConsolidacionManualPrecheck(false, false,
                construirMensajeSinPlanillasConsolidables(periodoValoracion, resumenPlanillas),
            false,
            bypassVentana,
            rango);
        }

        boolean tieneConsolidacionExitosa = consolidacionRepository
            .existsByPeriodoValoracionAndEstadoConsolidacionIn(periodoValoracion, ESTADOS_COMPLETADOS);
        if (tieneConsolidacionExitosa) {
            String mensajePeriodoConsolidado = consolidacionRepository
                    .findFirstByPeriodoValoracionAndEstadoConsolidacionInOrderByCreadoEnDesc(periodoValoracion, ESTADOS_COMPLETADOS)
                    .map(this::construirMensajePeriodoYaConsolidado)
                    .orElse("Este periodo ya fue consolidado. Para reconsolidar, primero elimine la consolidación existente desde el historial.");
            return new ConsolidacionManualPrecheck(false, false,
                mensajePeriodoConsolidado,
                false,
                bypassVentana,
                rango);
        }

        return new ConsolidacionManualPrecheck(true, false,
    construirMensajeDisponibilidad(periodoValoracion, rango, antesDelRango, despuesDelRango, bypassVentana,
        resumenPlanillas),
        false,
        bypassVentana,
        rango);
    }

    private String construirMensajePeriodoYaConsolidado(SiproDetalleConsolidacionesPlanillas consolidacion) {
        OffsetDateTime fechaReferencia = consolidacion.getFechaHoraFin() != null
                ? consolidacion.getFechaHoraFin()
                : consolidacion.getFechaHoraInicio();

        String fechaTexto = fechaReferencia != null
                ? FECHA_CONSOLIDACION_FORMATTER.format(fechaReferencia)
                : "sin fecha registrada";

        return "Este periodo ya fue consolidado el " + fechaTexto
                + ". Para reconsolidar, primero elimine la consolidación existente desde el historial.";
    }

    public Optional<ConsolidacionRangoOperativo> obtenerRangoConsolidacion(LocalDate periodoValoracion) {
    return ventanaCargaService.obtenerVentana(periodoValoracion)
        .map(this::construirRangoConsolidacion);
    }

    /**
     * Resume el resultado de la validación previa para iniciar una consolidación manual.
     */
    public record ConsolidacionManualPrecheck(boolean puedeIniciar,
                          boolean enCurso,
                          String mensaje,
                          boolean sobrescribeConsolidacionExistente,
                          boolean ventanaIgnoradaPorConfiguracion,
                          ConsolidacionRangoOperativo rango) {
    }

    public record ConsolidacionRangoOperativo(OffsetDateTime cierreVentana,
                          OffsetDateTime inicioRangoConsolidacion,
                          OffsetDateTime finRangoConsolidacion,
                          String fuenteVentana,
                          String motivoExcepcion) {
    }

    private void ejecutarBarridoHastaPeriodo(LocalDate periodoLimite,
                                             Long usuarioEjecutorId,
                                             String observacion) {
        List<LocalDate> periodos = planillaRepository
            .findDistinctFechasCorteInformacionAprobadasBySegmentoId(SEGMENTO_COLGAAP_MODIFICADO_ID);
        for (LocalDate periodo : periodos) {
            if (periodoLimite != null && periodo.isAfter(periodoLimite)) {
                continue;
            }

            if (consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(periodo, ESTADOS_NO_DUPLICABLES)) {
                logger.info("Periodo {}: no consolida porque ya existe una consolidación en estado COMPLETADO/INICIADO/EN_PROCESO.",
                        periodo);
                continue;
            }

            ResumenPlanillasPeriodo resumenPlanillas = construirResumenPlanillasPeriodo(periodo);
            String resumenEstadosNoAprobados = construirResumenEstadosNoAprobados(resumenPlanillas);
            if (!resumenEstadosNoAprobados.isBlank()) {
                logger.info("Periodo {}: {} activas no bloquean la consolidación; se intentará consolidar solo lo aprobado.",
                        periodo, resumenEstadosNoAprobados);
            }

            try {
                consolidacionPeriodoExecutor.consolidarPeriodoSiCorresponde(
                        periodo,
                        usuarioEjecutorId,
                        observacion);
            } catch (Exception e) {
                logger.error("Error en barrido de consolidación para periodo {}: {}", periodo, e.getMessage(), e);
            }
        }
    }

            private ConsolidacionRangoOperativo construirRangoConsolidacion(VentanaCargaService.VentanaCalculada ventana) {
            long postCloseHours = parametroUnicoService.getLong(
                "APP_CONSOLIDACION_POST_CLOSE_DELAY_HOURS",
                DEFAULT_POST_CLOSE_DELAY_HOURS);
            long maxPostDays = parametroUnicoService.getLong(
                "APP_CONSOLIDACION_MAX_POST_CLOSE_DAYS",
                DEFAULT_MAX_POST_CLOSE_DAYS);
            OffsetDateTime cierreVentana = ventana.getFechaHoraCierreOffset();

            return new ConsolidacionRangoOperativo(
                cierreVentana,
                cierreVentana.plusHours(postCloseHours),
                cierreVentana.plusDays(maxPostDays),
                ventana.getTipoVentanaRespuesta(),
                ventana.getMotivoExcepcion());
            }

    private boolean esPlanillaConsolidable(SiproDetalleCargaPlanillas planilla) {
        if (planilla == null) {
            return false;
        }

        if (Boolean.TRUE.equals(planilla.getNoReportaDatos())) {
            return true;
        }

        String ruta = planilla.getRutaArchivoAlmacenamiento();
        if (ruta == null || ruta.isBlank()) {
            return false;
        }

        return esRutaXlsx(ruta) || esRutaXlsx(planilla.getNombreArchivoFuente());
    }

    private boolean esRutaXlsx(String rutaArchivo) {
        return rutaArchivo != null && rutaArchivo.toLowerCase().endsWith(".xlsx");
    }

    private boolean isBypassVentanaManualActivo() {
        return environment.matchesProfiles("dev")
            && Boolean.parseBoolean(parametroUnicoService.getString(ADMIN_CONSOLIDACION_BYPASS_WINDOW_KEY, "false"));
    }

    private ResumenPlanillasPeriodo construirResumenPlanillasPeriodo(LocalDate periodoValoracion) {
        List<SiproDetalleCargaPlanillas> planillasActivas = planillaRepository
                .findPlanillasActivasByFechaCorteAndSegmentoId(periodoValoracion, SEGMENTO_COLGAAP_MODIFICADO_ID);

        int aprobadasConsolidables = 0;
        int pendientes = 0;
        int rechazadas = 0;

        for (SiproDetalleCargaPlanillas planilla : planillasActivas) {
            if (esEstadoAprobado(planilla.getEstadoPlanilla())) {
                if (esPlanillaConsolidable(planilla)) {
                    aprobadasConsolidables++;
                }
                continue;
            }

            if (esEstadoRechazado(planilla.getEstadoPlanilla())) {
                rechazadas++;
            } else {
                pendientes++;
            }
        }

        return new ResumenPlanillasPeriodo(planillasActivas.size(), aprobadasConsolidables, pendientes, rechazadas);
    }

    private boolean esEstadoAprobado(String estadoPlanilla) {
        String estadoNormalizado = normalizarEstadoPlanilla(estadoPlanilla);
        return "aprobado".equals(estadoNormalizado)
                || "archivo aprobado".equals(estadoNormalizado)
                || "aprobación sin datos".equals(estadoNormalizado)
                || "aprobacion sin datos".equals(estadoNormalizado);
    }

    private boolean esEstadoRechazado(String estadoPlanilla) {
        return normalizarEstadoPlanilla(estadoPlanilla).contains("rechaz");
    }

    private String normalizarEstadoPlanilla(String estadoPlanilla) {
        return estadoPlanilla == null ? "" : estadoPlanilla.trim().toLowerCase();
    }

    private String construirMensajeSinPlanillasConsolidables(LocalDate periodoValoracion,
                                                             ResumenPlanillasPeriodo resumenPlanillas) {
        String mensaje = "No hay planillas aprobadas del segmento 1 con XLSX disponible o certificación sin datos para consolidar en el periodo "
                + periodoValoracion + ".";
        String resumenEstadosNoAprobados = construirResumenEstadosNoAprobados(resumenPlanillas);
        if (resumenEstadosNoAprobados.isBlank()) {
            return mensaje;
        }

        return mensaje + " Actualmente hay " + resumenEstadosNoAprobados
                + " activas en el periodo, pero no bloquearán futuras consolidaciones cuando exista al menos una aprobada.";
    }

    private String construirResumenEstadosNoAprobados(ResumenPlanillasPeriodo resumenPlanillas) {
        List<String> partes = new ArrayList<>();
        if (resumenPlanillas.pendientes() > 0) {
            partes.add(resumenPlanillas.pendientes() + (resumenPlanillas.pendientes() == 1 ? " pendiente" : " pendientes"));
        }
        if (resumenPlanillas.rechazadas() > 0) {
            partes.add(resumenPlanillas.rechazadas() + (resumenPlanillas.rechazadas() == 1 ? " rechazada" : " rechazadas"));
        }
        return String.join(" y ", partes);
    }

    private String construirMensajeDisponibilidad(LocalDate periodoValoracion,
                                                  ConsolidacionRangoOperativo rango,
                                                  boolean antesDelRango,
                                                  boolean despuesDelRango,
                                                  boolean bypassVentana,
                                                  ResumenPlanillasPeriodo resumenPlanillas) {
        String advertenciaPlanillas = construirAdvertenciaPlanillasNoBloqueantes(resumenPlanillas);
        if (!bypassVentana || (!antesDelRango && !despuesDelRango)) {
            return "La consolidación manual puede iniciarse para el periodo " + periodoValoracion + "."
                    + advertenciaPlanillas;
        }

        if (antesDelRango) {
            return "Bypass DEV activo. Se permite consolidar el periodo " + periodoValoracion
                + " aunque la ventana de carga siga abierta. Cierre teórico: " + rango.cierreVentana() + "."
                + advertenciaPlanillas;
        }

        return "Bypass DEV activo. Se permite consolidar el periodo " + periodoValoracion
            + " aunque el rango teórico haya expirado el " + rango.finRangoConsolidacion() + "."
            + advertenciaPlanillas;
    }

    private String construirAdvertenciaPlanillasNoBloqueantes(ResumenPlanillasPeriodo resumenPlanillas) {
        String resumenEstadosNoAprobados = construirResumenEstadosNoAprobados(resumenPlanillas);
        if (resumenEstadosNoAprobados.isBlank()) {
            return "";
        }

        return " Hay " + resumenEstadosNoAprobados
                + " activas que no bloquean la consolidación; se consolidará solo lo aprobado.";
    }

    private record ResumenPlanillasPeriodo(int totalActivas,
                                           int aprobadasConsolidables,
                                           int pendientes,
                                           int rechazadas) {
    }
}