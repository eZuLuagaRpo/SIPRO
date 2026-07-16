package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidadoRegistroRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Genera el reporte Excel de conciliación entre el consolidado interno y el archivo CREFFSOS publicado.
 */
@Service
public class ConsolidacionConciliacionReportService {

    private static final String ESTADO_COMPLETADO = "COMPLETADO";
    private static final String ESTADO_COMPLETADO_CON_ADVERTENCIAS = "COMPLETADO_CON_ADVERTENCIAS";
    private static final int SEGMENTO_UNO_ID = 1;
    private static final String SHEET_SUMMARY = "Resumen";
    private static final String SHEET_DETAIL = "Detalle";
    private static final String REPORT_PASSWORD = "sipro_readonly";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String WITHOUT_DIFFERENCES_MESSAGE = "Sin diferencias encontradas";
        private static final String EXTRA_CREFFSOS_MESSAGE =
            "No se encontraron registros faltantes en consolidado; la diferencia corresponde a registros adicionales en CREFFSOS.";

    private static final List<String> DETAIL_HEADERS = List.of(
            "NIT", "OFICINA", "DOCUMENTO", "MONEDA", "MODALIDAD", "ANOINIOBL", "MESINIOBL",
            "DIAINIOBL", "ANOVCTO", "MESVCTO", "DIAVCTO", "ANOVCTOFIN", "MESVCTOFIN",
            "DIAVCTOFIN", "CTAPUC", "VLRINIOBL", "SALDO", "SDOOTRCTAS", "INTERESES",
            "SDOVENCIDO", "INTCTASORD", "CLASIFICACION", "USUARIO", "PRODUCTO",
            "TIPO_ID", "PRODUCTO_ORIGEN", "SEGMENTO", "FECHA_CORTE", "DESCRIPCION",
            "USUARIO_CARGADOR", "USUARIO_APROBADOR"
    );

    private final SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;
    private final SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository;
    private final CreffosComparisonService creffosComparisonService;

    public ConsolidacionConciliacionReportService(
            SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository,
            SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository,
            CreffosComparisonService creffosComparisonService) {
        this.consolidacionRepository = consolidacionRepository;
        this.consolidadoRegistroRepository = consolidadoRegistroRepository;
        this.creffosComparisonService = creffosComparisonService;
    }

    /**
     * Genera el archivo de conciliación del periodo solicitado o del más reciente disponible.
     */
    public GeneratedConciliacionReport generar(Integer anio, Integer mes) {
        SiproDetalleConsolidacionesPlanillas cabecera = resolverConsolidacion(anio, mes);
        return generar(cabecera);
        }

        /**
         * Genera el archivo de conciliación para una consolidación específica.
         */
        public GeneratedConciliacionReport generar(Long idConsolidacion) {
        SiproDetalleConsolidacionesPlanillas cabecera = consolidacionRepository.findById(idConsolidacion)
            .orElseThrow(() -> new IllegalStateException(
                "No existe la consolidación " + idConsolidacion + " para generar el reporte de conciliación."));
        return generar(cabecera);
        }

        private GeneratedConciliacionReport generar(SiproDetalleConsolidacionesPlanillas cabecera) {
        List<SiproDetalleConsolidadoRegistro> registros = consolidadoRegistroRepository
                .findByIdConsolidacionOrderByIdConsolidadoRegistroAsc(cabecera.getIdConsolidacion())
                .stream()
                .filter(this::esRegistroSegmentoUno)
                .toList();

        CreffosComparisonService.DetailedCreffosFile creffos = creffosComparisonService
                .cargarDetalleArchivo(cabecera.getPeriodoValoracion());

        byte[] content = renderWorkbook(cabecera.getPeriodoValoracion(), registros, creffos);
        return new GeneratedConciliacionReport(buildFileName(cabecera.getPeriodoValoracion()), content);
    }

    private SiproDetalleConsolidacionesPlanillas resolverConsolidacion(Integer anio, Integer mes) {
        List<SiproDetalleConsolidacionesPlanillas> consolidadas = new ArrayList<>();
        consolidadas.addAll(consolidacionRepository
            .findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc(ESTADO_COMPLETADO));
        consolidadas.addAll(consolidacionRepository
            .findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc(ESTADO_COMPLETADO_CON_ADVERTENCIAS));

        Map<YearMonth, SiproDetalleConsolidacionesPlanillas> periodos = deduplicarPorPeriodo(consolidadas);
        if (periodos.isEmpty()) {
            throw new IllegalStateException("No existe una consolidación completada para generar el reporte de conciliación.");
        }

        YearMonth seleccionado = resolverPeriodoSeleccionado(anio, mes, periodos.keySet());
        return periodos.get(seleccionado);
    }

    private Map<YearMonth, SiproDetalleConsolidacionesPlanillas> deduplicarPorPeriodo(
            List<SiproDetalleConsolidacionesPlanillas> consolidadas) {
        Map<YearMonth, SiproDetalleConsolidacionesPlanillas> periodos = new LinkedHashMap<>();
        for (SiproDetalleConsolidacionesPlanillas cabecera : consolidadas) {
            if (cabecera.getPeriodoValoracion() == null) {
                continue;
            }
            periodos.putIfAbsent(YearMonth.from(cabecera.getPeriodoValoracion()), cabecera);
        }
        return periodos;
    }

    private YearMonth resolverPeriodoSeleccionado(Integer anio,
                                                  Integer mes,
                                                  Collection<YearMonth> disponibles) {
        if (anio != null && mes != null) {
            try {
                YearMonth solicitado = YearMonth.of(anio, mes);
                if (disponibles.contains(solicitado)) {
                    return solicitado;
                }
            } catch (RuntimeException ignored) {
                // Si el periodo es inválido, se usa el más reciente.
            }
        }
        return disponibles.stream().max(Comparator.naturalOrder()).orElseThrow();
    }

    private byte[] renderWorkbook(LocalDate periodoValoracion,
                                  List<SiproDetalleConsolidadoRegistro> registros,
                                  CreffosComparisonService.DetailedCreffosFile creffos) {
        long totalPlanillas = registros.size();
        BigDecimal totalPlanillasVlr = registros.stream()
                .map(SiproDetalleConsolidadoRegistro::getVlriniobl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCreffos = creffos.cantidadRegistros();
        BigDecimal totalCreffosVlr = creffos.totalVlrIniObl();
        long diferenciaRegistros = totalPlanillas - totalCreffos;
        BigDecimal diferenciaValores = totalPlanillasVlr.subtract(totalCreffosVlr);
        List<DetailRow> detailRows = diferenciaRegistros != 0
                ? construirFilasDetalle(registros, creffos.rows(), diferenciaRegistros)
                : List.of();
        String detailMessage = diferenciaRegistros == 0
            ? WITHOUT_DIFFERENCES_MESSAGE
            : (detailRows.isEmpty() ? EXTRA_CREFFSOS_MESSAGE : null);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            CellStyle boldStyle = createBoldStyle(workbook);

            Sheet summarySheet = workbook.createSheet(SHEET_SUMMARY);
            writeSummarySheet(summarySheet, boldStyle, periodoValoracion, registros,
                    creffos, totalPlanillas, totalPlanillasVlr, diferenciaRegistros, diferenciaValores);
            summarySheet.protectSheet(REPORT_PASSWORD);

            Sheet detailSheet = workbook.createSheet(SHEET_DETAIL);
            writeDetailSheet(detailSheet, boldStyle, periodoValoracion, detailRows, detailMessage);
            detailSheet.protectSheet(REPORT_PASSWORD);

            workbook.write(outputStream);
            workbook.dispose();
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el reporte de conciliación.", ex);
        }
    }

    private void writeSummarySheet(Sheet sheet,
                                   CellStyle boldStyle,
                                   LocalDate periodoValoracion,
                                   List<SiproDetalleConsolidadoRegistro> registros,
                                   CreffosComparisonService.DetailedCreffosFile creffos,
                                   long totalPlanillas,
                                   BigDecimal totalPlanillasVlr,
                                   long diferenciaRegistros,
                                   BigDecimal diferenciaValores) {
        List<ProductoAggregate> aggregates = agruparPorProducto(registros);
        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        writeCell(titleRow.createCell(0), "Conciliación planillas manuales " + periodoValoracion, boldStyle);

        if (!creffos.encontrado() || !"OK".equals(creffos.estado())) {
            Row noteRow = sheet.createRow(rowIndex++);
            writeCell(noteRow.createCell(0), creffos.detalle(), null);
        }

        rowIndex++;
        Row sectionOneRow = sheet.createRow(rowIndex++);
        writeCell(sectionOneRow.createCell(0), "Tabla 1 - Planillas manuales consolidadas", boldStyle);

        Row headerRow = sheet.createRow(rowIndex++);
        writeCell(headerRow.createCell(0), "Producto", boldStyle);
        writeCell(headerRow.createCell(1), "Cant. registros", boldStyle);
        writeCell(headerRow.createCell(2), "Total VLRINIOBL", boldStyle);

        for (ProductoAggregate aggregate : aggregates) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row.createCell(0), aggregate.productName(), null);
            writeCell(row.createCell(1), String.valueOf(aggregate.count()), null);
            writeCell(row.createCell(2), formatDecimal(aggregate.totalVlrIniObl()), null);
        }

        Row totalRow = sheet.createRow(rowIndex++);
        writeCell(totalRow.createCell(0), "TOTAL PLANILLAS", boldStyle);
        writeCell(totalRow.createCell(1), String.valueOf(totalPlanillas), boldStyle);
        writeCell(totalRow.createCell(2), formatDecimal(totalPlanillasVlr), boldStyle);

        rowIndex += 2;
        Row sectionTwoRow = sheet.createRow(rowIndex++);
        writeCell(sectionTwoRow.createCell(0), "Tabla 2 - Archivo CREFFSOS generado", boldStyle);

        Row creffosHeader = sheet.createRow(rowIndex++);
        writeCell(creffosHeader.createCell(0), "Archivo", boldStyle);
        writeCell(creffosHeader.createCell(1), "Cant. registros", boldStyle);
        writeCell(creffosHeader.createCell(2), "Total VLRINIOBL", boldStyle);

        Row creffosRow = sheet.createRow(rowIndex++);
        writeCell(creffosRow.createCell(0), "CREFFSOS", null);
        writeCell(creffosRow.createCell(1), String.valueOf(creffos.cantidadRegistros()), null);
        writeCell(creffosRow.createCell(2), formatDecimal(creffos.totalVlrIniObl()), null);

        rowIndex += 2;
        Row sectionThreeRow = sheet.createRow(rowIndex++);
        writeCell(sectionThreeRow.createCell(0), "Tabla 3 - Diferencia", boldStyle);

        Row diffHeader = sheet.createRow(rowIndex++);
        writeCell(diffHeader.createCell(0), "Concepto", boldStyle);
        writeCell(diffHeader.createCell(1), "Valor", boldStyle);

        Row diffCountRow = sheet.createRow(rowIndex++);
        writeCell(diffCountRow.createCell(0), "Diferencia cant. registros", null);
        writeCell(diffCountRow.createCell(1), String.valueOf(diferenciaRegistros), null);

        Row diffValueRow = sheet.createRow(rowIndex);
        writeCell(diffValueRow.createCell(0), "Diferencia total VLRINIOBL", null);
        writeCell(diffValueRow.createCell(1), formatDecimal(diferenciaValores), null);
    }

    private void writeDetailSheet(Sheet sheet,
                                  CellStyle boldStyle,
                                  LocalDate periodoValoracion,
                                  List<DetailRow> detailRows,
                                  String detailMessage) {
        int rowIndex = 0;
        Row titleRow = sheet.createRow(rowIndex++);
        writeCell(titleRow.createCell(0), "Detalle conciliación " + periodoValoracion, boldStyle);

        Row headerRow = sheet.createRow(rowIndex++);
        for (int index = 0; index < DETAIL_HEADERS.size(); index++) {
            writeCell(headerRow.createCell(index), DETAIL_HEADERS.get(index), boldStyle);
        }

        if (detailRows.isEmpty()) {
            Row messageRow = sheet.createRow(rowIndex);
            writeCell(messageRow.createCell(0), detailMessage == null ? WITHOUT_DIFFERENCES_MESSAGE : detailMessage, null);
            return;
        }

        for (DetailRow detailRow : detailRows) {
            Row row = sheet.createRow(rowIndex++);
            for (int index = 0; index < DETAIL_HEADERS.size(); index++) {
                writeCell(row.createCell(index), detailRow.values().getOrDefault(DETAIL_HEADERS.get(index), ""), null);
            }
        }
    }

    private List<ProductoAggregate> agruparPorProducto(List<SiproDetalleConsolidadoRegistro> registros) {
        Map<String, ProductoAggregate> aggregates = new LinkedHashMap<>();
        for (SiproDetalleConsolidadoRegistro registro : registros) {
            String productName = registro.getProductoOrigen() == null || registro.getProductoOrigen().isBlank()
                    ? "Producto sin identificar"
                    : registro.getProductoOrigen().trim();
            ProductoAggregate current = aggregates.getOrDefault(productName, ProductoAggregate.empty(productName));
            aggregates.put(productName, new ProductoAggregate(
                    productName,
                    current.count() + 1,
                    current.totalVlrIniObl().add(defaultDecimal(registro.getVlriniobl()))
            ));
        }
        return aggregates.values().stream()
                .sorted(Comparator.comparing(ProductoAggregate::productName))
                .toList();
    }

    private List<DetailRow> construirFilasDetalle(List<SiproDetalleConsolidadoRegistro> registros,
                                                  List<CreffosComparisonService.CreffosRow> creffosRows,
                                                  long diferenciaRegistros) {
        if (diferenciaRegistros == 0) {
            return List.of();
        }

        Map<String, Integer> documentosCreffos = new HashMap<>();
        for (CreffosComparisonService.CreffosRow row : creffosRows) {
            String documento = normalizeDocument(row.value("DOCUMENTO"));
            if (documento.isBlank()) {
                continue;
            }
            documentosCreffos.merge(documento, 1, Integer::sum);
        }

        List<DetailRow> detailRows = new ArrayList<>();
        for (SiproDetalleConsolidadoRegistro registro : registros) {
            String documento = normalizeDocument(registro.getDocumento());
            if (documento.isBlank()) {
                detailRows.add(DetailRow.fromRegistro(registro, "Registro sin DOCUMENTO en consolidado."));
                continue;
            }

            Integer disponibles = documentosCreffos.get(documento);
            if (disponibles == null || disponibles <= 0) {
                detailRows.add(DetailRow.fromRegistro(registro, "Registro presente en consolidado pero no en CREFFSOS."));
                continue;
            }

            documentosCreffos.put(documento, disponibles - 1);
        }

        return detailRows;
    }

    private String normalizeDocument(Object value) {
        if (value == null) {
            return "";
        }

        String normalized = value.toString().trim();
        if (normalized.endsWith(".0")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        return normalized;
    }

    private boolean esRegistroSegmentoUno(SiproDetalleConsolidadoRegistro registro) {
        return registro.getIdSegmento() != null
                && registro.getIdSegmento() == SEGMENTO_UNO_ID;
    }

    private String buildFileName(LocalDate periodoValoracion) {
        return "conciliacion_planillas_manuales_" + FILE_DATE_FORMAT.format(periodoValoracion) + ".xlsx";
    }

    private CellStyle createBoldStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeCell(Cell cell, String value, CellStyle style) {
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private String formatDecimal(BigDecimal value) {
        return defaultDecimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record GeneratedConciliacionReport(String fileName, byte[] content) {
    }

    private record ProductoAggregate(String productName, long count, BigDecimal totalVlrIniObl) {
        private static ProductoAggregate empty(String productName) {
            return new ProductoAggregate(productName, 0L, BigDecimal.ZERO);
        }
    }

    private record DetailRow(Map<String, String> values) {
        private static DetailRow fromRegistro(SiproDetalleConsolidadoRegistro registro, String defaultDescription) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String header : DETAIL_HEADERS) {
                Object rawValue = switch (header) {
                    case "NIT" -> registro.getNit();
                    case "OFICINA" -> registro.getOficina();
                    case "DOCUMENTO" -> registro.getDocumento();
                    case "MONEDA" -> registro.getMoneda();
                    case "MODALIDAD" -> registro.getModalidad();
                    case "ANOINIOBL" -> registro.getAnoiniobl();
                    case "MESINIOBL" -> registro.getMesiniobl();
                    case "DIAINIOBL" -> registro.getDiainiobl();
                    case "ANOVCTO" -> registro.getAnovcto();
                    case "MESVCTO" -> registro.getMesvcto();
                    case "DIAVCTO" -> registro.getDiavcto();
                    case "ANOVCTOFIN" -> registro.getAnovctofin();
                    case "MESVCTOFIN" -> registro.getMesvctofin();
                    case "DIAVCTOFIN" -> registro.getDiavctofin();
                    case "CTAPUC" -> registro.getCtapuc();
                    case "VLRINIOBL" -> registro.getVlriniobl();
                    case "SALDO" -> registro.getSaldo();
                    case "SDOOTRCTAS" -> registro.getSdootrctas();
                    case "INTERESES" -> registro.getIntereses();
                    case "SDOVENCIDO" -> registro.getSdovencido();
                    case "INTCTASORD" -> registro.getIntctasord();
                    case "CLASIFICACION" -> registro.getClasificacion();
                    case "USUARIO" -> registro.getUsuario();
                    case "PRODUCTO" -> registro.getProducto();
                    case "TIPO_ID" -> registro.getTipoId();
                    case "PRODUCTO_ORIGEN" -> registro.getProductoOrigen();
                    case "SEGMENTO" -> registro.getSegmento();
                    case "FECHA_CORTE" -> registro.getFechaCorte();
                    case "DESCRIPCION" -> registro.getDescripcion() == null || registro.getDescripcion().isBlank()
                            ? defaultDescription
                            : registro.getDescripcion();
                    case "USUARIO_CARGADOR" -> registro.getUsuarioCargador();
                    case "USUARIO_APROBADOR" -> registro.getUsuarioAprobador();
                    default -> "";
                };
                values.put(header, rawValue == null ? "" : rawValue.toString());
            }
            return new DetailRow(values);
        }

    }
}