package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidadoRegistroRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsolidacionConciliacionReportServiceTest {

        private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    @Mock
    private SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;

    @Mock
    private SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository;

    @Mock
    private CreffosComparisonService creffosComparisonService;

    private ConsolidacionConciliacionReportService service;

    @BeforeEach
    void setUp() {
        service = new ConsolidacionConciliacionReportService(
                consolidacionRepository,
                consolidadoRegistroRepository,
                creffosComparisonService
        );
    }

    @Test
    void deberiaGenerarExcelConResumenYDetalleProtegidosCuandoHayDiferenciaDeRegistros() throws Exception {
        LocalDate periodo = LocalDate.of(2026, 3, 31);

        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(10L);
        cabecera.setPeriodoValoracion(periodo);
        cabecera.setEstadoConsolidacion("COMPLETADO");

        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setIdConsolidacion(10L);
        registro.setIdSegmento(1);
        registro.setIdProductoOrigen(1L);
        registro.setProductoOrigen("Acuerdos conjuntos");
        registro.setNit(9001L);
        registro.setDocumento(7001001L);
        registro.setVlriniobl(new BigDecimal("300.00"));
        registro.setDescripcion("Faltante en CREFFSOS");

        when(consolidacionRepository.findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc("COMPLETADO"))
                .thenReturn(List.of(cabecera));
        when(consolidacionRepository.findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc("COMPLETADO_CON_ADVERTENCIAS"))
                .thenReturn(List.of());
        when(consolidadoRegistroRepository.findByIdConsolidacionOrderByIdConsolidadoRegistroAsc(10L))
                .thenReturn(List.of(registro));
        when(creffosComparisonService.cargarDetalleArchivo(periodo)).thenReturn(
                new CreffosComparisonService.DetailedCreffosFile(
                        true,
                        "CREFFSOS.xlsx",
                        "XLSX",
                        "OK",
                        "STORAGE",
                        "consolidados/2026-03-31/CREFFSOS.xlsx",
                        61,
                        61,
                        0L,
                        BigDecimal.ZERO,
                        "Detalle CREFFSOS cargado correctamente para conciliación.",
                        List.of(
                                new CreffosComparisonService.CreffosRow(java.util.Map.of(
                                        "DOCUMENTO", "999999",
                                        "VLRINIOBL", "100.00"
                                ))
                        )
                )
        );

        ConsolidacionConciliacionReportService.GeneratedConciliacionReport report = service.generar(2026, 3);

        assertEquals("conciliacion_planillas_manuales_20260331.xlsx", report.fileName());
        assertTrue(report.content().length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report.content()))) {
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Detalle"));
            assertTrue(workbook.getSheet("Resumen").getProtect());
            assertTrue(workbook.getSheet("Detalle").getProtect());
                        assertEquals("Producto", readCell(workbook.getSheet("Resumen"), 3, 0));
                        assertTrue(findRowByFirstCell(workbook.getSheet("Resumen"), "TOTAL PLANILLAS") >= 0);
                        assertEquals("NIT", readCell(workbook.getSheet("Detalle"), 1, 0));
                        assertEquals("7001001", readCell(workbook.getSheet("Detalle"), 2, 2));
                        assertEquals("Faltante en CREFFSOS", readCell(workbook.getSheet("Detalle"), 2, 28));
        }
    }

    @Test
    void deberiaCrearDetalleVacioCuandoNoHayDiferencias() throws Exception {
        LocalDate periodo = LocalDate.of(2026, 4, 30);

        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(11L);
        cabecera.setPeriodoValoracion(periodo);
        cabecera.setEstadoConsolidacion("COMPLETADO_CON_ADVERTENCIAS");

        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setIdConsolidacion(11L);
        registro.setIdSegmento(1);
        registro.setProductoOrigen("Banco corrientes");
        registro.setDocumento(8002002L);
        registro.setVlriniobl(new BigDecimal("450.00"));

        when(consolidacionRepository.findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc("COMPLETADO"))
                .thenReturn(List.of());
        when(consolidacionRepository.findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc("COMPLETADO_CON_ADVERTENCIAS"))
                .thenReturn(List.of(cabecera));
        when(consolidadoRegistroRepository.findByIdConsolidacionOrderByIdConsolidadoRegistroAsc(11L))
                .thenReturn(List.of(registro));
        when(creffosComparisonService.cargarDetalleArchivo(periodo)).thenReturn(
                new CreffosComparisonService.DetailedCreffosFile(
                        true,
                        "CREFFSOS.xlsx",
                        "XLSX",
                        "OK",
                        "STORAGE",
                        "consolidados/2026-04-30/CREFFSOS.xlsx",
                        61,
                        61,
                        1L,
                        new BigDecimal("450.00"),
                        "Detalle CREFFSOS cargado correctamente para conciliación.",
                        List.of(
                                new CreffosComparisonService.CreffosRow(java.util.Map.of(
                                        "DOCUMENTO", "8002002",
                                        "VLRINIOBL", "450.00"
                                ))
                        )
                )
        );

        ConsolidacionConciliacionReportService.GeneratedConciliacionReport report = service.generar(2026, 4);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report.content()))) {
            assertNotNull(workbook.getSheet("Detalle"));
                        assertEquals("Sin diferencias encontradas", readCell(workbook.getSheet("Detalle"), 2, 0));
        }
    }

        private int findRowByFirstCell(Sheet sheet, String expected) {
                for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                        if (expected.equals(readCell(sheet, rowIndex, 0))) {
                                return rowIndex;
                        }
                }
                return -1;
        }

        private String readCell(Sheet sheet, int rowIndex, int cellIndex) {
                if (sheet.getRow(rowIndex) == null || sheet.getRow(rowIndex).getCell(cellIndex) == null) {
                        return "";
                }
                return DATA_FORMATTER.formatCellValue(sheet.getRow(rowIndex).getCell(cellIndex));
        }
}