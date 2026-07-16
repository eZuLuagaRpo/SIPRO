package com.bancolombia.sipro.validations.application.usecase;

import com.bancolombia.sipro.validations.application.dto.TableroControlResponse;
import com.bancolombia.sipro.validations.domain.model.Producto;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.service.ConsolidacionPlanillasService;
import com.bancolombia.sipro.validations.domain.service.ExcelMetadataService;
import com.bancolombia.sipro.validations.domain.service.FileStorageService;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.domain.service.PlanillaNotificationService;
import com.bancolombia.sipro.validations.domain.service.VentanaCargaService;
import com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SegmentoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleRechazosPlanillaRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproUsuarioProductoRolRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioLoginRepository;
import com.bancolombia.sipro.validations.service.LoteMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanillaUseCaseTableroControlTest {

    @Mock private SiproDetalleCargaPlanillasRepository planillaRepository;
    @Mock private SiproDetalleArchivoValidacionRepository validacionRepository;
    @Mock private SiproDetalleRechazosPlanillaRepository rechazoRepository;
    @Mock private SiproUsuarioProductoRolRepository uprRepository;
    @Mock private UsuarioLoginRepository usuarioLoginRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private ExcelMetadataService excelMetadataService;
    @Mock private VentanaCargaService ventanaCargaService;
    @Mock private ConsolidacionPlanillasService consolidacionPlanillasService;
    @Mock private PlanillaNotificationService planillaNotificationService;
    @Mock private LoteMemoryStore loteMemoryStore;
    @Mock private ProductoRepository productoRepository;
    @Mock private SegmentoRepository segmentoRepository;
        @Mock private ParametroUnicoService parametroUnicoService;

    private PlanillaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PlanillaUseCase(
                planillaRepository, validacionRepository, rechazoRepository,
                uprRepository, usuarioLoginRepository,
                fileStorageService, excelMetadataService, ventanaCargaService,
                consolidacionPlanillasService, planillaNotificationService,
                                loteMemoryStore, productoRepository, segmentoRepository, parametroUnicoService
        );
    }

    // ============================== Helpers ==============================

    private Producto producto(long id, String titulo, long idSegmento) {
        Producto p = new Producto();
        p.setIdProducto(id);
        p.setTitulo(titulo);
        p.setIdSegmento(idSegmento);
        p.setActivo(1);
        return p;
    }

    private SiproDetalleCargaPlanillas planilla(long id, long idProducto, String estado, boolean noReportaDatos) {
        SiproDetalleCargaPlanillas p = new SiproDetalleCargaPlanillas();
        p.setId(id);
        p.setIdProducto(idProducto);
        p.setEstadoPlanilla(estado);
        p.setNoReportaDatos(noReportaDatos);
        return p;
    }

    private TableroControlResponse.TableroFilaDto filaUnica(TableroControlResponse response) {
        assertEquals(1, response.getFilas().size(), "Se esperaba exactamente 1 fila en el tablero");
        return response.getFilas().get(0);
    }

    // ============================== Estado PENDIENTE_CARGA ==============================

    @Test
    void deberiaRetornarPendienteCargaYPendienteAprobacionCuandoNoHayPlanillaEnNingunSegmento() {
        // Sin planillas → ambos segmentos sin carga ni aprobación
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(
                producto(1L, "Banco Corrientes", 1L),
                producto(2L, "Banco Corrientes", 2L)
        ));

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 6);
        TableroControlResponse.TableroFilaDto fila = filaUnica(response);

        assertEquals("Banco Corrientes",     fila.getNombreProducto());
        assertEquals("PENDIENTE_CARGA",      fila.getEstadoCargadoColgaap());
        assertEquals("PENDIENTE_APROBACION", fila.getEstadoAprobadoColgaap());
        assertEquals("PENDIENTE_CARGA",      fila.getEstadoCargadoFullIfrs());
        assertEquals("PENDIENTE_APROBACION", fila.getEstadoAprobadoFullIfrs());
    }

    // ============================== Estado ARCHIVO_CARGADO / PENDIENTE_APROBACION ==============================

    @Test
    void deberiaRetornarArchivoCargadoYPendienteAprobacionCuandoPlanillaEstaPendienteEnColgaap() {
        // Planilla en Colgaap en estado PENDIENTE → cargado pero no aprobado aún
        // Segmento IFRS sin planilla → pendiente en ambos estados
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(
                producto(10L, "Crédito", 1L),
                producto(11L, "Crédito", 2L)
        ));
        when(planillaRepository.findActivasByAnioMesAndSegmentoId(2026, 5, 1L))
                .thenReturn(List.of(planilla(100L, 10L, "PENDIENTE", false)));

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 5);
        TableroControlResponse.TableroFilaDto fila = filaUnica(response);

        assertEquals("ARCHIVO_CARGADO",      fila.getEstadoCargadoColgaap());
        assertEquals("PENDIENTE_APROBACION", fila.getEstadoAprobadoColgaap());
        assertEquals("PENDIENTE_CARGA",      fila.getEstadoCargadoFullIfrs());
        assertEquals("PENDIENTE_APROBACION", fila.getEstadoAprobadoFullIfrs());
    }

    // ============================== Estado ARCHIVO_APROBADO ==============================

    @Test
    void deberiaRetornarArchivoAprobadoCuandoPlanillaAprobadaSinNoReportaDatos() {
        // Planilla APROBADO con datos reales en ambos segmentos
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(
                producto(20L, "Leasing", 1L),
                producto(21L, "Leasing", 2L)
        ));
        when(planillaRepository.findActivasByAnioMesAndSegmentoId(2026, 4, 1L))
                .thenReturn(List.of(planilla(200L, 20L, "APROBADO", false)));
        when(planillaRepository.findActivasByAnioMesAndSegmentoId(2026, 4, 2L))
                .thenReturn(List.of(planilla(201L, 21L, "APROBADO", false)));

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 4);
        TableroControlResponse.TableroFilaDto fila = filaUnica(response);

        assertEquals("ARCHIVO_CARGADO",  fila.getEstadoCargadoColgaap());
        assertEquals("ARCHIVO_APROBADO", fila.getEstadoAprobadoColgaap());
        assertEquals("ARCHIVO_CARGADO",  fila.getEstadoCargadoFullIfrs());
        assertEquals("ARCHIVO_APROBADO", fila.getEstadoAprobadoFullIfrs());
    }

    // ============================== Estado SIN_DATOS / APROBACION_SIN_DATOS ==============================

    @Test
    void deberiaRetornarSinDatosYAprobacionSinDatosCuandoPlanillaAprobadaConNoReportaDatos() {
        // noReportaDatos=true + APROBADO → certificación "sin datos" aprobada
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(
                producto(30L, "Nequi", 1L),
                producto(31L, "Nequi", 2L)
        ));
        when(planillaRepository.findActivasByAnioMesAndSegmentoId(2026, 3, 1L))
                .thenReturn(List.of(planilla(300L, 30L, "APROBADO", true)));

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 3);
        TableroControlResponse.TableroFilaDto fila = filaUnica(response);

        assertEquals("SIN_DATOS",           fila.getEstadoCargadoColgaap());
        assertEquals("APROBACION_SIN_DATOS", fila.getEstadoAprobadoColgaap());
        assertEquals("PENDIENTE_CARGA",      fila.getEstadoCargadoFullIfrs());
        assertEquals("PENDIENTE_APROBACION", fila.getEstadoAprobadoFullIfrs());
    }

    // ============================== Estado ARCHIVO_RECHAZADO ==============================

    @Test
    void deberiaRetornarArchivoCargadoYArchRechazadoCuandoPlanillaRechazadaSinNoReportaDatos() {
        // Archivo rechazado (con datos reales, no certificación sin datos)
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(
                producto(50L, "Cartera", 1L),
                producto(51L, "Cartera", 2L)
        ));
        when(planillaRepository.findActivasByAnioMesAndSegmentoId(2026, 1, 1L))
                .thenReturn(List.of(planilla(500L, 50L, "RECHAZADO", false)));

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 1);
        TableroControlResponse.TableroFilaDto fila = filaUnica(response);

        assertEquals("ARCHIVO_CARGADO",   fila.getEstadoCargadoColgaap());
        assertEquals("ARCHIVO_RECHAZADO", fila.getEstadoAprobadoColgaap());
        assertEquals("PENDIENTE_CARGA",   fila.getEstadoCargadoFullIfrs());
    }

    // ============================== Estado RECHAZO_SIN_DATOS ==============================

    @Test
    void deberiaRetornarSinDatosYRechazoSinDatosCuandoPlanillaRechazadaConNoReportaDatos() {
        // noReportaDatos=true + RECHAZADO → certificación "sin datos" rechazada
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(
                producto(40L, "Tarjeta de crédito", 1L),
                producto(41L, "Tarjeta de crédito", 2L)
        ));
        when(planillaRepository.findActivasByAnioMesAndSegmentoId(2026, 2, 1L))
                .thenReturn(List.of(planilla(400L, 40L, "RECHAZADO", true)));

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 2);
        TableroControlResponse.TableroFilaDto fila = filaUnica(response);

        assertEquals("SIN_DATOS",        fila.getEstadoCargadoColgaap());
        assertEquals("RECHAZO_SIN_DATOS", fila.getEstadoAprobadoColgaap());
    }

    // ============================== Regla NO_APLICA Colgaap (Recaudos y Seguridad) ==============================

    @Test
    void deberiaAsignarNoAplicaEnColgaapParaRecaudosYSeguridadConEstadoNormalEnFullIfrs() {
        // Recaudos y Seguridad nunca aplican en segmento Colgaap/Modificado
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(
                producto(60L, "Recaudos",  1L),
                producto(61L, "Recaudos",  2L),
                producto(62L, "Seguridad", 1L),
                producto(63L, "Seguridad", 2L)
        ));

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 6);

        assertEquals(2, response.getFilas().size());
        for (TableroControlResponse.TableroFilaDto fila : response.getFilas()) {
            String n = fila.getNombreProducto();
            assertEquals("NO_APLICA",           fila.getEstadoCargadoColgaap(),  n + ": estadoCargadoColgaap");
            assertEquals("NO_APLICA",           fila.getEstadoAprobadoColgaap(), n + ": estadoAprobadoColgaap");
            assertEquals("PENDIENTE_CARGA",      fila.getEstadoCargadoFullIfrs(),  n + ": estadoCargadoFullIfrs");
            assertEquals("PENDIENTE_APROBACION", fila.getEstadoAprobadoFullIfrs(), n + ": estadoAprobadoFullIfrs");
        }
    }

    // ============================== Regla NO_APLICA Full IFRS (Tipz) ==============================

    @Test
    void deberiaAsignarNoAplicaEnFullIfrsParaTipzConEstadoNormalEnColgaap() {
        // Tipz nunca aplica en segmento Full IFRS; Colgaap sí aplica
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(
                producto(70L, "Tipz", 1L),
                producto(71L, "Tipz", 2L)
        ));
        when(planillaRepository.findActivasByAnioMesAndSegmentoId(2026, 6, 1L))
                .thenReturn(List.of(planilla(700L, 70L, "APROBADO", false)));

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 6);
        TableroControlResponse.TableroFilaDto fila = filaUnica(response);

        assertEquals("ARCHIVO_CARGADO",  fila.getEstadoCargadoColgaap());
        assertEquals("ARCHIVO_APROBADO", fila.getEstadoAprobadoColgaap());
        assertEquals("NO_APLICA",        fila.getEstadoCargadoFullIfrs());
        assertEquals("NO_APLICA",        fila.getEstadoAprobadoFullIfrs());
    }

    // ============================== Deduplicación por casing ==============================

    @Test
    void deberiaAgruparProductosConTitulosDeCasingDistintoEnUnaFilaSinDuplicados() {
        // Bug reportado: "Acuerdos Conjuntos" y "Acuerdos conjuntos" generaban 2 filas.
        // El agrupamiento case-insensitive debe resolverlas en 1 fila con ambos segmentos.
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(
                producto(80L, "Acuerdos Conjuntos", 1L),
                producto(81L, "Acuerdos conjuntos", 2L)
        ));
        when(planillaRepository.findActivasByAnioMesAndSegmentoId(2026, 6, 1L))
                .thenReturn(List.of(planilla(800L, 80L, "APROBADO",  false)));
        when(planillaRepository.findActivasByAnioMesAndSegmentoId(2026, 6, 2L))
                .thenReturn(List.of(planilla(801L, 81L, "PENDIENTE", false)));

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 6);

        assertEquals(1, response.getFilas().size(), "Casing distinto en BD no debe generar filas duplicadas");
        TableroControlResponse.TableroFilaDto fila = response.getFilas().get(0);
        assertEquals("ARCHIVO_CARGADO",      fila.getEstadoCargadoColgaap());
        assertEquals("ARCHIVO_APROBADO",     fila.getEstadoAprobadoColgaap());
        assertEquals("ARCHIVO_CARGADO",      fila.getEstadoCargadoFullIfrs());
        assertEquals("PENDIENTE_APROBACION", fila.getEstadoAprobadoFullIfrs());
    }

    // ============================== Periodos disponibles ==============================

    @Test
    void deberiaIncluirPeriodoActualEnPeriodosDisponiblesAunSinPlanillasHistoricas() {
        // Sin planillas históricas el período solicitado debe aparecer igual en el selector
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of());

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 6);

        assertFalse(response.getPeriodosDisponibles().isEmpty(), "Debe haber al menos el período solicitado");
        TableroControlResponse.PeriodoAnualDto anio = response.getPeriodosDisponibles().get(0);
        assertEquals(2026, anio.getAnio());
        assertTrue(anio.getMeses().stream().anyMatch(m -> m.getNumero() == 6),
                "El mes 6 debe estar presente en los meses disponibles");
    }

    @Test
    void deberiaGenerarEtiquetaDePeriodoCorrectaConNombreMesYAnio() {
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of());

        TableroControlResponse response = useCase.obtenerTableroControl(2026, 6);

        assertEquals("Junio 2026", response.getPeriodoEtiqueta());
        assertEquals(2026, response.getAnioSeleccionado());
        assertEquals(6,    response.getMesSeleccionado());
    }
}