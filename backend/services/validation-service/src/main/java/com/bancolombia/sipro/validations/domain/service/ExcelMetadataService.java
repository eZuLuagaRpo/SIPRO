package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleArchivoValidacion;
import com.bancolombia.sipro.validations.domain.model.SiproResumenPorMoneda;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository;
import com.bancolombia.sipro.validations.shared.utils.XlsxStreamingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extrae metadatos del archivo cargado y persiste métricas técnicas y de negocio asociadas.
 */
@Service
public class ExcelMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelMetadataService.class);
    private static final int DEFAULT_ERROR_SUMMARY_TRUNCATE_LIMIT = 10;

    private final SiproDetalleArchivoValidacionRepository validacionRepository;
    private final com.bancolombia.sipro.validations.infrastructure.repository.SiproResumenPorMonedaRepository resumenRepository;
    private final DynamicExcelValidationService dynamicValidationService;
    private final ParametroUnicoService parametroUnicoService;

    public ExcelMetadataService(SiproDetalleArchivoValidacionRepository validacionRepository,
            com.bancolombia.sipro.validations.infrastructure.repository.SiproResumenPorMonedaRepository resumenRepository,
            DynamicExcelValidationService dynamicValidationService,
            ParametroUnicoService parametroUnicoService) {
        this.validacionRepository = validacionRepository;
        this.resumenRepository = resumenRepository;
        this.dynamicValidationService = dynamicValidationService;
        this.parametroUnicoService = parametroUnicoService;
    }

    /**
     * Procesa el archivo, extrae métricas y guarda el detalle de validación y
     * resumen por moneda.
     * 
     * @param archivoControl Opcional. Archivo control Full IFRS (.txt con cantidad de registros esperados)
     */
    @Transactional
    public void processAndSaveMetadata(MultipartFile file, Long idCargaPlanilla, String usuario,
            Long idUsuario, String producto, Long idSegmento, LocalDate fechaCorte, MultipartFile archivoControl) {
        try {
            logger.info("Iniciando extracción de metadatos para planilla ID: {}", idCargaPlanilla);

            // 1. Ejecutar validación de negocio para obtener errores y conteos
            List<DynamicExcelValidationService.ValidationError> businessErrors = dynamicValidationService
                .validateExcel(file, producto, idSegmento, fechaCorte, usuario);

            Set<Integer> filasConError = businessErrors.stream()
                    .filter(e -> e.getRowNumber() > 0)
                    .map(DynamicExcelValidationService.ValidationError::getRowNumber)
                    .collect(Collectors.toSet());

                int resumenLimit = parametroUnicoService.getInt(
                    "ERROR_SUMMARY_TRUNCATE_LIMIT",
                    DEFAULT_ERROR_SUMMARY_TRUNCATE_LIMIT);
            String erroresResumen = businessErrors.stream()
                    .limit(resumenLimit)
                    .map(DynamicExcelValidationService.ValidationError::toString)
                    .collect(Collectors.joining("; "));

                if (businessErrors.size() > resumenLimit) {
                erroresResumen += "; ... (" + (businessErrors.size() - resumenLimit) + " más)";
            }

            // 2. Analizar estructura y estadísticas (POI)
            SiproDetalleArchivoValidacion metadata = new SiproDetalleArchivoValidacion();
            metadata.setIdCargaPlanilla(idCargaPlanilla);
            metadata.setFechaValidacion(LocalDateTime.now());
            metadata.setUsuarioValidacion(usuario);
            // FK: id_usuario_validacion → usuario_persona.id_usuario
            if (idUsuario != null) {
                metadata.setIdUsuarioValidacion(idUsuario);
            }
            metadata.setTamanoMemoriaBytes(file.getSize());
            metadata.setCharsetDetectado("UTF-8"); // Excel OOXML es siempre UTF-8 internamente
            metadata.setFormatoFechaDetectado("dd/MM/yyyy"); // Default asunción
            metadata.setTieneEncabezados(true);

            // Inicializar valores obligatorios para evitar errores en BD
            metadata.setNumeroFilasDatos(0);
            metadata.setNumeroColumnas(0);
            metadata.setNumeroColumnasNumericas(0);
            metadata.setNumeroColumnasTexto(0);
            metadata.setTotalValoresNulos(0);
            metadata.setPorcentajeCompletitud(BigDecimal.ZERO);
            metadata.setRegistrosValidados(0);
            metadata.setRegistrosRechazados(0);
            metadata.setSaldoMinimo(BigDecimal.ZERO);
            metadata.setSaldoMaximo(BigDecimal.ZERO);
            metadata.setSaldoTotal(BigDecimal.ZERO);
            metadata.setInteresesTotal(BigDecimal.ZERO);
            metadata.setNombresColumnas("");
            metadata.setColumnasNumericas("");
            metadata.setColumnasTexto("");

            analyzeFileContent(file, metadata);

            // 3. Cruzar con resultados de validación de negocio
            int totalFilas = metadata.getNumeroFilasDatos();
            int rechazados = filasConError.size(); // Filas únicas con error
            int validados = totalFilas - rechazados;

            metadata.setRegistrosRechazados(rechazados);
            metadata.setRegistrosValidados(Math.max(0, validados));
            metadata.setErroresValidacion(erroresResumen.isEmpty() ? null : erroresResumen);
            metadata.setEstadoValidacion(rechazados == 0 ? "EXITOSO" : "CON_ERRORES");

            // 3.5. Procesar archivo control Full IFRS (si existe)
            if (archivoControl != null && !archivoControl.isEmpty()) {
                try {
                    String contenidoControl = new String(archivoControl.getBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                    Integer cantidadRegistrosControl = Integer.parseInt(contenidoControl);
                    metadata.setCantidadRegistrosControl(cantidadRegistrosControl);
                    logger.info("Control file Full IFRS procesado: {} registros esperados", cantidadRegistrosControl);
                } catch (NumberFormatException e) {
                    logger.warn("Archivo control no contiene un número entero válido: {}", archivoControl.getOriginalFilename(), e);
                    // No se relanza excepción: la validación puede continuar sin este valor
                }
            }

            // 4. Guardar
            metadata = validacionRepository.save(metadata);
            Long idValidacion = metadata.getId();
            logger.info("Metadatos guardados para planilla ID: {}. ID Validación generado: {}", idCargaPlanilla, idValidacion);

            // 5. Calcular y guardar resumen por moneda (#10)
            calculateAndSaveSummary(file, idValidacion);

        } catch (Exception e) {
            logger.error("Error al procesar metadatos para planilla {}: {}", idCargaPlanilla, e.getMessage(), e);
            // Relanzar excepción para evitar 'silent rollback' y permitir rollback completo de la transacción
            throw new RuntimeException("Error procesando metadatos del archivo: " + e.getMessage(), e);
        }
    }

    private void analyzeFileContent(MultipartFile file, SiproDetalleArchivoValidacion metadata) throws Exception {
        String originalFilename = file.getOriginalFilename();
        boolean isCsv = originalFilename != null && originalFilename.toLowerCase(Locale.ROOT).endsWith(".csv");

        if (isCsv) {
            analyzeCsvFileContent(file, metadata);
            return;
        }

        try (InputStream is = file.getInputStream()) {
            List<String> colNames = new ArrayList<>();
            List<String> colNumericas = new ArrayList<>();
            List<String> colTexto = new ArrayList<>();
            Map<Integer, Integer> numericCountMap = new HashMap<>();

            int[] rowCount = { 0 };
            int[] colCount = { 0 };
            int[] nullValues = { 0 };
            int[] totalCells = { 0 };

            BigDecimal[] saldoMin = { null };
            BigDecimal[] saldoMax = { null };
            BigDecimal[] saldoTotal = { BigDecimal.ZERO };
            BigDecimal[] interesesTotal = { BigDecimal.ZERO };
            int[] idxSaldo = { -1 };
            int[] idxIntereses = { -1 };

            XlsxStreamingReader.readFirstSheet(is, (rowNumber, rowValues) -> {
                if (rowNumber == 1) {
                    colNames.clear();
                    colNames.addAll(rowValues);
                    colCount[0] = colNames.size();
                    metadata.setNumeroColumnas(colCount[0]);
                    metadata.setNombresColumnas(String.join(",", colNames));
                    idxSaldo[0] = colNames.indexOf("SALDO");
                    idxIntereses[0] = colNames.indexOf("INTERESES");
                    return;
                }

                if (colCount[0] == 0) {
                    return;
                }

                boolean isEmptyRow = true;
                for (int index = 0; index < colCount[0]; index++) {
                    String value = getValue(rowValues, index);
                    if (value.isEmpty()) {
                        nullValues[0]++;
                    } else {
                        isEmptyRow = false;
                        try {
                            Double.parseDouble(value.replace(",", "."));
                            numericCountMap.merge(index, 1, Integer::sum);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    totalCells[0]++;
                }

                if (!isEmptyRow) {
                    rowCount[0]++;

                    BigDecimal saldo = getBigDecimalFromRow(rowValues, idxSaldo[0]);
                    if (saldo != null) {
                        saldoTotal[0] = saldoTotal[0].add(saldo);
                        if (saldoMin[0] == null || saldo.compareTo(saldoMin[0]) < 0) {
                            saldoMin[0] = saldo;
                        }
                        if (saldoMax[0] == null || saldo.compareTo(saldoMax[0]) > 0) {
                            saldoMax[0] = saldo;
                        }
                    }

                    BigDecimal intereses = getBigDecimalFromRow(rowValues, idxIntereses[0]);
                    if (intereses != null) {
                        interesesTotal[0] = interesesTotal[0].add(intereses);
                    }
                }
            });

            for (int index = 0; index < colCount[0]; index++) {
                int nums = numericCountMap.getOrDefault(index, 0);
                boolean isNumeric = rowCount[0] > 0 && ((double) nums / rowCount[0]) > 0.5;
                String name = index < colNames.size() ? colNames.get(index) : "COL_" + index;
                if (isNumeric) {
                    colNumericas.add(name);
                } else {
                    colTexto.add(name);
                }
            }

            metadata.setNumeroFilasDatos(rowCount[0]);
            metadata.setNumeroColumnasNumericas(colNumericas.size());
            metadata.setNumeroColumnasTexto(colTexto.size());
            metadata.setColumnasNumericas(String.join(",", colNumericas));
            metadata.setColumnasTexto(String.join(",", colTexto));

            metadata.setTotalValoresNulos(nullValues[0]);
            if (totalCells[0] > 0) {
                double completitud = ((double) (totalCells[0] - nullValues[0]) / totalCells[0]) * 100;
                metadata.setPorcentajeCompletitud(BigDecimal.valueOf(completitud));
            } else {
                metadata.setPorcentajeCompletitud(BigDecimal.ZERO);
            }

            metadata.setSaldoMinimo(saldoMin[0] != null ? saldoMin[0] : BigDecimal.ZERO);
            metadata.setSaldoMaximo(saldoMax[0] != null ? saldoMax[0] : BigDecimal.ZERO);
            metadata.setSaldoTotal(saldoTotal[0]);
            metadata.setInteresesTotal(interesesTotal[0]);
        }
    }

    private void analyzeCsvFileContent(MultipartFile file, SiproDetalleArchivoValidacion metadata) throws Exception {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }

            char delimiter = headerLine.contains(";") ? ';' : ',';
            List<String> colNames = Arrays.stream(headerLine.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)), -1))
                    .map(String::trim)
                    .collect(Collectors.toList());

            List<String> colNumericas = new ArrayList<>();
            List<String> colTexto = new ArrayList<>();
            Map<Integer, Integer> numericCountMap = new HashMap<>();

            int rowCount = 0;
            int colCount = colNames.size();
            int nullValues = 0;
            int totalCells = 0;

            BigDecimal saldoMin = null;
            BigDecimal saldoMax = null;
            BigDecimal saldoTotal = BigDecimal.ZERO;
            BigDecimal interesesTotal = BigDecimal.ZERO;
            int idxSaldo = colNames.indexOf("SALDO");
            int idxIntereses = colNames.indexOf("INTERESES");

            metadata.setNumeroColumnas(colCount);
            metadata.setNombresColumnas(String.join(",", colNames));

            String line;
            while ((line = reader.readLine()) != null) {
                List<String> rowValues = Arrays.stream(line.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)), -1))
                        .map(String::trim)
                        .collect(Collectors.toList());

                boolean isEmptyRow = true;
                for (int index = 0; index < colCount; index++) {
                    String value = getValue(rowValues, index);
                    if (value.isEmpty()) {
                        nullValues++;
                    } else {
                        isEmptyRow = false;
                        try {
                            Double.parseDouble(value.replace(",", "."));
                            numericCountMap.merge(index, 1, Integer::sum);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    totalCells++;
                }

                if (!isEmptyRow) {
                    rowCount++;

                    BigDecimal saldo = getBigDecimalFromRow(rowValues, idxSaldo);
                    if (saldo != null) {
                        saldoTotal = saldoTotal.add(saldo);
                        if (saldoMin == null || saldo.compareTo(saldoMin) < 0) {
                            saldoMin = saldo;
                        }
                        if (saldoMax == null || saldo.compareTo(saldoMax) > 0) {
                            saldoMax = saldo;
                        }
                    }

                    BigDecimal intereses = getBigDecimalFromRow(rowValues, idxIntereses);
                    if (intereses != null) {
                        interesesTotal = interesesTotal.add(intereses);
                    }
                }
            }

            for (int index = 0; index < colCount; index++) {
                int nums = numericCountMap.getOrDefault(index, 0);
                boolean isNumeric = rowCount > 0 && ((double) nums / rowCount) > 0.5;
                String name = index < colNames.size() ? colNames.get(index) : "COL_" + index;
                if (isNumeric) {
                    colNumericas.add(name);
                } else {
                    colTexto.add(name);
                }
            }

            metadata.setNumeroFilasDatos(rowCount);
            metadata.setNumeroColumnasNumericas(colNumericas.size());
            metadata.setNumeroColumnasTexto(colTexto.size());
            metadata.setColumnasNumericas(String.join(",", colNumericas));
            metadata.setColumnasTexto(String.join(",", colTexto));
            metadata.setTotalValoresNulos(nullValues);
            metadata.setPorcentajeCompletitud(totalCells > 0
                    ? BigDecimal.valueOf(((double) (totalCells - nullValues) / totalCells) * 100)
                    : BigDecimal.ZERO);
            metadata.setSaldoMinimo(saldoMin != null ? saldoMin : BigDecimal.ZERO);
            metadata.setSaldoMaximo(saldoMax != null ? saldoMax : BigDecimal.ZERO);
            metadata.setSaldoTotal(saldoTotal);
            metadata.setInteresesTotal(interesesTotal);
        }
    }

    private BigDecimal getBigDecimalFromRow(List<String> rowValues, int colIndex) {
        if (colIndex < 0)
            return null;
        try {
            String val = getValue(rowValues, colIndex).replace(",", ".");
            if (val.isEmpty()) {
                return null;
            }
            return new BigDecimal(val);
        } catch (Exception e) {
            return null;
        }
    }

    private void calculateAndSaveSummary(MultipartFile file, Long idValidacion) {
        try {
            // Idempotencia: borrar registros previos vinculados a esta validación
            resumenRepository.deleteByIdValidacion(idValidacion);

            String filename = file.getOriginalFilename();
            boolean isCsv = filename != null && filename.toLowerCase().endsWith(".csv");

            Map<String, Long> countByMoneda = new LinkedHashMap<>();
            Map<String, BigDecimal> sumByMoneda = new LinkedHashMap<>();

            if (isCsv) {
                procesarResumenCsv(file.getBytes(), countByMoneda, sumByMoneda);
            } else {
                try (InputStream is = file.getInputStream()) {
                    procesarResumenXlsx(is, countByMoneda, sumByMoneda);
                }
            }

            List<SiproResumenPorMoneda> lista = new ArrayList<>();
            for (Map.Entry<String, Long> entry : countByMoneda.entrySet()) {
                String monedaLabel = entry.getKey();
                SiproResumenPorMoneda entity = new SiproResumenPorMoneda();
                entity.setIdValidacion(idValidacion);
                entity.setCodigoMoneda(monedaLabel);
                entity.setTipoMoneda("COP".equals(monedaLabel) ? "LOCAL" : "EXTRANJERA");
                entity.setCantidadRegistros(entry.getValue());
                entity.setSumaVlrInicialObligacion(sumByMoneda.getOrDefault(monedaLabel, BigDecimal.ZERO));
                entity.setFechaCalculo(LocalDateTime.now());
                lista.add(entity);
            }

            if (!lista.isEmpty()) {
                resumenRepository.saveAll(lista);
                logger.info("Guardado resumen por moneda ({} registros) para validación ID: {}", lista.size(),
                        idValidacion);
            }

        } catch (Exception e) {
            logger.error("Error guardando resumen por moneda para validación {}: {}", idValidacion, e.getMessage());
            // No bloqueantes
        }
    }

    private void procesarResumenCsv(byte[] content, Map<String, Long> countByMoneda,
            Map<String, BigDecimal> sumByMoneda) throws Exception {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.ByteArrayInputStream(content),
                        java.nio.charset.StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null)
                return;

            char delim = headerLine.contains(";") ? ';' : ',';
            List<String> headers = Arrays.asList(headerLine.split(String.valueOf(delim), -1));

            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                idx.put(headers.get(i).trim().toUpperCase(), i);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] cols = line.split(String.valueOf(delim), -1);

                try {
                    String monedaCode = idx.containsKey("MONEDA") ? cols[idx.get("MONEDA")].trim() : "";
                    String monedaLabel = "0".equals(monedaCode) ? "COP" : ("1".equals(monedaCode) ? "USD" : "OTRA");

                    if (idx.containsKey("VLRINIOBL")) {
                        String vlrStr = cols[idx.get("VLRINIOBL")].trim().replace(",", ".");
                        if (!vlrStr.isEmpty()) {
                            BigDecimal vlr = new BigDecimal(vlrStr);
                            countByMoneda.put(monedaLabel, countByMoneda.getOrDefault(monedaLabel, 0L) + 1);
                            sumByMoneda.put(monedaLabel,
                                    sumByMoneda.getOrDefault(monedaLabel, BigDecimal.ZERO).add(vlr));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void procesarResumenXlsx(InputStream inputStream, Map<String, Long> countByMoneda,
            Map<String, BigDecimal> sumByMoneda) throws Exception {
        Map<String, Integer> idx = new HashMap<>();

        XlsxStreamingReader.readFirstSheet(inputStream, (rowNumber, rowValues) -> {
            if (rowNumber == 1) {
                idx.clear();
                for (int index = 0; index < rowValues.size(); index++) {
                    idx.put(rowValues.get(index).trim().toUpperCase(), index);
                }
                return;
            }

            try {
                String monedaCode = idx.containsKey("MONEDA") ? getValue(rowValues, idx.get("MONEDA")) : "";
                String monedaLabel = "0".equals(monedaCode) ? "COP" : ("1".equals(monedaCode) ? "USD" : "OTRA");

                if (idx.containsKey("VLRINIOBL")) {
                    String val = getValue(rowValues, idx.get("VLRINIOBL")).replace(",", ".");
                    if (!val.isEmpty()) {
                        BigDecimal vlr = new BigDecimal(val);
                        countByMoneda.put(monedaLabel, countByMoneda.getOrDefault(monedaLabel, 0L) + 1);
                        sumByMoneda.put(monedaLabel,
                                sumByMoneda.getOrDefault(monedaLabel, BigDecimal.ZERO).add(vlr));
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private String getValue(List<String> rowValues, int index) {
        if (index < 0 || index >= rowValues.size()) {
            return "";
        }
        return rowValues.get(index).trim();
    }
}
