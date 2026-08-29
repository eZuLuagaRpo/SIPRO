package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.infrastructure.repository.ClienteLzRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.shared.utils.XlsxStreamingReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Genera el consolidado de planillas Full IFRS (segmento 2) en paralelo al de Colgaap.
 * No persiste registros en BD; únicamente produce el archivo Excel y lo publica en red.
 */
@Service
public class ConsolidacionFullIfrsService {

    private static final Logger logger = LoggerFactory.getLogger(ConsolidacionFullIfrsService.class);

    private static final Long SEGMENTO_FULL_IFRS_ID = 2L;
    private static final String CONSOLIDADOS_PREFIX = "consolidados/";
    private static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FECHA_COMPACT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FECHA_HORA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_LZ_LOOKUP_CHUNK_SIZE = 1000;

    private static final List<String> HEADERS_ENTRADA = List.of(
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
            "USUARIO",
            "PRODUCTO");

    private static final List<String> HEADERS_SALIDA = List.of(
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
            "USUARIO",
            "PRODUCTO",
            "TIPO_ID",
            "PRODUCTO_ORIGEN",
            "SEGMENTO",
            "FECHA_CORTE",
            "DESCRIPCION",
            "USUARIO_CARGADOR",
            "USUARIO_APROBADOR",
            "FECHA_SOLICITUD",
            "FECHA_APROBACION");

    private final SiproDetalleCargaPlanillasRepository planillaRepository;
    private final ClienteLzRepository clienteLzRepository;
    private final FileStorageService fileStorageService;
    private final ParametroUnicoService parametroUnicoService;

    public ConsolidacionFullIfrsService(SiproDetalleCargaPlanillasRepository planillaRepository,
                                        ClienteLzRepository clienteLzRepository,
                                        FileStorageService fileStorageService,
                                        ParametroUnicoService parametroUnicoService) {
        this.planillaRepository = planillaRepository;
        this.clienteLzRepository = clienteLzRepository;
        this.fileStorageService = fileStorageService;
        this.parametroUnicoService = parametroUnicoService;
    }

    /**
     * Genera el Excel consolidado Full IFRS para el periodo dado.
     *
     * @return advertencia si falló algún paso no crítico, o {@code null} si todo salió bien.
     */
    public String generarConsolidado(LocalDate periodoValoracion) {
        List<SiproDetalleCargaPlanillas> planillas = obtenerPlanillas(periodoValoracion);
        if (planillas.isEmpty()) {
            logger.info("Full IFRS - periodo {}: no hay planillas aprobadas para consolidar.", periodoValoracion);
            return null;
        }

        logger.info("Full IFRS - periodo {}: iniciando consolidación de {} planillas.", periodoValoracion, planillas.size());

        try (FullIfrsExcelWriter writer = new FullIfrsExcelWriter()) {
            for (SiproDetalleCargaPlanillas planilla : planillas) {
                if (Boolean.TRUE.equals(planilla.getNoReportaDatos())) {
                    logger.info("Full IFRS - planilla {}: aprobación sin datos, se omite.", planilla.getId());
                    continue;
                }

                procesarPlanilla(periodoValoracion, planilla, writer);
            }

            byte[] contenido = writer.toByteArray();
            return publicarConsolidado(contenido, periodoValoracion);

        } catch (Exception ex) {
            logger.error("Full IFRS - periodo {}: error generando el consolidado: {}", periodoValoracion, ex.getMessage(), ex);
            return "Consolidado Full IFRS no generado: " + resumirMensaje(ex);
        }
    }

    private List<SiproDetalleCargaPlanillas> obtenerPlanillas(LocalDate periodoValoracion) {
        return planillaRepository
                .findPlanillasAprobadasByFechaCorteAndSegmentoId(periodoValoracion, SEGMENTO_FULL_IFRS_ID)
                .stream()
                .filter(this::esPlanillaConsolidable)
                .sorted(Comparator.comparing(SiproDetalleCargaPlanillas::getId))
                .collect(Collectors.toList());
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
        return esXlsx(ruta) || esXlsx(planilla.getNombreArchivoFuente());
    }

    private boolean esXlsx(String ruta) {
        return ruta != null && ruta.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    private void procesarPlanilla(LocalDate periodoValoracion,
                                  SiproDetalleCargaPlanillas planilla,
                                  FullIfrsExcelWriter writer) throws IOException {
        String rutaArchivo = planilla.getRutaArchivoAlmacenamiento();
        if (rutaArchivo == null || rutaArchivo.isBlank()) {
            logger.warn("Full IFRS - planilla {}: sin ruta de almacenamiento, se omite.", planilla.getId());
            return;
        }

        ExcelScanResult scanResult;
        try (InputStream is = fileStorageService.openStream(rutaArchivo)) {
            scanResult = escanearExcel(is, planilla.getNombreArchivoFuente());
        }

        Map<String, String> tipoIdPorNit = cargarTipoIdPorNit(scanResult.nits());

        try (InputStream is = fileStorageService.openStream(rutaArchivo)) {
            XlsxStreamingReader.readFirstSheet(is, (rowNumber, rowValues) -> {
                if (rowNumber == 1 || esFilaVacia(rowValues)) {
                    return;
                }
                Map<String, String> fila = convertirFila(scanResult.columnIndexByHeader(), rowValues);
                String nit = normalizeLookupDocument(getValue(fila, "NIT"));
                String tipoId = firstNonBlank(tipoIdPorNit.get(nit), getValue(fila, "TIPO_ID"));
                writer.appendRow(construirFila(fila, planilla, tipoId));
            });
        }

        logger.info("Full IFRS - periodo {} planilla {}: procesada.", periodoValoracion, planilla.getId());
    }

    private ExcelScanResult escanearExcel(InputStream inputStream, String nombreArchivo) throws IOException {
        Map<String, Integer> columnIndexByHeader = new LinkedHashMap<>();
        Set<String> nits = new LinkedHashSet<>();

        XlsxStreamingReader.readFirstSheet(inputStream, (rowNumber, rowValues) -> {
            if (rowNumber == 1) {
                for (int i = 0; i < rowValues.size(); i++) {
                    String raw = rowValues.get(i);
                    if (raw != null && !raw.isBlank()) {
                        columnIndexByHeader.put(normalizeHeader(raw), i);
                    }
                }
                validarHeaders(columnIndexByHeader.keySet(), nombreArchivo);
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

        return new ExcelScanResult(columnIndexByHeader, nits);
    }

    private void validarHeaders(Collection<String> headers, String nombreArchivo) {
        List<String> faltantes = HEADERS_ENTRADA.stream()
                .filter(h -> !headers.contains(h))
                .toList();
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("El archivo Full IFRS " + nombreArchivo
                    + " no contiene todas las columnas requeridas. Faltan: "
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
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .toList();

        Map<String, String> resultado = new LinkedHashMap<>();
        for (int start = 0; start < nitsNormalizados.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, nitsNormalizados.size());
            List<String> lote = nitsNormalizados.subList(start, end);
            for (ClienteLzRepository.DocumentoTipoIdProjection p : clienteLzRepository.findLatestTipoIdByNumeroIdIn(lote)) {
                String nit = normalizeLookupDocument(p.getNumeroId());
                if (!nit.isBlank()) {
                    resultado.putIfAbsent(nit, blankToNull(p.getTipoId()));
                }
            }
        }

        logger.info("Full IFRS - TIPO_ID resuelto para {} de {} NITs.", resultado.size(), nitsNormalizados.size());
        return resultado;
    }

    private Map<String, String> convertirFila(Map<String, Integer> columnIndexByHeader, List<String> rowValues) {
        Map<String, String> fila = new LinkedHashMap<>();
        for (String header : columnIndexByHeader.keySet()) {
            fila.put(header, obtenerValorFila(rowValues, columnIndexByHeader, header));
        }
        return fila;
    }

    private Map<String, String> construirFila(Map<String, String> fila,
                                               SiproDetalleCargaPlanillas planilla,
                                               String tipoId) {
        Map<String, String> resultado = new LinkedHashMap<>(fila);
        resultado.put("TIPO_ID", firstNonBlank(tipoId, getValue(fila, "TIPO_ID")));
        for (String campo : List.of("VLRINIOBL", "SALDO", "SDOOTRCTAS", "INTERESES", "SDOVENCIDO", "INTCTASORD")) {
            if (resultado.getOrDefault(campo, "").isBlank()) {
                resultado.put(campo, "0");
            }
        }
        resultado.put("PRODUCTO_ORIGEN", firstNonBlank(planilla.getProducto(), ""));
        resultado.put("SEGMENTO", firstNonBlank(planilla.getSegmento(), ""));
        resultado.put("FECHA_CORTE", planilla.getFechaCorteInformacion() == null
                ? "" : planilla.getFechaCorteInformacion().toString());
        resultado.put("DESCRIPCION", firstNonBlank(planilla.getDescripcionLarga(), ""));
        resultado.put("USUARIO_CARGADOR", firstNonBlank(planilla.getNombreUsuarioCarga(), ""));
        resultado.put("USUARIO_APROBADOR", firstNonBlank(planilla.getUsuarioAprobador(), ""));
        resultado.put("FECHA_SOLICITUD", planilla.getFechaCreacion() != null
                ? planilla.getFechaCreacion().format(FECHA_HORA_FMT) : "");
        resultado.put("FECHA_APROBACION", planilla.getFechaAprobacion() != null
                ? planilla.getFechaAprobacion().toLocalDateTime().format(FECHA_HORA_FMT) : "");
        return resultado;
    }

    private String publicarConsolidado(byte[] contenido, LocalDate periodoValoracion) {
        String advertenciaInterna = guardarEnStorage(contenido, periodoValoracion);
        String advertenciaRed = publicarEnRed(contenido, periodoValoracion);

        if (advertenciaInterna != null) {
            return advertenciaInterna;
        }
        return advertenciaRed;
    }

    private String guardarEnStorage(byte[] contenido, LocalDate periodoValoracion) {
        try {
            String fecha = periodoValoracion.format(FECHA_FMT);
            String ruta = CONSOLIDADOS_PREFIX + fecha + "/CONSOLIDADO_FULL_IFRS_" + fecha + ".xlsx";
            fileStorageService.storeBytes(contenido, ruta, CONTENT_TYPE_XLSX);
            logger.info("Full IFRS - Excel consolidado guardado en storage interno: {}", ruta);
            return null;
        } catch (Exception ex) {
            logger.warn("Full IFRS - no se pudo guardar en storage: {}", ex.getMessage());
            return "Consolidado Full IFRS generado pero no guardado en storage";
        }
    }

    private String publicarEnRed(byte[] contenido, LocalDate periodoValoracion) {
        String outputDir = parametroUnicoService.getString("CREFFSOS_RUTA_SALIDA", "");
        if (outputDir == null || outputDir.isBlank()) {
            return null;
        }
        try {
            Path periodoDir = Path.of(outputDir.trim()).getParent()
                    .resolve("Consolidado")
                    .resolve(periodoValoracion.format(FECHA_FMT));
            Files.createDirectories(periodoDir);
            String nombreArchivo = "CONSOLIDADO_FULL_IFRS_" + periodoValoracion.format(FECHA_COMPACT_FMT) + ".xlsx";
            Path targetFile = periodoDir.resolve(nombreArchivo);
            Files.write(targetFile, contenido,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            logger.info("Full IFRS - Excel consolidado publicado en red: {}", targetFile);
            return null;
        } catch (Exception ex) {
            logger.warn("Full IFRS - no se pudo copiar a ruta compartida: {}. Motivo: {}",
                    outputDir.trim(), ex.getMessage());
            return "Consolidado Full IFRS generado pero no copiado a red";
        }
    }

    // ── Utilidades de lectura Excel ──────────────────────────────────────────

    private String obtenerValorFila(List<String> rowValues, Map<String, Integer> columnIndexByHeader, String header) {
        Integer index = columnIndexByHeader.get(header);
        if (index == null || index < 0 || index >= rowValues.size()) {
            return "";
        }
        String value = rowValues.get(index);
        return value == null ? "" : value.trim();
    }

    private boolean esFilaVacia(List<String> rowValues) {
        return rowValues == null || rowValues.stream().allMatch(v -> v == null || v.isBlank());
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
    }

    private String normalizeLookupDocument(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            String cleaned = normalizeNumber(value);
            return Long.toString(new BigDecimal(cleaned).longValueExact());
        } catch (ArithmeticException | NumberFormatException ex) {
            return value.trim();
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

    private String getValue(Map<String, String> fila, String key) {
        return fila.getOrDefault(key, "").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        String a = blankToNull(first);
        return a != null ? a : blankToNull(second);
    }

    private String resumirMensaje(Exception ex) {
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? ex.getClass().getSimpleName() : msg;
    }

    private record ExcelScanResult(Map<String, Integer> columnIndexByHeader, Set<String> nits) {}

    // ── Writer interno ───────────────────────────────────────────────────────

    private final class FullIfrsExcelWriter implements AutoCloseable {

        private static final int COLUMN_WIDTH = 25 * 256;
        private static final Set<String> DECIMAL_COLUMNS = Set.of(
                "VLRINIOBL", "SALDO", "SDOOTRCTAS", "INTERESES", "SDOVENCIDO", "INTCTASORD");

        private final SXSSFWorkbook workbook;
        private final Sheet sheet;
        private final CellStyle decimalStyle;
        private int currentRowIndex = 1;

        private FullIfrsExcelWriter() {
            this.workbook = new SXSSFWorkbook(200);
            this.sheet = workbook.createSheet("CONSOLIDADO");
            DataFormat dataFormat = workbook.createDataFormat();
            this.decimalStyle = workbook.createCellStyle();
            this.decimalStyle.setDataFormat(dataFormat.getFormat("0.00"));
            crearHeader();
        }

        private void crearHeader() {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS_SALIDA.size(); i++) {
                sheet.setColumnWidth(i, COLUMN_WIDTH);
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS_SALIDA.get(i));
                cell.setCellStyle(headerStyle);
            }
        }

        private void appendRow(Map<String, String> fila) {
            Row row = sheet.createRow(currentRowIndex++);
            for (int i = 0; i < HEADERS_SALIDA.size(); i++) {
                String header = HEADERS_SALIDA.get(i);
                Cell cell = row.createCell(i);
                String valor = fila.getOrDefault(header, "");
                setCellValue(cell, valor);
                if (DECIMAL_COLUMNS.contains(header)) {
                    cell.setCellStyle(decimalStyle);
                }
            }
        }

        private void setCellValue(Cell cell, String valor) {
            if (valor == null || valor.isBlank()) {
                cell.setBlank();
                return;
            }
            try {
                String cleaned = valor.trim().replace(",", ".");
                cell.setCellValue(Double.parseDouble(cleaned));
                return;
            } catch (NumberFormatException ignored) {
                // no es numérico, se escribe como texto
            }
            cell.setCellValue(valor);
        }

        private byte[] toByteArray() throws IOException {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }

        @Override
        public void close() throws IOException {
            workbook.dispose();
            workbook.close();
        }
    }
}
