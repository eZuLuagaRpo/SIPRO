package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.ConsolidacionResumenResponse;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ConsolidacionResumenExcelReportService {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String SHEET_PROTECTION_PASSWORD = "sipro-readonly";

    private static final List<String> DETAIL_HEADERS = List.of(
            "NIT", "OFICINA", "DOCUMENTO", "MONEDA", "MODALIDAD", "ANOINIOBL", "MESINIOBL",
            "DIAINIOBL", "ANOVCTO", "MESVCTO", "DIAVCTO", "ANOVCTOFIN", "MESVCTOFIN",
            "DIAVCTOFIN", "CTAPUC", "VLRINIOBL", "SALDO", "SDOOTRCTAS", "INTERESES",
            "SDOVENCIDO", "INTCTASORD", "CLASIFICACION", "USUARIO", "PRODUCTO",
            "TIPO_ID", "PRODUCTO_ORIGEN", "SEGMENTO", "FECHA_CORTE", "DESCRIPCION",
            "USUARIO_CARGADOR", "USUARIO_APROBADOR"
    );

    private final ConsolidacionResumenService consolidacionResumenService;

    public ConsolidacionResumenExcelReportService(ConsolidacionResumenService consolidacionResumenService) {
        this.consolidacionResumenService = consolidacionResumenService;
    }

    public GeneratedResumenReport generar(Integer anio, Integer mes) {
        ConsolidacionResumenResponse resumen = consolidacionResumenService.obtenerResumen(anio, mes);
        if (resumen == null || !resumen.isHayDatos()) {
            throw new IllegalStateException("No existe un resumen consolidado con datos para exportar.");
        }

        List<SiproDetalleConsolidadoRegistro> detalle =
                consolidacionResumenService.obtenerRegistrosConsolidadosPorPeriodo(anio, mes);

        String fileName = String.format(
            "conciliacion_planillas_manuales_%s.xlsx",
            FILE_DATE_FORMAT.format(
                YearMonth.of(resumen.getAnioSeleccionado(), resumen.getMesSeleccionado()).atEndOfMonth()
            )
        );

        return new GeneratedResumenReport(fileName, renderWorkbook(resumen, detalle));
    }

    private byte[] renderWorkbook(ConsolidacionResumenResponse resumen,
                                  List<SiproDetalleConsolidadoRegistro> detalle) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle numericStyle = createNumericStyle(workbook);

            writeResumenSheet(workbook, resumen, headerStyle, boldStyle, numericStyle);

            BigDecimal diferenciaRegistros = obtenerMetrica(resumen, "REGISTROS");
            if (diferenciaRegistros.compareTo(BigDecimal.ZERO) != 0 && detalle != null && !detalle.isEmpty()) {
                writeDetalleSheet(workbook, detalle, headerStyle);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible generar el Excel de resumen consolidado.", ex);
        }
    }

    private void writeResumenSheet(XSSFWorkbook workbook,
                                   ConsolidacionResumenResponse resumen,
                                   CellStyle headerStyle,
                                   CellStyle boldStyle,
                                   CellStyle numericStyle) {
        Sheet sheet = workbook.createSheet("Resumen");
        int rowIndex = 0;

        rowIndex = writeTablaProductos(sheet, rowIndex, resumen, headerStyle, boldStyle, numericStyle);
        rowIndex += 2;
        rowIndex = writeTablaCreffos(sheet, rowIndex, resumen, headerStyle, boldStyle, numericStyle);
        rowIndex += 2;
        writeTablaDiferencias(sheet, rowIndex, resumen, headerStyle, boldStyle, numericStyle);

        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 24 * 256);
        sheet.setColumnWidth(2, 24 * 256);
        sheet.setColumnWidth(3, 24 * 256);

        protectReadOnlySheet(sheet);
    }

    private int writeTablaProductos(Sheet sheet,
                                    int rowIndex,
                                    ConsolidacionResumenResponse resumen,
                                    CellStyle headerStyle,
                                    CellStyle boldStyle,
                                    CellStyle numericStyle) {
        Row title = sheet.createRow(rowIndex++);
        writeCell(title.createCell(0), "PRODUCTOS CONSOLIDADOS", boldStyle);

        Row headerOne = sheet.createRow(rowIndex++);
        writeCell(headerOne.createCell(0), "Producto", headerStyle);
        writeCell(headerOne.createCell(1), "Colgaap/Modificado", headerStyle);
        writeCell(headerOne.createCell(2), "", headerStyle);
        writeCell(headerOne.createCell(3), "Full IFRS", headerStyle);

        Row headerTwo = sheet.createRow(rowIndex++);
        writeCell(headerTwo.createCell(0), "", headerStyle);
        writeCell(headerTwo.createCell(1), "Cantidad de registros", headerStyle);
        writeCell(headerTwo.createCell(2), "Suma VLRINIOBL", headerStyle);
        writeCell(headerTwo.createCell(3), "Cantidad de registros", headerStyle);

        sheet.addMergedRegion(new CellRangeAddress(headerOne.getRowNum(), headerTwo.getRowNum(), 0, 0));
        sheet.addMergedRegion(new CellRangeAddress(headerOne.getRowNum(), headerOne.getRowNum(), 1, 2));

        for (ConsolidacionResumenResponse.ProductoResumen producto : resumen.getProductos()) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row.createCell(0), defaultString(producto.getNombreProducto()), null);
            writeCell(row.createCell(1), formatSegmentoUnoCantidad(producto), numericStyle);
            writeCell(row.createCell(2), formatSegmentoUnoValor(producto), numericStyle);
            writeCell(row.createCell(3), formatSegmentoDosCantidad(producto), numericStyle);
        }

        long totalRegistrosFullIfrs = resumen.getProductos().stream()
                .mapToLong(ConsolidacionResumenResponse.ProductoResumen::getCantidadRegistrosFullIfrs)
                .sum();

        Row totals = sheet.createRow(rowIndex++);
        writeCell(totals.createCell(0), "Totales", boldStyle);
        writeCell(totals.createCell(1), formatInteger(BigDecimal.valueOf(resumen.getCantidadRegistrosConsolidados())), boldStyle);
        writeCell(totals.createCell(2), formatCurrency(defaultDecimal(resumen.getTotalVlrIniObl())), boldStyle);
        writeCell(totals.createCell(3), formatInteger(BigDecimal.valueOf(totalRegistrosFullIfrs)), boldStyle);
        return rowIndex;
    }

    private int writeTablaCreffos(Sheet sheet,
                                  int rowIndex,
                                  ConsolidacionResumenResponse resumen,
                                  CellStyle headerStyle,
                                  CellStyle boldStyle,
                                  CellStyle numericStyle) {
        Row title = sheet.createRow(rowIndex++);
        writeCell(title.createCell(0), "ARCHIVO CREFFSOS", boldStyle);

        Row headerOne = sheet.createRow(rowIndex++);
        writeCell(headerOne.createCell(0), "Archivo CREFFSOS", headerStyle);
        writeCell(headerOne.createCell(1), "Archivo Consolidación CREFFSOS", headerStyle);
        writeCell(headerOne.createCell(2), "", headerStyle);
        writeCell(headerOne.createCell(3), "Archivo Control", headerStyle);

        Row headerTwo = sheet.createRow(rowIndex++);
        writeCell(headerTwo.createCell(0), "", headerStyle);
        writeCell(headerTwo.createCell(1), "Cantidad de registros", headerStyle);
        writeCell(headerTwo.createCell(2), "Suma VLRINIOBL", headerStyle);
        writeCell(headerTwo.createCell(3), "Cantidad de registros", headerStyle);

        sheet.addMergedRegion(new CellRangeAddress(headerOne.getRowNum(), headerTwo.getRowNum(), 0, 0));
        sheet.addMergedRegion(new CellRangeAddress(headerOne.getRowNum(), headerOne.getRowNum(), 1, 2));

        ConsolidacionResumenResponse.CreffosArchivoResumen creffos = resumen.getCreffos();
        long totalRegistrosCreffos = creffos == null ? 0L : creffos.getCantidadRegistros();
        BigDecimal totalVlrCreffos = creffos == null ? BigDecimal.ZERO : defaultDecimal(creffos.getTotalVlrIniObl());

        Row totals = sheet.createRow(rowIndex++);
        writeCell(totals.createCell(0), "Totales", boldStyle);
        writeCell(totals.createCell(1), formatInteger(BigDecimal.valueOf(totalRegistrosCreffos)), numericStyle);
        writeCell(totals.createCell(2), formatCurrency(totalVlrCreffos), numericStyle);
        writeCell(totals.createCell(3), formatInteger(BigDecimal.valueOf(resumen.getCantidadRegistrosArchivoControl())), numericStyle);

        return rowIndex;
    }

    private void writeTablaDiferencias(Sheet sheet,
                                       int rowIndex,
                                       ConsolidacionResumenResponse resumen,
                                       CellStyle headerStyle,
                                       CellStyle boldStyle,
                                       CellStyle numericStyle) {
        Row title = sheet.createRow(rowIndex++);
        writeCell(title.createCell(0), "DIFERENCIAS", boldStyle);

        Row header = sheet.createRow(rowIndex++);
        writeCell(header.createCell(0), "Diferencia", headerStyle);
        writeCell(header.createCell(1), "Cantidad de registros", headerStyle);
        writeCell(header.createCell(2), "Diferencia valores", headerStyle);
        writeCell(header.createCell(3), "Cantidad de registros", headerStyle);

        BigDecimal diferenciaRegistros = obtenerMetrica(resumen, "REGISTROS");
        BigDecimal diferenciaValores = obtenerMetrica(resumen, "VLRINIOBL");
        long totalRegistrosFullIfrs = resumen.getProductos().stream()
                .mapToLong(ConsolidacionResumenResponse.ProductoResumen::getCantidadRegistrosFullIfrs)
                .sum();
        BigDecimal diferenciaRegistrosControl = BigDecimal.valueOf(totalRegistrosFullIfrs)
                .subtract(BigDecimal.valueOf(resumen.getCantidadRegistrosArchivoControl()));

        Row totals = sheet.createRow(rowIndex);
        writeCell(totals.createCell(0), "Totales", boldStyle);
        writeCell(totals.createCell(1), formatDifference(diferenciaRegistros, false), numericStyle);
        writeCell(totals.createCell(2), formatDifference(diferenciaValores, true), numericStyle);
        writeCell(totals.createCell(3), formatDifference(diferenciaRegistrosControl, false), numericStyle);
    }

    private void writeDetalleSheet(XSSFWorkbook workbook,
                                   List<SiproDetalleConsolidadoRegistro> registros,
                                   CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("Detalle Colgaap Modificado");
        int rowIndex = 0;

        Row header = sheet.createRow(rowIndex++);
        for (int i = 0; i < DETAIL_HEADERS.size(); i++) {
            writeCell(header.createCell(i), DETAIL_HEADERS.get(i), headerStyle);
            sheet.setColumnWidth(i, 16 * 256);
        }

        for (SiproDetalleConsolidadoRegistro registro : registros) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < DETAIL_HEADERS.size(); i++) {
                String headerName = DETAIL_HEADERS.get(i);
                writeCell(row.createCell(i), mapDetalle(headerName, registro), null);
            }
        }

        protectReadOnlySheet(sheet);
    }

    private void protectReadOnlySheet(Sheet sheet) {
        // Bloquea edicion de celdas y cambios de formato a nivel de hoja.
        sheet.protectSheet(SHEET_PROTECTION_PASSWORD);
    }

    private String mapDetalle(String header, SiproDetalleConsolidadoRegistro registro) {
        return switch (header) {
            case "NIT" -> defaultString(registro.getNit());
            case "OFICINA" -> defaultString(registro.getOficina());
            case "DOCUMENTO" -> defaultString(registro.getDocumento());
            case "MONEDA" -> defaultString(registro.getMoneda());
            case "MODALIDAD" -> defaultString(registro.getModalidad());
            case "ANOINIOBL" -> defaultString(registro.getAnoiniobl());
            case "MESINIOBL" -> defaultString(registro.getMesiniobl());
            case "DIAINIOBL" -> defaultString(registro.getDiainiobl());
            case "ANOVCTO" -> defaultString(registro.getAnovcto());
            case "MESVCTO" -> defaultString(registro.getMesvcto());
            case "DIAVCTO" -> defaultString(registro.getDiavcto());
            case "ANOVCTOFIN" -> defaultString(registro.getAnovctofin());
            case "MESVCTOFIN" -> defaultString(registro.getMesvctofin());
            case "DIAVCTOFIN" -> defaultString(registro.getDiavctofin());
            case "CTAPUC" -> defaultString(registro.getCtapuc());
            case "VLRINIOBL" -> defaultString(registro.getVlriniobl());
            case "SALDO" -> defaultString(registro.getSaldo());
            case "SDOOTRCTAS" -> defaultString(registro.getSdootrctas());
            case "INTERESES" -> defaultString(registro.getIntereses());
            case "SDOVENCIDO" -> defaultString(registro.getSdovencido());
            case "INTCTASORD" -> defaultString(registro.getIntctasord());
            case "CLASIFICACION" -> defaultString(registro.getClasificacion());
            case "USUARIO" -> defaultString(registro.getUsuario());
            case "PRODUCTO" -> defaultString(registro.getProducto());
            case "TIPO_ID" -> defaultString(registro.getTipoId());
            case "PRODUCTO_ORIGEN" -> defaultString(registro.getProductoOrigen());
            case "SEGMENTO" -> defaultString(registro.getSegmento());
            case "FECHA_CORTE" -> defaultString(registro.getFechaCorte());
            case "DESCRIPCION" -> defaultString(registro.getDescripcion());
            case "USUARIO_CARGADOR" -> defaultString(registro.getUsuarioCargador());
            case "USUARIO_APROBADOR" -> defaultString(registro.getUsuarioAprobador());
            default -> "";
        };
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBoldStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createNumericStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private void writeCell(Cell cell, String value, CellStyle style) {
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private BigDecimal obtenerMetrica(ConsolidacionResumenResponse resumen, String codigo) {
        if (resumen.getMetricasComparacion() == null) {
            return BigDecimal.ZERO;
        }
        return resumen.getMetricasComparacion().stream()
                .filter(m -> codigo.equals(m.getCodigo()))
                .map(ConsolidacionResumenResponse.ComparacionMetricaResumen::getDiferencia)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private String formatSegmentoUnoCantidad(ConsolidacionResumenResponse.ProductoResumen producto) {
        String nombre = normalizeProductName(producto.getNombreProducto());
        if (("recaudos".equals(nombre) || "seguridad".equals(nombre)) && producto.getCantidadRegistros() == 0L) {
            return "No aplica";
        }
        return formatInteger(BigDecimal.valueOf(producto.getCantidadRegistros()));
    }

    private String formatSegmentoUnoValor(ConsolidacionResumenResponse.ProductoResumen producto) {
        String nombre = normalizeProductName(producto.getNombreProducto());
        if (("recaudos".equals(nombre) || "seguridad".equals(nombre))
                && defaultDecimal(producto.getTotalVlrIniObl()).compareTo(BigDecimal.ZERO) == 0) {
            return "No aplica";
        }
        return formatCurrency(defaultDecimal(producto.getTotalVlrIniObl()));
    }

    private String formatSegmentoDosCantidad(ConsolidacionResumenResponse.ProductoResumen producto) {
        if ("tipz".equals(normalizeProductName(producto.getNombreProducto()))
                && producto.getCantidadRegistrosFullIfrs() == 0L) {
            return "No aplica";
        }
        return formatInteger(BigDecimal.valueOf(producto.getCantidadRegistrosFullIfrs()));
    }

    private String formatDifference(BigDecimal value, boolean currency) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        String formatted = currency ? formatCurrency(safe.abs()) : formatInteger(safe.abs());
        if (safe.compareTo(BigDecimal.ZERO) > 0) {
            return "+" + formatted;
        }
        if (safe.compareTo(BigDecimal.ZERO) < 0) {
            return "-" + formatted;
        }
        return formatted;
    }

    private String formatInteger(BigDecimal value) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO"));
        return nf.format(value == null ? BigDecimal.ZERO : value);
    }

    private String formatCurrency(BigDecimal value) {
        NumberFormat cf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        cf.setMinimumFractionDigits(0);
        cf.setMaximumFractionDigits(0);
        return cf.format(defaultDecimal(value).setScale(0, RoundingMode.HALF_UP));
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String defaultString(Object value) {
        return value == null ? "" : value.toString();
    }

    private String normalizeProductName(String productName) {
        return productName == null ? "" : productName.trim().toLowerCase(Locale.forLanguageTag("es-CO"));
    }

    public record GeneratedResumenReport(String fileName, byte[] content) {
    }
}
