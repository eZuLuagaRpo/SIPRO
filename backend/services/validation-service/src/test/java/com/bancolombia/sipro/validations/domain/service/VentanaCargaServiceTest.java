package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproParametroUnico;
import com.bancolombia.sipro.validations.domain.model.SiproExcepcionVentanaCarga;
import com.bancolombia.sipro.validations.domain.model.SiproReglaVentanaCarga;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproExcepcionVentanaCargaRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproReglaVentanaCargaRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * Verifica el cálculo de la ventana de carga con reglas, parámetros y excepciones.
 */
class VentanaCargaServiceTest {

    private static final String CLAVE_DIAS_GRACIA_VENTANA = "DIAS_GRACIA_VENTANA";

    @Mock
    private SiproReglaVentanaCargaRepository reglaRepository;

    @Mock
    private SiproExcepcionVentanaCargaRepository excepcionRepository;

    @Mock
    private ParametroUnicoService parametroUnicoService;

    @Mock
    private ObjectProvider<AdminDashboardDevSeedService> adminDashboardDevSeedServiceProvider;

    @Mock
    private AdminDashboardDevSeedService adminDashboardDevSeedService;

    private VentanaCargaService service;

    @BeforeEach
    void setUp() {
        service = new VentanaCargaService(
                reglaRepository,
                excepcionRepository,
                parametroUnicoService,
                adminDashboardDevSeedServiceProvider);
    }

    @Test
    void deberiaUsarDiasGraciaDesdeParametrosUnicos() {
        LocalDate periodo = LocalDate.of(2026, 2, 28);
        SiproReglaVentanaCarga regla = buildRegla(-1, 20);

        when(reglaRepository.findReglaVigente(periodo)).thenReturn(Optional.of(regla));
        when(excepcionRepository.findByPeriodoValoracion(periodo)).thenReturn(Optional.empty());
        when(parametroUnicoService.obtenerDirecto(CLAVE_DIAS_GRACIA_VENTANA))
                .thenReturn(Optional.of(buildParametro("25")));

        Optional<VentanaCargaService.VentanaCalculada> ventanaOpt = service.obtenerVentana(periodo);

        assertTrue(ventanaOpt.isPresent());
        assertEquals(LocalDateTime.of(2026, 3, 25, 14, 0), ventanaOpt.get().getFechaHoraCierre());
    }

    @Test
    void deberiaUsarOffsetDeReglaCuandoNoExisteParametro() {
        LocalDate periodo = LocalDate.of(2026, 2, 28);
        SiproReglaVentanaCarga regla = buildRegla(-1, 20);

        when(reglaRepository.findReglaVigente(periodo)).thenReturn(Optional.of(regla));
        when(excepcionRepository.findByPeriodoValoracion(periodo)).thenReturn(Optional.empty());
        when(parametroUnicoService.obtenerDirecto(CLAVE_DIAS_GRACIA_VENTANA)).thenReturn(Optional.empty());
        when(parametroUnicoService.getInt(CLAVE_DIAS_GRACIA_VENTANA, 20)).thenReturn(20);

        Optional<VentanaCargaService.VentanaCalculada> ventanaOpt = service.obtenerVentana(periodo);

        assertTrue(ventanaOpt.isPresent());
        assertEquals(LocalDateTime.of(2026, 3, 20, 14, 0), ventanaOpt.get().getFechaHoraCierre());
    }

    @Test
    void deberiaIgnorarDiasGraciaNegativos() {
        LocalDate periodo = LocalDate.of(2026, 2, 28);
        SiproReglaVentanaCarga regla = buildRegla(-1, 20);

        when(reglaRepository.findReglaVigente(periodo)).thenReturn(Optional.of(regla));
        when(excepcionRepository.findByPeriodoValoracion(periodo)).thenReturn(Optional.empty());
        when(parametroUnicoService.obtenerDirecto(CLAVE_DIAS_GRACIA_VENTANA))
                .thenReturn(Optional.of(buildParametro("-5")));
        when(parametroUnicoService.getInt(CLAVE_DIAS_GRACIA_VENTANA, 20)).thenReturn(20);

        Optional<VentanaCargaService.VentanaCalculada> ventanaOpt = service.obtenerVentana(periodo);

        assertTrue(ventanaOpt.isPresent());
        assertEquals(LocalDateTime.of(2026, 3, 20, 14, 0), ventanaOpt.get().getFechaHoraCierre());
    }

    @Test
    void deberiaAplicarExcepcionCuandoExisteParaElPeriodo() {
        LocalDate periodo = LocalDate.of(2026, 2, 28);
        SiproReglaVentanaCarga regla = buildRegla(-1, 20);
        SiproExcepcionVentanaCarga excepcion = buildExcepcion(periodo, LocalDate.of(2026, 3, 30));

        when(reglaRepository.findReglaVigente(periodo)).thenReturn(Optional.of(regla));
        when(excepcionRepository.findByPeriodoValoracion(periodo)).thenReturn(Optional.of(excepcion));
        when(parametroUnicoService.obtenerDirecto(CLAVE_DIAS_GRACIA_VENTANA))
                .thenReturn(Optional.of(buildParametro("25")));
        when(parametroUnicoService.getLong("MAX_EXTENSION_EXCEPCION_DIAS", 25)).thenReturn(25L);

        Optional<VentanaCargaService.VentanaCalculada> ventanaOpt = service.obtenerVentana(periodo);

        assertTrue(ventanaOpt.isPresent());
        assertEquals(LocalDateTime.of(2026, 3, 30, 14, 0), ventanaOpt.get().getFechaHoraCierre());
        assertTrue(ventanaOpt.get().esExcepcion());
    }

    @Test
    void deberiaUsarVentanaDummyDevCuandoNoExisteRegla() {
        LocalDate periodo = LocalDate.of(2026, 5, 31);
        VentanaCargaService.VentanaCalculada ventanaDummy = new VentanaCargaService.VentanaCalculada(
                periodo,
                LocalDateTime.of(2026, 4, 16, 0, 0),
                LocalDateTime.of(2026, 7, 15, 23, 59),
                LocalDateTime.of(2026, 7, 15, 23, 59),
            "REGLA_GENERAL",
                null,
                null,
                "Ventana técnica dev");

        when(reglaRepository.findReglaVigente(periodo)).thenReturn(Optional.empty());
        when(adminDashboardDevSeedServiceProvider.getIfAvailable()).thenReturn(adminDashboardDevSeedService);
        when(adminDashboardDevSeedService.obtenerVentanaDummy(periodo)).thenReturn(Optional.of(ventanaDummy));

        Optional<VentanaCargaService.VentanaCalculada> ventanaOpt = service.obtenerVentana(periodo);

        assertTrue(ventanaOpt.isPresent());
        assertEquals("REGLA_GENERAL", ventanaOpt.get().getFuenteVentana());
        assertEquals(LocalDateTime.of(2026, 7, 15, 23, 59), ventanaOpt.get().getFechaHoraCierre());
    }

    /**
     * Construye una regla base de ventana para los escenarios de prueba.
     */
    private SiproReglaVentanaCarga buildRegla(int offsetDiasApertura, int offsetDiasCierre) {
        SiproReglaVentanaCarga regla = new SiproReglaVentanaCarga();
        regla.setReglaId(99L);
        regla.setOffsetDiasApertura(offsetDiasApertura);
        regla.setHoraApertura(LocalTime.of(0, 1));
        regla.setOffsetDiasCierre(offsetDiasCierre);
        regla.setHoraCierre(LocalTime.of(14, 0));
        regla.setVigenteDesde(LocalDate.of(2026, 1, 1));
        regla.setActiva(true);
        return regla;
    }

    /**
     * Construye un parámetro único simple para simular días de gracia configurados.
     */
    private SiproParametroUnico buildParametro(String valor) {
        SiproParametroUnico parametro = new SiproParametroUnico();
        parametro.setClave(CLAVE_DIAS_GRACIA_VENTANA);
        parametro.setValor(valor);
        parametro.setTipo("INTEGER");
        return parametro;
    }

    /**
     * Construye una excepción de ventana aplicable al período evaluado.
     */
    private SiproExcepcionVentanaCarga buildExcepcion(LocalDate periodo, LocalDate fechaCierre) {
        SiproExcepcionVentanaCarga excepcion = new SiproExcepcionVentanaCarga();
        excepcion.setPeriodoValoracion(periodo);
        excepcion.setFechaAperturaOverride(periodo.minusDays(1));
        excepcion.setHoraAperturaOverride(LocalTime.of(0, 1));
        excepcion.setFechaCierreOverride(fechaCierre);
        excepcion.setHoraCierreOverride(LocalTime.of(14, 0));
        excepcion.setMotivo("Ajuste fecha cierre marzo para excepción de febrero");
        excepcion.setCreadoPorId(1L);
        excepcion.setModificadoPorId(1L);
        excepcion.setCreadoEn(OffsetDateTime.now());
        excepcion.setModificadoEn(OffsetDateTime.now());
        return excepcion;
    }
}