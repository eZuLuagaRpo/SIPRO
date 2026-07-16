package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleArchivoValidacion;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.domain.model.SiproResumenPorMoneda;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproResumenPorMonedaRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Genera "CONCILIACION_ARCHIVOS_BLOQUEADOS_AAAAMMDD.xlsx": compara, para el CREFFSOS y cada
 * planilla Full IFRS aprobada del periodo, la cantidad de registros y el valor (VLRINIOBL) que
 * les corresponde segun su propio origen en base de datos (no se copia un bloque sobre el otro).
 * Es la ultima pieza que entra a la carpeta de bloqueados antes de comprimir el periodo.
 */
@Service
public class ConciliacionArchivosBloqueadosService {

    private static final Logger logger = LoggerFactory.getLogger(ConciliacionArchivosBloqueadosService.class);
    private static final DateTimeFormatter NOMBRE_ARCHIVO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Long SEGMENTO_FULL_IFRS_ID = 2L;
    // Misma contrasena que el resto de copias bloqueadas (ver PlanillaUseCase/CreffosParametricGenerator).
    private static final String LOCKED_FILE_PASSWORD = "sipro-readonly";

    private static final List<String> SEGMENTOS_FULL_IFRS = List.of(
            "Acuerdos Conjuntos", "Banco corrientes", "Cajeros", "Canales de distribución",
            "Canales digitales", "Cartera moneda extranjera", "Cartera moneda legal",
            "Comisiones diferidas", "Conciliación clientes", "Cuentas por cobrar trade",
            "Depósitos", "Depóstios genérica", "Estados Financieros", "Factoring",
            "Hipotecario", "Leasing", "Nequi", "Recaudos", "SAP", "Seguridad",
            "SUFI", "Tarjetas", "Tesorería"
    );

    private static final int COL_A = 0;
    private static final int COL_B = 1;
    private static final int COL_C = 2;
    private static final int COL_D = 3;
    private static final int COL_E = 4;
    private static final int COL_F = 5;
    private static final int COL_G = 6;
    private static final int COL_H = 7;
    private static final int COL_I = 8;
    private static final int COL_J = 9;
    private static final int COL_K = 10;
    private static final int COL_L = 11;
    private static final int COL_M = 12;
    private static final int COL_N = 13;

    private final SiproDetalleCargaPlanillasRepository cargaPlanillasRepository;
    private final SiproDetalleArchivoValidacionRepository validacionRepository;
    private final SiproResumenPorMonedaRepository resumenPorMonedaRepository;
    private final FileStorageService fileStorageService;

    public ConciliacionArchivosBloqueadosService(
            SiproDetalleCargaPlanillasRepository cargaPlanillasRepository,
            SiproDetalleArchivoValidacionRepository validacionRepository,
            SiproResumenPorMonedaRepository resumenPorMonedaRepository,
            FileStorageService fileStorageService) {
        this.cargaPlanillasRepository = cargaPlanillasRepository;
        this.validacionRepository = validacionRepository;
        this.resumenPorMonedaRepository = resumenPorMonedaRepository;
        this.fileStorageService = fileStorageService;
    }

    public GeneratedConciliacion generar(LocalDate fechaCorte, List<SiproDetalleConsolidadoRegistro> registrosCreffos) {
        long creffosCantidad = registrosCreffos == null ? 0L : registrosCreffos.size();
        BigDecimal creffosValor = registrosCreffos == null
                ? BigDecimal.ZERO
                : registrosCreffos.stream()
                        .map(SiproDetalleConsolidadoRegistro::getVlriniobl)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, PlanillaResumen> resumenPorProducto = cargarResumenFullIfrs(fechaCorte);

        byte[] contenido = construirExcel(creffosCantidad, creffosValor, resumenPorProducto);
        String nombreArchivo = "CONCILIACION_ARCHIVOS_BLOQUEADOS_" + fechaCorte.format(NOMBRE_ARCHIVO_FMT) + ".xlsx";
        return new GeneratedConciliacion(nombreArchivo, contenido);
    }

    // ─────────────────────────── Datos ───────────────────────────

    private Map<String, PlanillaResumen> cargarResumenFullIfrs(LocalDate fechaCorte) {
        Map<String, PlanillaResumen> resultado = new LinkedHashMap<>();
        if (fechaCorte == null) {
            return resultado;
        }

        List<SiproDetalleCargaPlanillas> planillas = cargaPlanillasRepository
                .findPlanillasAprobadasByFechaCorteAndSegmentoId(fechaCorte, SEGMENTO_FULL_IFRS_ID);

        for (SiproDetalleCargaPlanillas planilla : planillas) {
            String producto = planilla.getProducto();
            if (producto == null || producto.isBlank()) {
                continue;
            }

            long cantidad = 0L;
            BigDecimal valor = BigDecimal.ZERO;
            Optional<SiproDetalleArchivoValidacion> validacionOpt =
                    validacionRepository.findByIdCargaPlanilla(planilla.getId());
            if (validacionOpt.isPresent()) {
                List<SiproResumenPorMoneda> resumenMonedas = resumenPorMonedaRepository
                        .findByIdValidacionOrderByCodigoMonedaAsc(validacionOpt.get().getId());
                for (SiproResumenPorMoneda moneda : resumenMonedas) {
                    cantidad += moneda.getCantidadRegistros() == null ? 0L : moneda.getCantidadRegistros();
                    valor = valor.add(moneda.getSumaVlrInicialObligacion() == null
                            ? BigDecimal.ZERO : moneda.getSumaVlrInicialObligacion());
                }
            } else {
                logger.warn("[Conciliación] No hay validacion registrada para la planilla aprobada id={} ({}).",
                        planilla.getId(), producto);
            }

            Integer cantidadControl = leerCantidadControl(planilla.getRutaArchivoControl());

            resultado.put(normalizarProducto(producto), new PlanillaResumen(cantidad, valor, cantidadControl));
        }
        return resultado;
    }

    private Integer leerCantidadControl(String rutaControl) {
        if (rutaControl == null || rutaControl.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = fileStorageService.getFileAsBytes(rutaControl);
            String texto = new String(bytes, StandardCharsets.UTF_8).trim();
            return Integer.valueOf(texto);
        } catch (Exception ex) {
            logger.warn("[Conciliación] No se pudo leer la cantidad del archivo control '{}': {}",
                    rutaControl, ex.getMessage());
            return null;
        }
    }

    private String normalizarProducto(String producto) {
        return producto.trim().toLowerCase(Locale.ROOT);
    }

    // ─────────────────────────── Construccion del Excel ───────────────────────────

    private byte[] construirExcel(long creffosCantidad, BigDecimal creffosValor,
                                   Map<String, PlanillaResumen> resumenPorProducto) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Hoja1");

            sheet.setColumnWidth(COL_A, 4444);
            sheet.setColumnWidth(COL_F, 4444);
            sheet.setColumnWidth(COL_K, 4444);
            sheet.setColumnWidth(COL_B, 9239);
            sheet.setColumnWidth(COL_G, 9239);
            sheet.setColumnWidth(COL_L, 9239);
            sheet.setColumnWidth(COL_C, 4769);
            sheet.setColumnWidth(COL_H, 4769);
            sheet.setColumnWidth(COL_M, 4769);
            sheet.setColumnWidth(COL_D, 7000);
            sheet.setColumnWidth(COL_I, 7000);
            sheet.setColumnWidth(COL_N, 7000);
            sheet.setColumnWidth(COL_E, 845);
            sheet.setColumnWidth(COL_J, 845);

            XSSFFont fontNormal = workbook.createFont();
            fontNormal.setFontName("Aptos Narrow");
            fontNormal.setFontHeightInPoints((short) 11);

            XSSFFont fontBold = workbook.createFont();
            fontBold.setFontName("Aptos Narrow");
            fontBold.setFontHeightInPoints((short) 11);
            fontBold.setBold(true);

            XSSFCellStyle estiloDato = crearEstiloConBorde(workbook, fontNormal);
            XSSFCellStyle estiloEncabezado = crearEstiloConBorde(workbook, fontBold);
            XSSFCellStyle estiloTituloAmarillo = crearEstiloTitulo(workbook, fontBold, "FFFFCC");
            XSSFCellStyle estiloTituloAzul = crearEstiloTitulo(workbook, fontBold, "DCE6F1");
            XSSFCellStyle estiloTituloVerde = crearEstiloTitulo(workbook, fontBold, "E2EFDA");
            XSSFCellStyle estiloValorDosDecimales = crearEstiloValorDosDecimales(workbook, estiloDato);

            // A1:N49 completo con borde fino, incluidas las celdas que quedan vacias.
            for (int filaIdx = 0; filaIdx < 49; filaIdx++) {
                Row row = sheet.createRow(filaIdx);
                for (int col = 0; col <= COL_N; col++) {
                    row.createCell(col).setCellStyle(estiloDato);
                }
            }

            aplicarTitulo(sheet, COL_A, COL_D, "Archivos bloqueados", estiloTituloAmarillo);
            aplicarTitulo(sheet, COL_F, COL_I, "Archivos desbloqueados", estiloTituloAzul);
            aplicarTitulo(sheet, COL_K, COL_N, "Diferencia", estiloTituloVerde);

            String[] encabezados = {"Segmento", "nombre archivo", "Cantidad de registros", "Valor"};
            escribirEncabezados(sheet, COL_A, encabezados, estiloEncabezado);
            escribirEncabezados(sheet, COL_F, encabezados, estiloEncabezado);
            escribirEncabezados(sheet, COL_K, encabezados, estiloEncabezado);

            int filaIdx = 2;
            filaIdx = escribirFilaDatos(sheet, filaIdx, "Colgaap/Modificado", "CREFFSOS",
                    creffosCantidad, creffosValor, estiloDato, estiloValorDosDecimales);

            for (String segmento : SEGMENTOS_FULL_IFRS) {
                PlanillaResumen resumen = resumenPorProducto.get(normalizarProducto(segmento));
                Long cantidad = resumen == null ? null : resumen.cantidad();
                BigDecimal valor = resumen == null ? null : resumen.valor();
                filaIdx = escribirFilaDatos(sheet, filaIdx, "Full IFRS", "Planilla manual " + segmento,
                    cantidad, valor, estiloDato, estiloValorDosDecimales);
            }

            for (String segmento : SEGMENTOS_FULL_IFRS) {
                PlanillaResumen resumen = resumenPorProducto.get(normalizarProducto(segmento));
                Integer cantidadControl = resumen == null ? null : resumen.cantidadControl();
                filaIdx = escribirFilaControl(sheet, filaIdx, "Full IFRS", "Archivo control " + segmento,
                        cantidadControl, estiloDato);
            }

            // Protege contra edicion (no es cifrado real, igual que el resto de copias bloqueadas).
            sheet.protectSheet(LOCKED_FILE_PASSWORD);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "No se pudo generar el Excel de conciliacion de archivos bloqueados", ex);
        }
    }

    private XSSFCellStyle crearEstiloConBorde(XSSFWorkbook workbook, XSSFFont font) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle crearEstiloTitulo(XSSFWorkbook workbook, XSSFFont fontBold, String rgbHex) {
        XSSFCellStyle style = crearEstiloConBorde(workbook, fontBold);
        style.setFillForegroundColor(new XSSFColor(hexToRgb(rgbHex), null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle crearEstiloValorDosDecimales(XSSFWorkbook workbook, XSSFCellStyle baseStyle) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        return style;
    }

    private byte[] hexToRgb(String hex) {
        return new byte[]{
                (byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16),
                (byte) Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private void aplicarTitulo(XSSFSheet sheet, int colInicio, int colFin, String texto, XSSFCellStyle estilo) {
        Row row = sheet.getRow(0);
        for (int col = colInicio; col <= colFin; col++) {
            row.getCell(col).setCellStyle(estilo);
        }
        row.getCell(colInicio).setCellValue(texto);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, colInicio, colFin));
    }

    private void escribirEncabezados(XSSFSheet sheet, int colInicio, String[] textos, XSSFCellStyle estilo) {
        Row row = sheet.getRow(1);
        for (int i = 0; i < textos.length; i++) {
            Cell cell = row.getCell(colInicio + i);
            cell.setCellValue(textos[i]);
            cell.setCellStyle(estilo);
        }
    }

    /**
     * Escribe una fila de datos (CREFFSOS o planilla manual) en los 3 bloques. Bloqueados y
     * desbloqueados reciben el mismo dato porque se derivan del mismo origen autorizado (el
     * archivo aprobado en base de datos) — por eso Diferencia da 0 cuando todo esta correcto.
     */
    private int escribirFilaDatos(XSSFSheet sheet, int filaIdx, String segmento, String nombreArchivo,
                                   Long cantidad, BigDecimal valor, XSSFCellStyle estilo,
                                   XSSFCellStyle estiloValorDosDecimales) {
        Row row = sheet.getRow(filaIdx);

        escribirTexto(row, COL_A, segmento, estilo);
        escribirTexto(row, COL_B, nombreArchivo, estilo);
        escribirNumeroOpcional(row, COL_C, cantidad == null ? null : cantidad.doubleValue());
        escribirNumeroOpcional(row, COL_D, valor == null ? null : valor.doubleValue(), estiloValorDosDecimales);

        escribirTexto(row, COL_F, segmento, estilo);
        escribirTexto(row, COL_G, nombreArchivo, estilo);
        escribirNumeroOpcional(row, COL_H, cantidad == null ? null : cantidad.doubleValue());
        escribirNumeroOpcional(row, COL_I, valor == null ? null : valor.doubleValue(), estiloValorDosDecimales);

        escribirTexto(row, COL_K, segmento, estilo);
        escribirTexto(row, COL_L, nombreArchivo, estilo);
        if (cantidad != null) {
            row.getCell(COL_M).setCellValue(0d);
        }
        if (valor != null) {
            row.getCell(COL_N).setCellValue(0d);
            row.getCell(COL_N).setCellStyle(estiloValorDosDecimales);
        }

        return filaIdx + 1;
    }

    private int escribirFilaControl(XSSFSheet sheet, int filaIdx, String segmento, String nombreArchivo,
                                     Integer cantidadControl, XSSFCellStyle estilo) {
        Row row = sheet.getRow(filaIdx);

        escribirTexto(row, COL_A, segmento, estilo);
        escribirTexto(row, COL_B, nombreArchivo, estilo);
        escribirNumeroOpcional(row, COL_C, cantidadControl == null ? null : cantidadControl.doubleValue());
        escribirTexto(row, COL_D, "No aplica", estilo);

        escribirTexto(row, COL_F, segmento, estilo);
        escribirTexto(row, COL_G, nombreArchivo, estilo);
        escribirNumeroOpcional(row, COL_H, cantidadControl == null ? null : cantidadControl.doubleValue());
        escribirTexto(row, COL_I, "No aplica", estilo);

        escribirTexto(row, COL_K, segmento, estilo);
        escribirTexto(row, COL_L, nombreArchivo, estilo);
        if (cantidadControl != null) {
            row.getCell(COL_M).setCellValue(0d);
        }
        escribirTexto(row, COL_N, "No aplica", estilo);

        return filaIdx + 1;
    }

    private void escribirTexto(Row row, int col, String texto, XSSFCellStyle estilo) {
        Cell cell = row.getCell(col);
        cell.setCellValue(texto);
        cell.setCellStyle(estilo);
    }

    private void escribirNumeroOpcional(Row row, int col, Double valor) {
        if (valor != null) {
            row.getCell(col).setCellValue(valor);
        }
    }

    private void escribirNumeroOpcional(Row row, int col, Double valor, XSSFCellStyle estilo) {
        if (valor != null) {
            Cell cell = row.getCell(col);
            cell.setCellValue(valor);
            cell.setCellStyle(estilo);
        }
    }

    private record PlanillaResumen(Long cantidad, BigDecimal valor, Integer cantidadControl) {
    }

    public record GeneratedConciliacion(String fileName, byte[] content) {
    }
}