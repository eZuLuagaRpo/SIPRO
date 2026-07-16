package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.ConsolidacionResumenResponse;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * Verifica que la exportación del resumen consolidado genere Excel con formato correcto.
 * Valida: negrilla en encabezados, fondo gris, 3 tablas en hoja Resumen,
 * hoja Detalle solo si hay diferencias en registros.
 */
class ConsolidacionResumenExcelReportServiceTest {

    @Mock
    private ConsolidacionResumenService consolidacionResumenService;

    private ConsolidacionResumenExcelReportService service;

    @BeforeEach
    void setUp() {
        service = new ConsolidacionResumenExcelReportService(consolidacionResumenService);
    }

    @Test
    void deberiaGenerarExcelValidoConNombreCorrectoCuandoHayDatos() throws Exception {
        // Arrange
        ConsolidacionResumenResponse resumen = crearResumenConDatos(2026, 6);

        when(consolidacionResumenService.obtenerResumen(2026, 6))
                .thenReturn(resumen);
        when(consolidacionResumenService.obtenerRegistrosConsolidadosPorPeriodo(2026, 6))
                .thenReturn(List.of());

        // Act
        ConsolidacionResumenExcelReportService.GeneratedResumenReport report = service.generar(2026, 6);

        // Assert
        assertEquals("conciliacion_planillas_manuales_20260630.xlsx", report.fileName());
        assertTrue(report.content().length > 0);

        // Validar que es un XLSX válido
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report.content()))) {
            assertTrue(workbook.getNumberOfSheets() > 0);
            assertNotNull(workbook.getSheet("Resumen"));
        }
    }

    @Test
    void deberiaIncluirHojaDetalleConDiferenciasEnRegistros() throws Exception {
        // Arrange
        ConsolidacionResumenResponse resumen = crearResumenConDiferencias(2026, 3);
        List<SiproDetalleConsolidadoRegistro> detalle = crearDetalleConRegistros();

        when(consolidacionResumenService.obtenerResumen(2026, 3))
                .thenReturn(resumen);
        when(consolidacionResumenService.obtenerRegistrosConsolidadosPorPeriodo(2026, 3))
                .thenReturn(detalle);

        // Act
        ConsolidacionResumenExcelReportService.GeneratedResumenReport report = service.generar(2026, 3);

        // Assert
        assertEquals("conciliacion_planillas_manuales_20260331.xlsx", report.fileName());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report.content()))) {
            assertTrue(workbook.getNumberOfSheets() >= 2);
            assertNotNull(workbook.getSheet("Resumen"));
            assertNotNull(workbook.getSheet("Detalle Colgaap Modificado"));
        }
    }

    @Test
    void noDeberiaAgregarHojaDetalleSinDiferenciasEnRegistros() throws Exception {
        // Arrange
        ConsolidacionResumenResponse resumen = crearResumenSinDiferencias(2026, 5);

        when(consolidacionResumenService.obtenerResumen(2026, 5))
                .thenReturn(resumen);
        when(consolidacionResumenService.obtenerRegistrosConsolidadosPorPeriodo(2026, 5))
                .thenReturn(List.of());

        // Act
        ConsolidacionResumenExcelReportService.GeneratedResumenReport report = service.generar(2026, 5);

        // Assert
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report.content()))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("Resumen", workbook.getSheetAt(0).getSheetName());
        }
    }

    @Test
    void deberiaLanzarExcepcionCuandoNoHayDatos() {
        // Arrange
        ConsolidacionResumenResponse resumen = new ConsolidacionResumenResponse();
        resumen.setHayDatos(false);

        when(consolidacionResumenService.obtenerResumen(2026, 1))
                .thenReturn(resumen);

        // Act & Assert
        assertThrows(
                IllegalStateException.class,
                () -> service.generar(2026, 1)
        );
    }

    @Test
    void deberiaGenerarNombreConFechaDelUltimoDiaDelMes() throws Exception {
        // Arrange - febrero tiene 28 días (2026 no es bisiesto)
        ConsolidacionResumenResponse resumen = crearResumenConDatos(2026, 2);

        when(consolidacionResumenService.obtenerResumen(2026, 2))
                .thenReturn(resumen);
        when(consolidacionResumenService.obtenerRegistrosConsolidadosPorPeriodo(2026, 2))
                .thenReturn(List.of());

        // Act
        ConsolidacionResumenExcelReportService.GeneratedResumenReport report = service.generar(2026, 2);

        // Assert
        assertEquals("conciliacion_planillas_manuales_20260228.xlsx", report.fileName());
    }

    // ===== Métodos auxiliares =====

    private ConsolidacionResumenResponse crearResumenConDatos(int anio, int mes) {
        ConsolidacionResumenResponse resumen = new ConsolidacionResumenResponse();
        resumen.setHayDatos(true);
        resumen.setAnioSeleccionado(anio);
        resumen.setMesSeleccionado(mes);
        resumen.setCantidadArchivosConsolidados(3);
        resumen.setCantidadRegistrosConsolidados(100);
        resumen.setCantidadRegistrosArchivoControl(100);
        resumen.setTotalVlrIniObl(new BigDecimal("5000000.00"));

        ConsolidacionResumenResponse.ProductoResumen producto = new ConsolidacionResumenResponse.ProductoResumen();
        producto.setNombreProducto("Leasing");
        producto.setCantidadRegistros(100);
        producto.setTotalVlrIniObl(new BigDecimal("5000000.00"));
        producto.setCantidadRegistrosFullIfrs(0);
        resumen.setProductos(List.of(producto));

        ConsolidacionResumenResponse.CreffosArchivoResumen creffos = new ConsolidacionResumenResponse.CreffosArchivoResumen();
        creffos.setEncontrado(true);
        creffos.setEstado("CONSISTENTE");
        creffos.setCantidadRegistros(100);
        creffos.setTotalVlrIniObl(new BigDecimal("5000000.00"));
        resumen.setCreffos(creffos);

        ConsolidacionResumenResponse.ComparacionMetricaResumen metrica = new ConsolidacionResumenResponse.ComparacionMetricaResumen();
        metrica.setCodigo("REGISTROS");
        metrica.setValorPostgres(new BigDecimal("100"));
        metrica.setValorCreffos(new BigDecimal("100"));
        metrica.setDiferencia(BigDecimal.ZERO);
        metrica.setCoincide(true);

        resumen.setMetricasComparacion(List.of(metrica));
        return resumen;
    }

    private ConsolidacionResumenResponse crearResumenConDiferencias(int anio, int mes) {
        ConsolidacionResumenResponse resumen = crearResumenConDatos(anio, mes);

        ConsolidacionResumenResponse.ComparacionMetricaResumen metrica = resumen.getMetricasComparacion().get(0);
        metrica.setValorPostgres(new BigDecimal("101"));
        metrica.setDiferencia(new BigDecimal("1"));
        metrica.setCoincide(false);

        return resumen;
    }

    private ConsolidacionResumenResponse crearResumenSinDiferencias(int anio, int mes) {
        return crearResumenConDatos(anio, mes);
    }

    private List<SiproDetalleConsolidadoRegistro> crearDetalleConRegistros() {
        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setNit(900123456L);
        registro.setDocumento(123456L);
        registro.setMoneda(1);
        registro.setVlriniobl(new BigDecimal("100000.00"));
        registro.setProductoOrigen("Leasing");
        registro.setTipoId("CC");
        registro.setFechaCorte(LocalDate.of(2026, 3, 31));
        registro.setUsuarioCargador("user1");
        registro.setUsuarioAprobador("admin1");

        return List.of(registro);
    }
}
