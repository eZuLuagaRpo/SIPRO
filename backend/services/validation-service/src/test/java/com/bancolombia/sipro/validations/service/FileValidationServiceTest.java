package com.bancolombia.sipro.validations.service;

import com.bancolombia.sipro.validations.domain.service.DynamicExcelValidationService;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Verifica que el reporte TXT de errores sea legible y útil para el usuario final.
 */
class FileValidationServiceTest {

    @Test
    void shouldGenerateReadableTxtErrorReport() throws Exception {
        FileValidationService service = new FileValidationService(null, null, null, mock(ParametroUnicoService.class));

        List<DynamicExcelValidationService.ValidationError> errors = List.of(
            new DynamicExcelValidationService.ValidationError(0, "ARCHIVO", "",
                "El archivo tiene 25 columnas y se esperaban 24."),
            new DynamicExcelValidationService.ValidationError(2, "CLASIFICACION", "9",
                "Debe contener un valor entre 1 y 4."),
            new DynamicExcelValidationService.ValidationError(0, "RESUMEN", "",
                "Se encontraron 3 errores adicionales del mismo tipo.")
        );

        String report = new String(service.generarArchivoErroresDetallado(errors), StandardCharsets.UTF_8);

        assertTrue(report.contains("REPORTE DE ERRORES DE VALIDACION SIPRO"));
        assertTrue(report.contains("OBSERVACIONES DEL ARCHIVO"));
        assertTrue(report.contains("1. El archivo tiene 25 columnas y se esperaban 24."));
        assertTrue(report.contains("DETALLE POR FILAS"));
        assertTrue(report.contains("1. Fila 2 | Columna CLASIFICACION"));
        assertTrue(report.contains("Valor recibido: 9"));
        assertTrue(report.contains("Motivo: Debe contener un valor entre 1 y 4."));
        assertTrue(report.contains("RESUMEN ADICIONAL"));
    }
}