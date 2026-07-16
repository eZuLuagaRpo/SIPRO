package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleArchivoValidacion;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionArchivo;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.domain.model.Producto;
import com.bancolombia.sipro.validations.infrastructure.notification.MailTemplateNotificationService;
import com.bancolombia.sipro.validations.infrastructure.repository.ClienteLzRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionArchivoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidadoRegistroRepository;
import com.bancolombia.sipro.validations.shared.utils.XlsxStreamingReader;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ejecuta la consolidación completa de un periodo, desde la validación previa hasta la publicación final.
 */
@Service
public class ConsolidacionPeriodoExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ConsolidacionPeriodoExecutor.class);
    private static final Long SEGMENTO_COLGAAP_MODIFICADO_ID = 1L;
    private static final String SEGMENTO_COLGAAP_MODIFICADO_NOMBRE = "Colgaap/Modificado";
    private static final String ESTADO_COMPLETADO = "COMPLETADO";
    private static final String ESTADO_COMPLETADO_CON_ADVERTENCIAS = "COMPLETADO_CON_ADVERTENCIAS";
    private static final String ESTADO_ERROR = "ERROR";
    private static final String ESTADO_INICIADO = "INICIADO";
    private static final String ESTADO_EN_PROCESO = "EN_PROCESO";
    private static final List<String> ESTADOS_EXITOSOS = List.of(ESTADO_COMPLETADO, ESTADO_COMPLETADO_CON_ADVERTENCIAS);
    private static final List<String> ESTADOS_EN_CURSO = List.of(ESTADO_INICIADO, ESTADO_EN_PROCESO);
    private static final String CONSOLIDADOS_PREFIX = "consolidados/";
    /** Nombre fijo del Excel consolidado en la ruta compartida: cada periodo reemplaza al anterior. */
    private static final String CONSOLIDADO_NOMBRE_ARCHIVO_COMPARTIDO = "CONSOLIDADO.xlsx";
    private static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int DEFAULT_BATCH_INSERT_SIZE = 500;
    private static final int DEFAULT_LZ_LOOKUP_CHUNK_SIZE = 1000;
    private static final List<String> HEADERS_ENTRADA_PLANILLA = List.of(
            "NIT",
            "OFICINA",
            "DOCUMENTO",
            "MONEDA",
            "MODALIDAD",
            "ANOINIOBL",
            "MESINIOBL",
            "DIAINIOBL",
            "ANOVCTO",
            "MESVCTO",
            "DIAVCTO",
            "ANOVCTOFIN",
            "MESVCTOFIN",
            "DIAVCTOFIN",
            "CTAPUC",
            "VLRINIOBL",
            "SALDO",
            "SDOOTRCTAS",
            "INTERESES",
            "SDOVENCIDO",
            "INTCTASORD",
            "CLASIFICACION",
            "USUARIO",
            "PRODUCTO");
    private static final List<String> HEADERS_CONSOLIDADO_SALIDA = List.of(
            "NIT",
            "OFICINA",
            "DOCUMENTO",
            "MONEDA",
            "MODALIDAD",
            "ANOINIOBL",
            "MESINIOBL",
            "DIAINIOBL",
            "ANOVCTO",
            "MESVCTO",
            "DIAVCTO",
            "ANOVCTOFIN",
            "MESVCTOFIN",
            "DIAVCTOFIN",
            "CTAPUC",
            "VLRINIOBL",
            "SALDO",
            "SDOOTRCTAS",
            "INTERESES",
            "SDOVENCIDO",
            "INTCTASORD",
            "CLASIFICACION",
            "USUARIO",
            "PRODUCTO",
            "TIPO_ID",
            "PRODUCTO_ORIGEN",
            "SEGMENTO",
            "FECHA_CORTE",
            "DESCRIPCION",
            "USUARIO_CARGADOR",
            "USUARIO_APROBADOR");

    private final SiproDetalleCargaPlanillasRepository planillaRepository;
    private final SiproDetalleArchivoValidacionRepository validacionRepository;
    private final ClienteLzRepository clienteLzRepository;
    private final SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;
    private final SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository;
    private final SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository;
    private final ProductoRepository productoRepository;
    private final VentanaCargaService ventanaCargaService;
    private final FileStorageService fileStorageService;
    private final CreffosConsolidationService creffosConsolidationService;
    private final ConsolidacionConciliacionReportService consolidacionConciliacionReportService;
    private final NotificacionConsolidacionService notificacionConsolidacionService;
    private final ParametroUnicoService parametroUnicoService;
    private final EntityManager entityManager;

    private static final long DEFAULT_POST_CLOSE_DELAY_HOURS = 1;
    private static final long DEFAULT_MAX_POST_CLOSE_DAYS = 5;
    private static final int DEFAULT_AUTO_SIZE_LIMIT = 50;
    private static final String ADMIN_CONSOLIDACION_BYPASS_WINDOW_KEY = "APP_ADMIN_CONSOLIDACION_BYPASS_WINDOW";

    public ConsolidacionPeriodoExecutor(SiproDetalleCargaPlanillasRepository planillaRepository,
                                        SiproDetalleArchivoValidacionRepository validacionRepository,
                                        ClienteLzRepository clienteLzRepository,
                                        SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository,
                                        SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository,
                                        SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository,
                                        ProductoRepository productoRepository,
                                        VentanaCargaService ventanaCargaService,
                                        FileStorageService fileStorageService,
                                        CreffosConsolidationService creffosConsolidationService,
                                        ConsolidacionConciliacionReportService consolidacionConciliacionReportService,
                                        NotificacionConsolidacionService notificacionConsolidacionService,
                                        ParametroUnicoService parametroUnicoService,
                                        EntityManager entityManager,
                                        Environment environment) {
        this.planillaRepository = planillaRepository;
        this.validacionRepository = validacionRepository;
        this.clienteLzRepository = clienteLzRepository;
        this.consolidacionRepository = consolidacionRepository;
        this.consolidacionArchivoRepository = consolidacionArchivoRepository;
        this.consolidadoRegistroRepository = consolidadoRegistroRepository;
        this.productoRepository = productoRepository;
        this.ventanaCargaService = ventanaCargaService;
        this.fileStorageService = fileStorageService;
        this.creffosConsolidationService = creffosConsolidationService;
        this.consolidacionConciliacionReportService = consolidacionConciliacionReportService;
        this.notificacionConsolidacionService = notificacionConsolidacionService;
        this.parametroUnicoService = parametroUnicoService;
        this.entityManager = entityManager;
        this.environment = environment;
    }

    private final Environment environment;

    /**
     * Consolida un periodo solo cuando la ventana y el estado operativo permiten hacerlo.
     */
    @Transactional
    public boolean consolidarPeriodoSiCorresponde(LocalDate periodoValoracion, Long usuarioEjecutorId, String observacion) {
        return consolidarPeriodo(periodoValoracion, usuarioEjecutorId, observacion, false);
    }

    /**
     * Consolida un periodo de forma manual. Valida el mismo rango post-cierre que la automática,
     * salvo cuando el bypass temporal de DEV está activo.
     */
    @Transactional
    public boolean consolidarPeriodoForzado(LocalDate periodoValoracion, Long usuarioEjecutorId, String observacion) {
        return consolidarPeriodo(periodoValoracion, usuarioEjecutorId, observacion, true);
    }

    private boolean consolidarPeriodo(LocalDate periodoValoracion,
                                      Long usuarioEjecutorId,
                                      String observacion,
                                      boolean ejecucionManual) {
        Optional<VentanaCargaService.VentanaCalculada> ventanaOpt = ventanaCargaService.obtenerVentana(periodoValoracion);
        if (ventanaOpt.isEmpty()) {
            return registrarNoConsolidacion(periodoValoracion,
                    "no existe configuración de ventana efectiva para el periodo");
        }

        VentanaCargaService.VentanaCalculada ventana = ventanaOpt.get();
        ConsolidacionRangoPostCierre rangoPostCierre = calcularRangoPostCierre(ventana);
        OffsetDateTime ahora = OffsetDateTime.now();
        boolean bypassVentana = ejecucionManual && isBypassVentanaManualActivo();

        if (ahora.isBefore(rangoPostCierre.momentoMinimoConsolidacion()) && !bypassVentana) {
            return registrarNoConsolidacion(periodoValoracion,
                    "la ventana aún no cerró con el delay configurado; disponible desde "
                            + rangoPostCierre.momentoMinimoConsolidacion());
        }

        if (ahora.isAfter(rangoPostCierre.momentoMaximoConsolidacion()) && !bypassVentana) {
            return registrarNoConsolidacion(periodoValoracion,
                    "ya superó el límite máximo de reintento post-cierre "
                            + rangoPostCierre.momentoMaximoConsolidacion());
        }

        if (consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(periodoValoracion, ESTADOS_EN_CURSO)) {
            return registrarNoConsolidacion(periodoValoracion,
                    "ya existe una consolidación registrada en estado INICIADO/EN_PROCESO");
        }

        if (consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(periodoValoracion, ESTADOS_EXITOSOS)) {
            return registrarNoConsolidacion(periodoValoracion,
                    "ya existe una consolidación registrada en estado COMPLETADO/COMPLETADO_CON_ADVERTENCIAS");
        }

        List<SiproDetalleCargaPlanillas> planillasAprobadas = obtenerPlanillasAprobadasSegmentoUno(periodoValoracion);
        if (planillasAprobadas.isEmpty()) {
            return registrarNoConsolidacion(periodoValoracion,
                    "no hay planillas aprobadas del segmento 1 con XLSX disponible o certificación sin datos para consolidar");
        }

        if (ejecucionManual) {
            logger.info("Consolidación manual iniciada para periodo {} por usuario {}", periodoValoracion, usuarioEjecutorId);
            if (bypassVentana) {
                logger.info("Periodo {}: bypass DEV activo para consolidación manual; se omite la validación de ventana.",
                        periodoValoracion);
            }
        }

        return ejecutarConsolidacion(periodoValoracion, planillasAprobadas, ventana, usuarioEjecutorId, observacion);
    }

        private List<SiproDetalleCargaPlanillas> obtenerPlanillasAprobadasSegmentoUno(LocalDate periodoValoracion) {
        return planillaRepository
                .findPlanillasAprobadasByFechaCorteAndSegmentoId(periodoValoracion, SEGMENTO_COLGAAP_MODIFICADO_ID)
                .stream()
                .filter(this::esPlanillaConsolidable)
                .sorted(Comparator.comparing(SiproDetalleCargaPlanillas::getId))
                .collect(Collectors.toList());
    }

    private boolean registrarNoConsolidacion(LocalDate periodoValoracion, String razon) {
        logger.info("Periodo {}: no consolida porque {}.", periodoValoracion, razon);
        return false;
    }

    private boolean esPlanillaConsolidable(SiproDetalleCargaPlanillas planilla) {
        if (planilla == null) {
            return false;
        }

        if (Boolean.TRUE.equals(planilla.getNoReportaDatos())) {
            return true;
        }

        String rutaArchivo = planilla.getRutaArchivoAlmacenamiento();
        if (rutaArchivo == null || rutaArchivo.isBlank()) {
            return false;
        }

        return esRutaXlsx(rutaArchivo) || esRutaXlsx(planilla.getNombreArchivoFuente());
    }

    private boolean esRutaXlsx(String rutaArchivo) {
        return rutaArchivo != null && rutaArchivo.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    private boolean isBypassVentanaManualActivo() {
        return environment.matchesProfiles("dev")
                && Boolean.parseBoolean(parametroUnicoService.getString(ADMIN_CONSOLIDACION_BYPASS_WINDOW_KEY, "false"));
    }

        private ConsolidacionRangoPostCierre calcularRangoPostCierre(VentanaCargaService.VentanaCalculada ventana) {
        long postCloseHours = parametroUnicoService.getLong(
            "APP_CONSOLIDACION_POST_CLOSE_DELAY_HOURS",
            DEFAULT_POST_CLOSE_DELAY_HOURS);
        long maxPostDays = parametroUnicoService.getLong(
            "APP_CONSOLIDACION_MAX_POST_CLOSE_DAYS",
            DEFAULT_MAX_POST_CLOSE_DAYS);
        OffsetDateTime cierreEfectivo = ventana.getFechaHoraCierreOffset();

        return new ConsolidacionRangoPostCierre(
            cierreEfectivo.plusHours(postCloseHours),
            cierreEfectivo.plusDays(maxPostDays));
        }

    private boolean ejecutarConsolidacion(LocalDate periodoValoracion,
                                          List<SiproDetalleCargaPlanillas> planillasAprobadas,
                                          VentanaCargaService.VentanaCalculada ventana,
                                          Long usuarioEjecutorId,
                                          String observacion) {
        eliminarConsolidacionesPrevias(periodoValoracion);

        OffsetDateTime ahora = OffsetDateTime.now();
        Long usuarioAuditoria = usuarioEjecutorId != null ? usuarioEjecutorId : 1L;
        SiproDetalleConsolidacionesPlanillas cabecera = crearCabeceraInicial(periodoValoracion, ventana, usuarioAuditoria,
                observacion, ahora);
        consolidacionRepository.save(cabecera);

        try (ConsolidatedExcelWriter excelWriter = new ConsolidatedExcelWriter()) {
            Map<Long, SiproDetalleArchivoValidacion> validacionesPorCarga = cargarValidaciones(planillasAprobadas);

            cabecera.setEstadoConsolidacion(ESTADO_EN_PROCESO);
            cabecera.setModificadoPorId(usuarioAuditoria);
            cabecera.setModificadoEn(ahora);
            consolidacionRepository.save(cabecera);

            int cantidadRegistros = 0;
            List<String> nombresArchivos = new ArrayList<>();

            for (SiproDetalleCargaPlanillas planilla : planillasAprobadas) {
                SiproDetalleArchivoValidacion validacion = validacionesPorCarga.get(planilla.getId());
                SiproDetalleConsolidacionArchivo archivoConsolidado = construirArchivoConsolidado(cabecera, planilla, validacion);
                consolidacionArchivoRepository.save(archivoConsolidado);

                int registrosArchivo = 0;
                if (Boolean.TRUE.equals(planilla.getNoReportaDatos())) {
                    logger.info("Periodo {} archivo {}: se registra como aprobación sin datos con 0 registros.",
                            periodoValoracion, archivoConsolidado.getNombreArchivo());
                } else {
                    registrosArchivo = procesarArchivoConDatos(
                            periodoValoracion,
                            cabecera,
                            archivoConsolidado,
                            planilla,
                            excelWriter);
                }

                archivoConsolidado.setCantidadRegistrosArchivo(registrosArchivo);
                consolidacionArchivoRepository.save(archivoConsolidado);

                cantidadRegistros += registrosArchivo;
                nombresArchivos.add(archivoConsolidado.getNombreArchivo());
            }

            String advertenciaExcelConsolidado = guardarExcelConsolidado(periodoValoracion, excelWriter);
            List<String> advertenciasPostProceso = new ArrayList<>(
                    ejecutarPostProcesamiento(periodoValoracion, cabecera.getIdConsolidacion()));
            if (advertenciaExcelConsolidado != null && !advertenciaExcelConsolidado.isBlank()) {
                advertenciasPostProceso.add(advertenciaExcelConsolidado);
            }

            OffsetDateTime fechaFin = OffsetDateTime.now();
            cabecera.setEstadoConsolidacion(advertenciasPostProceso.isEmpty()
                    ? ESTADO_COMPLETADO
                    : ESTADO_COMPLETADO_CON_ADVERTENCIAS);
            cabecera.setFechaHoraFin(fechaFin);
            cabecera.setCantidadArchivosConsolidados(planillasAprobadas.size());
            cabecera.setCantidadRegistrosConsolidados(cantidadRegistros);
            cabecera.setNombresArchivosConsolidados(String.join(", ", nombresArchivos));
            cabecera.setDuracionMinutos(calcularDuracionMinutos(cabecera.getFechaHoraInicio(), fechaFin));
            cabecera.setObservacion(construirObservacion(cabecera.getObservacion(), advertenciasPostProceso));
            cabecera.setModificadoPorId(usuarioAuditoria);
            cabecera.setModificadoEn(fechaFin);
            consolidacionRepository.save(cabecera);

                MailTemplateNotificationService.DeliveryResult resultadoCorreo =
                    notificacionConsolidacionService.enviarConfirmacion(cabecera.getIdConsolidacion());
                persistirAdvertenciaCorreoSiAplica(cabecera, usuarioAuditoria, fechaFin, resultadoCorreo);

            logger.info("Consolidación de periodo {} finalizada con estado {} ({} archivos, {} registros, advertencias={}).",
                    periodoValoracion,
                    cabecera.getEstadoConsolidacion(),
                    planillasAprobadas.size(),
                    cantidadRegistros,
                    advertenciasPostProceso.size());
            return true;
        } catch (Exception e) {
            OffsetDateTime fechaError = OffsetDateTime.now();
            cabecera.setEstadoConsolidacion(ESTADO_ERROR);
            cabecera.setFechaHoraFin(fechaError);
            cabecera.setDuracionMinutos(calcularDuracionMinutos(cabecera.getFechaHoraInicio(), fechaError));
            cabecera.setMensajeError(resumirError(e));
            cabecera.setModificadoPorId(usuarioAuditoria);
            cabecera.setModificadoEn(fechaError);
            consolidacionRepository.save(cabecera);

            try {
                MailTemplateNotificationService.DeliveryResult resultadoCorreoError =
                        notificacionConsolidacionService.notificarError(cabecera.getIdConsolidacion());
                persistirAdvertenciaCorreoSiAplica(cabecera, usuarioAuditoria, fechaError, resultadoCorreoError);
            } catch (Exception notificationEx) {
                logger.error("No se pudo procesar la notificación de error para el periodo {}: {}",
                        periodoValoracion,
                        notificationEx.getMessage(),
                        notificationEx);
            }

            throw new IllegalStateException(
                    "Error consolidando periodo " + periodoValoracion + ": " + resumirErrorUsuario(e),
                    e);
        }
    }

    private SiproDetalleConsolidacionesPlanillas crearCabeceraInicial(LocalDate periodoValoracion,
                                                                      VentanaCargaService.VentanaCalculada ventana,
                                                                      Long usuarioAuditoria,
                                                                      String observacion,
                                                                      OffsetDateTime ahora) {
        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setPeriodoValoracion(periodoValoracion);
        cabecera.setFechaHoraInicio(ahora);
        cabecera.setEstadoConsolidacion(ESTADO_INICIADO);
        cabecera.setCantidadArchivosConsolidados(0);
        cabecera.setCantidadRegistrosConsolidados(0);
        cabecera.setFuenteVentana(ventana.getTipoVentanaRespuesta());
        cabecera.setIdReglaVentana(ventana.esExcepcion() ? null : ventana.getIdReglaVentana());
        cabecera.setPeriodoExcepcionVentana(ventana.esExcepcion()
            ? firstNonNull(ventana.getPeriodoExcepcionVentana(), periodoValoracion)
            : null);
        cabecera.setFechaHoraAperturaEfectiva(ventana.getFechaHoraAperturaOffset());
        cabecera.setFechaHoraCierreEfectiva(ventana.getFechaHoraCierreOffset());
        cabecera.setCreadoPorId(usuarioAuditoria);
        cabecera.setCreadoEn(ahora);
        cabecera.setModificadoPorId(usuarioAuditoria);
        cabecera.setModificadoEn(ahora);
        cabecera.setObservacion(observacion);
        return cabecera;
    }

    private void eliminarConsolidacionesPrevias(LocalDate periodoValoracion) {
        List<SiproDetalleConsolidacionesPlanillas> consolidacionesPrevias = consolidacionRepository
            .findAllByPeriodoValoracionOrderByCreadoEnDesc(periodoValoracion)
            .stream()
            .filter(consolidacion -> !List.of(ESTADO_COMPLETADO, ESTADO_COMPLETADO_CON_ADVERTENCIAS, ESTADO_INICIADO, ESTADO_EN_PROCESO)
                .contains(consolidacion.getEstadoConsolidacion()))
            .collect(Collectors.toList());
        if (consolidacionesPrevias.isEmpty()) {
            return;
        }

        consolidacionRepository.deleteAllInBatch(consolidacionesPrevias);
        logger.info("Se eliminaron {} consolidaciones previas del periodo {} para reconstrucción limpia.",
                consolidacionesPrevias.size(), periodoValoracion);
    }

    private Map<Long, SiproDetalleArchivoValidacion> cargarValidaciones(List<SiproDetalleCargaPlanillas> planillas) {
        List<Long> idsCarga = planillas.stream()
                .map(SiproDetalleCargaPlanillas::getId)
                .toList();
        return validacionRepository.findByIdCargaPlanillaIn(idsCarga).stream()
                .collect(Collectors.toMap(
                        SiproDetalleArchivoValidacion::getIdCargaPlanilla,
                        validacion -> validacion,
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    private SiproDetalleConsolidacionArchivo construirArchivoConsolidado(SiproDetalleConsolidacionesPlanillas cabecera,
                                                                         SiproDetalleCargaPlanillas planilla,
                                                                         SiproDetalleArchivoValidacion validacion) {
        boolean sinDatos = Boolean.TRUE.equals(planilla.getNoReportaDatos());
        SiproDetalleConsolidacionArchivo archivo = new SiproDetalleConsolidacionArchivo();
        archivo.setIdConsolidacion(cabecera.getIdConsolidacion());
        archivo.setIdCargaPlanilla(planilla.getId());
        archivo.setIdValidacionArchivo((sinDatos ? Optional.<SiproDetalleArchivoValidacion>empty() : Optional.ofNullable(validacion))
                .map(SiproDetalleArchivoValidacion::getId)
                .map(Long::intValue)
                .orElse(null));
        archivo.setArchivoUid(planilla.getArchivoUid());
        archivo.setNombreArchivo(sinDatos ? resolverNombreVisualSinDatos(planilla) : planilla.getNombreArchivoFuente());
        archivo.setRutaArchivo(sinDatos ? null : planilla.getRutaArchivoAlmacenamiento());
        archivo.setFechaCorte(planilla.getFechaCorteInformacion());
        archivo.setIdProductoOrigen(planilla.getIdProducto());
        archivo.setProductoOrigen(planilla.getProducto());
        archivo.setIdSegmento(SEGMENTO_COLGAAP_MODIFICADO_ID.intValue());
        archivo.setSegmento(SEGMENTO_COLGAAP_MODIFICADO_NOMBRE);
        archivo.setDescripcion(planilla.getDescripcionLarga());
        archivo.setIdUsuarioCargador(planilla.getIdUsuarioCarga());
        archivo.setUsuarioCargador(planilla.getNombreUsuarioCarga());
        archivo.setIdUsuarioAprobador(planilla.getIdUsuarioAprobador());
        archivo.setUsuarioAprobador(planilla.getUsuarioAprobador());
        archivo.setCantidadRegistrosArchivo(sinDatos ? 0 : Optional.ofNullable(validacion)
                .map(SiproDetalleArchivoValidacion::getNumeroFilasDatos)
                .orElse(0));
        archivo.setFechaValidacion(Optional.ofNullable(validacion)
                .map(SiproDetalleArchivoValidacion::getFechaValidacion)
                .orElse(null));
        archivo.setCreadoEn(OffsetDateTime.now());
        return archivo;
    }

    private String resolverNombreVisualSinDatos(SiproDetalleCargaPlanillas planilla) {
        String nombreBase = productoRepository.findById(planilla.getIdProducto())
                .map(Producto::getNombreArchivoPermitido)
                .map(this::normalizarBaseDesdeMascara)
                .filter(base -> !base.isBlank())
                .orElseGet(() -> normalizarTokenProducto(planilla.getProducto()));

        LocalDate fechaCorte = planilla.getFechaCorteInformacion() != null
                ? planilla.getFechaCorteInformacion()
                : LocalDate.now();
        String fechaCompacta = String.format(Locale.ROOT, "%04d%02d%02d",
                fechaCorte.getYear(), fechaCorte.getMonthValue(), fechaCorte.getDayOfMonth());

        return nombreBase + "_" + fechaCompacta + "_SINDATOS";
    }

    private String normalizarBaseDesdeMascara(String mascara) {
        if (mascara == null || mascara.isBlank()) {
            return "";
        }
        String sinMascara = mascara.toUpperCase(Locale.ROOT)
                .replace("_AAAAMMDD", "")
                .replace("AAAAMMDD", "")
                .trim();
        return normalizarTokenProducto(sinMascara);
    }

    private String normalizarTokenProducto(String valor) {
        if (valor == null || valor.isBlank()) {
            return "PRODUCTO";
        }

        String normalized = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        return normalized.isBlank() ? "PRODUCTO" : normalized;
    }

    private int procesarArchivoConDatos(LocalDate periodoValoracion,
                                        SiproDetalleConsolidacionesPlanillas cabecera,
                                        SiproDetalleConsolidacionArchivo archivo,
                                        SiproDetalleCargaPlanillas planilla,
                                        ConsolidatedExcelWriter excelWriter) throws IOException {
        String rutaArchivo = planilla.getRutaArchivoAlmacenamiento();
        if (rutaArchivo == null || rutaArchivo.isBlank()) {
            throw new IllegalStateException("La planilla " + planilla.getId()
                    + " del periodo " + periodoValoracion + " está aprobada con datos pero no tiene ruta de almacenamiento.");
        }

        ExcelScanResult scanResult;
        try (InputStream inputStream = fileStorageService.openStream(rutaArchivo)) {
            scanResult = escanearExcel(inputStream, planilla.getNombreArchivoFuente());
        }

        Map<String, String> tipoIdPorNit = cargarTipoIdPorNit(scanResult.nits());
        int batchInsertSize = Math.max(1,
                parametroUnicoService.getInt("APP_CONSOLIDACION_BATCH_INSERT_SIZE", DEFAULT_BATCH_INSERT_SIZE));
        List<SiproDetalleConsolidadoRegistro> batch = new ArrayList<>(batchInsertSize);
        final int[] cantidadRegistros = {0};

        try (InputStream inputStream = fileStorageService.openStream(rutaArchivo)) {
            XlsxStreamingReader.readFirstSheet(inputStream, (rowNumber, rowValues) -> {
                if (rowNumber == 1 || esFilaVacia(rowValues)) {
                    return;
                }

                Map<String, String> fila = convertirFila(scanResult.columnIndexByHeader(), rowValues);
                String nit = normalizeLookupDocument(getValue(fila, "NIT"));
                String tipoId = firstNonBlank(tipoIdPorNit.get(nit), getValue(fila, "TIPO_ID"));

                batch.add(construirRegistro(cabecera, archivo, planilla, fila, tipoId));
                excelWriter.appendRow(construirFilaConsolidado(fila, planilla, tipoId));
                cantidadRegistros[0]++;

                if (batch.size() >= batchInsertSize) {
                    persistirBatch(batch);
                }
            });
        }

        persistirBatch(batch);
        logger.info("Periodo {} archivo {}: se consolidaron {} registros de datos.",
                periodoValoracion, planilla.getNombreArchivoFuente(), cantidadRegistros[0]);
        return cantidadRegistros[0];
    }

    private SiproDetalleConsolidadoRegistro construirRegistro(SiproDetalleConsolidacionesPlanillas cabecera,
                                                              SiproDetalleConsolidacionArchivo archivo,
                                                              SiproDetalleCargaPlanillas planilla,
                                                              Map<String, String> fila,
                                                              String tipoId) {
        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setIdConsolidacion(cabecera.getIdConsolidacion());
        registro.setIdConsolidacionArchivo(archivo.getIdConsolidacionArchivo());
        registro.setIdCargaPlanilla(planilla.getId());
        registro.setTipoId(blankToNull(tipoId));
        registro.setIdProductoOrigen(planilla.getIdProducto());
        registro.setProductoOrigen(planilla.getProducto());
        registro.setIdSegmento(SEGMENTO_COLGAAP_MODIFICADO_ID.intValue());
        registro.setSegmento(SEGMENTO_COLGAAP_MODIFICADO_NOMBRE);
        registro.setFechaCorte(planilla.getFechaCorteInformacion());
        registro.setDescripcion(planilla.getDescripcionLarga());
        registro.setIdUsuarioCargador(planilla.getIdUsuarioCarga());
        registro.setUsuarioCargador(planilla.getNombreUsuarioCarga());
        registro.setIdUsuarioAprobador(planilla.getIdUsuarioAprobador());
        registro.setUsuarioAprobador(planilla.getUsuarioAprobador());
        registro.setNit(parseLong(getValue(fila, "NIT")));
        registro.setOficina(parseLong(getValue(fila, "OFICINA")));
        registro.setDocumento(parseLong(getValue(fila, "DOCUMENTO")));
        registro.setMoneda(parseInteger(getValue(fila, "MONEDA")));
        registro.setModalidad(getValue(fila, "MODALIDAD"));
        registro.setAnoiniobl(parseInteger(getValue(fila, "ANOINIOBL")));
        registro.setMesiniobl(parseInteger(getValue(fila, "MESINIOBL")));
        registro.setDiainiobl(parseInteger(getValue(fila, "DIAINIOBL")));
        registro.setAnovcto(parseInteger(getValue(fila, "ANOVCTO")));
        registro.setMesvcto(parseInteger(getValue(fila, "MESVCTO")));
        registro.setDiavcto(parseInteger(getValue(fila, "DIAVCTO")));
        registro.setAnovctofin(parseInteger(getValue(fila, "ANOVCTOFIN")));
        registro.setMesvctofin(parseInteger(getValue(fila, "MESVCTOFIN")));
        registro.setDiavctofin(parseInteger(getValue(fila, "DIAVCTOFIN")));
        registro.setCtapuc(parseLong(getValue(fila, "CTAPUC")));
        registro.setVlriniobl(parseDecimal(getValue(fila, "VLRINIOBL")));
        registro.setSaldo(parseDecimal(getValue(fila, "SALDO")));
        registro.setSdootrctas(parseDecimal(getValue(fila, "SDOOTRCTAS")));
        registro.setIntereses(parseDecimal(getValue(fila, "INTERESES")));
        registro.setSdovencido(parseDecimal(getValue(fila, "SDOVENCIDO")));
        registro.setIntctasord(parseDecimal(getValue(fila, "INTCTASORD")));
        registro.setUsuario(getValue(fila, "USUARIO"));
        registro.setProducto(getValue(fila, "PRODUCTO"));
        registro.setClasificacion(parseShort(getValue(fila, "CLASIFICACION")));
        return registro;
    }

    private ExcelScanResult escanearExcel(InputStream inputStream, String nombreArchivo) throws IOException {
        List<String> headersOriginales = new ArrayList<>();
        Map<String, Integer> columnIndexByHeader = new LinkedHashMap<>();
        Set<String> nits = new LinkedHashSet<>();

        XlsxStreamingReader.readFirstSheet(inputStream, (rowNumber, rowValues) -> {
            if (rowNumber == 1) {
                for (int index = 0; index < rowValues.size(); index++) {
                    String headerOriginal = rowValues.get(index) == null ? "" : rowValues.get(index).trim();
                    if (headerOriginal.isBlank()) {
                        continue;
                    }
                    headersOriginales.add(headerOriginal);
                    columnIndexByHeader.put(normalizeHeader(headerOriginal), index);
                }
                validarHeadersRequeridos(columnIndexByHeader.keySet(), nombreArchivo);
                return;
            }

            if (esFilaVacia(rowValues)) {
                return;
            }

            String nit = obtenerValorFila(rowValues, columnIndexByHeader, "NIT");
            if (!nit.isBlank()) {
                nits.add(normalizeLookupDocument(nit));
            }
        });

        return new ExcelScanResult(headersOriginales, columnIndexByHeader, nits);
    }

    private void validarHeadersRequeridos(Collection<String> headers, String nombreArchivo) {
        List<String> faltantes = HEADERS_ENTRADA_PLANILLA.stream()
                .filter(header -> !headers.contains(header))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("El archivo " + nombreArchivo
                    + " no contiene todas las columnas requeridas para consolidación. Faltan: "
                    + String.join(", ", faltantes));
        }
    }

    private Map<String, String> cargarTipoIdPorNit(Set<String> nits) {
        if (nits == null || nits.isEmpty()) {
            return Map.of();
        }

        int chunkSize = Math.max(1,
                parametroUnicoService.getInt("APP_CONSOLIDACION_LZ_LOOKUP_CHUNK_SIZE", DEFAULT_LZ_LOOKUP_CHUNK_SIZE));
        List<String> nitsNormalizados = nits.stream()
                .map(this::normalizeLookupDocument)
                .filter(nit -> nit != null && !nit.isBlank())
                .distinct()
                .toList();

        Map<String, String> tipoIdPorNit = new LinkedHashMap<>();
        for (int start = 0; start < nitsNormalizados.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, nitsNormalizados.size());
            List<String> lote = nitsNormalizados.subList(start, end);
            for (ClienteLzRepository.DocumentoTipoIdProjection projection : clienteLzRepository.findLatestTipoIdByNumeroIdIn(lote)) {
                String nit = normalizeLookupDocument(projection.getNumeroId());
                if (!nit.isBlank()) {
                    tipoIdPorNit.putIfAbsent(nit, blankToNull(projection.getTipoId()));
                }
            }
        }

        logger.info("Cruce TIPO_ID resuelto para {} de {} NITs consultados en LZ local.",
                tipoIdPorNit.size(), nitsNormalizados.size());
        return tipoIdPorNit;
    }

    private Map<String, String> convertirFila(Map<String, Integer> columnIndexByHeader, List<String> rowValues) {
        Map<String, String> fila = new LinkedHashMap<>();
        for (String header : columnIndexByHeader.keySet()) {
            fila.put(header, obtenerValorFila(rowValues, columnIndexByHeader, header));
        }
        return fila;
    }

    private Map<String, String> construirFilaConsolidado(Map<String, String> fila,
                                                          SiproDetalleCargaPlanillas planilla,
                                                          String tipoId) {
        Map<String, String> filaConsolidado = new LinkedHashMap<>(fila);
        filaConsolidado.put("TIPO_ID", firstNonBlank(tipoId, getValue(fila, "TIPO_ID")));
        filaConsolidado.put("PRODUCTO_ORIGEN", firstNonBlank(planilla.getProducto(), ""));
        filaConsolidado.put("SEGMENTO", firstNonBlank(planilla.getSegmento(), ""));
        filaConsolidado.put("FECHA_CORTE", planilla.getFechaCorteInformacion() == null
                ? ""
                : planilla.getFechaCorteInformacion().toString());
        filaConsolidado.put("DESCRIPCION", firstNonBlank(planilla.getDescripcionLarga(), ""));
        filaConsolidado.put("USUARIO_CARGADOR", firstNonBlank(planilla.getNombreUsuarioCarga(), ""));
        filaConsolidado.put("USUARIO_APROBADOR", firstNonBlank(planilla.getUsuarioAprobador(), ""));
        return filaConsolidado;
    }

    private String obtenerValorFila(List<String> rowValues, Map<String, Integer> columnIndexByHeader, String header) {
        Integer index = columnIndexByHeader.get(header);
        if (index == null || index < 0 || index >= rowValues.size()) {
            return "";
        }
        String value = rowValues.get(index);
        return value == null ? "" : value.trim();
    }

    private boolean esFilaVacia(List<String> rowValues) {
        return rowValues == null || rowValues.stream().allMatch(value -> value == null || value.isBlank());
    }

    private void persistirBatch(List<SiproDetalleConsolidadoRegistro> batch) {
        if (batch.isEmpty()) {
            return;
        }
        consolidadoRegistroRepository.saveAll(batch);
        consolidadoRegistroRepository.flush();
        entityManager.clear();
        batch.clear();
    }

    /**
     * Guarda el Excel consolidado en el storage interno (con nombre por fecha, para el histórico del
     * panel admin) y lo publica en la misma ruta compartida que usa CREFFSOS ({@code CREFFSOS_RUTA_SALIDA})
     * con nombre fijo ({@link #CONSOLIDADO_NOMBRE_ARCHIVO_COMPARTIDO}): cada consolidación nueva
     * reemplaza ahí a la del periodo anterior, igual que ya ocurre con CREFFSOS.
     *
     * @return advertencia si falló la copia a la ruta compartida, o {@code null} si todo salió bien.
     */
    private String guardarExcelConsolidado(LocalDate periodoValoracion,
                                           ConsolidatedExcelWriter excelWriter) throws IOException {
        byte[] contenidoExcel = excelWriter.toByteArray();
        String rutaExcel = construirRutaExcelConsolidado(periodoValoracion);
        fileStorageService.storeBytes(contenidoExcel, rutaExcel, CONTENT_TYPE_XLSX);
        logger.info("Excel consolidado generado para periodo {} en {}", periodoValoracion, rutaExcel);

        return publicarConsolidadoEnRutaCompartida(contenidoExcel);
    }

    private String publicarConsolidadoEnRutaCompartida(byte[] contenidoExcel) {
        String outputDir = parametroUnicoService.getString("CREFFSOS_RUTA_SALIDA", "");
        if (outputDir == null || outputDir.isBlank()) {
            return null;
        }

        try {
            Path targetDir = Path.of(outputDir.trim());
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(CONSOLIDADO_NOMBRE_ARCHIVO_COMPARTIDO);
            Files.write(targetFile,
                    contenidoExcel,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            logger.info("Excel consolidado publicado en ruta compartida: {}", targetFile);
            return null;
        } catch (Exception ex) {
            logger.warn("No se pudo copiar el Excel consolidado a ruta compartida: {}. Motivo: {}",
                    outputDir.trim(), ex.getMessage());
            return "Excel consolidado generado pero no copiado a red";
        }
    }

    private List<String> ejecutarPostProcesamiento(LocalDate periodoValoracion, Long idConsolidacion) {
        List<String> advertencias = new ArrayList<>();

        try {
            CreffosConsolidationService.PublicationResult publicationResult =
                    creffosConsolidationService.reconstruirCompleto(periodoValoracion);
            if (publicationResult.sharedCopyWarning() != null && !publicationResult.sharedCopyWarning().isBlank()) {
                advertencias.add("CREFFSOS generado pero no copiado a red");
            }
        } catch (Exception ex) {
            logger.error("No se pudo publicar CREFFSOS para el periodo {}: {}", periodoValoracion, ex.getMessage(), ex);
            advertencias.add("CREFFSOS generado pero no copiado a red");
        }

        try {
            consolidacionConciliacionReportService.generar(idConsolidacion);
        } catch (Exception ex) {
            logger.error("No se pudo generar el reporte de conciliación para el periodo {}: {}",
                    periodoValoracion, ex.getMessage(), ex);
            advertencias.add("Reporte de conciliación no generado: " + resumirMensaje(ex));
        }
        return advertencias;
    }

    private String construirObservacion(String observacionBase, List<String> advertencias) {
        List<String> partes = new ArrayList<>();
        String observacionNormalizada = blankToNull(observacionBase);
        if (observacionNormalizada != null) {
            partes.add(observacionNormalizada);
        }
        partes.addAll(advertencias.stream()
                .map(this::blankToNull)
                .filter(Objects::nonNull)
                .toList());
        return partes.isEmpty() ? null : String.join(" | ", partes);
    }

    private void persistirAdvertenciaCorreoSiAplica(SiproDetalleConsolidacionesPlanillas cabecera,
                                                    Long usuarioAuditoria,
                                                    OffsetDateTime fechaEvento,
                                                    MailTemplateNotificationService.DeliveryResult resultadoCorreo) {
        if (cabecera == null || resultadoCorreo == null) {
            return;
        }

        if (resultadoCorreo.status() != MailTemplateNotificationService.DeliveryStatus.FAILED) {
            return;
        }

        String detalle = blankToNull(resultadoCorreo.detail());
        String advertencia = detalle == null ? "Correo no enviado" : "Correo no enviado: " + detalle;
        cabecera.setObservacion(construirObservacion(cabecera.getObservacion(), List.of(advertencia)));
        cabecera.setModificadoPorId(usuarioAuditoria);
        cabecera.setModificadoEn(fechaEvento != null ? fechaEvento : OffsetDateTime.now());
        consolidacionRepository.save(cabecera);
    }

    private String resumirMensaje(Exception ex) {
        String mensaje = ex.getMessage();
        return mensaje == null || mensaje.isBlank() ? ex.getClass().getSimpleName() : mensaje;
    }

    private String construirRutaExcelConsolidado(LocalDate periodoValoracion) {
        String fecha = periodoValoracion.format(FECHA_FMT);
        return CONSOLIDADOS_PREFIX + fecha + "/" + construirNombreArchivoConsolidado(periodoValoracion);
    }

    private String construirNombreArchivoConsolidado(LocalDate periodoValoracion) {
        return "CONSOLIDADO_" + periodoValoracion.format(FECHA_FMT) + ".xlsx";
    }

    private void setCellValue(Cell cell, String valor) {
        if (valor == null || valor.isBlank()) {
            cell.setBlank();
            return;
        }
        try {
            cell.setCellValue(Double.parseDouble(normalizeNumber(valor)));
            return;
        } catch (NumberFormatException ignored) {
            // Se mantiene como texto si no es numérico.
        }
        cell.setCellValue(valor);
    }

    private BigDecimal calcularDuracionMinutos(OffsetDateTime inicio, OffsetDateTime fin) {
        if (inicio == null || fin == null) {
            return null;
        }
        long segundos = ChronoUnit.SECONDS.between(inicio, fin);
        return BigDecimal.valueOf(segundos)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private record ConsolidacionRangoPostCierre(OffsetDateTime momentoMinimoConsolidacion,
                                                OffsetDateTime momentoMaximoConsolidacion) {
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
    }

    private String normalizeLookupDocument(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Long longValue = parseLong(value);
        return longValue != null ? longValue.toString() : value.trim();
    }

    private String getValue(Map<String, String> fila, String key) {
        return fila.getOrDefault(key, "").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = blankToNull(first);
        return normalizedFirst != null ? normalizedFirst : blankToNull(second);
    }

    private String resumirError(Exception e) {
        Throwable raiz = e;
        while (raiz.getCause() != null) {
            raiz = raiz.getCause();
        }
        String mensaje = raiz.getMessage() != null ? raiz.getMessage() : e.getMessage();
        return raiz.getClass().getSimpleName() + ": " + (mensaje != null ? mensaje : "sin detalle");
    }

    private String resumirErrorUsuario(Exception e) {
        Throwable raiz = e;
        while (raiz.getCause() != null) {
            raiz = raiz.getCause();
        }

        String mensaje = raiz.getMessage() != null ? raiz.getMessage() : e.getMessage();
        if (mensaje == null || mensaje.isBlank()) {
            return "falló la persistencia del consolidado sin detalle adicional";
        }

        if (mensaje.contains("ERROR: entero fuera de rango")) {
            return "ERROR: entero fuera de rango al guardar el consolidado. Revise la alineación BIGINT/NUMERIC de sipro_detalle_consolidado_registros.";
        }

        int sqlMarker = mensaje.indexOf("; SQL");
        if (sqlMarker > 0) {
            mensaje = mensaje.substring(0, sqlMarker);
        }

        mensaje = mensaje.replaceAll("\\s+", " ").trim();
        return mensaje;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalizeNumber(value)).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalizeNumber(value)).longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
    }

    private Short parseShort(String value) {
        Integer integer = parseInteger(value);
        return integer == null ? null : integer.shortValue();
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalizeNumber(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeNumber(String value) {
        String cleaned = value.trim().replace(" ", "");
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(",", "");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(",", ".");
        }
        return cleaned;
    }

    private record ExcelScanResult(List<String> headersOriginales,
                                   Map<String, Integer> columnIndexByHeader,
                                   Set<String> nits) {
    }

    private final class ConsolidatedExcelWriter implements AutoCloseable {

        private final SXSSFWorkbook workbook;
        private final Sheet sheet;
        private final int maxAutoSize;
        private int currentRowIndex = 1;

        private ConsolidatedExcelWriter() {
            this.workbook = new SXSSFWorkbook(200);
            this.sheet = workbook.createSheet("CONSOLIDADO");
            if (sheet instanceof SXSSFSheet streamingSheet) {
                streamingSheet.trackAllColumnsForAutoSizing();
            }
            this.maxAutoSize = Math.min(
                    HEADERS_CONSOLIDADO_SALIDA.size(),
                    parametroUnicoService.getInt("AUTO_SIZE_LIMIT", DEFAULT_AUTO_SIZE_LIMIT));
            crearHeader();
        }

        private void crearHeader() {
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < HEADERS_CONSOLIDADO_SALIDA.size(); index++) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(HEADERS_CONSOLIDADO_SALIDA.get(index));
                cell.setCellStyle(headerStyle);
            }
        }

        private void appendRow(Map<String, String> fila) {
            Row row = sheet.createRow(currentRowIndex++);
            for (int columnIndex = 0; columnIndex < HEADERS_CONSOLIDADO_SALIDA.size(); columnIndex++) {
                String header = HEADERS_CONSOLIDADO_SALIDA.get(columnIndex);
                Cell cell = row.createCell(columnIndex);
                setCellValue(cell, fila.getOrDefault(header, ""));
            }
        }

        private byte[] toByteArray() throws IOException {
            for (int index = 0; index < maxAutoSize; index++) {
                sheet.autoSizeColumn(index);
            }

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                workbook.write(outputStream);
                return outputStream.toByteArray();
            }
        }

        @Override
        public void close() throws IOException {
            workbook.dispose();
            workbook.close();
        }
    }
}