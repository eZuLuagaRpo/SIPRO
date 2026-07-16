package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.AdminDashboardResponse;
import com.bancolombia.sipro.validations.domain.model.Producto;
import com.bancolombia.sipro.validations.application.dto.ConsolidacionManualStatusResponse;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionArchivo;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.infrastructure.config.AdminPanelProperties;
import com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionArchivoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Construye la data operativa requerida por la pantalla de administración.
 */
@Service
public class AdminDashboardService {

    private static final String ADMIN_DASHBOARD_DUMMY_DATA_ENABLED_KEY =
            "APP_ADMIN_DASHBOARD_DUMMY_DATA_ENABLED";
    private static final List<String> ESTADOS_CONSOLIDACION_EXITOSA = List.of("COMPLETADO", "COMPLETADO_CON_ADVERTENCIAS");
    private static final Long SEGMENTO_COLGAAP_MODIFICADO_ID = 1L;
    private static final String ESTADO_VENTANA_SIN_CONFIGURACION = "SIN_CONFIGURACION";
    private static final String ESTADO_VENTANA_ABIERTA = "ABIERTA";
    private static final String ESTADO_VENTANA_EN_RANGO = "CERRADA_EN_RANGO";
    private static final String ESTADO_VENTANA_EXPIRADA = "EXPIRADA";
        private static final String ESTADO_DESCARTADO_SIN_REGISTRO = "SIN_REGISTRO_BD";
        private static final String ESTADO_DESCARTADO_FUERA_SEGMENTO = "FUERA_SEGMENTO";
        private static final String ESTADO_DESCARTADO_NO_XLSX = "NO_XLSX";
        private static final String ESTADO_DESCARTADO_YA_CONSOLIDADO = "YA_CONSOLIDADO";
        private static final String ESTADO_PANEL_PENDIENTE = "PENDIENTE";
        private static final String ESTADO_PANEL_RECHAZADO = "RECHAZADO";

    private static final String[] MESES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    private final SiproDetalleCargaPlanillasRepository planillaRepository;
    private final SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;
    private final SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository;
        private final ProductoRepository productoRepository;
    private final ConsolidacionManualAsyncService consolidacionManualAsyncService;
        private final ConsolidacionPlanillasService consolidacionPlanillasService;
        private final FileStorageService fileStorageService;
        private final AdminPanelProperties adminPanelProperties;
        private final ObjectProvider<AdminDashboardDevSeedService> adminDashboardDevSeedServiceProvider;
        private final ParametroUnicoService parametroUnicoService;

    public AdminDashboardService(
            SiproDetalleCargaPlanillasRepository planillaRepository,
            SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository,
            SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository,
                        ProductoRepository productoRepository,
                        ConsolidacionManualAsyncService consolidacionManualAsyncService,
                        ConsolidacionPlanillasService consolidacionPlanillasService,
                        FileStorageService fileStorageService,
                        AdminPanelProperties adminPanelProperties,
                        ObjectProvider<AdminDashboardDevSeedService> adminDashboardDevSeedServiceProvider,
                        ParametroUnicoService parametroUnicoService) {
        this.planillaRepository = planillaRepository;
        this.consolidacionRepository = consolidacionRepository;
        this.consolidacionArchivoRepository = consolidacionArchivoRepository;
                this.productoRepository = productoRepository;
                this.consolidacionManualAsyncService = consolidacionManualAsyncService;
                this.consolidacionPlanillasService = consolidacionPlanillasService;
                this.fileStorageService = fileStorageService;
                this.adminPanelProperties = adminPanelProperties;
                this.adminDashboardDevSeedServiceProvider = adminDashboardDevSeedServiceProvider;
                this.parametroUnicoService = parametroUnicoService;
    }

    public AdminDashboardResponse obtenerDashboard(LocalDate periodoSolicitado) {
                AdminDashboardDevSeedService dummySeedService = adminDashboardDevSeedServiceProvider.getIfAvailable();
                boolean dummyDataEnabled = isDummyDataEnabled();
                if (dummySeedService != null) {
                        if (dummyDataEnabled) {
                                dummySeedService.ensureDemoData();
                        } else {
                                dummySeedService.purgeDemoData();
                        }
                }

        List<LocalDate> periodosDisponibles = planillaRepository
                .findDistinctFechasCorteInformacionAprobadasBySegmentoId(SEGMENTO_COLGAAP_MODIFICADO_ID)
                .stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        Set<LocalDate> periodosConsolidado = consolidacionRepository
                .findDistinctPeriodoValoracionByEstadoConsolidacionIn(ESTADOS_CONSOLIDACION_EXITOSA)
                .stream()
                .collect(Collectors.toSet());

                if (dummyDataEnabled && dummySeedService != null) {
                        periodosDisponibles = dummySeedService.completarPeriodosDisponibles(periodosDisponibles);
                }

        LocalDate periodoSeleccionado = resolverPeriodo(periodoSolicitado, periodosDisponibles);
        ConsolidacionManualStatusResponse estado = periodoSeleccionado != null
                ? consolidacionManualAsyncService.obtenerEstado(periodoSeleccionado).orElse(null)
                : null;
        SiproDetalleConsolidacionesPlanillas ultimaConsolidacionExitosa = periodoSeleccionado != null
                ? obtenerUltimaConsolidacionExitosa(periodoSeleccionado).orElse(null)
                : null;
        Set<Long> idsCargaConsolidados = obtenerIdsCargaConsolidados(ultimaConsolidacionExitosa);
        List<SiproDetalleCargaPlanillas> planillasActivasSegmento = periodoSeleccionado != null
                ? planillaRepository.findPlanillasActivasByFechaCorteAndSegmentoId(periodoSeleccionado, SEGMENTO_COLGAAP_MODIFICADO_ID)
                : List.of();
        List<SiproDetalleCargaPlanillas> planillasAprobadasSegmento = planillasActivasSegmento.stream()
                .filter(planilla -> esEstadoAprobado(planilla.getEstadoPlanilla()))
                .toList();
        Map<Long, Producto> productosPorIdPeriodo = cargarProductosPorId(planillasActivasSegmento.stream()
                .map(SiproDetalleCargaPlanillas::getIdProducto)
                .filter(idProducto -> idProducto != null)
                .collect(Collectors.toSet()));
        List<AdminDashboardResponse.ArchivoEstado> archivosNoBloqueantes = periodoSeleccionado != null
                ? construirArchivosNoBloqueantes(planillasActivasSegmento, idsCargaConsolidados, productosPorIdPeriodo)
                : List.of();
        int cantidadPendientes = contarArchivosPorEstado(archivosNoBloqueantes, ESTADO_PANEL_PENDIENTE);
        int cantidadRechazadas = contarArchivosPorEstado(archivosNoBloqueantes, ESTADO_PANEL_RECHAZADO);
        AdminDashboardResponse.EstadoPeriodoConsolidacion estadoPeriodo = periodoSeleccionado != null
                ? construirEstadoPeriodo(periodoSeleccionado, estado, ultimaConsolidacionExitosa,
                cantidadPendientes, cantidadRechazadas)
                : null;

        List<AdminDashboardResponse.ArchivoEstado> archivosAConsolidar = periodoSeleccionado != null
                ? construirArchivosAConsolidar(planillasAprobadasSegmento, idsCargaConsolidados, productosPorIdPeriodo)
                : List.of();

        List<AdminDashboardResponse.ArchivoEstado> archivosConsolidados = ultimaConsolidacionExitosa != null
                ? construirArchivosConsolidados(ultimaConsolidacionExitosa)
                : List.of();

        AdminDashboardResponse.DiagnosticoDisponibilidad diagnosticoDisponibilidad = periodoSeleccionado != null
                ? construirDiagnosticoDisponibilidad(periodoSeleccionado, planillasAprobadasSegmento,
                planillasActivasSegmento, idsCargaConsolidados)
                : null;

                if (dummyDataEnabled && dummySeedService != null && periodoSeleccionado != null) {
            archivosAConsolidar = dummySeedService.completarArchivosAConsolidar(periodoSeleccionado, archivosAConsolidar);
            archivosConsolidados = dummySeedService.completarArchivosConsolidados(periodoSeleccionado, archivosConsolidados);
        }

        List<AdminDashboardResponse.HistoricoConsolidacion> historico = consolidacionRepository
                .findTop20ByOrderByCreadoEnDesc()
                .stream()
                .map(this::mapearHistorico)
                .toList();

        List<AdminDashboardResponse.PeriodoDisponible> periodos = periodosDisponibles.stream()
                .map(periodo -> mapearPeriodo(periodo, periodosConsolidado.contains(periodo)))
                .toList();

        return new AdminDashboardResponse(
                periodos,
                periodoSeleccionado != null ? periodoSeleccionado.toString() : null,
                estado,
                estadoPeriodo,
                archivosAConsolidar,
                archivosNoBloqueantes,
                archivosConsolidados,
                diagnosticoDisponibilidad,
                                historico,
                                construirConfiguracion());
    }

        private boolean isDummyDataEnabled() {
                return Boolean.parseBoolean(
                                parametroUnicoService.getString(ADMIN_DASHBOARD_DUMMY_DATA_ENABLED_KEY, "false"));
        }

    private AdminDashboardResponse.ConfiguracionPanel construirConfiguracion() {
        AdminPanelProperties.Sql sql = adminPanelProperties.getSql();
        AdminPanelProperties.Logs logs = adminPanelProperties.getLogs();

        return new AdminDashboardResponse.ConfiguracionPanel(
                new AdminDashboardResponse.SqlConfig(
                        sql.getEnabledOperationsNormalized().stream().map(String::toUpperCase).toList(),
                        sql.getAllowedTablesNormalized().stream().toList(),
                        sql.getEffectiveMaxSelectRows(),
                        sql.isRequireWhereOnSelect(),
                        sql.isRequireWhereOnUpdate()),
                new AdminDashboardResponse.LogsConfig(
                        logs.isStreamingEnabled(),
                        logs.isDownloadEnabled(),
                        logs.getEffectiveDefaultQueryLimit(),
                        logs.getEffectiveMaxQueryLimit()));
    }

    private LocalDate resolverPeriodo(LocalDate periodoSolicitado, List<LocalDate> periodosDisponibles) {
        if (periodoSolicitado != null) {
            return periodoSolicitado;
        }

        if (!periodosDisponibles.isEmpty()) {
            return periodosDisponibles.get(0);
        }

        return consolidacionRepository.findTopByOrderByCreadoEnDesc()
                .map(SiproDetalleConsolidacionesPlanillas::getPeriodoValoracion)
                .orElse(null);
    }

        private List<AdminDashboardResponse.ArchivoEstado> construirArchivosAConsolidar(List<SiproDetalleCargaPlanillas> planillasAprobadasSegmento,
                                                                                                                                                                         Set<Long> idsCargaConsolidados,
                                                                                                                                                                         Map<Long, Producto> productosPorId) {
                return planillasAprobadasSegmento
                .stream()
                                .filter(this::esPlanillaElegibleParaPanel)
                .filter(planilla -> !idsCargaConsolidados.contains(planilla.getId()))
                .sorted(Comparator.comparing(SiproDetalleCargaPlanillas::getFechaCreacion,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(planilla -> {
                                        String estado = "APROBADO";
                                        String detalle = "Archivo aprobado y disponible para consolidar.";

                    boolean sinDatos = Boolean.TRUE.equals(planilla.getNoReportaDatos());
                                        String nombreVisual = sinDatos
                                                        ? resolverNombreVisualSinDatos(planilla, productosPorId)
                                                        : planilla.getNombreArchivoFuente();
                    if (sinDatos) {
                                                estado = "APROBADO";
                                                detalle = "Declaración aprobada sin datos para el periodo.";
                    }

                    return new AdminDashboardResponse.ArchivoEstado(
                            planilla.getId(),
                                                        nombreVisual,
                            planilla.getProducto(),
                                                        sinDatos ? 0L : planilla.getPesoArchivoFuente(),
                            null,
                            estado,
                            detalle,
                            planilla.getRutaArchivoAlmacenamiento(),
                            sinDatos,
                            sinDatos ? planilla.getDescripcionLarga() : null,
                                                        nombreVisual);
                })
                .toList();
    }

    private List<AdminDashboardResponse.ArchivoEstado> construirArchivosNoBloqueantes(
            List<SiproDetalleCargaPlanillas> planillasActivasSegmento,
            Set<Long> idsCargaConsolidados,
            Map<Long, Producto> productosPorId) {
        return planillasActivasSegmento.stream()
                .filter(planilla -> !idsCargaConsolidados.contains(planilla.getId()))
                .filter(planilla -> !esEstadoAprobado(planilla.getEstadoPlanilla()))
                .sorted(Comparator.comparing(SiproDetalleCargaPlanillas::getFechaCreacion,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(planilla -> {
                    boolean sinDatos = Boolean.TRUE.equals(planilla.getNoReportaDatos());
                    String nombreVisual = sinDatos
                            ? resolverNombreVisualSinDatos(planilla, productosPorId)
                            : planilla.getNombreArchivoFuente();

                    return new AdminDashboardResponse.ArchivoEstado(
                            planilla.getId(),
                            nombreVisual,
                            planilla.getProducto(),
                            sinDatos ? 0L : planilla.getPesoArchivoFuente(),
                            null,
                            esEstadoRechazado(planilla.getEstadoPlanilla()) ? ESTADO_PANEL_RECHAZADO : ESTADO_PANEL_PENDIENTE,
                            construirDetalleNoBloqueante(planilla),
                            planilla.getRutaArchivoAlmacenamiento(),
                            sinDatos,
                            sinDatos ? planilla.getDescripcionLarga() : null,
                            nombreVisual);
                })
                .toList();
    }

    private AdminDashboardResponse.DiagnosticoDisponibilidad construirDiagnosticoDisponibilidad(
            LocalDate periodoSeleccionado,
            List<SiproDetalleCargaPlanillas> planillasAprobadasSegmento,
            List<SiproDetalleCargaPlanillas> planillasActivasSegmento,
            Set<Long> idsCargaConsolidados) {
        List<String> archivosStorage = fileStorageService.listObjects("aprobados/" + periodoSeleccionado + "/")
                .stream()
                .filter(this::esRutaXlsx)
                .sorted()
                .toList();

        int totalElegibles = (int) planillasAprobadasSegmento.stream()
                .filter(this::esPlanillaElegibleParaPanel)
                .filter(planilla -> !idsCargaConsolidados.contains(planilla.getId()))
                .count();

        if (archivosStorage.isEmpty()) {
            return new AdminDashboardResponse.DiagnosticoDisponibilidad(0, totalElegibles, List.of());
        }

        List<SiproDetalleCargaPlanillas> planillasActivasPeriodo = planillaRepository
                .findByFechaCorteInformacionAndActivoTrueOrderByIdAsc(periodoSeleccionado);
        Set<Long> idsSegmentoUnoActivos = planillasActivasSegmento
                .stream()
                .map(SiproDetalleCargaPlanillas::getId)
                .collect(Collectors.toSet());
        Map<String, SiproDetalleCargaPlanillas> planillasPorRuta = planillasActivasPeriodo.stream()
                .filter(planilla -> planilla.getRutaArchivoAlmacenamiento() != null && !planilla.getRutaArchivoAlmacenamiento().isBlank())
                .collect(Collectors.toMap(
                        planilla -> normalizarRuta(planilla.getRutaArchivoAlmacenamiento()),
                        planilla -> planilla,
                        this::seleccionarPlanillaMasReciente,
                        LinkedHashMap::new));

        List<AdminDashboardResponse.ArchivoEstado> archivosDescartados = new ArrayList<>();
        for (String rutaStorage : archivosStorage) {
            SiproDetalleCargaPlanillas planilla = planillasPorRuta.get(normalizarRuta(rutaStorage));
            if (planilla == null) {
                archivosDescartados.add(crearArchivoDescartado(
                        null,
                        extraerNombreArchivo(rutaStorage),
                        null,
                        ESTADO_DESCARTADO_SIN_REGISTRO,
                        "Existe en aprobados, pero no tiene una planilla activa asociada en BD para este periodo.",
                        rutaStorage));
                continue;
            }

            if (!idsSegmentoUnoActivos.contains(planilla.getId())) {
                archivosDescartados.add(crearArchivoDescartado(
                        planilla.getId(),
                        planilla.getNombreArchivoFuente(),
                        planilla.getProducto(),
                        ESTADO_DESCARTADO_FUERA_SEGMENTO,
                        "La planilla activa existe en BD, pero su producto no pertenece al segmento 1.",
                        rutaStorage));
                continue;
            }

            if (!esEstadoAprobado(planilla.getEstadoPlanilla())) {
                continue;
            }

            if (!esPlanillaElegibleParaPanel(planilla)) {
                archivosDescartados.add(crearArchivoDescartado(
                        planilla.getId(),
                        planilla.getNombreArchivoFuente(),
                        planilla.getProducto(),
                        ESTADO_DESCARTADO_NO_XLSX,
                        "La planilla está aprobada, pero no tiene un XLSX utilizable para consolidación.",
                        rutaStorage));
                continue;
            }

            if (idsCargaConsolidados.contains(planilla.getId())) {
                archivosDescartados.add(crearArchivoDescartado(
                        planilla.getId(),
                        planilla.getNombreArchivoFuente(),
                        planilla.getProducto(),
                        ESTADO_DESCARTADO_YA_CONSOLIDADO,
                        "El XLSX ya fue usado en la última consolidación exitosa del periodo.",
                        rutaStorage));
            }
        }

        return new AdminDashboardResponse.DiagnosticoDisponibilidad(
                archivosStorage.size(),
                totalElegibles,
                archivosDescartados);
    }

        private List<AdminDashboardResponse.ArchivoEstado> construirArchivosConsolidados(SiproDetalleConsolidacionesPlanillas consolidacionExitosa) {
                List<SiproDetalleConsolidacionArchivo> archivosConsolidados = Optional.ofNullable(consolidacionExitosa)
                                .map(SiproDetalleConsolidacionesPlanillas::getIdConsolidacion)
                                .map(consolidacionArchivoRepository::findByIdConsolidacionOrderByIdCargaPlanillaAsc)
                                .orElse(List.of());

                Map<Long, Producto> productosPorId = cargarProductosPorId(archivosConsolidados.stream()
                                .map(SiproDetalleConsolidacionArchivo::getIdProductoOrigen)
                                .filter(idProducto -> idProducto != null)
                                .collect(Collectors.toSet()));

                return archivosConsolidados.stream()
                                .map(archivo -> mapearArchivoConsolidado(archivo, productosPorId))
                                .toList();
    }

        private AdminDashboardResponse.ArchivoEstado mapearArchivoConsolidado(SiproDetalleConsolidacionArchivo archivo,
                                                                                                                                                  Map<Long, Producto> productosPorId) {
                boolean sinDatos = esArchivoConsolidadoSinDatos(archivo);
                String nombreVisual = sinDatos
                                ? resolverNombreVisualSinDatos(
                                                archivo.getProductoOrigen(),
                                                archivo.getFechaCorte(),
                                                Optional.ofNullable(productosPorId.get(archivo.getIdProductoOrigen()))
                                                                .map(Producto::getNombreArchivoPermitido)
                                                                .orElse(null))
                                : archivo.getNombreArchivo();

        return new AdminDashboardResponse.ArchivoEstado(
                archivo.getIdConsolidacionArchivo(),
                archivo.getNombreArchivo(),
                archivo.getProductoOrigen(),
                null,
                archivo.getCantidadRegistrosArchivo(),
                                sinDatos ? "APROBADO" : "CONSOLIDADO",
                                sinDatos
                                                ? "Declaración sin datos registrada en la consolidación del periodo."
                                                : "Incluido en la última consolidación registrada.",
                                archivo.getRutaArchivo(),
                                sinDatos,
                                sinDatos ? archivo.getDescripcion() : null,
                                nombreVisual);
    }

        private Map<Long, Producto> cargarProductosPorId(Set<Long> idsProducto) {
                if (idsProducto == null || idsProducto.isEmpty()) {
                        return Map.of();
                }

                return productoRepository.findAllById(idsProducto).stream()
                                .collect(Collectors.toMap(Producto::getIdProducto, producto -> producto, (left, right) -> left));
        }

        private String resolverNombreVisualSinDatos(SiproDetalleCargaPlanillas planilla, Map<Long, Producto> productosPorId) {
                Producto producto = productosPorId.get(planilla.getIdProducto());
                String nombreArchivoPermitido = producto != null ? producto.getNombreArchivoPermitido() : null;
                return resolverNombreVisualSinDatos(planilla.getProducto(), planilla.getFechaCorteInformacion(), nombreArchivoPermitido);
        }

        private String resolverNombreVisualSinDatos(String nombreProducto,
                                                                                                LocalDate fechaCorte,
                                                                                                String nombreArchivoPermitido) {
                String nombreBase = construirNombreBaseSinDatos(nombreProducto, nombreArchivoPermitido);
                return nombreBase + "_" + formatearFechaCorteCompacta(fechaCorte) + "_SINDATOS";
        }

        private String construirNombreBaseSinDatos(String nombreProducto, String nombreArchivoPermitido) {
                String patron = nullSafeTexto(nombreArchivoPermitido).toUpperCase(Locale.ROOT);
                if (!patron.equals("SIN_ESTADO")) {
                        String sinMascara = patron.replace("_AAAAMMDD", "").replace("AAAAMMDD", "").trim();
                        if (!sinMascara.isBlank()) {
                                return normalizarTokenProducto(sinMascara);
                        }
                }
                return normalizarTokenProducto(nombreProducto);
        }

        private String formatearFechaCorteCompacta(LocalDate fechaCorte) {
                LocalDate fechaBase = fechaCorte != null ? fechaCorte : LocalDate.now();
                return String.format(Locale.ROOT, "%04d%02d%02d", fechaBase.getYear(), fechaBase.getMonthValue(), fechaBase.getDayOfMonth());
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

        private boolean esArchivoConsolidadoSinDatos(SiproDetalleConsolidacionArchivo archivo) {
                if (archivo == null) {
                        return false;
                }

                if (archivo.getNombreArchivo() != null
                                && archivo.getNombreArchivo().toUpperCase(Locale.ROOT).endsWith("_SINDATOS")) {
                        return true;
                }

                return (archivo.getCantidadRegistrosArchivo() == null || archivo.getCantidadRegistrosArchivo() == 0)
                                && (archivo.getRutaArchivo() == null || archivo.getRutaArchivo().isBlank())
                                && archivo.getDescripcion() != null
                                && !archivo.getDescripcion().isBlank();
        }

    private AdminDashboardResponse.HistoricoConsolidacion mapearHistorico(SiproDetalleConsolidacionesPlanillas cabecera) {
        String detalleSalida = esConsolidacionExitosa(cabecera)
                ? resolverRutaArchivoCreffos(cabecera.getPeriodoValoracion())
                : cabecera.getNombresArchivosConsolidados();

        return new AdminDashboardResponse.HistoricoConsolidacion(
                cabecera.getIdConsolidacion(),
                cabecera.getPeriodoValoracion() != null ? formatearPeriodo(cabecera.getPeriodoValoracion()) : null,
                cabecera.getEstadoConsolidacion(),
                cabecera.getFechaHoraInicio(),
                cabecera.getFechaHoraFin(),
                cabecera.getCantidadArchivosConsolidados(),
                cabecera.getCantidadRegistrosConsolidados(),
                detalleSalida,
                cabecera.getObservacion(),
                cabecera.getMensajeError());
    }

        private AdminDashboardResponse.PeriodoDisponible mapearPeriodo(LocalDate periodo, boolean consolidado) {
        return new AdminDashboardResponse.PeriodoDisponible(
                periodo.toString(),
                periodo.getYear(),
                periodo.getMonthValue(),
                                formatearPeriodo(periodo),
                                consolidado);
    }

    private String formatearPeriodo(LocalDate periodo) {
        return MESES[periodo.getMonthValue() - 1] + " " + periodo.getYear();
    }

        private Optional<SiproDetalleConsolidacionesPlanillas> obtenerUltimaConsolidacionExitosa(LocalDate periodoSeleccionado) {
                return consolidacionRepository.findFirstByPeriodoValoracionAndEstadoConsolidacionInOrderByCreadoEnDesc(
                                periodoSeleccionado,
                                ESTADOS_CONSOLIDACION_EXITOSA);
        }

        private Set<Long> obtenerIdsCargaConsolidados(SiproDetalleConsolidacionesPlanillas consolidacionExitosa) {
                if (consolidacionExitosa == null || consolidacionExitosa.getIdConsolidacion() == null) {
                        return Set.of();
                }

                return consolidacionArchivoRepository.findByIdConsolidacionOrderByIdCargaPlanillaAsc(consolidacionExitosa.getIdConsolidacion())
                                .stream()
                                .map(SiproDetalleConsolidacionArchivo::getIdCargaPlanilla)
                                .filter(idCarga -> idCarga != null)
                                .collect(Collectors.toSet());
        }

        private AdminDashboardResponse.EstadoPeriodoConsolidacion construirEstadoPeriodo(
                        LocalDate periodoSeleccionado,
                        ConsolidacionManualStatusResponse estadoConsolidacion,
                        SiproDetalleConsolidacionesPlanillas ultimaConsolidacionExitosa,
                        int cantidadPendientes,
                        int cantidadRechazadas) {
                ConsolidacionPlanillasService.ConsolidacionManualPrecheck precheck =
                                consolidacionPlanillasService.validarInicioConsolidacionManual(periodoSeleccionado);
                ConsolidacionPlanillasService.ConsolidacionRangoOperativo rango = precheck.rango();
                String mensajeAdvertenciaOperativa = construirMensajeAdvertenciaOperativa(
                                cantidadPendientes,
                                cantidadRechazadas);

                String estadoVentana = ESTADO_VENTANA_SIN_CONFIGURACION;
                String mensajeEstado = precheck.mensaje();

                if (rango != null) {
                        OffsetDateTime ahora = OffsetDateTime.now();
                        if (precheck.ventanaIgnoradaPorConfiguracion() && ahora.isBefore(rango.inicioRangoConsolidacion())) {
                                estadoVentana = ESTADO_VENTANA_ABIERTA;
                                if (ahora.isBefore(rango.cierreVentana())) {
                                        mensajeEstado = "Bypass DEV activo. Se permite consolidar aunque la ventana de carga siga ABIERTA. Cierre teórico: "
                                                        + rango.cierreVentana()
                                                        + ". Fuente: "
                                                        + rango.fuenteVentana()
                                                        + ". Inicio teórico del rango: "
                                                        + rango.inicioRangoConsolidacion() + ".";
                                } else {
                                        mensajeEstado = "Bypass DEV activo. Se permite consolidar aunque el delay post-cierre aún no se cumpla. La ventana cerró el "
                                                        + rango.cierreVentana()
                                                        + " y el inicio teórico del rango es "
                                                        + rango.inicioRangoConsolidacion() + ".";
                                }
                        } else if (precheck.ventanaIgnoradaPorConfiguracion() && ahora.isAfter(rango.finRangoConsolidacion())) {
                                estadoVentana = ESTADO_VENTANA_EXPIRADA;
                                mensajeEstado = "Bypass DEV activo. Se permite consolidar aunque el rango teórico haya EXPIRADO. Cierre: "
                                                + rango.cierreVentana()
                                                + ". Fin teórico del rango: "
                                                + rango.finRangoConsolidacion() + ".";
                        } else if (ahora.isBefore(rango.inicioRangoConsolidacion())) {
                                estadoVentana = ESTADO_VENTANA_ABIERTA;
                                if (ahora.isBefore(rango.cierreVentana())) {
                                        mensajeEstado = "Ventana de carga ABIERTA. Cierre: "
                                                        + rango.cierreVentana()
                                                        + ". Fuente: "
                                                        + rango.fuenteVentana()
                                                        + ". La consolidación estará disponible a partir de "
                                                        + rango.inicioRangoConsolidacion() + ".";
                                } else {
                                        mensajeEstado = "Ventana de carga CERRADA, pero aún corre el delay post-cierre. La consolidación estará disponible a partir de "
                                                        + rango.inicioRangoConsolidacion()
                                                        + ". Fuente: "
                                                        + rango.fuenteVentana() + ".";
                                }
                        } else if (ahora.isAfter(rango.finRangoConsolidacion())) {
                                estadoVentana = ESTADO_VENTANA_EXPIRADA;
                                mensajeEstado = "Rango de consolidación EXPIRADO para este periodo. Cerró el "
                                                + rango.cierreVentana()
                                                + ". El rango máximo era hasta "
                                                + rango.finRangoConsolidacion()
                                                + ". Cree una excepción de ventana para habilitar.";
                        } else {
                                estadoVentana = ESTADO_VENTANA_EN_RANGO;
                                mensajeEstado = "Ventana CERRADA. Rango de consolidación: desde "
                                                + rango.inicioRangoConsolidacion()
                                                + " hasta "
                                                + rango.finRangoConsolidacion()
                                                + ". PUEDE consolidar.";
                        }
                }

                OffsetDateTime fechaUltimaConsolidacion = ultimaConsolidacionExitosa != null
                                ? firstNonNull(ultimaConsolidacionExitosa.getFechaHoraFin(), ultimaConsolidacionExitosa.getFechaHoraInicio())
                                : null;

                return new AdminDashboardResponse.EstadoPeriodoConsolidacion(
                                estadoVentana,
                                rango != null ? rango.fuenteVentana() : "SIN_CONFIGURACION",
                                rango != null ? rango.cierreVentana() : null,
                                rango != null ? rango.inicioRangoConsolidacion() : null,
                                rango != null ? rango.finRangoConsolidacion() : null,
                                rango != null ? rango.motivoExcepcion() : null,
                                ultimaConsolidacionExitosa != null
                                                ? "Consolidación completada el " + fechaUltimaConsolidacion
                                                                + ". Archivos: " + nullSafeInt(ultimaConsolidacionExitosa.getCantidadArchivosConsolidados())
                                                                + ". Registros: " + nullSafeInt(ultimaConsolidacionExitosa.getCantidadRegistrosConsolidados())
                                                                + ". CREFFSOS generado."
                                                : mensajeEstado,
                                precheck.mensaje(),
                                precheck.puedeIniciar(),
                                precheck.enCurso() || (estadoConsolidacion != null && !estadoConsolidacion.isTerminal()),
                                precheck.ventanaIgnoradaPorConfiguracion(),
                                precheck.sobrescribeConsolidacionExistente(),
                                fechaUltimaConsolidacion,
                                ultimaConsolidacionExitosa != null ? ultimaConsolidacionExitosa.getCantidadArchivosConsolidados() : null,
                                ultimaConsolidacionExitosa != null ? ultimaConsolidacionExitosa.getCantidadRegistrosConsolidados() : null,
                                ultimaConsolidacionExitosa != null ? resolverRutaArchivoCreffos(periodoSeleccionado) : null,
                                cantidadPendientes,
                                cantidadRechazadas,
                                mensajeAdvertenciaOperativa);
        }

        private boolean esConsolidacionExitosa(SiproDetalleConsolidacionesPlanillas cabecera) {
                return cabecera != null && ESTADOS_CONSOLIDACION_EXITOSA.contains(cabecera.getEstadoConsolidacion());
        }

        private String resolverRutaArchivoCreffos(LocalDate periodo) {
                if (periodo == null) {
                        return null;
                }

                String fileName = parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", "CREFFSOS.xlsx");
                String storageKey = "consolidados/" + periodo + "/" + fileName;
                return fileStorageService.getAbsolutePath(storageKey).toString();
        }

        private OffsetDateTime firstNonNull(OffsetDateTime primary, OffsetDateTime fallback) {
                return primary != null ? primary : fallback;
        }

        private boolean esPlanillaElegibleParaPanel(SiproDetalleCargaPlanillas planilla) {
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
                return estadoPlanilla == null ? "" : estadoPlanilla.trim().toLowerCase(Locale.ROOT);
        }

        private String construirDetalleNoBloqueante(SiproDetalleCargaPlanillas planilla) {
                String estadoActual = nullSafeTexto(planilla.getEstadoPlanilla());
                if (esEstadoRechazado(planilla.getEstadoPlanilla())) {
                        return "Estado actual en BD: " + estadoActual
                                        + ". No bloquea la consolidación; este archivo quedará por fuera hasta que se cargue o apruebe de nuevo.";
                }

                return "Estado actual en BD: " + estadoActual
                                + ". No bloquea la consolidación; el proceso seguirá solo con los archivos aprobados.";
        }

        private int contarArchivosPorEstado(List<AdminDashboardResponse.ArchivoEstado> archivos,
                                            String estadoEsperado) {
                return (int) archivos.stream()
                                .filter(archivo -> estadoEsperado.equalsIgnoreCase(archivo.estado()))
                                .count();
        }

        private String construirMensajeAdvertenciaOperativa(int cantidadPendientes,
                                                            int cantidadRechazadas) {
                List<String> partes = new ArrayList<>();
                if (cantidadPendientes > 0) {
                        partes.add(cantidadPendientes + (cantidadPendientes == 1 ? " pendiente" : " pendientes"));
                }
                if (cantidadRechazadas > 0) {
                        partes.add(cantidadRechazadas + (cantidadRechazadas == 1 ? " rechazada" : " rechazadas"));
                }

                if (partes.isEmpty()) {
                        return null;
                }

                return "Hay " + String.join(" y ", partes)
                                + " activas que no bloquean la consolidación; el proceso seguirá solo con lo aprobado.";
        }

        private boolean esRutaXlsx(String rutaArchivo) {
                return rutaArchivo != null && rutaArchivo.toLowerCase(Locale.ROOT).endsWith(".xlsx");
        }

        private String normalizarRuta(String rutaArchivo) {
                return rutaArchivo == null ? "" : rutaArchivo.replace('\\', '/').toLowerCase(Locale.ROOT);
        }

        private SiproDetalleCargaPlanillas seleccionarPlanillaMasReciente(SiproDetalleCargaPlanillas left,
                                                                          SiproDetalleCargaPlanillas right) {
                if (left == null) {
                        return right;
                }
                if (right == null) {
                        return left;
                }

                if (left.getFechaCreacion() == null) {
                        return right;
                }
                if (right.getFechaCreacion() == null) {
                        return left;
                }

                return left.getFechaCreacion().isAfter(right.getFechaCreacion()) ? left : right;
        }

        private AdminDashboardResponse.ArchivoEstado crearArchivoDescartado(Long id,
                                                                            String nombreArchivo,
                                                                            String producto,
                                                                            String estado,
                                                                            String detalle,
                                                                            String rutaArchivo) {
                return new AdminDashboardResponse.ArchivoEstado(
                        id,
                        nombreArchivo,
                        producto,
                        null,
                        null,
                        estado,
                        detalle,
                        rutaArchivo,
                        false,
                        null,
                        nombreArchivo);
        }

        private String extraerNombreArchivo(String rutaArchivo) {
                if (rutaArchivo == null || rutaArchivo.isBlank()) {
                        return "Archivo sin nombre";
                }

                int index = rutaArchivo.lastIndexOf('/');
                return index >= 0 ? rutaArchivo.substring(index + 1) : rutaArchivo;
        }

        private String nullSafeTexto(String value) {
                return value == null || value.isBlank() ? "SIN_ESTADO" : value;
        }

        private int nullSafeInt(Integer value) {
                return value != null ? value : 0;
        }
}