package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.ConsolidacionResumenResponse;
import com.bancolombia.sipro.validations.domain.model.CreffosColumnDefinition;
import com.bancolombia.sipro.validations.infrastructure.repository.CreffosParametroColumnasRepository;
import com.bancolombia.sipro.validations.shared.utils.XlsxStreamingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Compara el archivo CREFFSOS publicado contra los totales consolidados en PostgreSQL.
 */
@Service
public class CreffosComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(CreffosComparisonService.class);
    private static final String CONSOLIDADOS_PREFIX = "consolidados/";
    private static final String DEFAULT_OUTPUT_NAME = "CREFFSOS";
    private static final String DEFAULT_OUTPUT_FORMAT = "XLSX";
    private static final String DEFAULT_DETAIL_NOT_FOUND =
            "No se encontró el archivo CREFFSOS del periodo en storage ni en la ruta compartida configurada.";

    private final FileStorageService fileStorageService;
    private final ParametroUnicoService parametroUnicoService;
    private final CreffosParametroColumnasRepository parametroColumnasRepository;

    public CreffosComparisonService(FileStorageService fileStorageService,
                                    ParametroUnicoService parametroUnicoService,
                                    CreffosParametroColumnasRepository parametroColumnasRepository) {
        this.fileStorageService = fileStorageService;
        this.parametroUnicoService = parametroUnicoService;
        this.parametroColumnasRepository = parametroColumnasRepository;
    }

    /**
     * Ejecuta la conciliación básica entre PostgreSQL y el archivo CREFFSOS de un periodo.
     */
    public ComparisonSnapshot comparar(LocalDate fechaCorte,
                                       long cantidadRegistrosPostgres,
                                       BigDecimal totalVlrIniOblPostgres) {
        List<CreffosColumnDefinition> definitions = parametroColumnasRepository.findActiveDefinitions();
        int columnasEsperadas = definitions.size();
        int indiceVlrIniObl = indexOfColumn(definitions, "VLRINIOBL");
        ResolvedCreffosFile resolvedFile = resolveCreffosFile(fechaCorte);

        if (resolvedFile == null) {
            ConsolidacionResumenResponse.CreffosArchivoResumen resumenArchivo = new ConsolidacionResumenResponse.CreffosArchivoResumen(
                    false,
                    expectedFileName(),
                    normalizedOutputFormat(),
                    "NO_ENCONTRADO",
                    "SIN_ARCHIVO",
                    "",
                    columnasEsperadas,
                    0,
                    0L,
                    BigDecimal.ZERO,
                    DEFAULT_DETAIL_NOT_FOUND
            );
            return new ComparisonSnapshot(resumenArchivo, List.of(), false);
        }

        boolean includeHeader = Boolean.parseBoolean(
                parametroUnicoService.getString("CREFFSOS_INCLUIR_ENCABEZADO", "true"));

        try (InputStream inputStream = openStream(resolvedFile)) {
            CreffosFileMetrics metrics = switch (resolvedFile.format()) {
                case "CSV" -> readDelimited(inputStream, ';', includeHeader, indiceVlrIniObl);
                case "TSV" -> readDelimited(inputStream, '\t', includeHeader, indiceVlrIniObl);
                default -> readXlsx(inputStream, includeHeader, indiceVlrIniObl);
            };

            List<ConsolidacionResumenResponse.ComparacionMetricaResumen> diferencias = buildDifferences(
                    cantidadRegistrosPostgres,
                    totalVlrIniOblPostgres,
                    metrics);

            boolean tieneDiferencias = diferencias.stream().anyMatch(metric -> !metric.isCoincide());
            String estado = tieneDiferencias ? "CON_DIFERENCIAS" : "CONSISTENTE";
            String detalle = tieneDiferencias
                    ? "Se detectaron diferencias entre PostgreSQL y el archivo CREFFSOS del periodo."
                    : "El archivo CREFFSOS coincide con los totales consolidados del periodo.";

            ConsolidacionResumenResponse.CreffosArchivoResumen resumenArchivo = new ConsolidacionResumenResponse.CreffosArchivoResumen(
                    true,
                    resolvedFile.fileName(),
                    resolvedFile.format(),
                    estado,
                    resolvedFile.sourceType(),
                    resolvedFile.location(),
                    columnasEsperadas,
                    metrics.columnCount(),
                    metrics.rowCount(),
                    metrics.totalVlrIniObl(),
                    detalle
            );

            return new ComparisonSnapshot(resumenArchivo, diferencias, tieneDiferencias);
        } catch (Exception ex) {
            logger.warn("[CREFFSOS] No fue posible comparar el archivo del periodo {}: {}", fechaCorte, ex.getMessage());
            ConsolidacionResumenResponse.CreffosArchivoResumen resumenArchivo = new ConsolidacionResumenResponse.CreffosArchivoResumen(
                    true,
                    resolvedFile.fileName(),
                    resolvedFile.format(),
                    "ERROR_LECTURA",
                    resolvedFile.sourceType(),
                    resolvedFile.location(),
                    columnasEsperadas,
                    0,
                    0L,
                    BigDecimal.ZERO,
                    "No fue posible leer el archivo CREFFSOS para comparar: " + ex.getMessage()
            );
            return new ComparisonSnapshot(resumenArchivo, List.of(), false);
        }
    }

    /**
     * Carga el archivo CREFFSOS con filas materializadas para soportar conciliación detallada.
     */
    public DetailedCreffosFile cargarDetalleArchivo(LocalDate fechaCorte) {
        List<CreffosColumnDefinition> definitions = parametroColumnasRepository.findActiveDefinitions();
        int columnasEsperadas = definitions.size();
        int indiceVlrIniObl = indexOfColumn(definitions, "VLRINIOBL");
        ResolvedCreffosFile resolvedFile = resolveCreffosFile(fechaCorte);

        if (resolvedFile == null) {
            return DetailedCreffosFile.noEncontrado(
                    expectedFileName(),
                    normalizedOutputFormat(),
                    columnasEsperadas,
                    DEFAULT_DETAIL_NOT_FOUND
            );
        }

        boolean includeHeader = Boolean.parseBoolean(
                parametroUnicoService.getString("CREFFSOS_INCLUIR_ENCABEZADO", "true"));

        try (InputStream inputStream = openStream(resolvedFile)) {
            DetailedFileAccumulator accumulator = new DetailedFileAccumulator(definitions, includeHeader, indiceVlrIniObl);
            switch (resolvedFile.format()) {
                case "CSV" -> readDetailedDelimited(inputStream, ';', accumulator);
                case "TSV" -> readDetailedDelimited(inputStream, '\t', accumulator);
                default -> readDetailedXlsx(inputStream, accumulator);
            }
            return accumulator.toDetailedFile(resolvedFile, columnasEsperadas);
        } catch (Exception ex) {
            logger.warn("[CREFFSOS] No fue posible cargar el detalle del archivo del periodo {}: {}",
                    fechaCorte, ex.getMessage());
            return DetailedCreffosFile.error(
                    resolvedFile.fileName(),
                    resolvedFile.format(),
                    resolvedFile.sourceType(),
                    resolvedFile.location(),
                    columnasEsperadas,
                    "No fue posible leer el archivo CREFFSOS para conciliación detallada: " + ex.getMessage()
            );
        }
    }

    private ResolvedCreffosFile resolveCreffosFile(LocalDate fechaCorte) {
        String expectedName = expectedFileName();
        String prefix = CONSOLIDADOS_PREFIX + fechaCorte + "/";
        List<String> objects = fileStorageService.listObjects(prefix);

        Optional<String> exactMatch = objects.stream()
                .filter(key -> fileNameFromKey(key).equalsIgnoreCase(expectedName))
                .findFirst();
        if (exactMatch.isPresent()) {
            return new ResolvedCreffosFile(
                    fileNameFromKey(exactMatch.get()),
                    formatFromFileName(fileNameFromKey(exactMatch.get())),
                    "STORAGE",
                    exactMatch.get()
            );
        }

        Optional<String> fallbackMatch = objects.stream()
                .sorted(Comparator.naturalOrder())
                .filter(key -> fileNameFromKey(key).toUpperCase(Locale.ROOT).startsWith(DEFAULT_OUTPUT_NAME))
                .findFirst();
        if (fallbackMatch.isPresent()) {
            return new ResolvedCreffosFile(
                    fileNameFromKey(fallbackMatch.get()),
                    formatFromFileName(fileNameFromKey(fallbackMatch.get())),
                    "STORAGE",
                    fallbackMatch.get()
            );
        }

        String sharedDir = parametroUnicoService.getString("CREFFSOS_RUTA_SALIDA", "").trim();
        if (!sharedDir.isBlank()) {
            List<Path> candidatePaths = List.of(
                    Path.of(sharedDir, expectedName),
                    Path.of(sharedDir, fechaCorte.toString(), expectedName)
            );
            for (Path candidate : candidatePaths) {
                if (Files.exists(candidate)) {
                    return new ResolvedCreffosFile(
                            candidate.getFileName().toString(),
                            formatFromFileName(candidate.getFileName().toString()),
                            "RUTA_COMPARTIDA",
                            candidate.toAbsolutePath().toString()
                    );
                }
            }
        }

        return null;
    }

    private InputStream openStream(ResolvedCreffosFile file) throws IOException {
        if ("STORAGE".equals(file.sourceType())) {
            return fileStorageService.openStream(file.location());
        }
        return Files.newInputStream(Path.of(file.location()));
    }

    private CreffosFileMetrics readXlsx(InputStream inputStream,
                                        boolean includeHeader,
                                        int indiceVlrIniObl) throws IOException {
        FileMetricsAccumulator accumulator = new FileMetricsAccumulator(includeHeader, indiceVlrIniObl);
        XlsxStreamingReader.readFirstSheet(inputStream, (rowNumber, rowValues) -> accumulator.accept(rowValues));
        return accumulator.toMetrics();
    }

    private CreffosFileMetrics readDelimited(InputStream inputStream,
                                             char delimiter,
                                             boolean includeHeader,
                                             int indiceVlrIniObl) throws IOException {
        FileMetricsAccumulator accumulator = new FileMetricsAccumulator(includeHeader, indiceVlrIniObl);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                accumulator.accept(parseDelimitedLine(line, delimiter));
            }
        }
        return accumulator.toMetrics();
    }

    private List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (currentChar == delimiter && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        values.add(current.toString().trim());
        return values;
    }

    private void readDetailedXlsx(InputStream inputStream,
                                  DetailedFileAccumulator accumulator) throws IOException {
        XlsxStreamingReader.readFirstSheet(inputStream, (rowNumber, rowValues) -> accumulator.accept(rowValues));
    }

    private void readDetailedDelimited(InputStream inputStream,
                                       char delimiter,
                                       DetailedFileAccumulator accumulator) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                accumulator.accept(parseDelimitedLine(line, delimiter));
            }
        }
    }

    private List<ConsolidacionResumenResponse.ComparacionMetricaResumen> buildDifferences(
            long cantidadRegistrosPostgres,
            BigDecimal totalVlrIniOblPostgres,
            CreffosFileMetrics metrics) {
        List<ConsolidacionResumenResponse.ComparacionMetricaResumen> diferencias = new ArrayList<>();
        diferencias.add(buildMetric(
                "REGISTROS",
                "Cantidad de registros",
                "integer",
                BigDecimal.valueOf(cantidadRegistrosPostgres),
                BigDecimal.valueOf(metrics.rowCount())
        ));
        diferencias.add(buildMetric(
                "VLRINIOBL",
                "Total VLRINIOBL",
                "currency",
                normalize(totalVlrIniOblPostgres),
                metrics.totalVlrIniObl()
        ));
        return diferencias;
    }

    private ConsolidacionResumenResponse.ComparacionMetricaResumen buildMetric(String codigo,
                                                                                String etiqueta,
                                                                                String tipoValor,
                                                                                BigDecimal valorPostgres,
                                                                                BigDecimal valorCreffos) {
        BigDecimal normalizedPostgres = normalize(valorPostgres);
        BigDecimal normalizedCreffos = normalize(valorCreffos);
        BigDecimal diferencia = normalizedPostgres.subtract(normalizedCreffos);
        boolean coincide = diferencia.compareTo(BigDecimal.ZERO) == 0;
        return new ConsolidacionResumenResponse.ComparacionMetricaResumen(
                codigo,
                etiqueta,
                tipoValor,
                normalizedPostgres,
                normalizedCreffos,
                diferencia,
                coincide
        );
    }

    private String expectedFileName() {
        String format = normalizedOutputFormat();
        String defaultName = DEFAULT_OUTPUT_NAME + "." + format.toLowerCase(Locale.ROOT);
        String configuredName = parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", defaultName);
        return ensureExtension(configuredName, format);
    }

    private String normalizedOutputFormat() {
        String format = parametroUnicoService.getString("CREFFSOS_FORMATO_SALIDA", DEFAULT_OUTPUT_FORMAT)
                .trim()
                .toUpperCase(Locale.ROOT);
        return switch (format) {
            case "CSV", "TSV", "XLSX" -> format;
            default -> DEFAULT_OUTPUT_FORMAT;
        };
    }

    private String ensureExtension(String fileName, String format) {
        String expectedExtension = "." + format.toLowerCase(Locale.ROOT);
        if (fileName == null || fileName.isBlank()) {
            return DEFAULT_OUTPUT_NAME + expectedExtension;
        }
        String normalizedName = fileName.trim();
        if (normalizedName.toLowerCase(Locale.ROOT).endsWith(expectedExtension)) {
            return normalizedName;
        }
        int dot = normalizedName.lastIndexOf('.');
        if (dot > 0) {
            return normalizedName.substring(0, dot) + expectedExtension;
        }
        return normalizedName + expectedExtension;
    }

    private String fileNameFromKey(String key) {
        int separatorIndex = key.lastIndexOf('/');
        return separatorIndex >= 0 ? key.substring(separatorIndex + 1) : key;
    }

    private String formatFromFileName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return DEFAULT_OUTPUT_FORMAT;
        }
        String extension = fileName.substring(dotIndex + 1).toUpperCase(Locale.ROOT);
        return switch (extension) {
            case "CSV", "TSV", "XLSX" -> extension;
            default -> DEFAULT_OUTPUT_FORMAT;
        };
    }

    private int indexOfColumn(List<CreffosColumnDefinition> definitions, String columnName) {
        for (int index = 0; index < definitions.size(); index++) {
            if (columnName.equalsIgnoreCase(definitions.get(index).nombreColumna())) {
                return index;
            }
        }
        return -1;
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal parseDecimal(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO;
        }

        String normalized = rawValue.trim()
                .replace("\u00A0", "")
                .replace(",", "");
        if (normalized.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            logger.debug("[CREFFSOS] Valor numérico no interpretable '{}'. Se toma como 0.", rawValue);
            return BigDecimal.ZERO;
        }
    }

    public record ComparisonSnapshot(
            ConsolidacionResumenResponse.CreffosArchivoResumen archivo,
            List<ConsolidacionResumenResponse.ComparacionMetricaResumen> metricas,
            boolean tieneDiferencias) {
    }

    public record CreffosRow(Map<String, String> values) {
        public String value(String columnName) {
            if (columnName == null) {
                return "";
            }
            return values.getOrDefault(columnName.trim().toUpperCase(Locale.ROOT), "");
        }
    }

    public record DetailedCreffosFile(boolean encontrado,
                                      String nombreArchivo,
                                      String formato,
                                      String estado,
                                      String origenLectura,
                                      String ubicacion,
                                      int cantidadColumnasEsperadas,
                                      int cantidadColumnasArchivo,
                                      long cantidadRegistros,
                                      BigDecimal totalVlrIniObl,
                                      String detalle,
                                      List<CreffosRow> rows) {

        static DetailedCreffosFile noEncontrado(String nombreArchivo,
                                                String formato,
                                                int cantidadColumnasEsperadas,
                                                String detalle) {
            return new DetailedCreffosFile(
                    false,
                    nombreArchivo,
                    formato,
                    "NO_ENCONTRADO",
                    "SIN_ARCHIVO",
                    "",
                    cantidadColumnasEsperadas,
                    0,
                    0L,
                    BigDecimal.ZERO,
                    detalle,
                    List.of()
            );
        }

        static DetailedCreffosFile error(String nombreArchivo,
                                         String formato,
                                         String origenLectura,
                                         String ubicacion,
                                         int cantidadColumnasEsperadas,
                                         String detalle) {
            return new DetailedCreffosFile(
                    true,
                    nombreArchivo,
                    formato,
                    "ERROR_LECTURA",
                    origenLectura,
                    ubicacion,
                    cantidadColumnasEsperadas,
                    0,
                    0L,
                    BigDecimal.ZERO,
                    detalle,
                    List.of()
            );
        }
    }

    private record ResolvedCreffosFile(String fileName,
                                       String format,
                                       String sourceType,
                                       String location) {
    }

    private record CreffosFileMetrics(int columnCount,
                                      long rowCount,
                                      BigDecimal totalVlrIniObl) {
    }

    private final class FileMetricsAccumulator {
        private final boolean includeHeader;
        private final int indiceVlrIniObl;
        private boolean firstRowProcessed;
        private int columnCount;
        private long rowCount;
        private BigDecimal totalVlrIniObl = BigDecimal.ZERO;

        private FileMetricsAccumulator(boolean includeHeader, int indiceVlrIniObl) {
            this.includeHeader = includeHeader;
            this.indiceVlrIniObl = indiceVlrIniObl;
        }

        private void accept(List<String> rowValues) {
            columnCount = Math.max(columnCount, rowValues.size());
            if (!firstRowProcessed) {
                firstRowProcessed = true;
                if (includeHeader) {
                    return;
                }
            }

            if (rowValues.stream().filter(Objects::nonNull).allMatch(String::isBlank)) {
                return;
            }

            rowCount++;
            if (indiceVlrIniObl >= 0 && indiceVlrIniObl < rowValues.size()) {
                totalVlrIniObl = totalVlrIniObl.add(parseDecimal(rowValues.get(indiceVlrIniObl)));
            }
        }

        private CreffosFileMetrics toMetrics() {
            return new CreffosFileMetrics(columnCount, rowCount, totalVlrIniObl);
        }
    }

    private final class DetailedFileAccumulator {
        private final List<CreffosColumnDefinition> definitions;
        private final boolean includeHeader;
        private final int indiceVlrIniObl;
        private boolean firstRowProcessed;
        private int columnCount;
        private long rowCount;
        private BigDecimal totalVlrIniObl = BigDecimal.ZERO;
        private List<String> headerColumns = List.of();
        private final List<CreffosRow> rows = new ArrayList<>();

        private DetailedFileAccumulator(List<CreffosColumnDefinition> definitions,
                                        boolean includeHeader,
                                        int indiceVlrIniObl) {
            this.definitions = definitions;
            this.includeHeader = includeHeader;
            this.indiceVlrIniObl = indiceVlrIniObl;
        }

        private void accept(List<String> rowValues) {
            columnCount = Math.max(columnCount, rowValues.size());
            if (!firstRowProcessed) {
                firstRowProcessed = true;
                if (includeHeader) {
                    headerColumns = rowValues.stream()
                            .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                            .toList();
                    return;
                }
                headerColumns = definitions.stream()
                        .map(definition -> definition.nombreColumna().trim().toUpperCase(Locale.ROOT))
                        .toList();
            }

            if (rowValues.stream().filter(Objects::nonNull).allMatch(String::isBlank)) {
                return;
            }

            Map<String, String> mappedValues = mapRowValues(rowValues);
            rows.add(new CreffosRow(mappedValues));
            rowCount++;

            if (indiceVlrIniObl >= 0) {
                totalVlrIniObl = totalVlrIniObl.add(parseDecimal(mappedValues.getOrDefault("VLRINIOBL", "")));
            }
        }

        private Map<String, String> mapRowValues(List<String> rowValues) {
            Map<String, String> mappedValues = new LinkedHashMap<>();
            int maxColumns = Math.max(rowValues.size(), headerColumns.size());
            for (int index = 0; index < maxColumns; index++) {
                String header = index < headerColumns.size()
                        ? headerColumns.get(index)
                        : "COL_" + (index + 1);
                String value = index < rowValues.size() && rowValues.get(index) != null
                        ? rowValues.get(index).trim()
                        : "";
                mappedValues.put(header, value);
            }
            return mappedValues;
        }

        private DetailedCreffosFile toDetailedFile(ResolvedCreffosFile resolvedFile,
                                                   int columnasEsperadas) {
            return new DetailedCreffosFile(
                    true,
                    resolvedFile.fileName(),
                    resolvedFile.format(),
                    "OK",
                    resolvedFile.sourceType(),
                    resolvedFile.location(),
                    columnasEsperadas,
                    columnCount,
                    rowCount,
                    totalVlrIniObl,
                    "Detalle CREFFSOS cargado correctamente para conciliación.",
                    List.copyOf(rows)
            );
        }
    }
}