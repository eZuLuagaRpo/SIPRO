package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.ValidationRule;
import com.bancolombia.sipro.validations.infrastructure.repository.ClienteLzRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.ValidationRuleRepository;
import com.bancolombia.sipro.validations.service.ValidationProgressListener;
import com.bancolombia.sipro.validations.shared.exceptions.BusinessException;
import com.bancolombia.sipro.validations.shared.utils.XlsxStreamingReader;
import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Servicio de validación dinámica de archivos Excel basado en reglas de BD.
 * 
 * Soporta 3 tipos de reglas (rule_kind):
 * 1. FIELD: Validaciones de campo individual (regex, min/max, etc.)
 * 2. COMPOSITE_DATE: Construir fecha desde campos año/mes/día y validar que sea real
 * 3. DATE_RELATION: Comparar fechas entre sí o contra variables runtime (VAR_FECHA_CORTE)
 *
 * Además incluye una fase adicional:
 * 4. LZ_EXISTENCE: Validar que el NIT del Excel exista en sipro_lz_mdm_datos_generales_clientes
 * 5. USUARIO_EXISTENCE: Validar que el USUARIO del Excel exista en directorio activo Bancolombia
 *
 * Flujo de validación:
 * 1. Primero ejecuta reglas FIELD para validar cada campo individualmente
 * 2. Después ejecuta reglas COMPOSITE_DATE para armar fechas y validar que existan
 * 3. Finalmente ejecuta reglas DATE_RELATION para comparar fechas entre sí
 * 4. Validación de existencia NIT en LZ (max 30 errores, con fallback de periodo)
 * 5. Validación de existencia USUARIO en directorio corporativo (batch por usuarios únicos)
 *
 * Nota de mantenimiento 2026: comentarios funcionales agregados por
 * Junior Alexander Ortiz Arenas (junortiz), ANALITICO/A - EVC OTRAS FUNCIONES CORPORATIVAS.
 */
@Service
public class DynamicExcelValidationService {

    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream open() throws Exception;
    }

    private static final Logger logger = LoggerFactory.getLogger(DynamicExcelValidationService.class);
    // El progreso se reporta por bloques para no saturar logs ni eventos cuando el archivo es grande.
    private static final int ROW_PROGRESS_BATCH_SIZE = 5_000;

    // Las fórmulas vienen de reglas administradas por la aplicación, no de texto libre del usuario.
    // Por eso se permite un motor amplio, pero el origen de las reglas debe seguir controlado.
    private static final JexlEngine jexl = new JexlBuilder()
            .permissions(JexlPermissions.UNRESTRICTED)
            .strict(true)
            .silent(false)
            .create();

    private static final int DEFAULT_MAX_NIT_ERRORS = 30;
    private static final int DEFAULT_MAX_USUARIO_ERRORS = 50;
    private static final int DEFAULT_NIT_ERROR_SUMMARY_LIMIT = 5;
    private static final int DEFAULT_MAX_FIELD_ERRORS_PER_COLUMN = 50;

    private int MAX_NIT_ERRORS() {
        return parametroUnicoService.getInt("MAX_NIT_ERRORS", DEFAULT_MAX_NIT_ERRORS);
    }

    private int MAX_USUARIO_ERRORS() {
        return parametroUnicoService.getInt("MAX_USUARIO_ERRORS", DEFAULT_MAX_USUARIO_ERRORS);
    }

    private int NIT_ERROR_SUMMARY_LIMIT() {
        return parametroUnicoService.getInt("NIT_ERROR_SUMMARY_LIMIT", DEFAULT_NIT_ERROR_SUMMARY_LIMIT);
    }

    private int MAX_FIELD_ERRORS_PER_COLUMN() {
        return parametroUnicoService.getInt("MAX_FIELD_ERRORS_PER_COLUMN", DEFAULT_MAX_FIELD_ERRORS_PER_COLUMN);
    }

    private final ValidationRuleRepository ruleRepository;
    private final ClienteLzRepository clienteLzRepository;
    private final UsuarioDirectoryValidator usuarioDirectoryValidator;
    private final ParametroUnicoService parametroUnicoService;

    public DynamicExcelValidationService(ValidationRuleRepository ruleRepository,
                                          ClienteLzRepository clienteLzRepository,
                                          UsuarioDirectoryValidator usuarioDirectoryValidator,
                                          ParametroUnicoService parametroUnicoService) {
        this.ruleRepository = ruleRepository;
        this.clienteLzRepository = clienteLzRepository;
        this.usuarioDirectoryValidator = usuarioDirectoryValidator;
        this.parametroUnicoService = parametroUnicoService;
    }

    /**
     * Valida un archivo Excel contra las reglas definidas en BD
     * 
     * @param file Archivo multipart a validar
     * @param productoNombre Nombre del producto esperado
     * @param fechaCorte Fecha de corte seleccionada por el usuario (VAR_FECHA_CORTE)
     * @param usuarioAdmin Usuario administrador para validación metadata
     * @return Lista de errores encontrados (vacía si no hay errores)
     */
    public List<ValidationError> validateExcel(MultipartFile file, String productoNombre, 
                                                LocalDate fechaCorte, String usuarioAdmin) {
        return validateExcel(
            () -> file.getInputStream(),
            file.getOriginalFilename(),
            productoNombre,
            null,
            fechaCorte,
            usuarioAdmin,
            ValidationProgressListener.NOOP);
    }

    public List<ValidationError> validateExcel(MultipartFile file,
                                                String productoNombre,
                                                Long idSegmento,
                                                LocalDate fechaCorte,
                                                String usuarioAdmin) {
        return validateExcel(
            () -> file.getInputStream(),
            file.getOriginalFilename(),
            productoNombre,
            idSegmento,
            fechaCorte,
            usuarioAdmin,
            ValidationProgressListener.NOOP);
    }

    public List<ValidationError> validateExcel(InputStreamSupplier inputStreamSupplier,
                                                String productoNombre,
                                                Long idSegmento,
                                                LocalDate fechaCorte,
                                                String usuarioAdmin,
                                                ValidationProgressListener progressListener) {
        return validateExcel(inputStreamSupplier, null, productoNombre, idSegmento, fechaCorte, usuarioAdmin, progressListener);
    }

    public List<ValidationError> validateExcel(InputStreamSupplier inputStreamSupplier,
                                                String originalFilename,
                                                String productoNombre,
                                                Long idSegmento,
                                                LocalDate fechaCorte,
                                                String usuarioAdmin,
                                                ValidationProgressListener progressListener) {
        
        List<ValidationError> errors = new ArrayList<>();
        ValidationProgressListener safeProgressListener = progressListener != null ? progressListener : ValidationProgressListener.NOOP;
        
        try {
            String segmentKey = idSegmento != null ? String.valueOf(idSegmento) : null;
            if (segmentKey == null || segmentKey.isBlank()) {
                logger.warn("No se recibió idSegmento para validar el producto {}", productoNombre);
                errors.add(new ValidationError(0, "SISTEMA", "",
                    "No se pudo determinar el segmento para la validación del archivo"));
                return errors;
            }

            // 1. Obtener reglas desde BD según segmento real y reglas comunes (9999)
            List<ValidationRule> allRules = orderRules(
                ruleRepository.findActiveRulesBySegment(segmentKey, "9999"),
                segmentKey);
            
            if (allRules.isEmpty()) {
                logger.warn("No se encontraron reglas activas para el segmento {} y producto {}", segmentKey, productoNombre);
                errors.add(new ValidationError(0, "SISTEMA", "", 
                    "No hay reglas de validación configuradas para el segmento seleccionado"));
                return errors;
            }
            
            // Separar reglas por tipo
            List<ValidationRule> fieldRules = allRules.stream()
                .filter(ValidationRule::isFieldRule)
                .filter(ValidationRule::participatesInInputStructure)
                .collect(Collectors.toMap(
                    r -> normalizeHeaderName(r.getFieldName()),
                    r -> r,
                    (existing, ignored) -> existing,
                    LinkedHashMap::new
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
            
            List<ValidationRule> compositeDateRules = allRules.stream()
                .filter(ValidationRule::isCompositeDateRule)
                .collect(Collectors.toList());
            
            List<ValidationRule> dateRelationRules = allRules.stream()
                .filter(ValidationRule::isDateRelationRule)
                .collect(Collectors.toList());
            
            logger.info("Cargadas {} reglas: {} FIELD, {} COMPOSITE_DATE, {} DATE_RELATION para segmento: {} y producto: {}", 
                allRules.size(), fieldRules.size(), compositeDateRules.size(), 
                dateRelationRules.size(), segmentKey, productoNombre);
            
            // Mapa de reglas FIELD por nombre de campo
            Map<String, ValidationRule> fieldRulesMap = fieldRules.stream()
                .collect(Collectors.toMap(
                    r -> normalizeHeaderName(r.getFieldName()), 
                    r -> r,
                    (r1, r2) -> r1
                ));

            List<String> expectedColumns = buildExpectedColumns(fieldRules);

            // Variables runtime (fechaCorte del formulario)
            Map<String, LocalDate> runtimeVariables = new HashMap<>();
            runtimeVariables.put("VAR_FECHA_CORTE", fechaCorte);

            Map<Integer, String> colIndexToName = new LinkedHashMap<>();
            Map<String, Integer> colNameToIndex = new LinkedHashMap<>();
            Map<Integer, String> rowToNit = new LinkedHashMap<>();
            Map<String, List<Integer>> userToRows = new LinkedHashMap<>();
            Map<String, Integer> documentoMonedaPrimerFila = new LinkedHashMap<>();
            boolean[] headerProcessed = { false };
            boolean[] structuralFailure = { false };
            boolean[] outOfRangeDataReported = { false };
            long[] processedRows = { 0L };

            // Registrar error de múltiples hojas, pero continuar para consolidar
            // los demás errores de validación sobre la primera hoja.
            validateSingleSheet(inputStreamSupplier, originalFilename, errors);

            try {
                Long estimatedTotalRows = estimateTotalRows(inputStreamSupplier, originalFilename);
                if (estimatedTotalRows != null && estimatedTotalRows > 0) {
                    safeProgressListener.onTotalRowsEstimated(estimatedTotalRows);
                }
            } catch (Exception e) {
                logger.debug("No fue posible estimar filas totales del archivo para progreso lineal: {}", e.getMessage());
            }

            // Contadores de errores por tipo real (columna + mensaje) para limitar a TOP N por tipo
            Map<String, int[]> errorCountByColumn = new HashMap<>();
            Map<String, String> errorLabelByBucket = new LinkedHashMap<>();
            int maxFieldErrorsPerColumn = MAX_FIELD_ERRORS_PER_COLUMN();

            // 2. Procesar Excel
            try {
                readRows(inputStreamSupplier, originalFilename, (rowNumber, rowValues) -> {
                    if (!headerProcessed[0]) {
                        headerProcessed[0] = true;
                        List<String> normalizedHeaders = normalizeHeaders(rowValues);
                        initializeHeaders(normalizedHeaders, colIndexToName, colNameToIndex);
                        boolean shouldStopProcessing = validateColumnStructure(expectedColumns, normalizedHeaders, errors);
                        if (shouldStopProcessing) {
                            structuralFailure[0] = true;
                            throw new StopSheetProcessingException();
                        }
                        return;
                    }

                    if (isEmptyRow(rowValues)) {
                        return;
                    }

                    if (!outOfRangeDataReported[0]) {
                        Integer extraColumnIndex = findFirstDataOutsideExpectedRange(rowValues, expectedColumns.size());
                        if (extraColumnIndex != null) {
                            outOfRangeDataReported[0] = true;
                            errors.add(new ValidationError(
                                0,
                                "ARCHIVO",
                                "",
                                String.format(
                                    "Dentro del archivo manual existe informacion por fuera del rango de los %d campos que contiene la planilla manual, por favor eliminar esta informacion",
                                    expectedColumns.size())));
                        }
                    }

                    processedRows[0] = Math.max(processedRows[0], rowNumber - 1L);
                    if (processedRows[0] % ROW_PROGRESS_BATCH_SIZE == 0) {
                        safeProgressListener.onRowsProcessed(processedRows[0]);
                    }

                    Map<String, String> currentRowValues = buildRowValues(colIndexToName, rowValues);
                    Set<String> invalidColumnsInRow = new HashSet<>();

                    for (Map.Entry<String, String> entry : currentRowValues.entrySet()) {
                        String colName = entry.getKey();
                        String value = entry.getValue();
                        ValidationRule rule = fieldRulesMap.get(colName);

                        if (rule != null) {
                            try {
                                validateFieldRule(value, rule, currentRowValues,
                                        fechaCorte, productoNombre, usuarioAdmin);
                            } catch (ValidationException e) {
                                invalidColumnsInRow.add(colName);
                                registerValidationError(
                                    errors,
                                    errorCountByColumn,
                                    errorLabelByBucket,
                                    rowNumber,
                                    colName,
                                    value,
                                    e.getMessage(),
                                    maxFieldErrorsPerColumn);
                            }
                        }
                    }

                    try {
                        validateDocumentoMonedaUniqueness(currentRowValues, rowNumber, invalidColumnsInRow,
                                documentoMonedaPrimerFila);
                    } catch (BusinessException e) {
                        registerValidationError(
                            errors,
                            errorCountByColumn,
                            errorLabelByBucket,
                            rowNumber,
                            "DOCUMENTO",
                            currentRowValues.getOrDefault("DOCUMENTO", ""),
                            e.getMessage(),
                            maxFieldErrorsPerColumn);
                    }

                    // COMPOSITE_DATE: agrupamos bajo el fieldName de la regla
                    Map<String, LocalDate> composedDates = new HashMap<>();
                    for (ValidationRule cdRule : compositeDateRules) {
                        try {
                            LocalDate composedDate = validateCompositeDateRule(cdRule, currentRowValues);
                            composedDates.put(normalizeHeaderName(cdRule.getFieldName()), composedDate);
                        } catch (ValidationException e) {
                            registerValidationError(
                                errors,
                                errorCountByColumn,
                                errorLabelByBucket,
                                rowNumber,
                                cdRule.getFieldName(),
                                buildCompositeValue(cdRule, currentRowValues),
                                e.getMessage(),
                                maxFieldErrorsPerColumn,
                                "CD_");
                        }
                    }

                    for (ValidationRule drRule : dateRelationRules) {
                        try {
                            validateDateRelationRule(drRule, composedDates, runtimeVariables);
                        } catch (ValidationException e) {
                            registerValidationError(
                                errors,
                                errorCountByColumn,
                                errorLabelByBucket,
                                rowNumber,
                                drRule.getFieldName(),
                                "",
                                e.getMessage(),
                                maxFieldErrorsPerColumn,
                                "DR_");
                        }
                    }

                    collectNit(rowToNit, colNameToIndex.get("NIT"), rowValues, rowNumber);
                    collectUsuario(userToRows, colNameToIndex.get("USUARIO"), rowValues, rowNumber);
                });
            } catch (StopSheetProcessingException ignored) {
            }

            safeProgressListener.onRowsProcessed(processedRows[0]);

            if (structuralFailure[0]) {
                return errors;
            }

            // Si una misma columna falla cientos de veces, se muestran ejemplos y luego un resumen.
            // Así el usuario entiende el patrón del error sin recibir una respuesta inmanejable.
            for (Map.Entry<String, int[]> entry : errorCountByColumn.entrySet()) {
                int totalErrors = entry.getValue()[0];
                if (totalErrors > maxFieldErrorsPerColumn) {
                    int omitted = totalErrors - maxFieldErrorsPerColumn;
                    String bucketLabel = errorLabelByBucket.getOrDefault(entry.getKey(), entry.getKey().replaceFirst("^(CD_|DR_)", ""));
                    errors.add(new ValidationError(0, "RESUMEN", "",
                        String.format("Se encontraron %d errores adicionales del tipo '%s' (se muestran los primeros %d). Corrija estos ejemplos y vuelva a validar.",
                            omitted, bucketLabel, maxFieldErrorsPerColumn)));
                }
            }

            if (!headerProcessed[0]) {
                errors.add(new ValidationError(0, "ARCHIVO", "", "El archivo está vacío"));
                return errors;
            }

            if (!errors.isEmpty() && colNameToIndex.isEmpty()) {
                return errors;
            }

            // Estas validaciones externas solo corren si la estructura básica ya quedó sana.
            // Así evitamos consultas costosas cuando el archivo todavía viene roto de base.
            if (clienteLzRepository != null && colNameToIndex.containsKey("NIT")) {
                validateNitExistenceInLz(rowToNit, fechaCorte, errors);
            }

            if (usuarioDirectoryValidator != null && colNameToIndex.containsKey("USUARIO")) {
                validateUsuarioExistence(userToRows, errors);
            }
            
        } catch (Exception e) {
            logger.error("Error al validar archivo Excel: {}", e.getMessage(), e);
            errors.add(new ValidationError(0, "ARCHIVO", "",
                "No fue posible procesar el archivo. Revise el encabezado y el contenido e intente nuevamente."));
        }
        
        return errors;
    }

    // ========== VALIDACIÓN DE REGLAS FIELD ==========

    /**
     * Valida un valor individual contra una regla de tipo FIELD
     */
    private void validateFieldRule(String value, ValidationRule rule, 
                                   Map<String, String> rowContext, LocalDate fechaCorte,
                                   String productoEsperado, String usuarioEsperado) 
            throws ValidationException {
        
        // Validación de Nulos y Campos Obligatorios
        boolean isEmpty = value == null || value.trim().isEmpty();
        
        if (isEmpty) {
            if (allowsEmptyValue(rule)) {
                return;
            }
            if (Boolean.TRUE.equals(rule.getIsRequired())) {
                throw new ValidationException("El dato es obligatorio y no puede estar vacío.");
            }
            return;
        }

        if (isNullEquivalentValue(rule, value)) {
            return;
        }

        validateDataTypeAndNumericConstraints(value, rule);

        // Validación Regex
        if (rule.getRegexPattern() != null && !rule.getRegexPattern().isEmpty()) {
            if (!Pattern.matches(rule.getRegexPattern(), value)) {
                String hint = getDataTypeHint(rule.getDataType());
                String comment = rule.getComments() != null ? rule.getComments() : "";
                throw new ValidationException(String.format("%s %s (Valor recibido: '%s')", hint, comment, value));
            }
        }

        // Validación de Longitud Máxima
        if (rule.getMaxLength() != null && value.length() > rule.getMaxLength()) {
            throw new ValidationException(String.format(
                "La longitud del texto (%d caracteres) excede el máximo permitido de %d.", 
                value.length(), rule.getMaxLength()));
        }

        // Validación de Valores Permitidos
        if (rule.getAllowedValues() != null && !rule.getAllowedValues().isEmpty()) {
            if (!isValueAllowed(value, rule.getAllowedValues())) {
                throw new ValidationException(String.format(
                    "El valor '%s' no es válido. Valores permitidos: %s", 
                    value, rule.getAllowedValues()));
            }
        }

        // Validación de Fórmulas JEXL
        String formula = rule.getValidationFormula();
        if (formula != null && !formula.isEmpty()) {
            validateFormula(value, formula, rule, rowContext, fechaCorte, 
                          productoEsperado, usuarioEsperado);
        }
    }

    private boolean allowsEmptyValue(ValidationRule rule) {
        return Boolean.TRUE.equals(rule.getAllowNull()) || !Boolean.TRUE.equals(rule.getIsRequired());
    }

    private boolean isNullEquivalentValue(ValidationRule rule, String value) {
        return isClasificacionRule(rule)
            && Boolean.TRUE.equals(rule.getAllowNull())
            && "0".equals(value != null ? value.trim() : "");
    }

    private boolean isClasificacionRule(ValidationRule rule) {
        return normalizeHeaderName(rule.getFieldName()).equals("CLASIFICACION");
    }

    private void validateDataTypeAndNumericConstraints(String value, ValidationRule rule) throws ValidationException {
        String dataType = rule.getDataType() != null ? rule.getDataType().trim().toLowerCase(Locale.ROOT) : "";
        if (dataType.isEmpty()) {
            return;
        }

        if ("integer".equals(dataType)) {
            BigDecimal numericValue = parseNumericValue(value, true);
            validateNumericBounds(rule, numericValue);
            return;
        }

        if ("decimal".equals(dataType)) {
            BigDecimal numericValue = parseNumericValue(value, false);
            validateNumericBounds(rule, numericValue);
        }
    }

    private BigDecimal parseNumericValue(String value, boolean integerOnly) throws ValidationException {
        try {
            BigDecimal numericValue = new BigDecimal(value.trim().replace(",", "."));
            if (integerOnly && numericValue.stripTrailingZeros().scale() > 0) {
                throw new ValidationException("Se esperaba un número entero válido.");
            }
            return numericValue;
        } catch (NumberFormatException ex) {
            throw new ValidationException(integerOnly
                ? "Se esperaba un número entero válido."
                : "Se esperaba un número decimal válido (use punto para decimales).");
        }
    }

    private void validateNumericBounds(ValidationRule rule, BigDecimal numericValue) throws ValidationException {
        if (!Boolean.TRUE.equals(rule.getAllowNegative()) && numericValue.signum() < 0) {
            throw new ValidationException("El valor numérico no puede ser negativo.");
        }

        if (rule.getMinValue() != null && numericValue.compareTo(rule.getMinValue()) < 0) {
            throw new ValidationException(String.format(
                "El valor '%s' es menor al mínimo permitido de %s.",
                numericValue.stripTrailingZeros().toPlainString(),
                rule.getMinValue().stripTrailingZeros().toPlainString()));
        }

        if (rule.getMaxValue() != null && numericValue.compareTo(rule.getMaxValue()) > 0) {
            throw new ValidationException(String.format(
                "El valor '%s' es mayor al máximo permitido de %s.",
                numericValue.stripTrailingZeros().toPlainString(),
                rule.getMaxValue().stripTrailingZeros().toPlainString()));
        }
    }

    private String getDataTypeHint(String dataType) {
        if ("integer".equalsIgnoreCase(dataType)) {
            return "Se esperaba un número entero válido.";
        } else if ("decimal".equalsIgnoreCase(dataType)) {
            return "Se esperaba un número decimal válido (use punto para decimales).";
        } else if ("date".equalsIgnoreCase(dataType)) {
            return "El formato de fecha no es válido.";
        }
        return "El formato no cumple con los requisitos.";
    }

    private boolean isValueAllowed(String value, String allowedValues) {
        String[] allowed = allowedValues.split(",");
        for (String a : allowed) {
            if (a.contains("=")) {
                String code = a.split("=")[0].trim();
                if (value.equals(code)) {
                    return true;
                }
            } else if (value.equals(a.trim())) {
                return true;
            }
        }
        return false;
    }

    private void validateFormula(String value, String formula, ValidationRule rule,
                                 Map<String, String> rowContext, LocalDate fechaCorte,
                                 String productoEsperado, String usuarioEsperado) 
            throws ValidationException {
        
        try {
            JexlContext context = new MapContext();
            if (rowContext != null) {
                for (Map.Entry<String, String> e : rowContext.entrySet()) {
                    context.set(e.getKey(), e.getValue());
                }
            }
            
            context.set("value", value);
            context.set("util", new Util());
            context.set("PRODUCTO_ESPERADO", productoEsperado);
            context.set("USUARIO_ESPERADO", usuarioEsperado);
            context.set("FECHA_CORTE", fechaCorte);

            Object res = jexl.createExpression(formula).evaluate(context);
            
            if (Boolean.FALSE.equals(res)) {
                 String msg = rule.getComments() != null ? rule.getComments() : "La validación lógica falló";
                 throw new ValidationException(String.format("%s. (Valor analizado: '%s')", msg, value));
            }
            
        } catch (ValidationException e) {
            throw e;
        } catch (JexlException e) {
             logger.warn("Error evaluando fórmula JEXL '{}': {}", formula, e.getMessage());
             throw new ValidationException("No se pudo evaluar la regla lógica interna. Contacte soporte.");
        } catch (Exception e) {
            logger.warn("Error inesperado en fórmula '{}': {}", formula, e.getMessage());
            throw new ValidationException("Error inesperado validando el dato: " + e.getMessage());
        }
    }

    // ========== VALIDACIÓN DE REGLAS COMPOSITE_DATE ==========

    /**
     * Valida una regla COMPOSITE_DATE: toma year_field, month_field, day_field
     * y construye un LocalDate. Si no se puede construir, lanza error.
     * 
     * @return La fecha compuesta válida
     */
    private LocalDate validateCompositeDateRule(ValidationRule rule, Map<String, String> rowValues) 
            throws ValidationException {
        
        String yearFieldName = rule.getYearField();
        String monthFieldName = rule.getMonthField();
        String dayFieldName = rule.getDayField();

        if (yearFieldName == null || monthFieldName == null || dayFieldName == null) {
            throw new ValidationException(String.format(
                "Configuración incompleta para fecha compuesta '%s': faltan campos año/mes/día",
                rule.getFieldName()));
        }

        String yearStr = rowValues.get(yearFieldName.toUpperCase());
        String monthStr = rowValues.get(monthFieldName.toUpperCase());
        String dayStr = rowValues.get(dayFieldName.toUpperCase());

        // Verificar que los valores no estén vacíos
        if (isNullOrEmpty(yearStr) || isNullOrEmpty(monthStr) || isNullOrEmpty(dayStr)) {
            throw new ValidationException(String.format(
                "No se puede construir la fecha '%s': faltan valores en %s/%s/%s",
                rule.getFieldName(), yearFieldName, monthFieldName, dayFieldName));
        }

        try {
            int year = parseIntSafe(yearStr);
            int month = parseIntSafe(monthStr);
            int day = parseIntSafe(dayStr);

            // LocalDate.of lanza DateTimeException si la fecha no es válida
            // (ej: 31 de febrero, 30 de febrero en año bisiesto, etc.)
            LocalDate date = LocalDate.of(year, month, day);
            
            logger.debug("Fecha compuesta '{}' construida exitosamente: {}", rule.getFieldName(), date);
            return date;
            
        } catch (DateTimeException e) {
            throw new ValidationException(String.format(
                "Fecha inválida para '%s': El día %s/%s/%s no existe en el calendario. %s",
                rule.getFieldName(), dayStr, monthStr, yearStr,
                rule.getComments() != null ? rule.getComments() : ""));
        } catch (NumberFormatException e) {
            throw new ValidationException(String.format(
                "Valores no numéricos para fecha '%s': año=%s, mes=%s, día=%s",
                rule.getFieldName(), yearStr, monthStr, dayStr));
        }
    }

    private String buildCompositeValue(ValidationRule rule, Map<String, String> rowValues) {
        String y = rowValues.getOrDefault(rule.getYearField() != null ? rule.getYearField().toUpperCase() : "", "?");
        String m = rowValues.getOrDefault(rule.getMonthField() != null ? rule.getMonthField().toUpperCase() : "", "?");
        String d = rowValues.getOrDefault(rule.getDayField() != null ? rule.getDayField().toUpperCase() : "", "?");
        return String.format("%s-%s-%s", y, m, d);
    }

    private String buildErrorBucketKey(String columnName, String errorMessage) {
        return columnName + "||" + errorMessage;
    }

    private String buildErrorBucketLabel(String columnName, String errorMessage) {
        String normalizedColumn = columnName.replaceFirst("^(CD_|DR_)", "");
        return normalizedColumn + " -> " + errorMessage;
    }

    // ========== VALIDACIÓN DE REGLAS DATE_RELATION ==========

    /**
     * Valida una regla DATE_RELATION: compara left_code con right_code
     * usando operator_code.
     * 
     * Si right_is_variable=true, right_code se toma de runtimeVariables (ej: VAR_FECHA_CORTE)
     * Si right_is_variable=false, right_code es el field_name de una fecha compuesta
     */
    private void validateDateRelationRule(ValidationRule rule, 
                                         Map<String, LocalDate> composedDates,
                                         Map<String, LocalDate> runtimeVariables) 
            throws ValidationException {
        
        String leftCode = rule.getLeftCode();
        String rightCode = rule.getRightCode();
        String operator = rule.getOperatorCode();
        Boolean rightIsVariable = rule.getRightIsVariable();

        if (leftCode == null || rightCode == null || operator == null) {
            throw new ValidationException(String.format(
                "Configuración incompleta para relación de fechas '%s'", rule.getFieldName()));
        }

        // Obtener fecha izquierda (siempre de composedDates)
        LocalDate leftDate = composedDates.get(leftCode.toUpperCase());
        if (leftDate == null) {
            // Si no se pudo construir la fecha compuesta, ya se reportó error en COMPOSITE_DATE
            // Aquí simplemente saltamos la validación de relación
            logger.debug("Fecha izquierda '{}' no disponible, saltando validación de relación", leftCode);
            return;
        }

        // Obtener fecha derecha
        LocalDate rightDate;
        if (Boolean.TRUE.equals(rightIsVariable)) {
            // Es una variable runtime (ej: VAR_FECHA_CORTE)
            rightDate = runtimeVariables.get(rightCode.toUpperCase());
            if (rightDate == null) {
                throw new ValidationException(String.format(
                    "Variable runtime '%s' no está definida. Contacte soporte.", rightCode));
            }
        } else {
            // Es otra fecha compuesta
            rightDate = composedDates.get(rightCode.toUpperCase());
            if (rightDate == null) {
                logger.debug("Fecha derecha '{}' no disponible, saltando validación de relación", rightCode);
                return;
            }
        }

        // Evaluar la comparación
        boolean result = evaluateDateComparison(leftDate, operator, rightDate);
        
        if (!result) {
            String rightLabel = Boolean.TRUE.equals(rightIsVariable) 
                ? String.format("%s (%s)", rightCode, rightDate)
                : String.format("%s (%s)", rightCode, rightDate);
            
            throw new ValidationException(String.format(
                "%s. %s (%s) debe ser %s %s",
                rule.getComments() != null ? rule.getComments() : "Validación de fechas fallida",
                leftCode, leftDate, getOperatorDescription(operator), rightLabel));
        }
    }

    /**
     * Evalúa la comparación entre dos fechas según el operador
     */
    private boolean evaluateDateComparison(LocalDate left, String operator, LocalDate right) {
        switch (operator.trim()) {
            case "<=":
                return !left.isAfter(right); // left <= right
            case "<":
                return left.isBefore(right);
            case ">=":
                return !left.isBefore(right); // left >= right
            case ">":
                return left.isAfter(right);
            case "==":
            case "=":
                return left.isEqual(right);
            case "!=":
            case "<>":
                return !left.isEqual(right);
            default:
                logger.warn("Operador de comparación no reconocido: {}", operator);
                return true; // En caso de operador desconocido, no falla
        }
    }

    private String getOperatorDescription(String operator) {
        switch (operator.trim()) {
            case "<=": return "menor o igual a";
            case "<": return "menor que";
            case ">=": return "mayor o igual a";
            case ">": return "mayor que";
            case "==": case "=": return "igual a";
            case "!=": case "<>": return "diferente de";
            default: return operator;
        }
    }

    // ========== VALIDACIÓN NIT CONTRA LZ ==========

    /**
     * Fase 4: Valida que los NITs del Excel existan en sipro_lz_mdm_datos_generales_clientes.
     * <ul>
     *   <li>Recolecta NITs únicos del Excel</li>
     *   <li>Determina el periodo (year/month) desde fechaCorte, con fallback al mes anterior</li>
     *   <li>Hace UNA query batch para obtener NITs existentes</li>
     *   <li>Reporta hasta MAX_NIT_ERRORS errores para no sobrecargar</li>
     * </ul>
     */
    private void validateNitExistenceInLz(Map<Integer, String> rowToNit,
                                           LocalDate fechaCorte,
                                           List<ValidationError> errors) {
        if (rowToNit.isEmpty()) {
            return;
        }

        List<YearMonth> candidatePeriods = buildCandidateLzPeriods(fechaCorte);
        List<YearMonth> availablePeriods = candidatePeriods.stream()
            .filter(period -> clienteLzRepository.countByPeriod(period.getYear(), period.getMonthValue()) > 0)
            .collect(Collectors.toList());

        if (availablePeriods.isEmpty()) {
            String periodoOriginal = String.format("%d%02d", fechaCorte.getYear(), fechaCorte.getMonthValue());
            errors.add(new ValidationError(0, "NIT", "",
                "No se encontraron datos de clientes en LZ para el periodo " + periodoOriginal +
                " ni periodos cercanos. Contacte al administrador para verificar la ingesta de datos."));
            return;
        }

        logger.info("Validando NITs contra LZ usando periodos {} ({} NITs en Excel)",
                formatPeriods(availablePeriods), rowToNit.size());

        Set<String> uniqueNits = new LinkedHashSet<>(rowToNit.values());
        Set<String> existingNits = new LinkedHashSet<>();

        for (YearMonth period : availablePeriods) {
            Set<String> pendingNits = uniqueNits.stream()
                .filter(nit -> !existingNits.contains(nit))
                .collect(Collectors.toCollection(LinkedHashSet::new));

            if (pendingNits.isEmpty()) {
                break;
            }

            Set<String> foundInPeriod = clienteLzRepository.findExistingNits(
                period.getYear(),
                period.getMonthValue(),
                pendingNits);

            if (!foundInPeriod.isEmpty()) {
                existingNits.addAll(foundInPeriod);
            }
        }

        // Se corta el detalle para que la respuesta siga siendo útil incluso si todo el archivo
        // viene con NITs inexistentes.
        int maxNitErrors = MAX_NIT_ERRORS();
        int nitErrorCount = 0;
        for (Map.Entry<Integer, String> entry : rowToNit.entrySet()) {
            if (nitErrorCount >= maxNitErrors) break;

            String nit = entry.getValue();
            if (!existingNits.contains(nit)) {
                errors.add(new ValidationError(entry.getKey(), "NIT", nit,
                    "El registro no cruza con la base de datos de usuarios " +
                    "fcr_mdm_datos_generales_clientes. Por favor cargar nuevamente " +
                    "el archivo sin el registro y gestionar la inclusión para futuros archivos."));
                nitErrorCount++;
            }
        }

        if (nitErrorCount >= maxNitErrors) {
            errors.add(new ValidationError(0, "NIT", "",
                "Se alcanzó el límite de " + maxNitErrors + " registros NIT no encontrados en LZ. " +
                "Corrija estos registros y vuelva a validar para detectar posibles errores adicionales."));
        }

        logger.info("Validación NIT completada: {} errores de {} NITs verificados", nitErrorCount, uniqueNits.size());
    }

    private List<YearMonth> buildCandidateLzPeriods(LocalDate fechaCorte) {
        LinkedHashSet<YearMonth> candidatePeriods = new LinkedHashSet<>();
        candidatePeriods.add(YearMonth.from(fechaCorte));
        candidatePeriods.add(YearMonth.from(fechaCorte.minusMonths(1)));
        candidatePeriods.add(YearMonth.from(fechaCorte.plusMonths(1)));
        candidatePeriods.add(YearMonth.now());
        return new ArrayList<>(candidatePeriods);
    }

    private String formatPeriods(List<YearMonth> periods) {
        return periods.stream()
            .map(period -> period.getYear() + "/" + period.getMonthValue())
            .collect(Collectors.joining(", "));
    }

    // ========== FASE 5: VALIDACIÓN USUARIO EN DIRECTORIO CORPORATIVO ==========

    /**
     * Fase 5: Valida que los valores de la columna USUARIO existan en el directorio
     * corporativo de Bancolombia.
     * <p>
     * Estrategia optimizada:
     * <ul>
     *   <li>Recolecta usuarios únicos (lowercase) del Excel</li>
     *   <li>Hace UNA consulta batch al directorio (DB o Graph API)</li>
     *   <li>Reporta UN error consolidado por cada usuario no encontrado (no por fila)</li>
     *   <li>Máximo {@code MAX_USUARIO_ERRORS} errores</li>
     * </ul>
     */
    private void validateUsuarioExistence(Map<String, List<Integer>> userToRows,
                                           List<ValidationError> errors) {
        if (userToRows.isEmpty()) {
            return;
        }

        logger.info("Validando {} usuarios únicos contra directorio corporativo", userToRows.size());

        // 5.2 Query batch: obtener usuarios que SÍ existen
        Set<String> uniqueUsers = new LinkedHashSet<>(userToRows.keySet());
        Set<String> existingUsers = usuarioDirectoryValidator.findExistingUsers(uniqueUsers);

        // Se consolida un error por usuario y no por fila para que el equipo corrija el dato
        // base una sola vez, en lugar de leer decenas de mensajes repetidos.
        int maxUsuarioErrors = MAX_USUARIO_ERRORS();
        int userErrorCount = 0;
        for (Map.Entry<String, List<Integer>> entry : userToRows.entrySet()) {
            if (userErrorCount >= maxUsuarioErrors) break;

            String usuario = entry.getKey();
            if (!existingUsers.contains(usuario)) {
                List<Integer> filas = entry.getValue();
                int totalFilas = filas.size();
                int filasResumenLimit = NIT_ERROR_SUMMARY_LIMIT();
                
                // Construir mensaje consolidado (no fila por fila)
                String filasResumen;
                if (totalFilas <= filasResumenLimit) {
                    filasResumen = filas.stream().map(String::valueOf).collect(Collectors.joining(", "));
                } else {
                    filasResumen = filas.subList(0, filasResumenLimit).stream().map(String::valueOf)
                            .collect(Collectors.joining(", ")) + " y " + (totalFilas - filasResumenLimit) + " más";
                }

                errors.add(new ValidationError(0, "USUARIO", usuario,
                    "El usuario '" + usuario + "' no existe en el directorio activo de usuarios de " +
                    "Bancolombia. Aparece en " + totalFilas + " fila(s): [" + filasResumen + "]. " +
                    "Debe ser un usuario válido y activo. Corrija la columna USUARIO en esas filas."));
                userErrorCount++;
            }
        }

        if (userErrorCount >= maxUsuarioErrors) {
            errors.add(new ValidationError(0, "USUARIO", "",
                "Se alcanzó el límite de " + maxUsuarioErrors + " usuarios no encontrados en directorio. " +
                "Corrija estos registros y vuelva a validar para detectar posibles errores adicionales."));
        }

        logger.info("Validación USUARIO completada: {} usuarios no encontrados de {} verificados",
                userErrorCount, uniqueUsers.size());
    }

    // ========== MÉTODOS AUXILIARES ==========

    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private int parseIntSafe(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new NumberFormatException("Valor vacío");
        }
        // Manejar decimales (ej: "2026.0" -> 2026)
        String clean = s.trim();
        if (clean.contains(".")) {
            clean = clean.substring(0, clean.indexOf("."));
        }
        return Integer.parseInt(clean);
    }

    private void initializeHeaders(List<String> rowValues,
                                   Map<Integer, String> colIndexToName,
                                   Map<String, Integer> colNameToIndex) {
        colIndexToName.clear();
        colNameToIndex.clear();

        for (int index = 0; index < rowValues.size(); index++) {
            String colName = normalizeHeaderName(rowValues.get(index));
            if (!colName.isEmpty()) {
                colIndexToName.put(index, colName);
                colNameToIndex.putIfAbsent(colName, index);
            }
        }
    }

    private boolean validateColumnStructure(List<String> expectedColumns,
                                            List<String> actualHeaders,
                                            List<ValidationError> errors) {
        if (expectedColumns.isEmpty()) {
            return false;
        }

        if (actualHeaders.isEmpty() || actualHeaders.stream().allMatch(String::isBlank)) {
            errors.add(new ValidationError(0, "ARCHIVO", "",
                    "El archivo no tiene encabezados para validar."));
            return true;
        }

        boolean hasCountMismatch = actualHeaders.size() != expectedColumns.size();
        if (actualHeaders.size() != expectedColumns.size()) {
            errors.add(new ValidationError(0, "ARCHIVO", "",
                    String.format(
                        "El archivo tiene %d columnas y se esperaban %d.",
                        actualHeaders.size(),
                        expectedColumns.size())));
        }

        List<String> missingColumns = describeMissingHeaders(expectedColumns, actualHeaders);
        for (String missingColumn : missingColumns) {
            errors.add(new ValidationError(0, "ARCHIVO", "",
                    "Falta la columna " + missingColumn + "."));
        }

        List<String> extraColumns = describeExtraHeaders(expectedColumns, actualHeaders);
        for (String extraColumn : extraColumns) {
            if (extraColumn.startsWith("sin nombre")) {
                errors.add(new ValidationError(0, "ARCHIVO", "",
                        "Hay una columna sin nombre " + extraColumn.substring("sin nombre".length()) + "."));
            } else {
                errors.add(new ValidationError(0, "ARCHIVO", "",
                        "La columna " + extraColumn + " está de más."));
            }
        }

        List<HeaderOrderMismatch> orderMismatches = describeHeaderOrderMismatches(expectedColumns, actualHeaders);
        for (HeaderOrderMismatch mismatch : orderMismatches) {
            errors.add(new ValidationError(0, "ARCHIVO", "",
                    String.format(
                        "En la posición %d se esperaba la columna '%s', pero se encontró '%s'.",
                        mismatch.position(),
                        mismatch.expectedHeader(),
                        mismatch.actualHeader())));
        }

        return hasCountMismatch
                || !missingColumns.isEmpty()
                || !extraColumns.isEmpty()
                || !orderMismatches.isEmpty();
    }

    private List<String> describeMissingHeaders(List<String> expectedColumns, List<String> actualHeaders) {
        Map<String, Integer> actualOccurrences = new HashMap<>();
        for (String header : actualHeaders) {
            String normalized = normalizeHeaderName(header);
            actualOccurrences.merge(normalized, 1, Integer::sum);
        }

        Map<String, Integer> expectedSeen = new HashMap<>();
        List<String> missingHeaders = new ArrayList<>();
        for (int index = 0; index < expectedColumns.size(); index++) {
            String expectedHeader = normalizeHeaderName(expectedColumns.get(index));
            int seen = expectedSeen.merge(expectedHeader, 1, Integer::sum);
            if (actualOccurrences.getOrDefault(expectedHeader, 0) < seen) {
                missingHeaders.add(formatHeaderWithPosition(expectedHeader, index + 1));
            }
        }
        return missingHeaders;
    }

    private List<String> describeExtraHeaders(List<String> expectedColumns, List<String> actualHeaders) {
        Map<String, Integer> expectedOccurrences = new HashMap<>();
        for (String header : expectedColumns) {
            expectedOccurrences.merge(normalizeHeaderName(header), 1, Integer::sum);
        }

        Map<String, Integer> actualSeen = new HashMap<>();
        List<String> extraHeaders = new ArrayList<>();
        for (int index = 0; index < actualHeaders.size(); index++) {
            String header = normalizeHeaderName(actualHeaders.get(index));
            int seen = actualSeen.merge(header, 1, Integer::sum);
            int allowed = expectedOccurrences.getOrDefault(header, 0);
            if (header.isBlank() || seen > allowed) {
                extraHeaders.add(formatHeaderWithPosition(header, index + 1));
            }
        }
        return extraHeaders;
    }

    private String formatHeaderWithPosition(String header, int position) {
        if (header == null || header.isBlank()) {
            return "sin nombre (posición " + position + ")";
        }
        return "'" + header + "' (posición " + position + ")";
    }

    private List<HeaderOrderMismatch> describeHeaderOrderMismatches(List<String> expectedColumns,
                                                                    List<String> actualHeaders) {
        int limit = Math.min(expectedColumns.size(), actualHeaders.size());
        List<HeaderOrderMismatch> mismatches = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            if (!Objects.equals(expectedColumns.get(index), actualHeaders.get(index))) {
                mismatches.add(new HeaderOrderMismatch(
                        index + 1,
                        expectedColumns.get(index),
                        headerValueAt(actualHeaders, index)));
            }
        }
        return mismatches;
    }

    private String headerValueAt(List<String> actualHeaders, int index) {
        if (index < 0 || index >= actualHeaders.size()) {
            return "(sin columna)";
        }
        String value = actualHeaders.get(index);
        return value == null || value.isBlank() ? "(vacío)" : value;
    }

    private List<String> buildExpectedColumns(List<ValidationRule> fieldRules) {
        return fieldRules.stream()
                .sorted(Comparator
                        .comparing((ValidationRule rule) -> rule.getOrden() == null ? Integer.MAX_VALUE : rule.getOrden())
                        .thenComparing(rule -> normalizeHeaderName(rule.getFieldName())))
                .map(ValidationRule::getFieldName)
                .map(this::normalizeHeaderName)
                .collect(Collectors.toList());
    }

    private List<ValidationRule> orderRules(List<ValidationRule> rules, String segmentKey) {
        return rules.stream()
                .sorted(Comparator
                        .comparing((ValidationRule rule) -> !appliesSpecificallyToSegment(rule, segmentKey))
                        .thenComparing(rule -> rule.getOrden() == null ? Integer.MAX_VALUE : rule.getOrden())
                        .thenComparing(rule -> normalizeHeaderName(rule.getFieldName()))
                        .thenComparing(rule -> rule.getRuleId() == null ? Integer.MAX_VALUE : rule.getRuleId()))
                .collect(Collectors.toList());
    }

    private boolean appliesSpecificallyToSegment(ValidationRule rule, String segmentKey) {
        if (segmentKey == null || segmentKey.isBlank() || rule.getAppliesToProduct() == null) {
            return false;
        }
        return !"ALL".equalsIgnoreCase(rule.getAppliesToProduct())
                && !"9999".equalsIgnoreCase(rule.getAppliesToProduct())
                && segmentKey.equalsIgnoreCase(rule.getAppliesToProduct());
    }

    private Map<String, String> buildRowValues(Map<Integer, String> colIndexToName,
                                               List<String> rowValues) {
        Map<String, String> currentRowValues = new HashMap<>();
        for (Map.Entry<Integer, String> entry : colIndexToName.entrySet()) {
            currentRowValues.putIfAbsent(entry.getValue(), getValue(rowValues, entry.getKey()));
        }
        return currentRowValues;
    }

    private record HeaderOrderMismatch(int position, String expectedHeader, String actualHeader) {
    }

    private void validateDocumentoMonedaUniqueness(Map<String, String> currentRowValues,
                                                   int rowNumber,
                                                   Set<String> invalidColumnsInRow,
                                                   Map<String, Integer> documentoMonedaPrimerFila) {
        if (invalidColumnsInRow.contains("DOCUMENTO") || invalidColumnsInRow.contains("MONEDA")) {
            return;
        }

        String documento = currentRowValues.getOrDefault("DOCUMENTO", "").trim();
        String moneda = currentRowValues.getOrDefault("MONEDA", "").trim();

        if (documento.isEmpty() || moneda.isEmpty()) {
            return;
        }

        String compositeKey = documento + "|" + moneda;
        Integer firstRow = documentoMonedaPrimerFila.putIfAbsent(compositeKey, rowNumber);
        if (firstRow != null) {
            throw new BusinessException(String.format(
                "La combinación DOCUMENTO '%s' y MONEDA '%s' está duplicada. Ya apareció en la fila %d. DOCUMENTO solo puede repetirse cuando MONEDA sea diferente.",
                documento,
                moneda,
                firstRow));
        }
    }

    private void collectNit(Map<Integer, String> rowToNit,
                            Integer nitColIdx,
                            List<String> rowValues,
                            int rowNumber) {
        if (nitColIdx == null) {
            return;
        }

        String nitValue = getValue(rowValues, nitColIdx);
        if (!nitValue.isEmpty()) {
            rowToNit.put(rowNumber, nitValue);
        }
    }

    private void collectUsuario(Map<String, List<Integer>> userToRows,
                                Integer usuarioColIdx,
                                List<String> rowValues,
                                int rowNumber) {
        if (usuarioColIdx == null) {
            return;
        }

        String userValue = getValue(rowValues, usuarioColIdx).toLowerCase();
        if (!userValue.isEmpty()) {
            userToRows.computeIfAbsent(userValue, key -> new ArrayList<>()).add(rowNumber);
        }
    }

    private String getValue(List<String> rowValues, int colIdx) {
        if (colIdx < 0 || colIdx >= rowValues.size()) {
            return "";
        }
        return rowValues.get(colIdx).trim();
    }

    private Integer findFirstDataOutsideExpectedRange(List<String> rowValues, int expectedColumnCount) {
        if (expectedColumnCount < 0 || rowValues == null || rowValues.isEmpty()) {
            return null;
        }

        for (int colIdx = expectedColumnCount; colIdx < rowValues.size(); colIdx++) {
            String value = rowValues.get(colIdx);
            if (value != null && !value.trim().isEmpty()) {
                return colIdx;
            }
        }

        return null;
    }

    private List<String> normalizeHeaders(List<String> rowValues) {
        return rowValues.stream()
                .map(this::normalizeHeaderName)
                .collect(Collectors.toList());
    }

    private String normalizeHeaderName(String header) {
        return header == null ? "" : header.trim().toUpperCase(Locale.ROOT);
    }

    private Long estimateTotalRows(InputStreamSupplier inputStreamSupplier, String originalFilename) throws Exception {
        if (isCsvFile(originalFilename)) {
            return estimateCsvDataRows(inputStreamSupplier);
        }

        try (InputStream estimateStream = inputStreamSupplier.open()) {
            return XlsxStreamingReader.estimateFirstSheetDataRowCount(estimateStream);
        }
    }

    private Long estimateCsvDataRows(InputStreamSupplier inputStreamSupplier) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStreamSupplier.open(), StandardCharsets.UTF_8))) {
            long lineCount = 0;
            while (reader.readLine() != null) {
                lineCount++;
            }
            return Math.max(0, lineCount - 1);
        }
    }

    private void readRows(InputStreamSupplier inputStreamSupplier,
                          String originalFilename,
                          XlsxStreamingReader.RowConsumer rowConsumer) throws Exception {
        if (isCsvFile(originalFilename)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStreamSupplier.open(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line == null) {
                    return;
                }

                char delimiter = detectDelimiter(line);
                int rowNumber = 1;
                rowConsumer.accept(rowNumber++, splitDelimitedLine(line, delimiter));

                while ((line = reader.readLine()) != null) {
                    rowConsumer.accept(rowNumber++, splitDelimitedLine(line, delimiter));
                }
            }
            return;
        }

        try (InputStream inputStream = inputStreamSupplier.open()) {
            XlsxStreamingReader.readFirstSheet(inputStream, rowConsumer);
        }
    }

    private char detectDelimiter(String headerLine) {
        return headerLine != null && headerLine.contains(";") ? ';' : ',';
    }

    private List<String> splitDelimitedLine(String line, char delimiter) {
        if (line == null) {
            return List.of();
        }
        return Arrays.stream(line.split(Pattern.quote(String.valueOf(delimiter)), -1))
                .map(value -> value == null ? "" : value.trim())
                .collect(Collectors.toList());
    }

    private boolean isCsvFile(String originalFilename) {
        return originalFilename != null && originalFilename.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private void registerValidationError(List<ValidationError> errors,
                                         Map<String, int[]> errorCountByColumn,
                                         Map<String, String> errorLabelByBucket,
                                         int rowNumber,
                                         String columnName,
                                         String value,
                                         String message,
                                         int maxFieldErrorsPerColumn) {
        registerValidationError(errors, errorCountByColumn, errorLabelByBucket, rowNumber, columnName, value,
                message, maxFieldErrorsPerColumn, "");
    }

    private void registerValidationError(List<ValidationError> errors,
                                         Map<String, int[]> errorCountByColumn,
                                         Map<String, String> errorLabelByBucket,
                                         int rowNumber,
                                         String columnName,
                                         String value,
                                         String message,
                                         int maxFieldErrorsPerColumn,
                                         String bucketPrefix) {
        String normalizedColumn = normalizeHeaderName(columnName);
        String bucketKey = buildErrorBucketKey(bucketPrefix + normalizedColumn, message);
        errorLabelByBucket.putIfAbsent(bucketKey, buildErrorBucketLabel(columnName, message));
        int[] count = errorCountByColumn.computeIfAbsent(bucketKey, ignored -> new int[]{0});
        count[0]++;
        if (count[0] <= maxFieldErrorsPerColumn) {
            errors.add(new ValidationError(rowNumber, columnName, value, message));
        }
    }

    private boolean isEmptyRow(List<String> rowValues) {
        for (String value : rowValues) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ========== CLASES INTERNAS ==========

    /**
     * Clase de utilidad expuesta al contexto JEXL para usar en fórmulas
     */
    public static class Util {
        public int toInt(Object o) {
            if (o == null) return 0;
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) return 0;
            try {
                return (int) Double.parseDouble(s);
            } catch (Exception e) {
                return 0;
            }
        }

        public int nowYear() {
            return java.time.Year.now().getValue();
        }

        public int nowMonth() {
            return java.time.LocalDate.now().getMonthValue();
        }

        public BigDecimal toBigDecimal(Object o) {
            if (o == null) return BigDecimal.ZERO;
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) return BigDecimal.ZERO;
            s = s.replace(",", ".");
            try {
                return new BigDecimal(s);
            } catch(Exception e) {
                return BigDecimal.ZERO;
            }
        }

        public boolean isValidDate(Object y, Object m, Object d) {
            try {
                LocalDate.of(toInt(y), toInt(m), toInt(d));
                return true;
            } catch (Exception ex) {
                return false;
            }
        }

        public LocalDate date(Object y, Object m, Object d) {
            return LocalDate.of(toInt(y), toInt(m), toInt(d));
        }

        public boolean onOrAfter(Object y1, Object m1, Object d1, Object y2, Object m2, Object d2) {
            try {
                return !date(y1, m1, d1).isBefore(date(y2, m2, d2));
            } catch (Exception ex) {
                return false;
            }
        }

        public boolean after(Object y1, Object m1, Object d1, Object y2, Object m2, Object d2) {
            try {
                return date(y2, m2, d2).isAfter(date(y1, m1, d1));
            } catch (Exception ex) {
                return false;
            }
        }

        public boolean isAfter(Object y1, Object m1, Object d1, Object y2, Object m2, Object d2) {
            return after(y1, m1, d1, y2, m2, d2);
        }
    }

    /**
     * Clase para representar un error de validación detallado
     */
    public static class ValidationError {
        private final int rowNumber;
        private final String columnName;
        private final String cellValue;
        private final String errorMessage;

        public ValidationError(int rowNumber, String columnName, String cellValue, String errorMessage) {
            this.rowNumber = rowNumber;
            this.columnName = columnName;
            this.cellValue = cellValue;
            this.errorMessage = errorMessage;
        }

        public int getRowNumber() {
            return rowNumber;
        }

        public String getColumnName() {
            return columnName;
        }

        public String getCellValue() {
            return cellValue;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            if (rowNumber == 0) {
                return String.format("%s: %s", getDisplayLabel(columnName), errorMessage);
            }
            return String.format("Fila %d, Columna '%s': %s (Tu valor fue: '%s')", 
                rowNumber, columnName, errorMessage, cellValue);
        }

        private String getDisplayLabel(String columnName) {
            if (columnName == null || columnName.isBlank()) {
                return "Archivo";
            }
            if ("ARCHIVO".equalsIgnoreCase(columnName)) {
                return "Archivo";
            }
            if ("RESUMEN".equalsIgnoreCase(columnName)) {
                return "Resumen";
            }
            return columnName;
        }
    }

    /**
     * Excepción personalizada para validaciones
     */
    private static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    private static class StopSheetProcessingException extends RuntimeException {
    }

    // ========== VALIDACIÓN DE ARCHIVO CONTROL (.txt) ==========

    /**
     * Cuenta las filas de datos de un Excel (excluye encabezado y filas vacías).
     * Se usa para comparar contra el número declarado en el archivo control.txt.
     */
    public long countDataRows(InputStreamSupplier inputStreamSupplier, String originalFilename) {
        long[] count = { 0L };
        boolean[] headerSkipped = { false };
        try {
            readRows(inputStreamSupplier, originalFilename, (rowNumber, rowValues) -> {
                if (!headerSkipped[0]) {
                    headerSkipped[0] = true;
                    return;
                }
                if (!isEmptyRow(rowValues)) {
                    count[0]++;
                }
            });
        } catch (Exception e) {
            logger.warn("No fue posible contar filas del xlsx para validación de control: {}", e.getMessage());
        }
        return count[0];
    }

    /**
     * Valida el contenido del archivo control (.txt) para planillas Full IFRS.
     *
     * Ejecuta las reglas CTRL_CONTENT y CTRL_RECORD_COUNT cargadas desde BD:
     * - CTRL_CONTENT  : el texto del .txt debe ser un entero puro (regex ^[0-9]+$).
     * - CTRL_RECORD_COUNT: el número declarado debe coincidir con las filas de la planilla.
     *
     * @param ctrlContent     contenido crudo del .txt (sin trim previo)
     * @param planillaRowCount filas de datos de la planilla (sin encabezado)
     * @param idSegmento      segmento del producto (2 = Full IFRS)
     * @return lista de errores; vacía si todo es correcto
     */
    public List<ValidationError> validateControlFile(String ctrlContent, long planillaRowCount, Long idSegmento) {
        List<ValidationError> errors = new ArrayList<>();

        if (ctrlContent == null) {
            errors.add(new ValidationError(0, "ARCHIVO_CONTROL", "",
                "El archivo control (.txt) está vacío o no se pudo leer."));
            return errors;
        }

        String segmentKey = idSegmento != null ? String.valueOf(idSegmento) : "2";
        List<ValidationRule> ctrlRules = ruleRepository.findActiveRulesBySegment(segmentKey, "9999")
            .stream()
            .filter(r -> r.isCtrlContentRule() || r.isCtrlRecordCountRule())
            .collect(Collectors.toList());

        String trimmed = ctrlContent.trim();

        for (ValidationRule rule : ctrlRules) {
            if (rule.isCtrlContentRule()) {
                String pattern = rule.getRegexPattern();
                boolean hasSpaces = ctrlContent.contains(" ");
                boolean failsPattern = pattern != null && !pattern.isEmpty() && !trimmed.matches(pattern);
                if (hasSpaces || failsPattern) {
                    String raw = rule.getComments() != null ? rule.getComments() : "";
                    // El comentario usa ###### como placeholder del valor recibido
                    String msg = raw.contains("######")
                        ? raw.replace("######", ctrlContent)
                        : String.format(
                            "Se esperaba un número entero válido sin puntos, comas, espacios, " +
                            "letras ni caracteres especiales (Valor recibido: '%s') (Tu valor fue: '%s')",
                            ctrlContent, ctrlContent);
                    errors.add(new ValidationError(0, "CONTENIDO_CONTROL", ctrlContent, msg));
                }

            } else if (rule.isCtrlRecordCountRule()) {
                // Solo validar conteo si el contenido ya pasó la validación de formato
                boolean ctrlContentFailed = errors.stream()
                    .anyMatch(e -> "CONTENIDO_CONTROL".equals(e.getColumnName()));
                if (!ctrlContentFailed) {
                    try {
                        long ctrlCount = Long.parseLong(trimmed);
                        if (ctrlCount != planillaRowCount) {
                            String msg = rule.getComments() != null ? rule.getComments() :
                                "el número indicado en el archivo control no coincide con la cantidad " +
                                "de registros de la planilla manual para el mismo producto";
                            errors.add(new ValidationError(0, "CONTEO_REGISTROS_CONTROL", trimmed, msg));
                        }
                    } catch (NumberFormatException e) {
                        errors.add(new ValidationError(0, "CONTEO_REGISTROS_CONTROL", trimmed,
                            "No fue posible interpretar el contenido del archivo control como número entero."));
                    }
                }
            }
        }

        return errors;
    }

    /**
     * Valida que el archivo Excel tenga solo una hoja (la primera).
     * Los archivos CSV no tienen múltiples hojas por lo que se omite la validación.
     * Si detecta múltiples hojas, registra error y permite continuar para consolidar
     * el resto de errores funcionales en una sola respuesta.
     */
    private void validateSingleSheet(InputStreamSupplier inputStreamSupplier,
                                     String originalFilename,
                                     List<ValidationError> errors) {
        // Solo aplica para XLSX.
        if (isCsvFile(originalFilename)) {
            return;
        }

        try {
            int sheetCount = countExcelSheets(inputStreamSupplier);
            if (sheetCount > 1) {
                errors.add(new ValidationError(0, "ARCHIVO", "",
                    "El archivo contiene más de una hoja, por favor solo conservar una hoja con la información a consolidar"));
                logger.warn("El archivo tiene múltiples hojas ({}). Se continúa validando la primera hoja para consolidar errores.", sheetCount);
            }
        } catch (Exception e) {
            logger.warn("No fue posible validar el número de hojas del archivo: {}", e.getMessage());
        }
    }

    /**
     * Cuenta cuántas hojas tiene el archivo Excel sin cargar todo en memoria.
     */
    private int countExcelSheets(InputStreamSupplier inputStreamSupplier) throws Exception {
        try (InputStream inputStream = inputStreamSupplier.open();
             OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader reader = new XSSFReader(opcPackage);
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            int count = 0;
            while (sheets.hasNext()) {
                sheets.next();
                count++;
            }
            return count;
        } catch (OpenXML4JException e) {
            throw new IOException("Error al contar las hojas del archivo XLSX", e);
        }
    }
}
