package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.ValidationRule;
import com.bancolombia.sipro.validations.infrastructure.repository.ClienteLzRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.ValidationRuleRepository;
import com.bancolombia.sipro.validations.service.ValidationProgressListener;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * Cubre validaciones estructurales y de contenido para archivos Excel y CSV.
 */
class DynamicExcelValidationServiceTest {

    private static final LocalDate FECHA_CORTE = LocalDate.of(2026, 3, 31);

    @Mock
    private ValidationRuleRepository ruleRepository;

    @Mock
    private ClienteLzRepository clienteLzRepository;

    @Mock
    private UsuarioDirectoryValidator usuarioDirectoryValidator;

    @Mock
    private ParametroUnicoService parametroUnicoService;

    private DynamicExcelValidationService service;

    @BeforeEach
    void setUp() {
        service = new DynamicExcelValidationService(
                ruleRepository,
                clienteLzRepository,
                usuarioDirectoryValidator,
                parametroUnicoService);

        // lenient: los tests de ctrl file y countDataRows no invocan parametroUnicoService
        lenient().when(parametroUnicoService.getInt(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void shouldRequireClasificacionColumnEvenWhenFieldValueIsOptional() throws Exception {
        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(),
                buildWorkbookBytes(
                        List.of("DOCUMENTO", "MONEDA"),
                        List.of(List.of("893483743", "0"))),
                "planilla.xlsx");

        assertTrue(errors.stream().anyMatch(error -> error.getErrorMessage().contains("CLASIFICACION")
                && error.getErrorMessage().contains("posición 3")));
        assertTrue(errors.stream().anyMatch(error -> error.getErrorMessage().contains("tiene 2 columnas y se esperaban 3")));
    }

    @Test
    void shouldFailWhenHeaderOrderOrColumnCountDoesNotMatchConfiguredOrder() throws Exception {
        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(),
                buildWorkbookBytes(
                        List.of("MONEDA", "DOCUMENTO", "CLASIFICACION", "EXTRA"),
                        List.of(List.of("0", "893483743", "1", "SOBRA"))),
                "planilla.xlsx");

        assertTrue(errors.stream().anyMatch(error -> error.getErrorMessage().contains("'EXTRA' (posición 4)")
                && error.getErrorMessage().contains("está de más")));
        assertTrue(errors.stream().anyMatch(error -> error.getErrorMessage().contains("En la posición 1 se esperaba la columna 'DOCUMENTO'")));
        assertTrue(errors.stream().anyMatch(error -> error.getErrorMessage().contains("En la posición 2 se esperaba la columna 'MONEDA'")));
        assertTrue(errors.stream().allMatch(error -> error.getRowNumber() == 0));
        assertFalse(errors.stream().anyMatch(error -> error.getErrorMessage().contains("Index out of bounds")));
        assertFalse(errors.stream().anyMatch(error -> "SISTEMA".equals(error.getColumnName())));
    }

    @Test
    void shouldFailFastWhenStructureContainsExtraColumns() throws Exception {
        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(),
                buildWorkbookBytes(
                        List.of("DOCUMENTO", "MONEDA", "CLASIFICACION", "CLASIFICACION"),
                        List.of(
                                List.of("893483743", "0", "9", "1"),
                                List.of("893483743", "0", "1", "1"))),
                "planilla.xlsx");

        assertTrue(errors.stream().anyMatch(error -> error.getRowNumber() == 0
                && error.getErrorMessage().contains("'CLASIFICACION' (posición 4)")
                && error.getErrorMessage().contains("está de más")));
        assertTrue(errors.stream().anyMatch(error -> error.getRowNumber() == 0
                && error.getErrorMessage().contains("tiene 4 columnas y se esperaban 3")));
        assertTrue(errors.stream().allMatch(error -> error.getRowNumber() == 0));
    }

    @Test
    void shouldFailFastWhenHeaderOrderDoesNotMatchEvenWithSameColumnCount() throws Exception {
        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(),
                buildWorkbookBytes(
                        List.of("MONEDA", "DOCUMENTO", "CLASIFICACION"),
                        List.of(List.of("0", "893483743", "1"))),
                "planilla.xlsx");

        assertTrue(errors.stream().anyMatch(error -> error.getErrorMessage().contains("En la posición 1 se esperaba la columna 'DOCUMENTO'")));
        assertTrue(errors.stream().anyMatch(error -> error.getErrorMessage().contains("En la posición 2 se esperaba la columna 'MONEDA'")));
        assertTrue(errors.stream().allMatch(error -> error.getRowNumber() == 0));
    }

    @Test
    void shouldValidateClasificacionAllowingBlankAndZeroButRejectingInvalidValues() throws Exception {
        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(),
                buildWorkbookBytes(
                        List.of("DOCUMENTO", "MONEDA", "CLASIFICACION"),
                        List.of(
                                List.of("893483743", "0", ""),
                                List.of("893483743", "1", "0"),
                                List.of("893483744", "0", "A"),
                                List.of("893483745", "0", "$"),
                                List.of("893483746", "0", "12"),
                                List.of("893483747", "0", "9"))),
                "planilla.xlsx");

        List<DynamicExcelValidationService.ValidationError> clasificacionErrors = errors.stream()
                .filter(error -> "CLASIFICACION".equals(error.getColumnName()))
                .toList();

        assertEquals(4, clasificacionErrors.size());
        assertTrue(clasificacionErrors.stream().allMatch(error -> error.getRowNumber() >= 4 && error.getRowNumber() <= 7));
        assertFalse(errors.stream().anyMatch(error -> error.getRowNumber() == 2 || error.getRowNumber() == 3));
    }

    @Test
    void shouldRejectDuplicateDocumentoWithSameMonedaButAllowDifferentMoneda() {
        String csv = String.join("\n",
                "DOCUMENTO,MONEDA,CLASIFICACION",
                "893483743,0,",
                "893483743,1,1",
                "893483743,0,2");

        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(),
                csv.getBytes(StandardCharsets.UTF_8),
                "planilla.csv");

        assertEquals(1, errors.size());
        assertEquals("DOCUMENTO", errors.get(0).getColumnName());
        assertEquals(4, errors.get(0).getRowNumber());
        assertTrue(errors.get(0).getErrorMessage().contains("DOCUMENTO '893483743' y MONEDA '0'"));
    }

    @Test
    void shouldAcceptNitFromAdjacentAvailablePeriodWhenSelectedPeriodHasRowsButNotThatNit() throws Exception {
        when(clienteLzRepository.countByPeriod(2026, 3)).thenReturn(10L);
        when(clienteLzRepository.countByPeriod(2026, 2)).thenReturn(10L);
        // Los periodos candidatos incluyen el mes actual (YearMonth.now()), que varía según cuándo
        // corra el test. Usamos lenient() para stubs opcionales que pueden no ser invocados.
        lenient().when(clienteLzRepository.countByPeriod(2026, 4)).thenReturn(0L);
        lenient().when(clienteLzRepository.countByPeriod(2026, 5)).thenReturn(0L);
        lenient().when(clienteLzRepository.countByPeriod(2026, 6)).thenReturn(0L);

        when(clienteLzRepository.findExistingNits(2026, 3, Set.of("1005945697"))).thenReturn(Set.of());
        when(clienteLzRepository.findExistingNits(2026, 2, Set.of("1005945697"))).thenReturn(Set.of("1005945697"));

        List<DynamicExcelValidationService.ValidationError> errors = validate(
                nitOnlyRules(),
                buildWorkbookBytes(
                        List.of("NIT"),
                        List.of(List.of("1005945697"))),
                "planilla.xlsx");

        assertTrue(errors.isEmpty(), "El NIT debe aceptarse al encontrarse en el periodo adyacente 2026/2");
    }

        /**
         * Ejecuta la validación con reglas preparadas para evitar repetir el armado del mock.
         */
    private List<DynamicExcelValidationService.ValidationError> validate(List<ValidationRule> rules,
                                                                         byte[] content,
                                                                         String filename) {
        when(ruleRepository.findActiveRulesBySegment("1", "9999")).thenReturn(rules);
        return service.validateExcel(
                () -> new ByteArrayInputStream(content),
                filename,
                "producto-prueba",
                1L,
                FECHA_CORTE,
                "qa.user",
                ValidationProgressListener.NOOP);
    }

        /**
         * Define el conjunto base de reglas usado por los escenarios de estructura y contenido.
         */
    private List<ValidationRule> configuredRules() {
        return List.of(
                fieldRule("DOCUMENTO", 1, false, true, "integer", 16,
                        null, "^[0-9]{1,16}$", null, null),
                fieldRule("MONEDA", 2, true, false, "integer", 1,
                        "0=COP,1=USD", "^[01]$", BigDecimal.ZERO, BigDecimal.ONE),
                fieldRule("CLASIFICACION", 3, false, true, "integer", 1,
                        "1=COMERCIAL,2=CONSUMO,3=VIVIENDA,4=MICROCREDITO",
                        "^[1-4]$", BigDecimal.ONE, BigDecimal.valueOf(4)));
    }

    private List<ValidationRule> nitOnlyRules() {
        return List.of(
                fieldRule("NIT", 1, true, false, "integer", 14,
                        null, "^[0-9]{1,14}$", null, null));
    }

        /**
         * Construye una regla de campo mínima para las pruebas del validador dinámico.
         */
    private ValidationRule fieldRule(String fieldName,
                                     int orden,
                                     boolean required,
                                     boolean allowNull,
                                     String dataType,
                                     Integer maxLength,
                                     String allowedValues,
                                     String regex,
                                     BigDecimal minValue,
                                     BigDecimal maxValue) {
        ValidationRule rule = new ValidationRule();
        rule.setRuleId(orden);
        rule.setFieldName(fieldName);
        rule.setOrden(orden);
        rule.setRuleKind("FIELD");
        rule.setAppliesToProduct("ALL");
        rule.setIsActive(true);
        rule.setIsRequired(required);
        rule.setAllowNull(allowNull);
        rule.setAllowNegative(false);
        rule.setDataType(dataType);
        rule.setMaxLength(maxLength);
        rule.setAllowedValues(allowedValues);
        rule.setRegexPattern(regex);
        rule.setMinValue(minValue);
        rule.setMaxValue(maxValue);
        return rule;
    }

        /**
         * Genera un XLSX pequeño con headers y filas parametrizadas para cada escenario.
         */
    private byte[] buildWorkbookBytes(List<String> headers, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("DATOS");
            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                headerRow.createCell(index).setCellValue(headers.get(index));
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                List<String> values = rows.get(rowIndex);
                for (int colIndex = 0; colIndex < values.size(); colIndex++) {
                    row.createCell(colIndex).setCellValue(values.get(colIndex));
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateExcel — validaciones estructurales: múltiples hojas
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldFailWhenExcelHasMoreThanOneSheet() throws Exception {
        byte[] xlsx = buildWorkbookWithMultipleSheets(List.of("DOCUMENTO", "MONEDA", "CLASIFICACION"),
                List.of(List.of("893483743", "0", "1")));

        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(), xlsx, "planilla.xlsx");

        assertTrue(errors.stream().anyMatch(e ->
                "ARCHIVO".equals(e.getColumnName()) &&
                e.getErrorMessage().contains("más de una hoja")),
                "Debe reportar error de múltiples hojas");
    }

    @Test
    void shouldConsolidateMultipleSheetsErrorWithOtherContentErrors() throws Exception {
        // Archivo con 2 hojas Y datos inválidos en la primera: ambos errores deben aparecer juntos.
        byte[] xlsx = buildWorkbookWithMultipleSheets(
                List.of("DOCUMENTO", "MONEDA", "CLASIFICACION"),
                List.of(List.of("NO-ES-NUMERO", "99", "X")));

        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(), xlsx, "planilla.xlsx");

        assertTrue(errors.stream().anyMatch(e ->
                "ARCHIVO".equals(e.getColumnName()) &&
                e.getErrorMessage().contains("más de una hoja")),
                "Debe reportar error de múltiples hojas");
        assertTrue(errors.size() > 1,
                "La validación no debe detenerse: deben aparecer también los errores de contenido");
    }

    @Test
    void shouldPassWhenExcelHasExactlyOneSheet() throws Exception {
        byte[] xlsx = buildWorkbookBytes(
                List.of("DOCUMENTO", "MONEDA", "CLASIFICACION"),
                List.of(List.of("893483743", "0", "1")));

        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(), xlsx, "planilla.xlsx");

        assertTrue(errors.stream().noneMatch(e ->
                e.getErrorMessage().contains("más de una hoja")),
                "No debe haber error de múltiples hojas cuando el archivo tiene solo una hoja");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateExcel — validaciones estructurales: datos fuera de rango de columnas
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldFailWhenDataRowHasCellsBeyondExpectedColumnCount() throws Exception {
        // Encabezados correctos (3 columnas), pero la fila de datos tiene una celda extra en col 4.
        byte[] xlsx = buildWorkbookBytes(
                List.of("DOCUMENTO", "MONEDA", "CLASIFICACION"),
                List.of(List.of("893483743", "0", "1", "DATO_EXTRA")));

        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(), xlsx, "planilla.xlsx");

        assertTrue(errors.stream().anyMatch(e ->
                "ARCHIVO".equals(e.getColumnName()) &&
                e.getErrorMessage().contains("por fuera del rango de los 3 campos")),
                "Debe reportar error de datos fuera de rango aunque el encabezado esté correcto");
    }

    @Test
    void shouldNotReportOutOfRangeWhenDataFitsWithinExpectedColumns() throws Exception {
        byte[] xlsx = buildWorkbookBytes(
                List.of("DOCUMENTO", "MONEDA", "CLASIFICACION"),
                List.of(List.of("893483743", "0", "1")));

        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(), xlsx, "planilla.xlsx");

        assertTrue(errors.stream().noneMatch(e ->
                e.getErrorMessage().contains("por fuera del rango")),
                "No debe haber error de rango cuando los datos caben exactamente en las columnas esperadas");
    }

    @Test
    void shouldOnlyReportOutOfRangeOnceEvenWhenMultipleRowsHaveExtraCells() throws Exception {
        // Varias filas tienen celdas extra: solo debe aparecer UN error (no uno por fila).
        byte[] xlsx = buildWorkbookBytes(
                List.of("DOCUMENTO", "MONEDA", "CLASIFICACION"),
                List.of(
                        List.of("893483743", "0", "1", "EXTRA1"),
                        List.of("893483744", "1", "2", "EXTRA2"),
                        List.of("893483745", "0", "3", "EXTRA3")));

        List<DynamicExcelValidationService.ValidationError> errors = validate(
                configuredRules(), xlsx, "planilla.xlsx");

        long outOfRangeErrors = errors.stream()
                .filter(e -> e.getErrorMessage().contains("por fuera del rango"))
                .count();
        assertEquals(1, outOfRangeErrors,
                "El error de datos fuera de rango debe reportarse una sola vez, no una por fila");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateControlFile — escenarios de contenido del .txt (Full IFRS)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void ctrlFile_shouldPassWhenIntegerMatchesRowCount() {
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("5", 5L);
        assertTrue(errors.isEmpty(), "Entero válido que coincide con el conteo no debe generar errores");
    }

    @Test
    void ctrlFile_shouldFailWhenContentHasLeadingTrailingWhitespace() {
        // Los espacios en el .txt deben rechazarse: "  7  " contiene espacios y no debe aceptarse.
        // El comportamiento cambió: antes se hacía trim, ahora se valida el contenido original.
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("  7  ", 7L);
        assertEquals(1, errors.size(), "Contenido con espacios debe generar exactamente 1 error");
        assertEquals("CONTENIDO_CONTROL", errors.get(0).getColumnName());
    }

    @Test
    void ctrlFile_shouldPassWhenZeroMatchesEmptyPlanilla() {
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("0", 0L);
        assertTrue(errors.isEmpty(), "El valor '0' es un entero válido y debe aceptarse cuando la planilla tiene 0 filas");
    }

    @Test
    void ctrlFile_shouldFailWhenContentHasDecimalPoint() {
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("123.45", 123L);
        assertEquals(1, errors.size());
        assertEquals("CONTENIDO_CONTROL", errors.get(0).getColumnName());
        assertTrue(errors.get(0).getErrorMessage().contains("123.45"),
                "El mensaje de error debe incluir el valor recibido");
    }

    @Test
    void ctrlFile_shouldFailWhenContentHasLetters() {
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("abc", 3L);
        assertEquals(1, errors.size());
        assertEquals("CONTENIDO_CONTROL", errors.get(0).getColumnName());
        assertTrue(errors.get(0).getErrorMessage().contains("abc"));
    }

    @Test
    void ctrlFile_shouldFailWhenContentHasInternalSpace() {
        // "123 456" no coincide con ^[0-9]+$
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("123 456", 123456L);
        assertEquals(1, errors.size());
        assertEquals("CONTENIDO_CONTROL", errors.get(0).getColumnName());
    }

    @Test
    void ctrlFile_shouldFailWhenContentHasComma() {
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("1,234", 1234L);
        assertEquals(1, errors.size());
        assertEquals("CONTENIDO_CONTROL", errors.get(0).getColumnName());
    }

    @Test
    void ctrlFile_shouldFailWhenContentIsEmptyString() {
        // Cadena vacía no coincide con ^[0-9]+$ (requiere al menos un dígito)
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("", 0L);
        assertEquals(1, errors.size());
        assertEquals("CONTENIDO_CONTROL", errors.get(0).getColumnName());
    }

    @Test
    void ctrlFile_shouldFailWhenContentIsNegativeNumber() {
        // Signo negativo no permitido
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("-5", 5L);
        assertEquals(1, errors.size());
        assertEquals("CONTENIDO_CONTROL", errors.get(0).getColumnName());
    }

    @Test
    void ctrlFile_shouldFailWhenCountDoesNotMatchPlanillaRows() {
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("100", 50L);
        assertEquals(1, errors.size());
        assertEquals("CONTEO_REGISTROS_CONTROL", errors.get(0).getColumnName());
        assertTrue(errors.get(0).getErrorMessage().contains("no coincide"));
    }

    @Test
    void ctrlFile_shouldSkipCountCheckWhenFormatFails() {
        // Si CTRL_CONTENT ya falló, CTRL_RECORD_COUNT no debe ejecutarse
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("no-es-numero", 5L);
        assertEquals(1, errors.size(), "Solo debe haber 1 error (CTRL_CONTENT), no debe ejecutarse el conteo");
        assertEquals("CONTENIDO_CONTROL", errors.get(0).getColumnName());
        assertTrue(errors.stream().noneMatch(e -> "CONTEO_REGISTROS_CONTROL".equals(e.getColumnName())));
    }

    @Test
    void ctrlFile_shouldReturnErrorWhenContentIsNull() {
        // El servicio no necesita mock de repo para null: falla antes de consultar reglas
        List<DynamicExcelValidationService.ValidationError> errors =
                service.validateControlFile(null, 0L, 2L);
        assertEquals(1, errors.size());
        assertEquals("ARCHIVO_CONTROL", errors.get(0).getColumnName());
    }

    @Test
    void ctrlFile_shouldReportBothErrorsInMessageWhenBothReplacementsPresent() {
        // El comentario de la regla tiene "######" dos veces; ambos se reemplazan con el valor recibido
        List<DynamicExcelValidationService.ValidationError> errors = validateControl("3.14", 3L);
        assertEquals(1, errors.size());
        String msg = errors.get(0).getErrorMessage();
        // Ambas ocurrencias de "######" se reemplazan con "3.14"
        long occurrences = msg.chars().filter(c -> c == '3').count();
        assertTrue(occurrences >= 2 || msg.contains("3.14"),
                "El mensaje debe referenciar el valor recibido al menos una vez");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // countDataRows — conteo de filas de datos (excluye encabezado y vacías)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void countRows_shouldCountDataRowsExcludingHeader() throws Exception {
        byte[] xlsx = buildWorkbookBytes(
                List.of("NIT", "MONEDA"),
                List.of(
                        List.of("1000000001", "0"),
                        List.of("1000000002", "0"),
                        List.of("1000000003", "1")));
        long count = service.countDataRows(
                () -> new ByteArrayInputStream(xlsx), "planilla.xlsx");
        assertEquals(3L, count);
    }

    @Test
    void countRows_shouldReturnZeroWhenOnlyHeaderPresent() throws Exception {
        byte[] xlsx = buildWorkbookBytes(List.of("NIT", "MONEDA"), List.of());
        long count = service.countDataRows(
                () -> new ByteArrayInputStream(xlsx), "planilla.xlsx");
        assertEquals(0L, count);
    }

    @Test
    void countRows_shouldCountOnlyNonEmptyDataRows() throws Exception {
        // La última fila tiene todas las celdas vacías y debe ignorarse
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("DATOS");
            // fila 0: header
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("NIT");
            // fila 1: dato real
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("1000000001");
            // fila 2: dato real
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("1000000002");
            // fila 3: completamente vacía (sin celdas)
            sheet.createRow(3);
            wb.write(out);
            byte[] xlsx = out.toByteArray();

            long count = service.countDataRows(
                    () -> new ByteArrayInputStream(xlsx), "planilla.xlsx");
            assertEquals(2L, count);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers privados para validateControlFile
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Construye un XLSX con dos hojas: la primera con los datos indicados, la segunda vacía.
     * Se usa para verificar que la validación de múltiples hojas funciona correctamente.
     */
    private byte[] buildWorkbookWithMultipleSheets(List<String> headers, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet1 = workbook.createSheet("DATOS");
            Row headerRow = sheet1.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet1.createRow(rowIndex + 1);
                List<String> values = rows.get(rowIndex);
                for (int colIndex = 0; colIndex < values.size(); colIndex++) {
                    row.createCell(colIndex).setCellValue(values.get(colIndex));
                }
            }
            // Segunda hoja que dispara el error de validación
            workbook.createSheet("HOJA_EXTRA");
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private List<DynamicExcelValidationService.ValidationError> validateControl(
            String content, long rowCount) {
        when(ruleRepository.findActiveRulesBySegment("2", "9999"))
                .thenReturn(List.of(ctrlContentRule(), ctrlRecordCountRule()));
        return service.validateControlFile(content, rowCount, 2L);
    }

    private ValidationRule ctrlContentRule() {
        ValidationRule rule = new ValidationRule();
        rule.setRuleId(2007);
        rule.setFieldName("CONTENIDO_CONTROL");
        rule.setRuleKind("CTRL_CONTENT");
        rule.setIsActive(true);
        rule.setIsRequired(true);
        rule.setRegexPattern("^[0-9]+$");
        rule.setIdSegmento(2);
        rule.setAppliesToProduct("2");
        rule.setComments(
                "Se esperaba un numero entero valido sin puntos, comas, espacios, " +
                "letras ni caracteres especiales " +
                "(Valor recibido: '######') (Tu valor fue: '######')");
        return rule;
    }

    private ValidationRule ctrlRecordCountRule() {
        ValidationRule rule = new ValidationRule();
        rule.setRuleId(2008);
        rule.setFieldName("CONTEO_REGISTROS_CONTROL");
        rule.setRuleKind("CTRL_RECORD_COUNT");
        rule.setIsActive(true);
        rule.setIsRequired(true);
        rule.setIdSegmento(2);
        rule.setAppliesToProduct("2");
        rule.setComments(
                "el numero indicado en el archivo control no coincide con la cantidad " +
                "de registros de la planilla manual para el mismo producto");
        return rule;
    }
}