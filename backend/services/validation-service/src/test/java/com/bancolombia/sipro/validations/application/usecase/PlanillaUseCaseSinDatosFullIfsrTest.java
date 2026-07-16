package com.bancolombia.sipro.validations.application.usecase;

import com.bancolombia.sipro.validations.application.dto.SolicitudAprobacionRequest;
import com.bancolombia.sipro.validations.application.dto.VentanaCargaResponse;
import com.bancolombia.sipro.validations.domain.model.Producto;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.UsuarioLogin;
import com.bancolombia.sipro.validations.domain.service.PlanillaNotificationService;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioLoginRepository;
import com.bancolombia.sipro.validations.service.LoteMemoryStore;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanillaUseCaseSinDatosFullIfrsTest {

    @Mock private SiproDetalleCargaPlanillasRepository planillaRepository;
    @Mock private com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository validacionRepository;
    @Mock private com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleRechazosPlanillaRepository rechazoRepository;
    @Mock private com.bancolombia.sipro.validations.infrastructure.repository.SiproUsuarioProductoRolRepository uprRepository;
    @Mock private UsuarioLoginRepository usuarioLoginRepository;
    @Mock private com.bancolombia.sipro.validations.domain.service.FileStorageService fileStorageService;
    @Mock private com.bancolombia.sipro.validations.domain.service.ExcelMetadataService excelMetadataService;
    @Mock private com.bancolombia.sipro.validations.domain.service.VentanaCargaService ventanaCargaService;
    @Mock private com.bancolombia.sipro.validations.domain.service.ConsolidacionPlanillasService consolidacionPlanillasService;
    @Mock private PlanillaNotificationService planillaNotificationService;
    @Mock private LoteMemoryStore loteMemoryStore;
    @Mock private com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository productoRepository;
    @Mock private com.bancolombia.sipro.validations.infrastructure.repository.SegmentoRepository segmentoRepository;
    @Mock private ParametroUnicoService parametroUnicoService;

    private PlanillaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PlanillaUseCase(
                planillaRepository,
                validacionRepository,
                rechazoRepository,
                uprRepository,
                usuarioLoginRepository,
                fileStorageService,
                excelMetadataService,
                ventanaCargaService,
                consolidacionPlanillasService,
                planillaNotificationService,
                loteMemoryStore,
                productoRepository,
                segmentoRepository,
                parametroUnicoService
        );
    }

    @Test
    void solicitarAprobacion_sinDatos_fullIfrs_creaXlsxYCtrlYGuardaPlanilla() throws Exception {
        // Preparar request real
        SolicitudAprobacionRequest request = new SolicitudAprobacionRequest();
        request.setSinDatos(Boolean.TRUE);
        request.setIdProducto(1L);
        LocalDate fechaCorte = LocalDate.of(2026, 6, 26);
        request.setFechaCorte(fechaCorte);
        request.setUsuario("usuario_test");
        request.setSegmento("Full IFRS");

        // Producto con segmento Full IFRS (idSegmento = 2) y patrón de nombre con AAAAMMDD
        Producto producto = new Producto();
        producto.setIdProducto(1L);
        producto.setIdSegmento(2L);
        producto.setNombreArchivoPermitido("IFRS_AAAAMMDD_PRUEBA");
        producto.setNombreControlPermitido("CTRL_IFRS_AAAAMMDD");
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        // Ventana válida
        VentanaCargaResponse ventana = new VentanaCargaResponse();
        ventana.setDentroDeVentana(true);
        when(ventanaCargaService.validarVentana(fechaCorte)).thenReturn(ventana);

        // Usuario existente (instancia real)
        UsuarioLogin usuario = new UsuarioLogin();
        usuario.setIdUsuario(42L);
        usuario.setUsuario("usuario_test");
        when(usuarioLoginRepository.findByUsuario("usuario_test")).thenReturn(Optional.of(usuario));

        // No hay planilla anterior
        when(planillaRepository.findActiveByFechaCorteAndIdProductoForUpdate(any(), anyLong())).thenReturn(Optional.empty());

        // Mock fileStorageService.storeBytes para devolver rutas fake
        when(fileStorageService.storeBytes(any(byte[].class), contains("pendientes/"), eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .thenReturn("pendientes/2026-06-26/uuid__IFRS_20260626_PRUEBA.xlsx");
        when(fileStorageService.storeBytes(any(byte[].class), contains("pendientes/"), eq("text/plain")))
                .thenReturn("pendientes/2026-06-26/uuid__CTRL_IFRS_20260626.txt");

        // Simular saveAndFlush asignando id
        when(planillaRepository.saveAndFlush(Mockito.any(SiproDetalleCargaPlanillas.class))).thenAnswer(invocation -> {
            SiproDetalleCargaPlanillas p = invocation.getArgument(0);
            p.setId(123L);
            return p;
        });

        // Ejecutar
        Map<String, String> resultado = useCase.solicitarAprobacion(request, null, null, "127.0.0.1");

        // Verificaciones: storeBytes invocado para xlsx y control, y contenido del ctrl = "0"
        ArgumentCaptor<byte[]> xlsxCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).storeBytes(xlsxCaptor.capture(), contains("pendientes/"), eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        byte[] xlsxBytes = xlsxCaptor.getValue();
        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0, "El xlsx generado no debe estar vacío");

        ArgumentCaptor<byte[]> ctrlCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).storeBytes(ctrlCaptor.capture(), contains("pendientes/"), eq("text/plain"));
        byte[] ctrlBytes = ctrlCaptor.getValue();
        assertArrayEquals("0".getBytes(), ctrlBytes, "El archivo control debe contener '0'");

        // Verificar entidad guardada
        ArgumentCaptor<SiproDetalleCargaPlanillas> planillaCaptor = ArgumentCaptor.forClass(SiproDetalleCargaPlanillas.class);
        verify(planillaRepository).saveAndFlush(planillaCaptor.capture());
        SiproDetalleCargaPlanillas planillaGuardada = planillaCaptor.getValue();

        assertTrue(Boolean.TRUE.equals(planillaGuardada.getNoReportaDatos()), "noReportaDatos debe ser true");
        assertEquals("pendientes/2026-06-26/uuid__IFRS_20260626_PRUEBA.xlsx", planillaGuardada.getRutaArchivoAlmacenamiento());
        assertEquals("pendientes/2026-06-26/uuid__CTRL_IFRS_20260626.txt", planillaGuardada.getRutaArchivoControl());

        String expectedFecha = fechaCorte.toString().replace("-", "");
        assertEquals("IFRS_" + expectedFecha + "_PRUEBA.xlsx", planillaGuardada.getNombreArchivoFuente());

        assertEquals((long) xlsxBytes.length, planillaGuardada.getPesoArchivoFuente());

        // Resultado contiene datos del lider (aunque no haya area asignada)
        assertNotNull(resultado);
        assertTrue(resultado.containsKey("nombreLider"));
        assertTrue(resultado.containsKey("correoLider"));
    }
}