package com.bancolombia.sipro.validations.service;

import com.bancolombia.sipro.validations.domain.service.DynamicExcelValidationService;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.model.ValidationResult;
import com.bancolombia.sipro.validations.shared.utils.XlsxStreamingReader;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Coordina la validación de archivos cargados y construye el resultado consumido por el frontend.
 */
@Service
public class FileValidationService {

    /**
     * Abstrae el origen del archivo para reutilizar la misma lógica con multipart o temporales.
     */
    private interface ValidationInputSource {
        String getOriginalFilename();

        long getSize();

        InputStream openStream() throws IOException;

        byte[] readAllBytes() throws IOException;
    }

    private static class MultipartValidationInputSource implements ValidationInputSource {
        private final MultipartFile multipartFile;

        private MultipartValidationInputSource(MultipartFile multipartFile) {
            this.multipartFile = multipartFile;
        }

        @Override
        public String getOriginalFilename() {
            return multipartFile.getOriginalFilename();
        }

        @Override
        public long getSize() {
            return multipartFile.getSize();
        }

        @Override
        public InputStream openStream() throws IOException {
            return multipartFile.getInputStream();
        }

        @Override
        public byte[] readAllBytes() throws IOException {
            return multipartFile.getBytes();
        }
    }

    private static class PathValidationInputSource implements ValidationInputSource {
        private final Path path;
        private final String originalFilename;
        private final long size;

        private PathValidationInputSource(Path path, String originalFilename, long size) {
            this.path = path;
            this.originalFilename = originalFilename;
            this.size = size;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public InputStream openStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public byte[] readAllBytes() throws IOException {
            return Files.readAllBytes(path);
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(FileValidationService.class);
    
    private final LoteMemoryStore store;
    private final DynamicExcelValidationService dynamicValidator;
    private final com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository productoRepository;
    private final ParametroUnicoService parametroUnicoService;
    
    public FileValidationService(LoteMemoryStore store, 
                                 DynamicExcelValidationService dynamicValidator,
                                 com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository productoRepository,
                                 ParametroUnicoService parametroUnicoService) {
        this.store = store;
        this.dynamicValidator = dynamicValidator;
        this.productoRepository = productoRepository;
        this.parametroUnicoService = parametroUnicoService;
    }
    
    /**
     * Ejecuta la validación síncrona de un archivo recibido desde la petición HTTP.
     *
     * @param archivoControl archivo control .txt (requerido para Full IFRS, null para Colgaap)
     */
    public ValidationResult validarArchivo(
        Long idProducto,
        Long idSegmento,
        String productoNombreFrontend,
        String fechaCorte,
        String descripcion,
        MultipartFile archivo,
        MultipartFile archivoControl,
        String usuarioAdmin
    ) throws IOException {
        String sha256;
        try (InputStream inputStream = archivo.getInputStream()) {
            sha256 = DigestUtils.sha256Hex(inputStream);
        }
        return validarArchivoInternal(
            idProducto,
            idSegmento,
            productoNombreFrontend,
            fechaCorte,
            descripcion,
            new MultipartValidationInputSource(archivo),
            archivoControl != null ? new MultipartValidationInputSource(archivoControl) : null,
            sha256,
            usuarioAdmin,
            ValidationProgressListener.NOOP
        );
    }

    /**
     * Ejecuta la validación reutilizando un archivo temporal ya preparado por el flujo asíncrono.
     *
     * @param archivoControlPath ruta temporal del .txt control (null si no aplica)
     */
    public ValidationResult validarArchivoAsync(
        Long idProducto,
        Long idSegmento,
        String productoNombreFrontend,
        String fechaCorte,
        String descripcion,
        Path archivoPath,
        String originalFilename,
        long fileSize,
        String sha256,
        Path archivoControlPath,
        String archivoControlFilename,
        long archivoControlSize,
        String usuarioAdmin,
        ValidationProgressListener progressListener
    ) throws IOException {
        return validarArchivoInternal(
            idProducto,
            idSegmento,
            productoNombreFrontend,
            fechaCorte,
            descripcion,
            new PathValidationInputSource(archivoPath, originalFilename, fileSize),
            archivoControlPath != null
                ? new PathValidationInputSource(archivoControlPath, archivoControlFilename, archivoControlSize)
                : null,
            sha256,
            usuarioAdmin,
            progressListener
        );
    }

    /**
     * Centraliza el flujo común: nombre, formato, reglas de negocio y resumen final.
     *
     * @param controlSource fuente del archivo control .txt (null para Colgaap/Modificado)
     */
    private ValidationResult validarArchivoInternal(
        Long idProducto,
        Long idSegmento,
        String productoNombreFrontend,
        String fechaCorte,
        String descripcion,
        ValidationInputSource source,
        ValidationInputSource controlSource,
        String sha256,
        String usuarioAdmin,
        ValidationProgressListener progressListener
    ) throws IOException {
        ValidationProgressListener safeProgressListener = progressListener != null
            ? progressListener
            : ValidationProgressListener.NOOP;

        ValidationResult result = new ValidationResult();
        result.setLoteId(UUID.randomUUID().toString());
        result.setArchivoTemporalDisponible(false);

        String productoNombreFinal = productoNombreFrontend;
        Long segmentoIdFinal = idSegmento;

        if (idProducto != null) {
            java.util.Optional<com.bancolombia.sipro.validations.domain.model.Producto> prodEntityOpt =
                productoRepository.findById(idProducto);

            if (prodEntityOpt.isPresent()) {
                com.bancolombia.sipro.validations.domain.model.Producto prodEntity = prodEntityOpt.get();
                productoNombreFinal = prodEntity.getTitulo();
                segmentoIdFinal = prodEntity.getIdSegmento();

                if (idSegmento != null && prodEntity.getIdSegmento() != null && !idSegmento.equals(prodEntity.getIdSegmento())) {
                    result.setStatus("ERROR");
                    result.getErrores().add("El producto seleccionado no pertenece al segmento enviado en la solicitud.");
                    store.save(result);
                    return result;
                }

                String patronPermitido = prodEntity.getNombreArchivoPermitido();
                String nombreArchivoSubido = source.getOriginalFilename();

                if (patronPermitido != null && !patronPermitido.isEmpty() && nombreArchivoSubido != null) {
                    String fechaFormulario = fechaCorte;
                    if (fechaFormulario != null && fechaFormulario.contains("-")) {
                        fechaFormulario = fechaFormulario.replace("-", "");
                    }

                    String nombreEsperado = patronPermitido.replace("AAAAMMDD", fechaFormulario);
                    String baseSubido = nombreArchivoSubido;
                    if (baseSubido.toLowerCase().endsWith(".xlsx")) {
                        baseSubido = baseSubido.substring(0, baseSubido.length() - 5);
                    } else if (baseSubido.toLowerCase().endsWith(".csv")) {
                        baseSubido = baseSubido.substring(0, baseSubido.length() - 4);
                    }

                    if (!baseSubido.equals(nombreEsperado)) {
                        result.setStatus("ERROR");
                        result.getErrores().add(String.format(
                            "El nombre del archivo no coincide con la fecha de corte seleccionada. Se esperaba: '%s.xlsx'. Nombre recibido: '%s'",
                            nombreEsperado,
                            nombreArchivoSubido
                        ));
                        store.save(result);
                        return result;
                    }

                    logger.info("Nombre de archivo validado correctamente: {}", nombreArchivoSubido);
                }

                // La validación del archivo control (.txt) para Full IFRS es responsabilidad
                // exclusiva del frontend — el backend no la valida.

            } else {
                logger.warn("Producto con ID '{}' no encontrado en BD.", idProducto);
                result.setStatus("ERROR");
                result.getErrores().add("El producto seleccionado no es válido (ID no encontrado).");
                store.save(result);
                return result;
            }
        }

        logger.info("Iniciando validación de archivo: {} para producto: {}",
            source.getOriginalFilename(), productoNombreFinal);

        safeProgressListener.onPhase("PREPARING", "Validando archivo recibido...", 0);
        result.setSha256(sha256);

        String filename = source.getOriginalFilename();
        boolean isCsv = filename != null && filename.toLowerCase().endsWith(".csv");
        boolean isXlsx = filename != null && filename.toLowerCase().endsWith(".xlsx");

        if (!isCsv && !isXlsx) {
            result.setStatus("ERROR");
            result.getErrores().add("Solo se aceptan archivos .csv o .xlsx");
            store.save(result);
            return result;
        }

        LocalDate fechaCorteDate;
        try {
            if (fechaCorte != null && fechaCorte.contains("-")) {
                fechaCorteDate = LocalDate.parse(fechaCorte, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else {
                fechaCorteDate = LocalDate.parse(fechaCorte, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
        } catch (Exception e) {
            fechaCorteDate = LocalDate.now();
        }

        safeProgressListener.onPhase("VALIDATING", "Validando reglas del archivo...", 0);

        // Contar filas del xlsx antes de validar (necesario para CTRL_RECORD_COUNT de Full IFRS)
        long xlsxDataRowCount = 0L;
        if (controlSource != null && Long.valueOf(2L).equals(segmentoIdFinal)) {
            xlsxDataRowCount = dynamicValidator.countDataRows(source::openStream, source.getOriginalFilename());
            logger.info("Filas de datos xlsx contadas para validación de control: {}", xlsxDataRowCount);
        }

        List<DynamicExcelValidationService.ValidationError> validationErrors =
            dynamicValidator.validateExcel(
                source::openStream,
                source.getOriginalFilename(),
                productoNombreFinal,
                segmentoIdFinal,
                fechaCorteDate,
                usuarioAdmin,
                safeProgressListener);

        // Validar contenido del archivo control (.txt) para Full IFRS
        if (controlSource != null && Long.valueOf(2L).equals(segmentoIdFinal)) {
            try {
                StringBuilder sbCtrl = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(controlSource.openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (sbCtrl.length() > 0) sbCtrl.append("\n");
                        sbCtrl.append(line);
                    }
                }
                List<DynamicExcelValidationService.ValidationError> ctrlErrors =
                    dynamicValidator.validateControlFile(sbCtrl.toString(), xlsxDataRowCount, segmentoIdFinal);
                validationErrors = new java.util.ArrayList<>(validationErrors);
                validationErrors.addAll(ctrlErrors);
            } catch (Exception e) {
                logger.error("Error validando archivo control: {}", e.getMessage(), e);
                validationErrors = new java.util.ArrayList<>(validationErrors);
                validationErrors.add(new DynamicExcelValidationService.ValidationError(0, "ARCHIVO_CONTROL", "",
                    "No fue posible procesar el archivo control (.txt): " + e.getMessage()));
            }
        }

        if (!validationErrors.isEmpty()) {
            safeProgressListener.onPhase("SUMMARIZING", "Consolidando top de errores...", 92);
            result.setStatus("ERROR");

            final int MAX_ERRORS_IN_RESPONSE = parametroUnicoService.getInt("MAX_ERRORS_IN_RESPONSE", 50);
            Map<String, Integer> responseCountByType = new LinkedHashMap<>();
            Map<String, String> responseLabelByType = new LinkedHashMap<>();
            List<String> errorMessages = new ArrayList<>();

            for (DynamicExcelValidationService.ValidationError error : validationErrors) {
                String errorTypeKey = buildErrorTypeKey(error);
                int count = responseCountByType.getOrDefault(errorTypeKey, 0);
                if (count < MAX_ERRORS_IN_RESPONSE) {
                    errorMessages.add(error.toString());
                }
                responseCountByType.put(errorTypeKey, count + 1);
                responseLabelByType.putIfAbsent(errorTypeKey, buildErrorTypeLabel(error));
            }

            for (Map.Entry<String, Integer> entry : responseCountByType.entrySet()) {
                if (entry.getValue() > MAX_ERRORS_IN_RESPONSE) {
                    errorMessages.add(String.format(
                        "[%s] Se muestran %d de %d errores de este tipo. Corrija estos ejemplos y vuelva a validar.",
                        responseLabelByType.getOrDefault(entry.getKey(), entry.getKey()),
                        MAX_ERRORS_IN_RESPONSE,
                        entry.getValue()));
                }
            }

            result.setErrores(errorMessages);

            logger.warn("Validación falló con {} errores (se envían {} al frontend)",
                validationErrors.size(), errorMessages.size());

            byte[] erroresFile = generarArchivoErroresDetallado(validationErrors);
            store.saveErroresFile(result.getLoteId(), erroresFile);
        } else {
            safeProgressListener.onPhase("SUMMARIZING", "Generando resumen del archivo...", 92);
            result.setStatus("OK");
            logger.info("Validación exitosa para archivo: {}", filename);

            Map<String, Long> countByMoneda = new LinkedHashMap<>();
            Map<String, BigDecimal> sumByMoneda = new LinkedHashMap<>();

            try {
                if (isCsv) {
                    procesarResumenCsv(source.readAllBytes(), countByMoneda, sumByMoneda);
                } else {
                    try (InputStream inputStream = source.openStream()) {
                        procesarResumenXlsx(inputStream, countByMoneda, sumByMoneda);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error generando resumen: {}", e.getMessage());
            }

            List<ValidationResult.ResumenFilaDto> resumenDetallado = new ArrayList<>();
            for (String moneda : countByMoneda.keySet()) {
                resumenDetallado.add(new ValidationResult.ResumenFilaDto(
                    moneda,
                    countByMoneda.get(moneda),
                    sumByMoneda.getOrDefault(moneda, BigDecimal.ZERO)
                ));
            }
            result.setResumenDetallado(resumenDetallado);

            long totalRegistros = countByMoneda.values().stream().mapToLong(Long::longValue).sum();
            BigDecimal totalVlr = sumByMoneda.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            String monedaPrincipal = countByMoneda.isEmpty() ? "—"
                : countByMoneda.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("—");
            result.setResumen(new ValidationResult.ResumenDto(
                monedaPrincipal,
                totalRegistros,
                totalVlr
            ));
        }

        store.save(result);
        safeProgressListener.onPhase("DONE", "Validación completada.", 100);
        return result;
    }
    
    private void procesarResumenCsv(byte[] content, Map<String, Long> countByMoneda, Map<String, BigDecimal> sumByMoneda) throws IOException {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8)
        )) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;
            
            char delim = headerLine.contains(";") ? ';' : ',';
            List<String> headers = Arrays.asList(headerLine.split(String.valueOf(delim), -1));
            
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                idx.put(headers.get(i).trim().toUpperCase(), i);
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(String.valueOf(delim), -1);
                
                try {
                    String monedaCode = idx.containsKey("MONEDA") ? cols[idx.get("MONEDA")].trim() : "";
                    String monedaLabel = "0".equals(monedaCode) ? "COP" : ("1".equals(monedaCode) ? "USD" : "OTRA");
                    
                    if (idx.containsKey("VLRINIOBL")) {
                        BigDecimal vlr = parseSummaryDecimal(cols[idx.get("VLRINIOBL")]);
                        if (vlr != null) {
                            countByMoneda.put(monedaLabel, countByMoneda.getOrDefault(monedaLabel, 0L) + 1);
                            sumByMoneda.put(monedaLabel, sumByMoneda.getOrDefault(monedaLabel, BigDecimal.ZERO).add(vlr));
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Fila ignorada en resumen: {}", e.getMessage());
                }
            }
        }
    }
    
    private void procesarResumenXlsx(InputStream inputStream, Map<String, Long> countByMoneda, Map<String, BigDecimal> sumByMoneda) throws IOException {
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
                    BigDecimal vlr = parseSummaryDecimal(getValue(rowValues, idx.get("VLRINIOBL")));
                    if (vlr != null) {
                        countByMoneda.put(monedaLabel, countByMoneda.getOrDefault(monedaLabel, 0L) + 1);
                        sumByMoneda.put(monedaLabel, sumByMoneda.getOrDefault(monedaLabel, BigDecimal.ZERO).add(vlr));
                    }
                }
            } catch (Exception e) {
                logger.debug("Fila ignorada en resumen: {}", e.getMessage());
            }
        });
    }

    static BigDecimal parseSummaryDecimal(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String normalized = DynamicExcelValidationService.normalizeNumericInput(rawValue);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    
    /**
     * Genera un reporte TXT legible con observaciones generales y errores por fila.
     */
    byte[] generarArchivoErroresDetallado(List<DynamicExcelValidationService.ValidationError> errors) throws IOException {
        List<DynamicExcelValidationService.ValidationError> fileErrors = errors.stream()
            .filter(error -> error.getRowNumber() == 0 && !"RESUMEN".equalsIgnoreCase(error.getColumnName()))
            .toList();
        List<DynamicExcelValidationService.ValidationError> rowErrors = errors.stream()
            .filter(error -> error.getRowNumber() > 0)
            .toList();
        List<DynamicExcelValidationService.ValidationError> summaryErrors = errors.stream()
            .filter(error -> error.getRowNumber() == 0 && "RESUMEN".equalsIgnoreCase(error.getColumnName()))
            .toList();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            writeLine(writer, "REPORTE DE ERRORES DE VALIDACION SIPRO");
            writeLine(writer, "====================================");
            writeLine(writer, "");
            writeLine(writer, "Resumen");
            writeLine(writer, "-------");
            writeLine(writer, "- Total de observaciones: " + errors.size());
            writeLine(writer, "- Observaciones del archivo: " + fileErrors.size());
            writeLine(writer, "- Observaciones por filas: " + rowErrors.size());
            writeLine(writer, "");
            writeLine(writer, "Como usar este reporte");
            writeLine(writer, "----------------------");
            writeLine(writer, "1. Revise primero las observaciones generales del archivo.");
            writeLine(writer, "2. Corrija despues las filas y columnas indicadas.");
            writeLine(writer, "3. Cuando ajuste la planilla, vuelva a cargar el archivo.");
            writeLine(writer, "");
            writeLine(writer, "OBSERVACIONES DEL ARCHIVO");
            writeLine(writer, "-------------------------");
            if (fileErrors.isEmpty()) {
                writeLine(writer, "No se registraron observaciones generales del archivo.");
            } else {
                for (int index = 0; index < fileErrors.size(); index++) {
                    writeLine(writer, (index + 1) + ". " + sanitizeForTxt(fileErrors.get(index).getErrorMessage()));
                }
            }
            writeLine(writer, "");
            writeLine(writer, "DETALLE POR FILAS");
            writeLine(writer, "-----------------");
            if (rowErrors.isEmpty()) {
                writeLine(writer, "No se registraron errores en filas.");
            } else {
                for (int index = 0; index < rowErrors.size(); index++) {
                    DynamicExcelValidationService.ValidationError error = rowErrors.get(index);
                    writeLine(writer, (index + 1) + ". Fila " + error.getRowNumber() + " | Columna " + sanitizeForTxt(error.getColumnName()));
                    if (error.getCellValue() != null && !error.getCellValue().isBlank()) {
                        writeLine(writer, "   Valor recibido: " + sanitizeForTxt(error.getCellValue()));
                    }
                    writeLine(writer, "   Motivo: " + sanitizeForTxt(error.getErrorMessage()));
                    writeLine(writer, "");
                }
            }

            if (!summaryErrors.isEmpty()) {
                writeLine(writer, "RESUMEN ADICIONAL");
                writeLine(writer, "-----------------");
                for (int index = 0; index < summaryErrors.size(); index++) {
                    writeLine(writer, (index + 1) + ". " + sanitizeForTxt(summaryErrors.get(index).getErrorMessage()));
                }
            }
        }
        return baos.toByteArray();
    }

    private void writeLine(Writer writer, String value) throws IOException {
        writer.write(value);
        writer.write(System.lineSeparator());
    }

    private String sanitizeForTxt(String value) {
        if (value == null || value.isBlank()) {
            return "(sin valor)";
        }
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    private String buildErrorTypeKey(DynamicExcelValidationService.ValidationError error) {
        return error.getColumnName() + "||" + error.getErrorMessage();
    }

    private String buildErrorTypeLabel(DynamicExcelValidationService.ValidationError error) {
        return error.getColumnName() + " -> " + error.getErrorMessage();
    }
    
    private String getValue(List<String> rowValues, int index) {
        if (index < 0 || index >= rowValues.size()) {
            return "";
        }
        return rowValues.get(index).trim();
    }

}