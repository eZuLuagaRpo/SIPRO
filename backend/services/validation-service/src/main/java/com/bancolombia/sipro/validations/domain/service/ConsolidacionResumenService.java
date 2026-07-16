package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.ConsolidacionResumenResponse;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleArchivoValidacion;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidadoRegistroRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Construye el resumen consolidado por periodo y agrega la comparación operativa contra CREFFSOS.
 */
@Service
public class ConsolidacionResumenService {

    private static final Locale LOCALE_ES_CO = Locale.forLanguageTag("es-CO");
    private static final String ESTADO_COMPLETADO = "COMPLETADO";
    private static final Long SEGMENTO_FULL_IFRS_ID = 2L;

    private final SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;
    private final SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository;
    private final SiproDetalleCargaPlanillasRepository cargaPlanillasRepository;
    private final SiproDetalleArchivoValidacionRepository archivoValidacionRepository;
    private final CreffosComparisonService creffosComparisonService;

    public ConsolidacionResumenService(
            SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository,
            SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository,
            SiproDetalleCargaPlanillasRepository cargaPlanillasRepository,
            SiproDetalleArchivoValidacionRepository archivoValidacionRepository,
            CreffosComparisonService creffosComparisonService) {
        this.consolidacionRepository = consolidacionRepository;
        this.consolidadoRegistroRepository = consolidadoRegistroRepository;
        this.cargaPlanillasRepository = cargaPlanillasRepository;
        this.archivoValidacionRepository = archivoValidacionRepository;
        this.creffosComparisonService = creffosComparisonService;
    }

    /**
     * Devuelve la vista resumida del periodo solicitado o del último consolidado disponible.
     */
    public ConsolidacionResumenResponse obtenerResumen(Integer anio, Integer mes) {
        List<SiproDetalleConsolidacionesPlanillas> consolidadas = consolidacionRepository
                .findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc(ESTADO_COMPLETADO);

        Map<YearMonth, SiproDetalleConsolidacionesPlanillas> periodosMap = deduplicarPorPeriodo(consolidadas);
        ConsolidacionResumenResponse response = new ConsolidacionResumenResponse();
        response.setPeriodosDisponibles(construirPeriodosDisponibles(periodosMap.keySet()));

        if (periodosMap.isEmpty()) {
            LocalDate hoy = LocalDate.now();
            response.setAnioSeleccionado(hoy.getYear());
            response.setMesSeleccionado(hoy.getMonthValue());
            response.setPeriodoEtiqueta(formatearPeriodo(YearMonth.from(hoy)));
            response.setHayDatos(false);
            response.setEstadoConsolidacion("SIN_DATOS");
            response.setCantidadArchivosConsolidados(0);
            response.setCantidadRegistrosConsolidados(0);
            response.setTotalVlrIniObl(BigDecimal.ZERO);
            response.setRegistrosObservados(0);
            response.setProductosObservados(0);
                response.setCreffos(new ConsolidacionResumenResponse.CreffosArchivoResumen(
                    false,
                    "CREFFSOS",
                    "SIN_PERIODO",
                    "SIN_PERIODO",
                    "SIN_PERIODO",
                    "",
                    0,
                    0,
                    0L,
                    BigDecimal.ZERO,
                    "No existe un periodo consolidado para ejecutar la comparación CREFFSOS."
                ));
                response.setMetricasComparacion(List.of());
                response.setTieneDiferenciasConciliacion(false);
                response.setMetricasConDiferencia(0);
            response.setAlerta(new ConsolidacionResumenResponse.AlertaResumen(
                    "info",
                    "Sin consolidaciones disponibles",
                    "Aún no hay periodos consolidados listos para visualizar en esta pantalla."
            ));
            return response;
        }

        YearMonth seleccionado = resolverPeriodoSeleccionado(anio, mes, periodosMap.keySet());
        SiproDetalleConsolidacionesPlanillas cabecera = periodosMap.get(seleccionado);
        List<SiproDetalleConsolidadoRegistro> registros = consolidadoRegistroRepository
                .findByIdConsolidacionOrderByIdConsolidadoRegistroAsc(cabecera.getIdConsolidacion());

        response.setAnioSeleccionado(seleccionado.getYear());
        response.setMesSeleccionado(seleccionado.getMonthValue());
        response.setPeriodoEtiqueta(formatearPeriodo(seleccionado));
        response.setEstadoConsolidacion(cabecera.getEstadoConsolidacion());
        response.setCantidadArchivosConsolidados(valorEntero(cabecera.getCantidadArchivosConsolidados()));
        response.setFechaActualizacion(cabecera.getModificadoEn() != null
                ? cabecera.getModificadoEn()
                : (cabecera.getFechaHoraFin() != null ? cabecera.getFechaHoraFin() : cabecera.getCreadoEn()));

        BigDecimal totalVlrIniOblPostgres = registros.stream()
            .map(SiproDetalleConsolidadoRegistro::getVlriniobl)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cantidadRegistrosPostgres = registros.isEmpty()
            ? valorEntero(cabecera.getCantidadRegistrosConsolidados())
            : registros.size();

        CreffosComparisonService.ComparisonSnapshot comparisonSnapshot = creffosComparisonService.comparar(
            cabecera.getPeriodoValoracion(),
            cantidadRegistrosPostgres,
            totalVlrIniOblPostgres);
        applyComparison(response, comparisonSnapshot);

        if (registros.isEmpty()) {
            response.setHayDatos(false);
            response.setCantidadRegistrosConsolidados(valorEntero(cabecera.getCantidadRegistrosConsolidados()));
            response.setTotalVlrIniObl(BigDecimal.ZERO);
            response.setRegistrosObservados(0);
            response.setProductosObservados(0);
            response.setAlerta(construirAlerta(cabecera, 0, 0, comparisonSnapshot, true));
            return response;
        }

        FullIfrsRegistros registrosFullIfrs = obtenerRegistrosControlFullIfrsPorProducto(
                cabecera.getPeriodoValoracion());
        List<ConsolidacionResumenResponse.ProductoResumen> productos = agruparProductos(
            registros,
                registrosFullIfrs);
        response.setCantidadRegistrosArchivoControl(registrosFullIfrs.totalRegistrosControl());
        long registrosObservados = productos.stream().mapToLong(ConsolidacionResumenResponse.ProductoResumen::getRegistrosObservados).sum();
        int productosObservados = (int) productos.stream().filter(ConsolidacionResumenResponse.ProductoResumen::isTieneDiscrepancias).count();
        BigDecimal totalVlrIniObl = productos.stream()
                .map(ConsolidacionResumenResponse.ProductoResumen::getTotalVlrIniObl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        response.setHayDatos(true);
        response.setProductos(productos);
        response.setCantidadRegistrosConsolidados(registros.size());
        response.setTotalVlrIniObl(totalVlrIniObl);
        response.setRegistrosObservados(registrosObservados);
        response.setProductosObservados(productosObservados);
        response.setAlerta(construirAlerta(cabecera, registrosObservados, productosObservados, comparisonSnapshot, false));
        return response;
    }

    private void applyComparison(ConsolidacionResumenResponse response,
                                 CreffosComparisonService.ComparisonSnapshot comparisonSnapshot) {
        response.setCreffos(comparisonSnapshot.archivo());
        response.setMetricasComparacion(comparisonSnapshot.metricas());
        response.setTieneDiferenciasConciliacion(comparisonSnapshot.tieneDiferencias());
        response.setMetricasConDiferencia((int) comparisonSnapshot.metricas().stream()
                .filter(metric -> !metric.isCoincide())
                .count());
    }

    private Map<YearMonth, SiproDetalleConsolidacionesPlanillas> deduplicarPorPeriodo(
            List<SiproDetalleConsolidacionesPlanillas> consolidadas) {
        Map<YearMonth, SiproDetalleConsolidacionesPlanillas> periodos = new LinkedHashMap<>();
        for (SiproDetalleConsolidacionesPlanillas cabecera : consolidadas) {
            if (cabecera.getPeriodoValoracion() == null) {
                continue;
            }
            YearMonth key = YearMonth.from(cabecera.getPeriodoValoracion());
            periodos.putIfAbsent(key, cabecera);
        }
        return periodos;
    }

    private List<ConsolidacionResumenResponse.PeriodoAnual> construirPeriodosDisponibles(Collection<YearMonth> periodos) {
        Map<Integer, List<ConsolidacionResumenResponse.MesDisponible>> porAnio = new LinkedHashMap<>();
        Set<Integer> ordenAnios = new LinkedHashSet<>();

        periodos.stream()
                .sorted(Comparator.reverseOrder())
                .forEach(periodo -> {
                    ordenAnios.add(periodo.getYear());
                    porAnio.computeIfAbsent(periodo.getYear(), ignored -> new ArrayList<>())
                            .add(new ConsolidacionResumenResponse.MesDisponible(
                                    periodo.getMonthValue(),
                                    capitalizar(periodo.getMonth().getDisplayName(TextStyle.FULL, LOCALE_ES_CO)),
                                    capitalizar(periodo.getMonth().getDisplayName(TextStyle.SHORT, LOCALE_ES_CO))
                                            .replace('.', ' ')
                                            .trim(),
                                    periodo.toString()
                            ));
                });

        List<ConsolidacionResumenResponse.PeriodoAnual> respuesta = new ArrayList<>();
        for (Integer anio : ordenAnios) {
            List<ConsolidacionResumenResponse.MesDisponible> meses = porAnio.getOrDefault(anio, new ArrayList<>());
            meses.sort(Comparator.comparingInt(ConsolidacionResumenResponse.MesDisponible::getNumero));
            respuesta.add(new ConsolidacionResumenResponse.PeriodoAnual(anio, meses));
        }
        return respuesta;
    }

    private YearMonth resolverPeriodoSeleccionado(Integer anio, Integer mes, Collection<YearMonth> disponibles) {
        if (anio != null && mes != null) {
            try {
                YearMonth solicitado = YearMonth.of(anio, mes);
                if (disponibles.contains(solicitado)) {
                    return solicitado;
                }
            } catch (RuntimeException ignored) {
                // Si el periodo es inválido, se devuelve el más reciente disponible.
            }
        }

        return disponibles.stream()
                .max(Comparator.naturalOrder())
                .orElse(YearMonth.now());
    }

    private List<ConsolidacionResumenResponse.ProductoResumen> agruparProductos(
            List<SiproDetalleConsolidadoRegistro> registros,
            FullIfrsRegistros registrosFullIfrs) {
        Map<String, ProductoAcumulado> agrupados = new LinkedHashMap<>();

        for (SiproDetalleConsolidadoRegistro registro : registros) {
            Long idProducto = registro.getIdProductoOrigen();
            String nombreProducto = normalizarNombreProducto(registro.getProductoOrigen());
            String key = (idProducto == null ? "sin-id" : idProducto) + "|" + nombreProducto;

            ProductoAcumulado acumulado = agrupados.computeIfAbsent(key,
                    ignored -> new ProductoAcumulado(idProducto, nombreProducto));
            acumulado.cantidadRegistros++;
            acumulado.totalVlrIniObl = acumulado.totalVlrIniObl.add(valorMonetario(registro.getVlriniobl()));
            if (acumulado.cantidadRegistrosFullIfrs == 0L) {
                long cantidadPorId = idProducto == null ? 0L
                        : registrosFullIfrs.porIdProducto().getOrDefault(idProducto, 0L);
                long cantidadPorNombre = registrosFullIfrs.porNombreProducto()
                        .getOrDefault(normalizarClaveProducto(nombreProducto), 0L);
                acumulado.cantidadRegistrosFullIfrs = cantidadPorId > 0L ? cantidadPorId : cantidadPorNombre;
            }
            if (tieneObservacionCreffos(registro)) {
                acumulado.registrosObservados++;
            }
        }

        // Tabla 1 debe mostrar la unión de productos cargados en segmento 1 y segmento 2.
        Set<String> clavesNombreExistentes = new LinkedHashSet<>();
        for (ProductoAcumulado item : agrupados.values()) {
            clavesNombreExistentes.add(normalizarClaveProducto(item.nombreProducto));
        }

        for (Map.Entry<Long, Long> entry : registrosFullIfrs.porIdProducto().entrySet()) {
            Long idProducto = entry.getKey();
            long cantidadFullIfrs = entry.getValue() == null ? 0L : entry.getValue();
            if (cantidadFullIfrs <= 0L) {
                continue;
            }

            String nombreProducto = normalizarNombreProducto(
                    registrosFullIfrs.nombrePorIdProducto().get(idProducto));
            String claveNombre = normalizarClaveProducto(nombreProducto);
            if (clavesNombreExistentes.contains(claveNombre)) {
                continue;
            }

            String key = (idProducto == null ? "sin-id" : idProducto) + "|" + nombreProducto;
            ProductoAcumulado acumulado = agrupados.computeIfAbsent(key,
                    ignored -> new ProductoAcumulado(idProducto, nombreProducto));
            acumulado.cantidadRegistrosFullIfrs = cantidadFullIfrs;
            clavesNombreExistentes.add(claveNombre);
        }

        for (Map.Entry<String, Long> entry : registrosFullIfrs.porNombreProducto().entrySet()) {
            String claveProducto = entry.getKey();
            long cantidadFullIfrs = entry.getValue() == null ? 0L : entry.getValue();
            if (cantidadFullIfrs <= 0L || clavesNombreExistentes.contains(claveProducto)) {
                continue;
            }

            String nombreProducto = normalizarNombreProducto(
                    registrosFullIfrs.nombreOriginalPorClave().get(claveProducto));
            String key = "sin-id|" + nombreProducto;
            ProductoAcumulado acumulado = agrupados.computeIfAbsent(key,
                    ignored -> new ProductoAcumulado(null, nombreProducto));
            acumulado.cantidadRegistrosFullIfrs = cantidadFullIfrs;
            clavesNombreExistentes.add(claveProducto);
        }

        return agrupados.values().stream()
                .map(item -> new ConsolidacionResumenResponse.ProductoResumen(
                        item.idProducto,
                        item.nombreProducto,
                        item.cantidadRegistros,
                        item.totalVlrIniObl,
                item.cantidadRegistrosFullIfrs,
                        item.registrosObservados,
                        item.registrosObservados > 0
                ))
                .sorted(Comparator
                        .comparing(ConsolidacionResumenResponse.ProductoResumen::getTotalVlrIniObl,
                                Comparator.nullsFirst(BigDecimal::compareTo)).reversed()
                        .thenComparing(ConsolidacionResumenResponse.ProductoResumen::getCantidadRegistros,
                                Comparator.reverseOrder())
                        .thenComparing(ConsolidacionResumenResponse.ProductoResumen::getNombreProducto))
                .toList();
    }

    private FullIfrsRegistros obtenerRegistrosControlFullIfrsPorProducto(LocalDate fechaCorte) {
        if (fechaCorte == null) {
            return FullIfrsRegistros.vacio();
        }

        List<SiproDetalleCargaPlanillas> planillasFullIfrs = cargaPlanillasRepository
                .findPlanillasAprobadasByFechaCorteAndSegmentoId(fechaCorte, SEGMENTO_FULL_IFRS_ID);
        if (planillasFullIfrs.isEmpty()) {
            return FullIfrsRegistros.vacio();
        }

        List<Long> idsCarga = planillasFullIfrs.stream()
                .map(SiproDetalleCargaPlanillas::getId)
                .filter(Objects::nonNull)
                .toList();
        if (idsCarga.isEmpty()) {
            return FullIfrsRegistros.vacio();
        }

        Map<Long, SiproDetalleArchivoValidacion> validacionByCarga = archivoValidacionRepository
                .findByIdCargaPlanillaIn(idsCarga)
                .stream()
                .filter(validacion -> validacion.getIdCargaPlanilla() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SiproDetalleArchivoValidacion::getIdCargaPlanilla,
                        validacion -> validacion,
                        (actual, ignored) -> actual
                ));

        Map<Long, Long> resultadoPorId = new LinkedHashMap<>();
        Map<String, Long> resultadoPorNombre = new LinkedHashMap<>();
        Map<Long, String> nombrePorId = new LinkedHashMap<>();
        Map<String, String> nombreOriginalPorClave = new LinkedHashMap<>();
        long totalRegistrosControl = 0L;
        for (SiproDetalleCargaPlanillas planilla : planillasFullIfrs) {
            SiproDetalleArchivoValidacion validacion = validacionByCarga.get(planilla.getId());
            long cantidadFilasExcel = validacion != null && validacion.getNumeroFilasDatos() != null
                    ? validacion.getNumeroFilasDatos().longValue()
                    : 0L;
            long cantidadControl = validacion != null && validacion.getCantidadRegistrosControl() != null
                    ? validacion.getCantidadRegistrosControl().longValue()
                    : 0L;

            totalRegistrosControl += cantidadControl;

            Long idProducto = planilla.getIdProducto();
            String nombreProducto = normalizarNombreProducto(planilla.getProducto());
            if (idProducto != null) {
                resultadoPorId.merge(idProducto, cantidadFilasExcel, Long::sum);
                nombrePorId.putIfAbsent(idProducto, nombreProducto);
            }

            String claveProducto = normalizarClaveProducto(planilla.getProducto());
            if (!claveProducto.isBlank()) {
                resultadoPorNombre.merge(claveProducto, cantidadFilasExcel, Long::sum);
                nombreOriginalPorClave.putIfAbsent(claveProducto, nombreProducto);
            }
        }
        return new FullIfrsRegistros(
                resultadoPorId,
                resultadoPorNombre,
                nombrePorId,
                nombreOriginalPorClave,
                totalRegistrosControl);
    }

    private ConsolidacionResumenResponse.AlertaResumen construirAlerta(
            SiproDetalleConsolidacionesPlanillas cabecera,
            long registrosObservados,
            int productosObservados,
            CreffosComparisonService.ComparisonSnapshot comparisonSnapshot,
            boolean sinDetallePostgres) {
        ConsolidacionResumenResponse.CreffosArchivoResumen creffos = comparisonSnapshot.archivo();
        if (creffos != null && "NO_ENCONTRADO".equals(creffos.getEstado())) {
            return new ConsolidacionResumenResponse.AlertaResumen(
                    "warning",
                    "No se encontró el CREFFSOS del periodo.",
                    creffos.getDetalle()
            );
        }

        if (creffos != null && "ERROR_LECTURA".equals(creffos.getEstado())) {
            return new ConsolidacionResumenResponse.AlertaResumen(
                    "warning",
                    "No fue posible leer el CREFFSOS del periodo.",
                    creffos.getDetalle()
            );
        }

        if (comparisonSnapshot.tieneDiferencias()) {
            return new ConsolidacionResumenResponse.AlertaResumen(
                    "warning",
                    "Hay diferencias entre PostgreSQL y CREFFSOS.",
                    "Se encontraron " + comparisonSnapshot.metricas().stream().filter(metric -> !metric.isCoincide()).count()
                            + " métrica(s) con diferencia al comparar el consolidado del periodo con el archivo CREFFSOS."
            );
        }

        if (sinDetallePostgres) {
            return new ConsolidacionResumenResponse.AlertaResumen(
                    "info",
                    "Periodo sin registros detallados",
                    "El periodo seleccionado no tiene registros detallados disponibles en PostgreSQL, pero la conciliación CREFFSOS ya quedó preparada."
            );
        }

        if (cabecera.getMensajeError() != null && !cabecera.getMensajeError().isBlank()) {
            return new ConsolidacionResumenResponse.AlertaResumen(
                    "warning",
                    "La consolidación terminó con observaciones.",
                    cabecera.getMensajeError().trim()
            );
        }

        if (registrosObservados > 0) {
            return null;
        }

        return new ConsolidacionResumenResponse.AlertaResumen(
                "success",
            "Consolidación lista y conciliada.",
            "El periodo seleccionado quedó consolidado y coincide con el archivo CREFFSOS publicado."
        );
    }

    private boolean tieneObservacionCreffos(SiproDetalleConsolidadoRegistro registro) {
        return esBlanco(registro.getTipoId()) || registro.getClasificacion() == null;
    }

    private String normalizarNombreProducto(String nombreProducto) {
        if (nombreProducto == null || nombreProducto.isBlank()) {
            return "Producto sin identificar";
        }
        return nombreProducto.trim();
    }

    private String normalizarClaveProducto(String nombreProducto) {
        if (nombreProducto == null || nombreProducto.isBlank()) {
            return "";
        }
        return nombreProducto
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(LOCALE_ES_CO);
    }

    private BigDecimal valorMonetario(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private int valorEntero(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private String formatearPeriodo(YearMonth periodo) {
        return capitalizar(periodo.getMonth().getDisplayName(TextStyle.FULL, LOCALE_ES_CO)) + " " + periodo.getYear();
    }

    private String capitalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        return valor.substring(0, 1).toUpperCase(LOCALE_ES_CO) + valor.substring(1).toLowerCase(LOCALE_ES_CO);
    }

    private boolean esBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    /**
     * Devuelve todos los registros consolidados de un periodo específico.
     * Usado para generar reportes detallados y exportaciones Excel.
     *
     * @param anio año del periodo (si null, usa el año actual)
     * @param mes mes del periodo (si null, usa el mes actual)
     * @return lista de registros consolidados del periodo
     */
    public List<SiproDetalleConsolidadoRegistro> obtenerRegistrosConsolidadosPorPeriodo(Integer anio, Integer mes) {
        List<SiproDetalleConsolidacionesPlanillas> consolidadas = consolidacionRepository
                .findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc(ESTADO_COMPLETADO);

        Map<YearMonth, SiproDetalleConsolidacionesPlanillas> periodosMap = deduplicarPorPeriodo(consolidadas);
        if (periodosMap.isEmpty()) {
            return List.of();
        }

        YearMonth periodoSeleccionado = resolverPeriodoSeleccionado(anio, mes, periodosMap.keySet());
        SiproDetalleConsolidacionesPlanillas consolidacion = periodosMap.get(periodoSeleccionado);
        if (consolidacion == null) {
            return List.of();
        }

        return consolidadoRegistroRepository.findByIdConsolidacionOrderByIdConsolidadoRegistroAsc(consolidacion.getIdConsolidacion());
    }

    private static final class ProductoAcumulado {
        private final Long idProducto;
        private final String nombreProducto;
        private long cantidadRegistros;
        private BigDecimal totalVlrIniObl = BigDecimal.ZERO;
        private long cantidadRegistrosFullIfrs;
        private long registrosObservados;

        private ProductoAcumulado(Long idProducto, String nombreProducto) {
            this.idProducto = idProducto;
            this.nombreProducto = nombreProducto;
            this.cantidadRegistrosFullIfrs = 0L;
        }
    }

    private record FullIfrsRegistros(
            Map<Long, Long> porIdProducto,
            Map<String, Long> porNombreProducto,
            Map<Long, String> nombrePorIdProducto,
            Map<String, String> nombreOriginalPorClave,
            long totalRegistrosControl) {
        private static FullIfrsRegistros vacio() {
            return new FullIfrsRegistros(Map.of(), Map.of(), Map.of(), Map.of(), 0L);
        }
    }
}