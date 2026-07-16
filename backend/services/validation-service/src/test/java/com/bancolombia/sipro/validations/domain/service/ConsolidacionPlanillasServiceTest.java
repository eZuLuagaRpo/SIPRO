package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsolidacionPlanillasServiceTest {

    @Mock
    private SiproDetalleCargaPlanillasRepository planillaRepository;

    @Mock
    private SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;

    @Mock
    private VentanaCargaService ventanaCargaService;

    @Mock
    private ConsolidacionPeriodoExecutor consolidacionPeriodoExecutor;

        @Mock
        private ParametroUnicoService parametroUnicoService;

        @Mock
        private Environment environment;

    private ConsolidacionPlanillasService service;

    @BeforeEach
    void setUp() {
        service = new ConsolidacionPlanillasService(
                planillaRepository,
                consolidacionRepository,
                ventanaCargaService,
                consolidacionPeriodoExecutor,
                parametroUnicoService,
                environment
        );

        lenient().when(parametroUnicoService.getLong("APP_CONSOLIDACION_POST_CLOSE_DELAY_HOURS", 1L))
                .thenReturn(1L);
        lenient().when(parametroUnicoService.getLong("APP_CONSOLIDACION_MAX_POST_CLOSE_DAYS", 5L))
                .thenReturn(5L);
        lenient().when(environment.matchesProfiles("dev")).thenReturn(false);
    }

        // Verifica que el barrido consolida periodos con aprobadas aunque existan pendientes o rechazadas.
        @Test
        void barridoDebeIntentarConsolidarAunqueExistanPendientesORechazadas() {
                LocalDate periodoConPendientes = LocalDate.of(2026, 5, 31);
                LocalDate periodoListo = LocalDate.of(2026, 6, 30);
                SiproDetalleCargaPlanillas planillaAprobada = new SiproDetalleCargaPlanillas();
                planillaAprobada.setRutaArchivoAlmacenamiento("aprobados/2026-05-31/aprobada.xlsx");
                planillaAprobada.setEstadoPlanilla("APROBADO");
                SiproDetalleCargaPlanillas planillaPendiente = new SiproDetalleCargaPlanillas();
                planillaPendiente.setEstadoPlanilla("PENDIENTE_APROBACION");

                SiproDetalleCargaPlanillas planillaLista = new SiproDetalleCargaPlanillas();
                planillaLista.setRutaArchivoAlmacenamiento("aprobados/2026-06-30/lista.xlsx");
                planillaLista.setEstadoPlanilla("APROBADO");

                when(planillaRepository.findDistinctFechasCorteInformacionAprobadasBySegmentoId(1L))
                        .thenReturn(List.of(periodoConPendientes, periodoListo));
                when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodoConPendientes), anyCollection()))
                        .thenReturn(false);
                when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodoListo), anyCollection()))
                        .thenReturn(false);
                when(planillaRepository.findPlanillasActivasByFechaCorteAndSegmentoId(periodoConPendientes, 1L))
                        .thenReturn(List.of(planillaAprobada, planillaPendiente));
                when(planillaRepository.findPlanillasActivasByFechaCorteAndSegmentoId(periodoListo, 1L))
                        .thenReturn(List.of(planillaLista));

                service.ejecutarBarridoConsolidacionesPendientes();

                verify(planillaRepository).findDistinctFechasCorteInformacionAprobadasBySegmentoId(1L);
                verify(planillaRepository, never()).findDistinctFechasCorteInformacionByEstadoPlanillaAndActivoTrue("APROBADO");
                verify(consolidacionPeriodoExecutor)
                        .consolidarPeriodoSiCorresponde(periodoConPendientes, 1L, "Barrido automático de consolidaciones pendientes");
                verify(consolidacionPeriodoExecutor)
                        .consolidarPeriodoSiCorresponde(periodoListo, 1L, "Barrido automático de consolidaciones pendientes");
        }

        // Verifica que la validacion manual permite iniciar y consolida solo planillas aprobadas.
        @Test
        void validacionManualDebePermitirAunqueHayaPlanillasActivasSinAprobar() {
                LocalDate periodo = LocalDate.now();
                LocalDateTime ahora = LocalDateTime.now();
                VentanaCargaService.VentanaCalculada ventana = new VentanaCargaService.VentanaCalculada(
                        periodo,
                        ahora.minusDays(2),
                        ahora.minusHours(2),
                        ahora.minusDays(1),
                        "EXCEPCION",
                        3L,
                        periodo,
                        "Prueba"
                );
                SiproDetalleCargaPlanillas planillaAprobada = new SiproDetalleCargaPlanillas();
                planillaAprobada.setEstadoPlanilla("APROBADO");
                planillaAprobada.setRutaArchivoAlmacenamiento("aprobados/2026-05-31/aprobada.xlsx");
                SiproDetalleCargaPlanillas planillaPendiente = new SiproDetalleCargaPlanillas();
                planillaPendiente.setEstadoPlanilla("PENDIENTE_APROBACION");

                when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventana));
                when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
                        .thenReturn(false, false);
                when(planillaRepository.findPlanillasActivasByFechaCorteAndSegmentoId(periodo, 1L))
                        .thenReturn(List.of(planillaAprobada, planillaPendiente));

                ConsolidacionPlanillasService.ConsolidacionManualPrecheck response = service.validarInicioConsolidacionManual(periodo);

                assertTrue(response.puedeIniciar());
                assertFalse(response.enCurso());
                assertTrue(response.mensaje().contains("solo lo aprobado"));
                        assertFalse(response.sobrescribeConsolidacionExistente());
        }

        // Verifica que la validacion manual bloquea cuando ya hay una consolidacion en curso.
        @Test
                void validacionManualDebeBloquearCuandoYaExisteConsolidacionEnCurso() {
                LocalDate periodo = LocalDate.now();
                        LocalDateTime ahora = LocalDateTime.now();
                VentanaCargaService.VentanaCalculada ventana = new VentanaCargaService.VentanaCalculada(
                                periodo,
                                        ahora.minusDays(2),
                                        ahora.minusHours(2),
                                        ahora.minusDays(1),
                                "EXCEPCION",
                                3L,
                                periodo,
                                "Prueba"
                );

                when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventana));
                when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
                                .thenReturn(true, false);

                ConsolidacionPlanillasService.ConsolidacionManualPrecheck response = service.validarInicioConsolidacionManual(periodo);

                assertFalse(response.puedeIniciar());
                assertTrue(response.enCurso());
                assertTrue(response.mensaje().contains("en curso"));
        }

            // Verifica que la validacion manual permite consolidar periodos con certificacion sin datos.
            @Test
            void validacionManualDebePermitirPeriodoConPlanillasSoloSinDatos() {
                LocalDate periodo = LocalDate.now();
                LocalDateTime ahora = LocalDateTime.now();
                VentanaCargaService.VentanaCalculada ventana = new VentanaCargaService.VentanaCalculada(
                        periodo,
                        ahora.minusDays(2),
                        ahora.minusHours(2),
                        ahora.minusDays(1),
                        "EXCEPCION",
                        3L,
                        periodo,
                        "Prueba"
                );
                SiproDetalleCargaPlanillas planillaSinDatos = new SiproDetalleCargaPlanillas();
                planillaSinDatos.setId(10L);
                planillaSinDatos.setEstadoPlanilla("APROBACION SIN DATOS");
                planillaSinDatos.setNoReportaDatos(true);
                planillaSinDatos.setRutaArchivoAlmacenamiento(null);

                when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventana));
                when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
                        .thenReturn(false, false);
                when(planillaRepository.findPlanillasActivasByFechaCorteAndSegmentoId(periodo, 1L))
                        .thenReturn(List.of(planillaSinDatos));

                ConsolidacionPlanillasService.ConsolidacionManualPrecheck response = service.validarInicioConsolidacionManual(periodo);

                assertTrue(response.puedeIniciar());
                assertFalse(response.enCurso());
                                assertFalse(response.sobrescribeConsolidacionExistente());
            }

        // Verifica que la validacion manual bloquea cuando el periodo ya fue consolidado.
        @Test
        void validacionManualDebeBloquearCuandoPeriodoYaEstaCompletadoConAdvertencias() {
                LocalDate periodo = LocalDate.now();
                LocalDateTime ahora = LocalDateTime.now();
            VentanaCargaService.VentanaCalculada ventana = new VentanaCargaService.VentanaCalculada(
                    periodo,
                        ahora.minusDays(5),
                        ahora.minusHours(2),
                        ahora.minusDays(4),
                    "REGLA",
                    5L,
                    null,
                    null
            );
            SiproDetalleCargaPlanillas planillaConDatos = new SiproDetalleCargaPlanillas();
            planillaConDatos.setId(77L);
            planillaConDatos.setEstadoPlanilla("APROBADO");
            planillaConDatos.setNoReportaDatos(false);
            planillaConDatos.setRutaArchivoAlmacenamiento("aprobados/2026-06-30/archivo.xlsx");

            when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventana));
            when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
                    .thenReturn(false, true);
            when(planillaRepository.findPlanillasActivasByFechaCorteAndSegmentoId(periodo, 1L))
                    .thenReturn(List.of(planillaConDatos));
        SiproDetalleConsolidacionesPlanillas consolidacionExistente = new SiproDetalleConsolidacionesPlanillas();
        consolidacionExistente.setFechaHoraFin(OffsetDateTime.now().minusDays(1));
        when(consolidacionRepository.findFirstByPeriodoValoracionAndEstadoConsolidacionInOrderByCreadoEnDesc(eq(periodo), anyCollection()))
                .thenReturn(Optional.of(consolidacionExistente));

            ConsolidacionPlanillasService.ConsolidacionManualPrecheck response = service.validarInicioConsolidacionManual(periodo);

            assertFalse(response.puedeIniciar());
            assertFalse(response.enCurso());
            assertFalse(response.sobrescribeConsolidacionExistente());
        assertTrue(response.mensaje().contains("ya fue consolidado el"));
    }

        // Verifica que en DEV se permite iniciar manualmente cuando el bypass de ventana esta activo.
        @Test
    void validacionManualDebePermitirCuandoBypassDevEstaActivoAunqueLaVentanaSigaAbierta() {
        LocalDate periodo = LocalDate.now().plusDays(1);
        VentanaCargaService.VentanaCalculada ventana = new VentanaCargaService.VentanaCalculada(
                periodo,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusMinutes(15),
                LocalDateTime.now().plusMinutes(15),
                "REGLA",
                7L,
                null,
                null
        );
        SiproDetalleCargaPlanillas planillaConDatos = new SiproDetalleCargaPlanillas();
        planillaConDatos.setId(88L);
        planillaConDatos.setEstadoPlanilla("APROBADO");
        planillaConDatos.setNoReportaDatos(false);
        planillaConDatos.setRutaArchivoAlmacenamiento("aprobados/2026-07-31/archivo.xlsx");

        when(environment.matchesProfiles("dev")).thenReturn(true);
        when(parametroUnicoService.getString("APP_ADMIN_CONSOLIDACION_BYPASS_WINDOW", "false"))
                .thenReturn("true");
        when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventana));
        when(consolidacionRepository.existsByPeriodoValoracionAndEstadoConsolidacionIn(eq(periodo), anyCollection()))
                .thenReturn(false, false);
        when(planillaRepository.findPlanillasActivasByFechaCorteAndSegmentoId(periodo, 1L))
                .thenReturn(List.of(planillaConDatos));

        ConsolidacionPlanillasService.ConsolidacionManualPrecheck response = service.validarInicioConsolidacionManual(periodo);

        assertTrue(response.puedeIniciar());
        assertTrue(response.ventanaIgnoradaPorConfiguracion());
        assertTrue(response.mensaje().contains("Bypass DEV activo"));
    }

        // Verifica que la validacion manual bloquea antes del cierre de ventana y del inicio del rango.
        @Test
    void validacionManualDebeBloquearCuandoTodaviaNoIniciaElRangoPostCierre() {
        LocalDate periodo = LocalDate.now().plusDays(1);
        VentanaCargaService.VentanaCalculada ventana = new VentanaCargaService.VentanaCalculada(
                periodo,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusMinutes(15),
                LocalDateTime.now().plusMinutes(15),
                "REGLA",
                7L,
                null,
                null
        );

        when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventana));

        ConsolidacionPlanillasService.ConsolidacionManualPrecheck response = service.validarInicioConsolidacionManual(periodo);

        assertFalse(response.puedeIniciar());
        assertFalse(response.enCurso());
        assertTrue(response.mensaje().contains("aún no ha cerrado"));
        assertTrue(response.rango().inicioRangoConsolidacion().isAfter(OffsetDateTime.now().minusMinutes(1)));
    }

        // Verifica que la validacion manual bloquea cuando el rango post-cierre ya expiro.
        @Test
    void validacionManualDebeBloquearCuandoElRangoPostCierreYaExpiro() {
        LocalDate periodo = LocalDate.now().minusDays(10);
        VentanaCargaService.VentanaCalculada ventana = new VentanaCargaService.VentanaCalculada(
                periodo,
                LocalDateTime.now().minusDays(20),
                LocalDateTime.now().minusDays(6),
                LocalDateTime.now().minusDays(6),
                "EXCEPCION",
                8L,
                periodo,
                "Prueba"
        );

        when(ventanaCargaService.obtenerVentana(periodo)).thenReturn(Optional.of(ventana));

        ConsolidacionPlanillasService.ConsolidacionManualPrecheck response = service.validarInicioConsolidacionManual(periodo);

        assertFalse(response.puedeIniciar());
        assertFalse(response.enCurso());
        assertTrue(response.mensaje().contains("expiró"));
        assertTrue(response.rango().finRangoConsolidacion().isBefore(OffsetDateTime.now().plusMinutes(1)));
    }
}