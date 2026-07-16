package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.ConsolidacionResumenResponse;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleArchivoValidacion;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidadoRegistroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * Verifica que el resumen consolidado diferencie observaciones internas de diferencias reales.
 */
class ConsolidacionResumenServiceTest {

    @Mock
    private SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;

    @Mock
    private SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository;

        @Mock
        private SiproDetalleCargaPlanillasRepository cargaPlanillasRepository;

        @Mock
        private SiproDetalleArchivoValidacionRepository archivoValidacionRepository;

    @Mock
    private CreffosComparisonService creffosComparisonService;

    private ConsolidacionResumenService service;

    @BeforeEach
    void setUp() {
        service = new ConsolidacionResumenService(
                consolidacionRepository,
                consolidadoRegistroRepository,
                cargaPlanillasRepository,
                archivoValidacionRepository,
                creffosComparisonService
        );
    }

    @Test
    void noDeberiaGenerarAlertaGlobalCuandoSoloHayObservacionesInternas() {
        LocalDate periodo = LocalDate.of(2026, 3, 31);

        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(10L);
        cabecera.setPeriodoValoracion(periodo);
        cabecera.setEstadoConsolidacion("COMPLETADO");
        cabecera.setCantidadArchivosConsolidados(1);
        cabecera.setCantidadRegistrosConsolidados(1);
        cabecera.setModificadoEn(OffsetDateTime.now());

        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setIdConsolidacion(10L);
        registro.setIdProductoOrigen(7L);
        registro.setProductoOrigen("Leasing");
        registro.setVlriniobl(new BigDecimal("1040060.00"));
        registro.setTipoId(" ");
        registro.setClasificacion(null);

        ConsolidacionResumenResponse.CreffosArchivoResumen archivo =
                new ConsolidacionResumenResponse.CreffosArchivoResumen(
                        true,
                        "CREFFSOS.xlsx",
                        "XLSX",
                        "CONSISTENTE",
                        "STORAGE",
                        "consolidados/2026-03-31/CREFFSOS.xlsx",
                        2,
                        2,
                        1L,
                        new BigDecimal("1040060.00"),
                        "El archivo CREFFSOS coincide con los totales consolidados del periodo."
                );

        when(consolidacionRepository.findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc("COMPLETADO"))
                .thenReturn(List.of(cabecera));
        when(consolidadoRegistroRepository.findByIdConsolidacionOrderByIdConsolidadoRegistroAsc(10L))
                .thenReturn(List.of(registro));
        when(creffosComparisonService.comparar(periodo, 1L, new BigDecimal("1040060.00")))
                .thenReturn(new CreffosComparisonService.ComparisonSnapshot(archivo, List.of(), false));

        ConsolidacionResumenResponse response = service.obtenerResumen(2026, 3);

        assertTrue(response.isHayDatos());
        assertEquals(1L, response.getRegistrosObservados());
        assertEquals(1, response.getProductosObservados());
        assertFalse(response.isTieneDiferenciasConciliacion());
        assertNull(response.getAlerta());
    }

    @Test
    void deberiaIncluirProductosSoloFullIfrsConMetricasSegmentoUnoEnCero() {
        LocalDate periodo = LocalDate.of(2026, 4, 30);

        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(11L);
        cabecera.setPeriodoValoracion(periodo);
        cabecera.setEstadoConsolidacion("COMPLETADO");
        cabecera.setCantidadArchivosConsolidados(2);
        cabecera.setCantidadRegistrosConsolidados(1);
        cabecera.setModificadoEn(OffsetDateTime.now());

        SiproDetalleConsolidadoRegistro registroSegmentoUno = new SiproDetalleConsolidadoRegistro();
        registroSegmentoUno.setIdConsolidacion(11L);
        registroSegmentoUno.setIdProductoOrigen(7L);
        registroSegmentoUno.setProductoOrigen("Leasing");
        registroSegmentoUno.setVlriniobl(new BigDecimal("1000.00"));
        registroSegmentoUno.setTipoId("CC");
        registroSegmentoUno.setClasificacion((short) 1);

        SiproDetalleCargaPlanillas planillaFullIfrs = new SiproDetalleCargaPlanillas();
        planillaFullIfrs.setId(101L);
        planillaFullIfrs.setIdProducto(99L);
        planillaFullIfrs.setProducto("Tarjeta de credito");

        SiproDetalleArchivoValidacion validacionFullIfrs = new SiproDetalleArchivoValidacion();
        validacionFullIfrs.setIdCargaPlanilla(101L);
        validacionFullIfrs.setNumeroFilasDatos(45);
        validacionFullIfrs.setCantidadRegistrosControl(45);

        ConsolidacionResumenResponse.CreffosArchivoResumen archivo =
                new ConsolidacionResumenResponse.CreffosArchivoResumen(
                        true,
                        "CREFFSOS.xlsx",
                        "XLSX",
                        "CONSISTENTE",
                        "STORAGE",
                        "consolidados/2026-04-30/CREFFSOS.xlsx",
                        2,
                        2,
                        1L,
                        new BigDecimal("1000.00"),
                        "El archivo CREFFSOS coincide con los totales consolidados del periodo."
                );

        when(consolidacionRepository.findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc("COMPLETADO"))
                .thenReturn(List.of(cabecera));
        when(consolidadoRegistroRepository.findByIdConsolidacionOrderByIdConsolidadoRegistroAsc(11L))
                .thenReturn(List.of(registroSegmentoUno));
        when(cargaPlanillasRepository.findPlanillasAprobadasByFechaCorteAndSegmentoId(periodo, 2L))
                .thenReturn(List.of(planillaFullIfrs));
        when(archivoValidacionRepository.findByIdCargaPlanillaIn(List.of(101L)))
                .thenReturn(List.of(validacionFullIfrs));
        when(creffosComparisonService.comparar(periodo, 1L, new BigDecimal("1000.00")))
                .thenReturn(new CreffosComparisonService.ComparisonSnapshot(archivo, List.of(), false));

        ConsolidacionResumenResponse response = service.obtenerResumen(2026, 4);

        assertTrue(response.isHayDatos());
        assertEquals(2, response.getProductos().size());

        ConsolidacionResumenResponse.ProductoResumen productoSoloFullIfrs = response.getProductos().stream()
                .filter(producto -> "Tarjeta de credito".equals(producto.getNombreProducto()))
                .findFirst()
                .orElse(null);

        assertNotNull(productoSoloFullIfrs);
        assertEquals(0L, productoSoloFullIfrs.getCantidadRegistros());
        assertEquals(BigDecimal.ZERO, productoSoloFullIfrs.getTotalVlrIniObl());
        assertEquals(45L, productoSoloFullIfrs.getCantidadRegistrosFullIfrs());
    }

    @Test
    void deberiaObtenerRegistrosConsolidadosPorPeriodoParaExportacion() {
        LocalDate periodo = LocalDate.of(2026, 5, 31);

        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(12L);
        cabecera.setPeriodoValoracion(periodo);
        cabecera.setEstadoConsolidacion("COMPLETADO");

        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setIdConsolidacion(12L);
        registro.setNit(900000001L);
        registro.setDocumento(123456L);
        registro.setProductoOrigen("Crédito");
        registro.setVlriniobl(new BigDecimal("500000.00"));

        when(consolidacionRepository.findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc("COMPLETADO"))
                .thenReturn(List.of(cabecera));
        when(consolidadoRegistroRepository.findByIdConsolidacionOrderByIdConsolidadoRegistroAsc(12L))
                .thenReturn(List.of(registro));

        List<SiproDetalleConsolidadoRegistro> registros = service.obtenerRegistrosConsolidadosPorPeriodo(2026, 5);

        assertEquals(1, registros.size());
        assertEquals(900000001L, registros.get(0).getNit());
        assertEquals(123456L, registros.get(0).getDocumento());
        assertEquals(new BigDecimal("500000.00"), registros.get(0).getVlriniobl());
    }
}