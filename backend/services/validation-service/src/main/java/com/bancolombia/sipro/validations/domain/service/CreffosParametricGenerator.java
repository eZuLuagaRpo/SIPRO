package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.CreffosColumnDefinition;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.infrastructure.lz.LzJdbcService;
import com.bancolombia.sipro.validations.infrastructure.repository.CreffosParametroColumnasRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Genera el archivo CREFFSOS leyendo la parametrización activa de columnas y formato de salida.
 */
@Service
public class CreffosParametricGenerator {

    private static final Logger logger = LoggerFactory.getLogger(CreffosParametricGenerator.class);
    private static final String DEFAULT_OUTPUT_NAME = "CREFFSOS";
    private static final String DEFAULT_OUTPUT_FORMAT = "XLSX";
    private static final String DEFAULT_SHEET_NAME = "CREFFSOS";
    private static final int DEFAULT_NUMERIC_SCALE = 2;
    private static final int LZ_LOOKUP_CHUNK_SIZE = 500;
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    // Misma contrasena que ya usa ConsolidacionResumenExcelReportService para "proteger hoja"
    // (no es cifrado real, es la proteccion de edicion de Excel). Intencionalmente igual, a
    // pedido del negocio, para la copia bloqueada que se publica en ARCHIVOS_BLOQUEADOS_RUTA_SALIDA.
    private static final String LOCKED_SHEET_PASSWORD = "sipro-readonly";

    private final CreffosParametroColumnasRepository parametroColumnasRepository;
    private final ParametroUnicoService parametroUnicoService;
    private final LzJdbcService lzJdbcService;

    public CreffosParametricGenerator(CreffosParametroColumnasRepository parametroColumnasRepository,
                                      ParametroUnicoService parametroUnicoService,
                                      LzJdbcService lzJdbcService) {
        this.parametroColumnasRepository = parametroColumnasRepository;
        this.parametroUnicoService = parametroUnicoService;
        this.lzJdbcService = lzJdbcService;
    }

    /**
     * Construye el archivo final de CREFFSOS para una fecha de corte y un conjunto de registros consolidados.
     */
    public GeneratedCreffosFile generate(LocalDate fechaCorte,
                                         List<SiproDetalleConsolidadoRegistro> registros) {
        List<CreffosColumnDefinition> definitions = parametroColumnasRepository.findActiveDefinitions();
        if (definitions.isEmpty()) {
            throw new IllegalStateException("No hay columnas activas configuradas en sipro_parametros_columnas_creffsos");
        }

        OutputConfig outputConfig = resolveOutputConfig();
        Map<String, Map<String, String>> lookupCache = preloadLookups(definitions, registros);
        SequenceState sequenceState = resolveSequenceState(definitions, registros);

        List<Map<String, String>> rows = new ArrayList<>(registros.size());
        for (SiproDetalleConsolidadoRegistro registro : registros) {
            Map<String, Object> source = buildSourceMap(registro);
            LinkedHashMap<String, String> output = new LinkedHashMap<>();
            for (CreffosColumnDefinition definition : definitions) {
                String resolvedValue = resolveValue(definition, source, output, lookupCache, sequenceState);
                output.put(definition.nombreColumna(), formatValue(definition, resolvedValue));
            }
            rows.add(output);
        }
        byte[] content = renderFile(definitions, rows, outputConfig);

        // Copia protegida contra edicion para ARCHIVOS_BLOQUEADOS_RUTA_SALIDA: se arma con las
        // mismas filas ya calculadas arriba (sin repetir consultas a la LZ), siempre en XLSX sin
        // importar el formato configurado para la salida normal (CSV/TSV/XLSX).
        byte[] protectedContent = renderXlsx(definitions, rows, outputConfig.includeHeader(),
                outputConfig.sheetName(), LOCKED_SHEET_PASSWORD);
        String protectedFileName = ensureExtension(DEFAULT_OUTPUT_NAME, "XLSX");

        return new GeneratedCreffosFile(
                ensureExtension(outputConfig.fileName(), outputConfig.format()),
                outputConfig.contentType(),
                content,
                rows.size(),
                outputConfig.format(),
                protectedFileName,
                protectedContent
        );
    }

    private OutputConfig resolveOutputConfig() {
        String format = parametroUnicoService.getString("CREFFSOS_FORMATO_SALIDA", DEFAULT_OUTPUT_FORMAT)
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!List.of("XLSX", "CSV", "TSV").contains(format)) {
            logger.warn("Formato CREFFSOS '{}' no soportado. Se usará {}.", format, DEFAULT_OUTPUT_FORMAT);
            format = DEFAULT_OUTPUT_FORMAT;
        }

        String defaultName = DEFAULT_OUTPUT_NAME + "." + format.toLowerCase(Locale.ROOT);
        String configuredName = parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", defaultName);
        boolean includeHeader = Boolean.parseBoolean(
                parametroUnicoService.getString("CREFFSOS_INCLUIR_ENCABEZADO", "true"));
        String sheetName = parametroUnicoService.getString("CREFFSOS_HOJA_XLSX", DEFAULT_SHEET_NAME);

        String contentType = switch (format) {
            case "CSV" -> "text/csv; charset=UTF-8";
            case "TSV" -> "text/tab-separated-values; charset=UTF-8";
            default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        };

        return new OutputConfig(format, configuredName, includeHeader, sheetName, contentType);
    }

    private Map<String, Map<String, String>> preloadLookups(List<CreffosColumnDefinition> definitions,
                                                            List<SiproDetalleConsolidadoRegistro> registros) {
        Map<String, Map<String, String>> cache = new HashMap<>();

        for (CreffosColumnDefinition definition : definitions) {
            String function = safeText(definition.funcionJava());
            if (!List.of("resolverCuentaBankvision", "resolverClaseGarantiaDesdeCenie", "resolverCalificacionDesdeCenie")
                    .contains(function)) {
                continue;
            }

            String sourceField = firstNonBlank(
                    definition.parametroTexto("sourceField"),
                    definition.columnaOrigen(),
                    definition.nombreColumna()
            );

            Set<String> keys = new LinkedHashSet<>();
            for (SiproDetalleConsolidadoRegistro registro : registros) {
                String value = getSourceValue(buildSourceMap(registro), sourceField);
                if (!isBlank(value)) {
                    keys.add(value);
                }
            }

            if (keys.isEmpty()) {
                cache.put(definition.nombreColumna(), Map.of());
                continue;
            }

            Map<String, String> values = switch (function) {
                case "resolverCuentaBankvision" -> resolvePgLookup(definition, keys);
                case "resolverClaseGarantiaDesdeCenie", "resolverCalificacionDesdeCenie" -> resolveLzLookup(definition, keys);
                default -> Map.of();
            };
            cache.put(definition.nombreColumna(), values);
        }

        return cache;
    }

    private Map<String, String> resolvePgLookup(CreffosColumnDefinition definition, Collection<String> keys) {
        String lookupTable = definition.parametroTexto("lookupTable");
        String lookupKey = definition.parametroTexto("lookupKey");
        String lookupValue = definition.parametroTexto("lookupValue");

        if (isBlank(lookupTable) || isBlank(lookupKey) || isBlank(lookupValue)) {
            logger.warn("Lookup PostgreSQL incompleto para columna {}. Se omite resolución.", definition.nombreColumna());
            return Map.of();
        }

        return parametroColumnasRepository.findLookupValues(lookupTable, lookupKey, lookupValue, keys);
    }

    private Map<String, String> resolveLzLookup(CreffosColumnDefinition definition, Collection<String> keys) {
        if (!lzJdbcService.isEnabled()) {
            logger.warn("LZ no está configurado. {} usará valor por defecto.", definition.nombreColumna());
            return Map.of();
        }

        String schema = firstNonBlank(definition.parametroTexto("lookupSchema"), "s_productos");
        String table = definition.parametroTexto("lookupTable");
        String lookupKey = firstNonBlank(definition.parametroTexto("lookupKey"), "ceac21");
        String lookupValue = definition.parametroTexto("lookupValue");
        JsonNode filters = definition.parametros().path("filters");

        if (isBlank(table) || isBlank(lookupValue)) {
            logger.warn("Lookup LZ incompleto para columna {}. Se omite resolución.", definition.nombreColumna());
            return Map.of();
        }

        String qualifiedTable = qualifyLzTable(schema, table);
        boolean latestRequested = shouldUseLatestIngestion(filters);
        Map<String, String> resolved = new HashMap<>();
        for (List<String> chunk : chunk(keys, LZ_LOOKUP_CHUNK_SIZE)) {
            try {
                List<Map<String, Object>> rows = lzJdbcService.executeQuery(
                        latestRequested
                                ? buildLzLatestLookupSql(qualifiedTable, lookupKey, lookupValue, filters, chunk)
                                : buildLzMaxLookupSql(qualifiedTable, lookupKey, lookupValue, filters, chunk)
                );
                for (Map<String, Object> row : rows) {
                    Object key = row.get("lookup_key");
                    Object value = row.get("lookup_value");
                    if (key != null && value != null) {
                        resolved.put(key.toString().trim(), value.toString().trim());
                    }
                }
            } catch (Exception ex) {
                if (latestRequested) {
                    logger.warn("No se pudo aplicar ultimaFechaIngestion para {}. Se hará fallback a MAX por llave: {}",
                            definition.nombreColumna(), ex.getMessage());
                    try {
                        List<Map<String, Object>> rows = lzJdbcService.executeQuery(
                                buildLzMaxLookupSql(qualifiedTable, lookupKey, lookupValue, filters, chunk));
                        for (Map<String, Object> row : rows) {
                            Object key = row.get("lookup_key");
                            Object value = row.get("lookup_value");
                            if (key != null && value != null) {
                                resolved.put(key.toString().trim(), value.toString().trim());
                            }
                        }
                        continue;
                    } catch (Exception fallbackEx) {
                        logger.warn("Fallback MAX también falló para {}: {}",
                                definition.nombreColumna(), fallbackEx.getMessage());
                    }
                } else {
                    logger.warn("No se pudo resolver lookup LZ para {}: {}",
                            definition.nombreColumna(), ex.getMessage());
                }
                return Map.of();
            }
        }
        return resolved;
    }

    private String buildLzMaxLookupSql(String qualifiedTable,
                                       String rawLookupKey,
                                       String rawLookupValue,
                                       JsonNode filters,
                                       List<String> keys) {
        String lookupKey = validateSqlIdentifier(rawLookupKey);
        String lookupValue = validateSqlIdentifier(rawLookupValue);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT CAST(")
                .append(lookupKey)
                .append(" AS STRING) AS lookup_key, ")
                .append("MAX(CAST(")
                .append(lookupValue)
                .append(" AS STRING)) AS lookup_value ")
                .append("FROM ")
                .append(qualifiedTable)
                .append(" WHERE CAST(")
                .append(lookupKey)
                .append(" AS STRING) IN (")
                .append(joinQuoted(keys))
                .append(")");

        appendLzFilters(sql, filters);

        sql.append(" GROUP BY CAST(").append(lookupKey).append(" AS STRING)");
        return sql.toString();
    }

    private String buildLzLatestLookupSql(String qualifiedTable,
                                          String rawLookupKey,
                                          String rawLookupValue,
                                          JsonNode filters,
                                          List<String> keys) {
        String lookupKey = validateSqlIdentifier(rawLookupKey);
        String lookupValue = validateSqlIdentifier(rawLookupValue);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT lookup_key, lookup_value FROM (")
                .append("SELECT CAST(")
                .append(lookupKey)
                .append(" AS STRING) AS lookup_key, CAST(")
                .append(lookupValue)
                .append(" AS STRING) AS lookup_value, ROW_NUMBER() OVER (PARTITION BY CAST(")
                .append(lookupKey)
                .append(" AS STRING) ORDER BY ")
                .append(buildLatestOrderByClause(filters))
                .append(") AS rn FROM ")
                .append(qualifiedTable)
                .append(" WHERE CAST(")
                .append(lookupKey)
                .append(" AS STRING) IN (")
                .append(joinQuoted(keys))
                .append(")");

        appendLzFilters(sql, filters);
        sql.append(") ranked WHERE rn = 1");
        return sql.toString();
    }

    private void appendLzFilters(StringBuilder sql, JsonNode filters) {
        if (filters == null || filters.isMissingNode() || filters.isNull()) {
            return;
        }
        if (filters.has("cetr21")) {
            sql.append(" AND CAST(cetr21 AS INT) = ").append(filters.path("cetr21").asInt());
        }
        if (filters.has("ceap21Trim")) {
            sql.append(" AND TRIM(CAST(ceap21 AS STRING)) = '")
                    .append(escapeSql(filters.path("ceap21Trim").asText()))
                    .append("'");
        }
        if (filters.has("cest21NotIn") && filters.path("cest21NotIn").isArray()) {
            List<String> blocked = new ArrayList<>();
            filters.path("cest21NotIn").forEach(node -> blocked.add(node.asText()));
            if (!blocked.isEmpty()) {
                sql.append(" AND CAST(cest21 AS STRING) NOT IN (")
                        .append(joinQuoted(blocked))
                        .append(")");
            }
        }
    }

    private boolean shouldUseLatestIngestion(JsonNode filters) {
        return filters != null && !filters.isMissingNode() && !filters.isNull()
                && filters.path("ultimaFechaIngestion").asBoolean(false);
    }

    private String buildLatestOrderByClause(JsonNode filters) {
        List<String> orderColumns = new ArrayList<>();
        if (filters != null && !filters.isMissingNode() && !filters.isNull()
                && filters.path("latestOrderBy").isArray()) {
            filters.path("latestOrderBy").forEach(node -> {
                String column = firstNonBlank(node.asText());
                if (!column.isBlank()) {
                    orderColumns.add(validateSqlIdentifier(column));
                }
            });
        }
        if (orderColumns.isEmpty()) {
            orderColumns.add("period_year");
            orderColumns.add("period_month");
            orderColumns.add("ingestion_run_id");
        }

        List<String> fragments = new ArrayList<>(orderColumns.size());
        for (String column : orderColumns) {
            fragments.add(validateSqlIdentifier(column) + " DESC");
        }
        return String.join(", ", fragments);
    }

    private SequenceState resolveSequenceState(List<CreffosColumnDefinition> definitions,
                                               List<SiproDetalleConsolidadoRegistro> registros) {
        for (CreffosColumnDefinition definition : definitions) {
            if (!"resolverDocumentoConsecutivo".equals(safeText(definition.funcionJava()))) {
                continue;
            }

            String currentKey = definition.parametroTexto("currentSequenceParam");
            String initialKey = definition.parametroTexto("initialSequenceParam");
            int increment = definition.parametroEntero("increment", 1);
            if (isBlank(currentKey)) {
                return SequenceState.disabled();
            }

            int requestedCount = countMissingSequenceValues(definition, registros);
            if (requestedCount <= 0) {
                return SequenceState.disabled();
            }

            ParametroUnicoService.SequenceReservation reservation = parametroUnicoService.reserveSequenceRange(
                    currentKey,
                    initialKey,
                    increment,
                    requestedCount
            );
            return SequenceState.fromReservation(reservation);
        }
        return SequenceState.disabled();
    }

    private int countMissingSequenceValues(CreffosColumnDefinition definition,
                                           List<SiproDetalleConsolidadoRegistro> registros) {
        if (!definition.parametroBooleano("useSequenceWhenNullOrZero", true)) {
            return 0;
        }

        String sourceField = firstNonBlank(definition.parametroTexto("sourceField"),
                definition.columnaOrigen(), definition.nombreColumna());
        int requestedCount = 0;
        for (SiproDetalleConsolidadoRegistro registro : registros) {
            String currentValue = getSourceValue(buildSourceMap(registro), sourceField);
            if (isBlankOrZero(currentValue)) {
                requestedCount++;
            }
        }
        return requestedCount;
    }

    private String resolveValue(CreffosColumnDefinition definition,
                                Map<String, Object> source,
                                Map<String, String> output,
                                Map<String, Map<String, String>> lookupCache,
                                SequenceState sequenceState) {
        String function = safeText(definition.funcionJava());
        if (function.isBlank()) {
            function = safeText(definition.origenDato());
        }

        return switch (function) {
            case "copiarDirecto", "copyDirect", "DIRECTO" -> copyDirect(definition, source);
            case "asignarConstante", "constantValue", "CONSTANTE" -> constantValue(definition);
            case "dejarVacio", "VACIO" -> "";
            case "resolverDocumentoConsecutivo", "resolveDocumentoConsecutivo" -> resolveSequence(definition, source, sequenceState);
            case "copiarMismoValorSiInformado", "copyIfSourcePresent" -> copyIfSourcePresent(definition, source);
            case "asignarConstanteSiCampoInformado", "constantIfSourcePresent" -> constantIfSourcePresent(definition, source);
            case "asignarCeroSiSalidaInformada", "constantIfOutputPresent" ->
                constantIfOutputPresent(definition, output, firstNonBlank(definition.parametroTexto("valueIfTrue"), definition.valorConstante(), "0"));
            case "asignarConstanteSiSalidaInformada" ->
                constantIfOutputPresent(definition, output, firstNonBlank(definition.parametroTexto("valueIfTrue"), definition.valorConstante(), ""));
            case "copiarSalidaSiInformada", "copyIfOutputPresent" -> copyIfOutputPresent(definition, output);
            case "resolverClasificacionCpuc", "resolveClasificacionCpuc" -> resolveClasificacionCpuc(definition, source);
            case "resolverCuentaBankvision", "resolverClaseGarantiaDesdeCenie", "resolverCalificacionDesdeCenie", "resolveLookup", "LOOKUP" ->
                    resolveLookup(definition, source, lookupCache);
            default -> {
                logger.warn("Función CREFFSOS '{}' no soportada para columna {}. Se dejará vacía.", function, definition.nombreColumna());
                yield "";
            }
        };
    }

    private String copyDirect(CreffosColumnDefinition definition, Map<String, Object> source) {
        String sourceField = firstNonBlank(definition.parametroTexto("sourceField"), definition.columnaOrigen(), definition.nombreColumna());
        return getSourceValue(source, sourceField);
    }

    private String constantValue(CreffosColumnDefinition definition) {
        return configuredLiteral(definition, "value", definition.valorConstante());
    }

    private String resolveSequence(CreffosColumnDefinition definition,
                                   Map<String, Object> source,
                                   SequenceState sequenceState) {
        String sourceField = firstNonBlank(definition.parametroTexto("sourceField"), definition.columnaOrigen(), definition.nombreColumna());
        String currentValue = getSourceValue(source, sourceField);
        if (!definition.parametroBooleano("useSequenceWhenNullOrZero", true) || !isBlankOrZero(currentValue)) {
            return currentValue;
        }
        return String.valueOf(sequenceState.nextValue());
    }

    private String copyIfSourcePresent(CreffosColumnDefinition definition, Map<String, Object> source) {
        String dependsOn = firstNonBlank(definition.parametroTexto("dependsOnSource"), definition.columnaOrigen());
        String candidate = getSourceValue(source, dependsOn);
        if (!matchesCondition(definition.parametroTexto("when"), candidate)) {
            return "";
        }
        String copyFrom = firstNonBlank(definition.parametroTexto("copyFromSource"), dependsOn);
        return getSourceValue(source, copyFrom);
    }

    private String constantIfSourcePresent(CreffosColumnDefinition definition, Map<String, Object> source) {
        String dependsOn = firstNonBlank(definition.parametroTexto("dependsOnSource"), definition.columnaOrigen());
        String candidate = getSourceValue(source, dependsOn);
        if (!matchesCondition(definition.parametroTexto("when"), candidate)) {
            return "";
        }
        return configuredLiteral(definition, "valueIfTrue", definition.valorConstante());
    }

    private String constantIfOutputPresent(CreffosColumnDefinition definition,
                                           Map<String, String> output,
                                           String valueIfTrue) {
        String dependsOnOutput = definition.parametroTexto("dependsOnOutput");
        String candidate = output.getOrDefault(dependsOnOutput, "");
        if (!matchesCondition(definition.parametroTexto("when"), candidate)) {
            return "";
        }
        return valueIfTrue;
    }

    private String copyIfOutputPresent(CreffosColumnDefinition definition, Map<String, String> output) {
        String dependsOnOutput = definition.parametroTexto("dependsOnOutput");
        String candidate = output.getOrDefault(dependsOnOutput, "");
        if (!matchesCondition(definition.parametroTexto("when"), candidate)) {
            return "";
        }
        String copyFrom = firstNonBlank(definition.parametroTexto("copyFromOutput"), dependsOnOutput);
        return output.getOrDefault(copyFrom, "");
    }

    private String resolveClasificacionCpuc(CreffosColumnDefinition definition, Map<String, Object> source) {
        String modalidad = getSourceValue(source, "modalidad");
        String tipoId = firstNonBlank(getSourceValue(source, "tipo_id"), getSourceValue(source, "tipoId"));
        String modalidadHip = firstNonBlank(definition.parametroTexto("modalidadHip"), "HIP");
        String modalidadDsc = firstNonBlank(definition.parametroTexto("modalidadDsc"), "DSC");

        if (modalidadHip.equalsIgnoreCase(modalidad)) {
            return "3";
        }

        JsonNode tiposClasificacionUno = definition.parametros().path("tiposClasificacionUno");
        if (tiposClasificacionUno.isArray()) {
            for (JsonNode tipo : tiposClasificacionUno) {
                if (tipo.asText().equalsIgnoreCase(tipoId)) {
                    return "1";
                }
            }
        }

        if (modalidadDsc.equalsIgnoreCase(modalidad)) {
            return "1";
        }

        return String.valueOf(definition.parametroEntero("defaultValue", 2));
    }

    private String resolveLookup(CreffosColumnDefinition definition,
                                 Map<String, Object> source,
                                 Map<String, Map<String, String>> lookupCache) {
        String sourceField = firstNonBlank(definition.parametroTexto("sourceField"), definition.columnaOrigen(), definition.nombreColumna());
        String sourceValue = getSourceValue(source, sourceField);
        if (isBlank(sourceValue)) {
            return "";
        }

        String resolved = lookupCache.getOrDefault(definition.nombreColumna(), Map.of()).get(sourceValue);
        if (!isBlank(resolved)) {
            return resolved;
        }

        if ("resolverCuentaBankvision".equals(safeText(definition.funcionJava()))) {
            logger.warn("Cuenta SAP {} sin homologación para columna {}", sourceValue, definition.nombreColumna());
        }

        String ifNotFound = firstNonBlank(definition.parametroTexto("ifNotFound"), definition.parametroTexto("defaultValue"), definition.valorConstante(), "");
        return "NULL".equalsIgnoreCase(ifNotFound) ? "" : ifNotFound;
    }

    private Map<String, Object> buildSourceMap(SiproDetalleConsolidadoRegistro registro) {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(registro);
        Map<String, Object> source = new HashMap<>();
        for (var descriptor : wrapper.getPropertyDescriptors()) {
            String propertyName = descriptor.getName();
            if ("class".equals(propertyName) || !wrapper.isReadableProperty(propertyName)) {
                continue;
            }
            Object value = wrapper.getPropertyValue(propertyName);
            source.put(propertyName.toLowerCase(Locale.ROOT), value);
            source.put(toSnakeCase(propertyName), value);
            source.put(propertyName.toUpperCase(Locale.ROOT), value);
        }
        return source;
    }

    private byte[] renderFile(List<CreffosColumnDefinition> definitions,
                              List<Map<String, String>> rows,
                              OutputConfig outputConfig) {
        return switch (outputConfig.format()) {
            case "CSV" -> renderDelimited(definitions, rows, outputConfig.includeHeader(), ';');
            case "TSV" -> renderDelimited(definitions, rows, outputConfig.includeHeader(), '\t');
            default -> renderXlsx(definitions, rows, outputConfig.includeHeader(), outputConfig.sheetName(), null);
        };
    }

    private byte[] renderDelimited(List<CreffosColumnDefinition> definitions,
                                   List<Map<String, String>> rows,
                                   boolean includeHeader,
                                   char delimiter) {
        StringBuilder builder = new StringBuilder();
        if (includeHeader) {
            builder.append(joinColumns(definitions.stream().map(CreffosColumnDefinition::nombreColumna).toList(), delimiter))
                    .append(System.lineSeparator());
        }

        for (Map<String, String> row : rows) {
            List<String> orderedValues = new ArrayList<>(definitions.size());
            for (CreffosColumnDefinition definition : definitions) {
                orderedValues.add(escapeDelimited(row.getOrDefault(definition.nombreColumna(), ""), delimiter));
            }
            builder.append(joinColumns(orderedValues, delimiter)).append(System.lineSeparator());
        }

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] renderXlsx(List<CreffosColumnDefinition> definitions,
                              List<Map<String, String>> rows,
                              boolean includeHeader,
                              String sheetName,
                              String protectSheetPassword) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(500);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(firstNonBlank(sheetName, DEFAULT_SHEET_NAME));
            if (protectSheetPassword != null && !protectSheetPassword.isBlank()) {
                // Protege contra edicion (no es cifrado real): el archivo se abre sin pedir
                // nada, pero no se puede modificar sin la contrasena. Igual a como ya lo hace
                // ConsolidacionResumenExcelReportService para el resumen consolidado.
                sheet.protectSheet(protectSheetPassword);
            }
            int rowIndex = 0;
            if (includeHeader) {
                Row headerRow = sheet.createRow(rowIndex++);
                for (int columnIndex = 0; columnIndex < definitions.size(); columnIndex++) {
                    Cell cell = headerRow.createCell(columnIndex);
                    cell.setCellValue(definitions.get(columnIndex).nombreColumna());
                }
            }

            for (Map<String, String> row : rows) {
                Row xlsxRow = sheet.createRow(rowIndex++);
                for (int columnIndex = 0; columnIndex < definitions.size(); columnIndex++) {
                    CreffosColumnDefinition definition = definitions.get(columnIndex);
                    Cell cell = xlsxRow.createCell(columnIndex);
                    cell.setCellValue(row.getOrDefault(definition.nombreColumna(), ""));
                }
            }

            workbook.write(outputStream);
            workbook.dispose();
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el archivo XLSX de CREFFSOS", ex);
        }
    }

    private String formatValue(CreffosColumnDefinition definition, String rawValue) {
        if (rawValue == null) {
            return "";
        }

        if ("STRING".equalsIgnoreCase(definition.tipoDatoSalida())) {
            return rawValue;
        }

        if (rawValue.isBlank()) {
            return "";
        }

        try {
            return switch (safeText(definition.tipoDatoSalida())) {
                case "INTEGER" -> new BigDecimal(rawValue.trim()).setScale(0, RoundingMode.DOWN).toPlainString();
                case "NUMERIC" -> {
                    int scale = definition.escalaNumerica() != null ? definition.escalaNumerica() : DEFAULT_NUMERIC_SCALE;
                    yield new BigDecimal(rawValue.trim()).setScale(scale, RoundingMode.HALF_UP).toPlainString();
                }
                default -> rawValue;
            };
        } catch (NumberFormatException ex) {
            logger.debug("No se pudo formatear '{}' como {} para columna {}. Se conserva texto.",
                    rawValue, definition.tipoDatoSalida(), definition.nombreColumna());
            return rawValue;
        }
    }

    private String getSourceValue(Map<String, Object> source, String key) {
        if (key == null) {
            return "";
        }
        Object value = source.get(key);
        if (value == null) {
            value = source.get(key.toLowerCase(Locale.ROOT));
        }
        if (value == null) {
            value = source.get(toSnakeCase(key));
        }
        if (value == null) {
            return "";
        }
        return normalizeValue(value);
    }

    private String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return Objects.toString(value, "").trim();
    }

    private boolean matchesCondition(String when, String candidate) {
        String normalizedWhen = safeText(when);
        if (normalizedWhen.isBlank()) {
            normalizedWhen = "NOT_BLANK";
        }
        return switch (normalizedWhen) {
            case "NOT_NULL", "NOT_BLANK" -> !isBlank(candidate);
            default -> !isBlank(candidate);
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isBlankOrZero(String value) {
        if (isBlank(value)) {
            return true;
        }
        try {
            return new BigDecimal(value.trim()).compareTo(BigDecimal.ZERO) == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String configuredLiteral(CreffosColumnDefinition definition, String jsonField, String fallback) {
        JsonNode node = definition.parametros().path(jsonField);
        if (!node.isMissingNode() && !node.isNull()) {
            return node.asText();
        }
        return fallback == null ? "" : fallback;
    }

    private String ensureExtension(String fileName, String format) {
        String normalizedName = firstNonBlank(fileName, DEFAULT_OUTPUT_NAME);
        String expectedExtension = "." + format.toLowerCase(Locale.ROOT);
        if (normalizedName.toLowerCase(Locale.ROOT).endsWith(expectedExtension)) {
            return normalizedName;
        }

        int dot = normalizedName.lastIndexOf('.');
        if (dot > 0) {
            return normalizedName.substring(0, dot) + expectedExtension;
        }
        return normalizedName + expectedExtension;
    }

    private String joinColumns(List<String> values, char delimiter) {
        return String.join(String.valueOf(delimiter), values);
    }

    private String escapeDelimited(String value, char delimiter) {
        String safeValue = value == null ? "" : value;
        boolean shouldQuote = safeValue.indexOf(delimiter) >= 0
                || safeValue.contains("\n")
                || safeValue.contains("\r")
                || safeValue.contains("\"");
        if (!shouldQuote) {
            return safeValue;
        }
        return '"' + safeValue.replace("\"", "\"\"") + '"';
    }

    private String qualifyLzTable(String schema, String table) {
        return validateSqlIdentifier(schema) + "." + validateSqlIdentifier(table);
    }

    private String validateSqlIdentifier(String rawIdentifier) {
        String identifier = firstNonBlank(rawIdentifier);
        if (!SQL_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Identificador SQL inválido para CREFFSOS: " + rawIdentifier);
        }
        return identifier;
    }

    private String joinQuoted(Collection<String> values) {
        List<String> quotedValues = new ArrayList<>(values.size());
        for (String value : values) {
            quotedValues.add('\'' + escapeSql(value) + '\'');
        }
        return String.join(",", quotedValues);
    }

    private String escapeSql(String rawValue) {
        return rawValue == null ? "" : rawValue.replace("'", "''");
    }

    private String toSnakeCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (char current : value.toCharArray()) {
            if (Character.isUpperCase(current) && !builder.isEmpty()) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(current));
        }
        return builder.toString();
    }

    private <T> List<List<T>> chunk(Collection<T> values, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        List<T> current = new ArrayList<>(chunkSize);
        for (T value : values) {
            current.add(value);
            if (current.size() == chunkSize) {
                chunks.add(current);
                current = new ArrayList<>(chunkSize);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    public record GeneratedCreffosFile(String fileName,
                                       String contentType,
                                       byte[] content,
                                       int rowCount,
                                       String format,
                                       String protectedFileName,
                                       byte[] protectedContent) {
    }

    private record OutputConfig(String format,
                                String fileName,
                                boolean includeHeader,
                                String sheetName,
                                String contentType) {
    }

    private static final class SequenceState {

        private final int increment;
        private long nextValue;
        private final long lastReservedValue;
        private final boolean enabled;

        private SequenceState(long nextValue, long lastReservedValue, int increment, boolean enabled) {
            this.nextValue = nextValue;
            this.lastReservedValue = lastReservedValue;
            this.increment = increment;
            this.enabled = enabled;
        }

        static SequenceState disabled() {
            return new SequenceState(0L, 0L, 0, false);
        }

        static SequenceState fromReservation(ParametroUnicoService.SequenceReservation reservation) {
            if (reservation == null || !reservation.enabled()) {
                return disabled();
            }
            return new SequenceState(
                    reservation.nextValue(),
                    reservation.lastReservedValue(),
                    reservation.increment(),
                    true
            );
        }

        long nextValue() {
            if (!enabled) {
                return 0L;
            }
            if (nextValue > lastReservedValue) {
                throw new IllegalStateException("Se agotó el bloque de consecutivos reservado para CREFFSOS.");
            }
            long allocated = nextValue;
            nextValue += increment;
            return allocated;
        }
    }
}