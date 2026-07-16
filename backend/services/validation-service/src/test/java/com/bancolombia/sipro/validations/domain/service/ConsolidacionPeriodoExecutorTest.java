package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleArchivoValidacion;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionArchivo;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.infrastructure.repository.ClienteLzRepository;
import com.bancolombia.sipro.validations.infrastructure.notification.MailTemplateNotificationService;
import com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionArchivoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidadoRegistroRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsolidacionPeriodoExecutorTest {

    @Mock
    private SiproDetalleCargaPlanillasRepository planillaRepository;

    @Mock
    private SiproDetalleArchivoValidacionRepository validacionRepository;

    @Mock
    private ClienteLzRepository clienteLzRepository;

    @Mock
    private SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;

    @Mock
    private SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository;

    @Mock
    private SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private VentanaCargaService ventanaCargaService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private CreffosConsolidationService creffosConsolidationService;

    @Mock
    private ConsolidacionConciliacionReportService consolidacionConciliacionReportService;

    @Mock
    private NotificacionConsolidacionService notificacionConsolidacionService;

    @Mock
    private ParametroUnicoService parametroUnicoService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Environment environment;

    private ConsolidacionPeriodoExecutor service;

    @BeforeEach
    void setUp() {
        service = new ConsolidacionPeriodoExecutor(
                planillaRepository,
                validacionRepository,
                clienteLzRepository,
                consolidacionRepository,
                consolidacionArchivoRepository,
                consolidadoRegistroRepository,
                productoRepository,
                ventanaCargaService,
                fileStorageService,
                creffosConsolidationService,
                consolidacionConciliacionReportService,
                notificacionConsolidacionService,
                parametroUnicoService,
                entityManager,
                environment
        );

            lenient().when(parametroUnicoService.getLong("APP_CONSOLIDACION_POST_CLOSE_DELAY_HOURS", 1L))
                .thenReturn(1L);
            lenient().when(parametroUnicoService.getLong("APP_CONSOLIDACION_MAX_POST_CLOSE_DAYS", 5L))
                .thenReturn(5L);
            when(environment.matchesProfiles("dev")).thenReturn(false);
    }

            @Test
            void consolidarPeriodoForzadoDebeSeguirFlujoCuandoBypassDevEstaActivoAunqueLaVentanaNoAbraAun() {
            LocalDate periodo = LocalDate.now().plusDays(1);

            when(environment.matchesProfiles("dev")).thenReturn(true);
            when(parametroUnicoService.getString("APP_ADMIN_CONSOLIDACION_BYPASS_WINDOW", "false"))
                .thenReturn("true");
            when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventanaRegla(periodo)));
            when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
                .thenReturn(false, false);
            when(planillaRepository.findPlanillasAprobadasByFechaCorteAndSegmentoId(periodo, 1L))
                .thenReturn(List.of());

            boolean consolidado = service.consolidarPeriodoForzado(periodo, 1L, "Bypass DEV");

            assertFalse(consolidado);
            verify(planillaRepository).findPlanillasAprobadasByFechaCorteAndSegmentoId(periodo, 1L);
            }

    @Test
    void consolidarPeriodoForzadoDebeIncluirSinDatosYPersistirEnBatchConTipoIdDesdeLz() throws Exception {
        LocalDate periodo = LocalDate.of(2026, 5, 31);
        SiproDetalleCargaPlanillas planillaConDatos = crearPlanilla(96L, 1L, "Producto A", false, "aprobados/2026-05-31/archivo-a.xlsx");
        SiproDetalleCargaPlanillas planillaSinDatos = crearPlanilla(97L, 2L, "Producto B", true, null);
        SiproDetalleArchivoValidacion validacion = new SiproDetalleArchivoValidacion();
        validacion.setId(501L);
        validacion.setIdCargaPlanilla(96L);
        validacion.setNumeroFilasDatos(2);
        validacion.setFechaValidacion(LocalDateTime.of(2026, 6, 1, 10, 30));

        when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventanaRegla(periodo)));
        when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
                .thenReturn(false);
        when(planillaRepository.findPlanillasAprobadasByFechaCorteAndSegmentoId(periodo, 1L))
                .thenReturn(List.of(planillaConDatos, planillaSinDatos));
        when(consolidacionRepository.findAllByPeriodoValoracionOrderByCreadoEnDesc(periodo)).thenReturn(List.of());
        when(validacionRepository.findByIdCargaPlanillaIn(List.of(96L, 97L))).thenReturn(List.of(validacion));
        byte[] excelBytes = crearExcelBytes(
            List.of(
                List.of("9001", "101", "12345", "1", "MOD1", "2026", "1", "1", "2026", "12", "31", "2026", "12", "31", "140405", "100.50", "80.10", "1.25", "2.50", "0", "0", "4", "usuario1", "Producto A"),
                List.of("9002", "102", "67890", "1", "MOD2", "2026", "2", "2", "2026", "11", "30", "2026", "11", "30", "140406", "200.00", "150.00", "3.00", "4.00", "0", "0", "5", "usuario2", "Producto A")
            ));
        when(fileStorageService.openStream("aprobados/2026-05-31/archivo-a.xlsx"))
            .thenAnswer(invocation -> new ByteArrayInputStream(excelBytes));
        when(fileStorageService.storeBytes(any(), anyString(), anyString())).thenReturn("consolidados/2026-05-31/CONSOLIDADO_2026-05-31.xlsx");
        when(clienteLzRepository.findLatestTipoIdByNumeroIdIn(anyCollection())).thenAnswer(invocation -> {
            // El cruce en LZ se hace por NIT (columna 1 de la planilla: "9001"/"9002"),
            // no por DOCUMENTO (columna 3: "12345"/"67890"). Ver
            // ConsolidacionPeriodoExecutor.escanearExcel()/cargarTipoIdPorNit().
            List<String> nits = new ArrayList<>(invocation.getArgument(0));
            List<ClienteLzRepository.DocumentoTipoIdProjection> response = new ArrayList<>();
            if (nits.contains("9001")) {
                response.add(projection("9001", "CC"));
            }
            if (nits.contains("9002")) {
                response.add(projection("9002", "NIT"));
            }
            return response;
        });
        when(parametroUnicoService.getInt(eq("APP_CONSOLIDACION_BATCH_INSERT_SIZE"), anyInt())).thenReturn(1);
        when(parametroUnicoService.getInt(eq("APP_CONSOLIDACION_LZ_LOOKUP_CHUNK_SIZE"), anyInt())).thenReturn(1000);
        when(parametroUnicoService.getInt(eq("AUTO_SIZE_LIMIT"), anyInt())).thenReturn(3);
        when(creffosConsolidationService.reconstruirCompleto(periodo))
            .thenReturn(CreffosConsolidationService.PublicationResult.generated(
                "consolidados/2026-05-31/CREFFSOS.xlsx",
                "CREFFSOS.xlsx",
                null
            ));
        when(consolidacionConciliacionReportService.generar(anyLong()))
            .thenReturn(new ConsolidacionConciliacionReportService.GeneratedConciliacionReport(
                "conciliacion_planillas_manuales_20260531.xlsx",
                new byte[]{1, 2, 3}
            ));
        AtomicLong consolidacionId = new AtomicLong(1000L);
        doAnswer(invocation -> {
            SiproDetalleConsolidacionesPlanillas value = invocation.getArgument(0);
            if (value.getIdConsolidacion() == null) {
                value.setIdConsolidacion(consolidacionId.getAndIncrement());
            }
            return value;
        }).when(consolidacionRepository).save(any(SiproDetalleConsolidacionesPlanillas.class));

        AtomicLong archivoId = new AtomicLong(2000L);
        doAnswer(invocation -> {
            SiproDetalleConsolidacionArchivo value = invocation.getArgument(0);
            if (value.getIdConsolidacionArchivo() == null) {
                value.setIdConsolidacionArchivo(archivoId.getAndIncrement());
            }
            return value;
        }).when(consolidacionArchivoRepository).save(any(SiproDetalleConsolidacionArchivo.class));

        List<List<SiproDetalleConsolidadoRegistro>> savedBatches = new ArrayList<>();
        doAnswer(invocation -> {
            List<SiproDetalleConsolidadoRegistro> batch = invocation.getArgument(0);
            savedBatches.add(new ArrayList<>(batch));
            return batch;
        }).when(consolidadoRegistroRepository).saveAll(any());

        boolean consolidado = service.consolidarPeriodoForzado(periodo, 1L, "Prueba consolidación");

        assertTrue(consolidado, "consolidarPeriodoForzado debio retornar true");
        assertEquals(2, savedBatches.size(), "cantidad de batches persistidos");
        assertEquals(2, savedBatches.stream().mapToInt(List::size).sum(), "cantidad total de registros persistidos");
        SiproDetalleConsolidadoRegistro primerRegistro = savedBatches.get(0).get(0);
        assertEquals("CC", primerRegistro.getTipoId(), "tipoId del primer registro (documento 12345)");
        assertEquals("NIT", savedBatches.get(1).get(0).getTipoId(), "tipoId del segundo registro (documento 67890)");
        assertEquals(Integer.valueOf(1), primerRegistro.getIdSegmento(), "idSegmento del primer registro");
        assertEquals("Colgaap/Modificado", primerRegistro.getSegmento(), "segmento del primer registro");
        assertEquals("Cargador 96", primerRegistro.getUsuarioCargador(),
                () -> "usuarioCargador del primer registro (idCargaPlanilla=" + primerRegistro.getIdCargaPlanilla()
                        + ", idUsuarioCargador=" + primerRegistro.getIdUsuarioCargador() + ")");

        ArgumentCaptor<SiproDetalleConsolidacionArchivo> archivoCaptor = ArgumentCaptor.forClass(SiproDetalleConsolidacionArchivo.class);
        verify(consolidacionArchivoRepository, atLeast(4)).save(archivoCaptor.capture());
        assertTrue(archivoCaptor.getAllValues().stream()
                .anyMatch(archivo -> archivo.getIdCargaPlanilla().equals(97L)
                        && Integer.valueOf(0).equals(archivo.getCantidadRegistrosArchivo())
                        && Integer.valueOf(1).equals(archivo.getIdSegmento())
                        && "Colgaap/Modificado".equals(archivo.getSegmento())
                        && "Cargador 97".equals(archivo.getUsuarioCargador())));

        verify(fileStorageService, never()).openStream((String) org.mockito.ArgumentMatchers.isNull());
        verify(consolidadoRegistroRepository, atLeast(2)).flush();
        verify(entityManager, atLeast(2)).clear();
        verify(notificacionConsolidacionService).enviarConfirmacion(anyLong());
    }

    @Test
    void consolidarPeriodoForzadoDebeGuardarCabeceraConFuenteExcepcionYReglaNull() throws Exception {
        LocalDate periodo = LocalDate.now();
        SiproDetalleCargaPlanillas planillaSinDatos = crearPlanilla(98L, 3L, "Producto C", true, null);

        when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventanaExcepcion(periodo)));
        when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
                .thenReturn(false);
        when(planillaRepository.findPlanillasAprobadasByFechaCorteAndSegmentoId(periodo, 1L))
                .thenReturn(List.of(planillaSinDatos));
        when(consolidacionRepository.findAllByPeriodoValoracionOrderByCreadoEnDesc(periodo)).thenReturn(List.of());
        when(validacionRepository.findByIdCargaPlanillaIn(List.of(98L))).thenReturn(List.of());
        when(parametroUnicoService.getInt(eq("AUTO_SIZE_LIMIT"), anyInt())).thenReturn(3);
        when(fileStorageService.storeBytes(any(), anyString(), anyString())).thenReturn("consolidados/2026-05-31/CONSOLIDADO_2026-05-31.xlsx");
        when(creffosConsolidationService.reconstruirCompleto(periodo))
            .thenReturn(CreffosConsolidationService.PublicationResult.generated(
                "consolidados/2026-05-31/CREFFSOS.xlsx",
                "CREFFSOS.xlsx",
                null
            ));
        when(consolidacionConciliacionReportService.generar(anyLong()))
            .thenReturn(new ConsolidacionConciliacionReportService.GeneratedConciliacionReport(
                "conciliacion_planillas_manuales_20260531.xlsx",
                new byte[]{1}
            ));
        AtomicLong consolidacionId = new AtomicLong(3000L);
        List<SiproDetalleConsolidacionesPlanillas> snapshots = new ArrayList<>();
        doAnswer(invocation -> {
            SiproDetalleConsolidacionesPlanillas value = invocation.getArgument(0);
            if (value.getIdConsolidacion() == null) {
                value.setIdConsolidacion(consolidacionId.getAndIncrement());
            }
            snapshots.add(snapshot(value));
            return value;
        }).when(consolidacionRepository).save(any(SiproDetalleConsolidacionesPlanillas.class));

        AtomicLong archivoId = new AtomicLong(4000L);
        doAnswer(invocation -> {
            SiproDetalleConsolidacionArchivo value = invocation.getArgument(0);
            if (value.getIdConsolidacionArchivo() == null) {
                value.setIdConsolidacionArchivo(archivoId.getAndIncrement());
            }
            return value;
        }).when(consolidacionArchivoRepository).save(any(SiproDetalleConsolidacionArchivo.class));

        boolean consolidado = service.consolidarPeriodoForzado(periodo, 1L, "Prueba excepción");

        assertTrue(consolidado);
        SiproDetalleConsolidacionesPlanillas inicial = snapshots.get(0);
        assertEquals("INICIADO", inicial.getEstadoConsolidacion());
        assertEquals("EXCEPCION", inicial.getFuenteVentana());
        assertEquals(null, inicial.getIdReglaVentana());
        assertEquals(periodo, inicial.getPeriodoExcepcionVentana());
        assertNotNull(inicial.getFechaHoraInicio());
        verify(fileStorageService, never()).openStream(anyString());
        verify(consolidadoRegistroRepository, never()).saveAll(any());
    }

        @Test
        void consolidarPeriodoForzadoDebeMarcarAdvertenciasCuandoFallaPostProceso() throws Exception {
        LocalDate periodo = LocalDate.of(2026, 5, 31);
        SiproDetalleCargaPlanillas planillaSinDatos = crearPlanilla(99L, 4L, "Producto D", true, null);

        when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventanaRegla(periodo)));
        when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
            .thenReturn(false);
        when(planillaRepository.findPlanillasAprobadasByFechaCorteAndSegmentoId(periodo, 1L))
            .thenReturn(List.of(planillaSinDatos));
        when(consolidacionRepository.findAllByPeriodoValoracionOrderByCreadoEnDesc(periodo)).thenReturn(List.of());
        when(validacionRepository.findByIdCargaPlanillaIn(List.of(99L))).thenReturn(List.of());
        when(parametroUnicoService.getInt(eq("AUTO_SIZE_LIMIT"), anyInt())).thenReturn(3);
        when(fileStorageService.storeBytes(any(), anyString(), anyString())).thenReturn("consolidados/2026-05-31/CONSOLIDADO_2026-05-31.xlsx");
        when(creffosConsolidationService.reconstruirCompleto(periodo))
            .thenReturn(CreffosConsolidationService.PublicationResult.generated(
                "consolidados/2026-05-31/CREFFSOS.xlsx",
                "CREFFSOS.xlsx",
                "No se pudo copiar CREFFSOS a ruta compartida: \\\\share. Motivo: access denied"
            ));
        when(notificacionConsolidacionService.enviarConfirmacion(anyLong()))
            .thenReturn(MailTemplateNotificationService.DeliveryResult.failed("smtp timeout"));
        when(consolidacionConciliacionReportService.generar(anyLong()))
            .thenReturn(new ConsolidacionConciliacionReportService.GeneratedConciliacionReport(
                "conciliacion_planillas_manuales_20260531.xlsx",
                new byte[]{4, 5, 6}
            ));
        AtomicLong consolidacionId = new AtomicLong(5000L);
        List<SiproDetalleConsolidacionesPlanillas> snapshots = new ArrayList<>();
        doAnswer(invocation -> {
            SiproDetalleConsolidacionesPlanillas value = invocation.getArgument(0);
            if (value.getIdConsolidacion() == null) {
            value.setIdConsolidacion(consolidacionId.getAndIncrement());
            }
            snapshots.add(snapshot(value));
            return value;
        }).when(consolidacionRepository).save(any(SiproDetalleConsolidacionesPlanillas.class));

        AtomicLong archivoId = new AtomicLong(6000L);
        doAnswer(invocation -> {
            SiproDetalleConsolidacionArchivo value = invocation.getArgument(0);
            if (value.getIdConsolidacionArchivo() == null) {
            value.setIdConsolidacionArchivo(archivoId.getAndIncrement());
            }
            return value;
        }).when(consolidacionArchivoRepository).save(any(SiproDetalleConsolidacionArchivo.class));

        boolean consolidado = service.consolidarPeriodoForzado(periodo, 1L, "Prueba advertencias");

        assertTrue(consolidado);
        SiproDetalleConsolidacionesPlanillas finalSnapshot = snapshots.get(snapshots.size() - 1);
        assertEquals("COMPLETADO_CON_ADVERTENCIAS", finalSnapshot.getEstadoConsolidacion());
        assertTrue(finalSnapshot.getObservacion().contains("Prueba advertencias"));
        assertTrue(finalSnapshot.getObservacion().contains("CREFFSOS generado pero no copiado a red"));
        assertTrue(finalSnapshot.getObservacion().contains("Correo no enviado: smtp timeout"));
        verify(notificacionConsolidacionService).enviarConfirmacion(anyLong());
        }

        @Test
        void consolidarPeriodoForzadoDebeNotificarErrorYPersistirAdvertenciaCuandoFallaElCorreoDeError() throws Exception {
        LocalDate periodo = LocalDate.of(2026, 5, 31);
        SiproDetalleCargaPlanillas planillaSinDatos = crearPlanilla(120L, 7L, "Producto Error", true, null);

        when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventanaRegla(periodo)));
        when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
            .thenReturn(false);
        when(planillaRepository.findPlanillasAprobadasByFechaCorteAndSegmentoId(periodo, 1L))
            .thenReturn(List.of(planillaSinDatos));
        when(consolidacionRepository.findAllByPeriodoValoracionOrderByCreadoEnDesc(periodo)).thenReturn(List.of());
        when(validacionRepository.findByIdCargaPlanillaIn(List.of(120L))).thenReturn(List.of());
        when(parametroUnicoService.getInt(eq("AUTO_SIZE_LIMIT"), anyInt())).thenReturn(3);
        when(fileStorageService.storeBytes(any(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("fallo escribiendo consolidado en storage"));
        when(notificacionConsolidacionService.notificarError(anyLong()))
            .thenReturn(MailTemplateNotificationService.DeliveryResult.failed("smtp caido"));

        AtomicLong consolidacionId = new AtomicLong(7000L);
        List<SiproDetalleConsolidacionesPlanillas> snapshots = new ArrayList<>();
        doAnswer(invocation -> {
            SiproDetalleConsolidacionesPlanillas value = invocation.getArgument(0);
            if (value.getIdConsolidacion() == null) {
            value.setIdConsolidacion(consolidacionId.getAndIncrement());
            }
            snapshots.add(snapshot(value));
            return value;
        }).when(consolidacionRepository).save(any(SiproDetalleConsolidacionesPlanillas.class));

        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> service.consolidarPeriodoForzado(periodo, 1L, "Prueba correo error"));

        assertTrue(error.getMessage().contains("Error consolidando periodo 2026-05-31"));
        SiproDetalleConsolidacionesPlanillas finalSnapshot = snapshots.get(snapshots.size() - 1);
        assertEquals("ERROR", finalSnapshot.getEstadoConsolidacion());
        assertTrue(finalSnapshot.getMensajeError().contains("IllegalStateException"));
        assertTrue(finalSnapshot.getObservacion().contains("Prueba correo error"));
        assertTrue(finalSnapshot.getObservacion().contains("Correo no enviado: smtp caido"));
        verify(notificacionConsolidacionService).notificarError(anyLong());
        }

    private SiproDetalleCargaPlanillas crearPlanilla(Long id,
                                                     Long idProducto,
                                                     String producto,
                                                     boolean sinDatos,
                                                     String ruta) {
        SiproDetalleCargaPlanillas planilla = new SiproDetalleCargaPlanillas();
        planilla.setId(id);
        planilla.setIdProducto(idProducto);
        planilla.setProducto(producto);
        planilla.setEstadoPlanilla(sinDatos ? "APROBACION SIN DATOS" : "APROBADO");
        planilla.setNoReportaDatos(sinDatos);
        planilla.setRutaArchivoAlmacenamiento(ruta);
        planilla.setArchivoUid("uid-" + id);
        planilla.setNombreArchivoFuente("archivo-" + id + ".xlsx");
        planilla.setFechaCorteInformacion(LocalDate.of(2026, 5, 31));
        planilla.setDescripcionLarga("Descripcion " + id);
        planilla.setIdUsuarioCarga(100L + id);
        planilla.setNombreUsuarioCarga("Cargador " + id);
        planilla.setCorreoUsuarioCarga("cargador" + id + "@mail.com");
        planilla.setIdUsuarioAprobador(200L + id);
        planilla.setUsuarioAprobador("Aprobador " + id);
        planilla.setSegmento("SIMULACION");
        return planilla;
    }

    private VentanaCargaService.VentanaCalculada ventanaRegla(LocalDate periodo) {
        LocalDateTime ahora = LocalDateTime.now();
        return new VentanaCargaService.VentanaCalculada(
                periodo,
            ahora.minusDays(2),
            ahora.minusHours(2),
            ahora.minusHours(2),
                "REGLA_GENERAL",
                3L,
                null,
                null
        );
    }

    private VentanaCargaService.VentanaCalculada ventanaExcepcion(LocalDate periodo) {
        LocalDateTime ahora = LocalDateTime.now();
        return new VentanaCargaService.VentanaCalculada(
                periodo,
            ahora.minusDays(2),
            ahora.minusHours(2),
            ahora.minusHours(2),
                "EXCEPCION",
                3L,
                periodo,
                "Cierre ampliado"
        );
    }

    private ClienteLzRepository.DocumentoTipoIdProjection projection(String numeroId, String tipoId) {
        return new ClienteLzRepository.DocumentoTipoIdProjection() {
            @Override
            public String getNumeroId() {
                return numeroId;
            }

            @Override
            public String getTipoId() {
                return tipoId;
            }
        };
    }

    private SiproDetalleConsolidacionesPlanillas snapshot(SiproDetalleConsolidacionesPlanillas source) {
        SiproDetalleConsolidacionesPlanillas copy = new SiproDetalleConsolidacionesPlanillas();
        copy.setIdConsolidacion(source.getIdConsolidacion());
        copy.setPeriodoValoracion(source.getPeriodoValoracion());
        copy.setFechaHoraInicio(source.getFechaHoraInicio());
        copy.setEstadoConsolidacion(source.getEstadoConsolidacion());
        copy.setFuenteVentana(source.getFuenteVentana());
        copy.setIdReglaVentana(source.getIdReglaVentana());
        copy.setPeriodoExcepcionVentana(source.getPeriodoExcepcionVentana());
        copy.setObservacion(source.getObservacion());
        copy.setMensajeError(source.getMensajeError());
        return copy;
    }

    private byte[] crearExcelBytes(List<List<String>> filas) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Datos");
            Row header = sheet.createRow(0);
            List<String> headers = List.of(
                    "NIT", "OFICINA", "DOCUMENTO", "MONEDA", "MODALIDAD",
                    "ANOINIOBL", "MESINIOBL", "DIAINIOBL",
                    "ANOVCTO", "MESVCTO", "DIAVCTO",
                    "ANOVCTOFIN", "MESVCTOFIN", "DIAVCTOFIN",
                    "CTAPUC", "VLRINIOBL", "SALDO", "SDOOTRCTAS", "INTERESES",
                    "SDOVENCIDO", "INTCTASORD", "CLASIFICACION", "USUARIO", "PRODUCTO");
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }

            int rowIndex = 1;
            for (List<String> fila : filas) {
                Row row = sheet.createRow(rowIndex++);
                for (int index = 0; index < fila.size(); index++) {
                    row.createCell(index).setCellValue(fila.get(index));
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}