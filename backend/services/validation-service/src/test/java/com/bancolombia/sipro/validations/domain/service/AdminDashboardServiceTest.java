package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.infrastructure.config.AdminPanelProperties;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionArchivo;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionArchivoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private SiproDetalleCargaPlanillasRepository planillaRepository;

    @Mock
    private SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;

    @Mock
    private SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private ConsolidacionManualAsyncService consolidacionManualAsyncService;

    @Mock
    private ConsolidacionPlanillasService consolidacionPlanillasService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ObjectProvider<AdminDashboardDevSeedService> adminDashboardDevSeedServiceProvider;

    @Mock
    private AdminDashboardDevSeedService adminDashboardDevSeedService;

    @Mock
    private ParametroUnicoService parametroUnicoService;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
                planillaRepository,
                consolidacionRepository,
                consolidacionArchivoRepository,
            productoRepository,
                consolidacionManualAsyncService,
                consolidacionPlanillasService,
                fileStorageService,
                new AdminPanelProperties(),
                adminDashboardDevSeedServiceProvider,
                parametroUnicoService);
    }

    @Test
    void deberiaPurgarDummyYNoComplementarDashboardCuandoElFlagEstaApagado() {
        when(parametroUnicoService.getString("APP_ADMIN_DASHBOARD_DUMMY_DATA_ENABLED", "false"))
                .thenReturn("false");
        when(adminDashboardDevSeedServiceProvider.getIfAvailable()).thenReturn(adminDashboardDevSeedService);
        when(planillaRepository.findDistinctFechasCorteInformacionAprobadasBySegmentoId(1L))
                .thenReturn(List.of());
        when(consolidacionRepository.findTopByOrderByCreadoEnDesc()).thenReturn(Optional.empty());
        when(consolidacionRepository.findTop20ByOrderByCreadoEnDesc()).thenReturn(List.of());

        service.obtenerDashboard(null);

        verify(adminDashboardDevSeedService).purgeDemoData();
        verify(adminDashboardDevSeedService, never()).ensureDemoData();
        verify(adminDashboardDevSeedService, never()).completarPeriodosDisponibles(anyList());
    }

        @Test
        void deberiaMostrarPendientesNoBloqueantesYRutaCreffosDeUltimaConsolidacionExitosa() {
        LocalDate periodo = LocalDate.of(2026, 5, 31);

        SiproDetalleCargaPlanillas planillaDisponible = new SiproDetalleCargaPlanillas();
        planillaDisponible.setId(200L);
        planillaDisponible.setNombreArchivoFuente("DISPONIBLE.xlsx");
        planillaDisponible.setProducto("Producto disponible");
        planillaDisponible.setRutaArchivoAlmacenamiento("aprobados/2026-05-31/DISPONIBLE.xlsx");
        planillaDisponible.setEstadoPlanilla("APROBADO");
        planillaDisponible.setPesoArchivoFuente(200L);

        SiproDetalleCargaPlanillas planillaPendiente = new SiproDetalleCargaPlanillas();
        planillaPendiente.setId(250L);
        planillaPendiente.setNombreArchivoFuente("PENDIENTE.xlsx");
        planillaPendiente.setProducto("Producto pendiente");
        planillaPendiente.setRutaArchivoAlmacenamiento("pendientes/2026-05-31/PENDIENTE.xlsx");
        planillaPendiente.setEstadoPlanilla("PENDIENTE_APROBACION");
        planillaPendiente.setPesoArchivoFuente(250L);

        SiproDetalleCargaPlanillas planillaYaConsolidada = new SiproDetalleCargaPlanillas();
        planillaYaConsolidada.setId(100L);
        planillaYaConsolidada.setNombreArchivoFuente("CONSOLIDADA.xlsx");
        planillaYaConsolidada.setProducto("Producto consolidado");
        planillaYaConsolidada.setRutaArchivoAlmacenamiento("aprobados/2026-05-31/CONSOLIDADA.xlsx");
        planillaYaConsolidada.setEstadoPlanilla("APROBADO");
        planillaYaConsolidada.setPesoArchivoFuente(100L);

        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(77L);
        cabecera.setPeriodoValoracion(periodo);
        cabecera.setEstadoConsolidacion("COMPLETADO");
        cabecera.setFechaHoraFin(OffsetDateTime.parse("2026-06-13T01:10:00Z"));
        cabecera.setCantidadArchivosConsolidados(1);
        cabecera.setCantidadRegistrosConsolidados(120);

        SiproDetalleConsolidacionArchivo archivoConsolidado = new SiproDetalleConsolidacionArchivo();
        archivoConsolidado.setIdConsolidacionArchivo(1L);
        archivoConsolidado.setIdConsolidacion(77L);
        archivoConsolidado.setIdCargaPlanilla(100L);
        archivoConsolidado.setNombreArchivo("CONSOLIDADA.xlsx");
        archivoConsolidado.setProductoOrigen("Producto consolidado");
        archivoConsolidado.setCantidadRegistrosArchivo(120);
        archivoConsolidado.setRutaArchivo("consolidados/2026-05-31/CONSOLIDADA.xlsx");

        SiproDetalleCargaPlanillas planillaDescartada = new SiproDetalleCargaPlanillas();
        planillaDescartada.setId(300L);
        planillaDescartada.setNombreArchivoFuente("DESCARTADA.xlsx");
        planillaDescartada.setProducto("Producto fuera de segmento");
        planillaDescartada.setRutaArchivoAlmacenamiento("aprobados/2026-05-31/DESCARTADA.xlsx");
        planillaDescartada.setEstadoPlanilla("APROBADO");
        planillaDescartada.setPesoArchivoFuente(300L);

        ConsolidacionPlanillasService.ConsolidacionRangoOperativo rango =
            new ConsolidacionPlanillasService.ConsolidacionRangoOperativo(
                OffsetDateTime.parse("2026-06-12T23:59:00Z"),
                OffsetDateTime.parse("2026-06-13T00:59:00Z"),
                OffsetDateTime.parse("2026-06-17T23:59:00Z"),
                "EXCEPCION",
                "Prueba");
        ConsolidacionPlanillasService.ConsolidacionManualPrecheck precheck =
            new ConsolidacionPlanillasService.ConsolidacionManualPrecheck(
                true,
                false,
                "La consolidación manual puede iniciarse para el periodo 2026-05-31.",
                false,
                false,
                rango);

        when(parametroUnicoService.getString("APP_ADMIN_DASHBOARD_DUMMY_DATA_ENABLED", "false"))
            .thenReturn("false");
        when(parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", "CREFFSOS.xlsx"))
            .thenReturn("CREFFSOS.xlsx");
        when(adminDashboardDevSeedServiceProvider.getIfAvailable()).thenReturn(null);
        when(planillaRepository.findDistinctFechasCorteInformacionAprobadasBySegmentoId(1L))
            .thenReturn(List.of(periodo));
        when(planillaRepository.findPlanillasActivasByFechaCorteAndSegmentoId(periodo, 1L))
            .thenReturn(List.of(planillaYaConsolidada, planillaDisponible, planillaPendiente));
        when(planillaRepository.findByFechaCorteInformacionAndActivoTrueOrderByIdAsc(periodo))
            .thenReturn(List.of(planillaYaConsolidada, planillaDisponible, planillaPendiente, planillaDescartada));
        when(consolidacionRepository.findFirstByPeriodoValoracionAndEstadoConsolidacionInOrderByCreadoEnDesc(periodo, List.of("COMPLETADO", "COMPLETADO_CON_ADVERTENCIAS")))
            .thenReturn(Optional.of(cabecera));
        when(consolidacionRepository.findTop20ByOrderByCreadoEnDesc()).thenReturn(List.of(cabecera));
        when(consolidacionArchivoRepository.findByIdConsolidacionOrderByIdCargaPlanillaAsc(77L))
            .thenReturn(List.of(archivoConsolidado));
        when(consolidacionManualAsyncService.obtenerEstado(periodo)).thenReturn(Optional.empty());
        when(consolidacionPlanillasService.validarInicioConsolidacionManual(periodo)).thenReturn(precheck);
        when(fileStorageService.listObjects("aprobados/2026-05-31/"))
            .thenReturn(List.of(
                "aprobados/2026-05-31/CONSOLIDADA.xlsx",
                "aprobados/2026-05-31/DISPONIBLE.xlsx",
                "aprobados/2026-05-31/DESCARTADA.xlsx"));
        when(fileStorageService.getAbsolutePath(anyString()))
            .thenAnswer(invocation -> Path.of("C:/s3mock2/sipro-local-storage").resolve(invocation.getArgument(0, String.class)));

        var response = service.obtenerDashboard(periodo);

        assertEquals(1, response.archivosAConsolidar().size());
        assertEquals(200L, response.archivosAConsolidar().get(0).id());
        assertEquals(1, response.archivosNoBloqueantes().size());
        assertEquals("PENDIENTE", response.archivosNoBloqueantes().get(0).estado());
        assertEquals(1, response.archivosConsolidados().size());
        assertEquals("CONSOLIDADA.xlsx", response.archivosConsolidados().get(0).nombreArchivo());
        assertNotNull(response.estadoPeriodo());
        assertEquals("EXCEPCION", response.estadoPeriodo().fuenteVentana());
        assertEquals(1, response.estadoPeriodo().cantidadPlanillasPendientes());
        assertEquals("C:/s3mock2/sipro-local-storage/consolidados/2026-05-31/CREFFSOS.xlsx",
            response.estadoPeriodo().rutaArchivoCreffos().replace('\\', '/'));
        assertEquals("C:/s3mock2/sipro-local-storage/consolidados/2026-05-31/CREFFSOS.xlsx",
            response.historico().get(0).detalleSalida().replace('\\', '/'));
        assertNotNull(response.diagnosticoDisponibilidad());
        assertEquals(3, response.diagnosticoDisponibilidad().totalArchivosStorageAprobados());
        assertEquals(1, response.diagnosticoDisponibilidad().totalArchivosElegibles());
        assertEquals(2, response.diagnosticoDisponibilidad().archivosDescartados().size());
        }
}