package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.AdminDeleteConsolidacionRequest;
import com.bancolombia.sipro.validations.application.dto.AdminDeleteConsolidacionResponse;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionArchivoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidadoRegistroRepository;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConsolidacionServiceTest {

    @Mock
    private SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;

    @Mock
    private SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository;

    @Mock
    private SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ParametroUnicoService parametroUnicoService;

    @Mock
    private NotificacionConsolidacionService notificacionConsolidacionService;

    @Mock
    private ArchivosBloqueadosService archivosBloqueadosService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private AdminConsolidacionService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new AdminConsolidacionService(
                consolidacionRepository,
                consolidacionArchivoRepository,
                consolidadoRegistroRepository,
                fileStorageService,
                parametroUnicoService,
                notificacionConsolidacionService,
                archivosBloqueadosService,
                transactionManager
        );
    }

    @Test
    void eliminarConsolidacionDebeNotificarAlFinalSinRomperLaRespuesta() {
        SiproDetalleConsolidacionesPlanillas cabecera = new SiproDetalleConsolidacionesPlanillas();
        cabecera.setIdConsolidacion(91L);
        cabecera.setPeriodoValoracion(LocalDate.of(2026, 5, 31));
        cabecera.setEstadoConsolidacion("COMPLETADO");
        cabecera.setCreadoPorId(3L);
        cabecera.setFechaHoraFin(OffsetDateTime.parse("2026-05-26T16:25:00-05:00"));

        SiproAuthenticatedUser principal = new SiproAuthenticatedUser(
                3L,
                "junortiz",
                "junortiz",
                "junortiz",
                "junortiz@bancolombia.com.co",
                null,
                Set.of(),
                false
        );

        when(consolidacionRepository.findById(91L)).thenReturn(Optional.of(cabecera));
        when(consolidadoRegistroRepository.deleteBatchByIdConsolidacion(91L, 10_000)).thenReturn(150, 0);
        when(consolidacionArchivoRepository.deleteByIdConsolidacion(91L)).thenReturn(2);
        when(fileStorageService.listObjects("consolidados/2026-05-31/")).thenReturn(List.of());
        when(fileStorageService.getAbsolutePath("consolidados/2026-05-31/")).thenReturn(Path.of("C:/tmp/consolidados/2026-05-31"));
        when(parametroUnicoService.getString("CREFFSOS_RUTA_SALIDA", "")).thenReturn("");
        doNothing().when(consolidacionRepository).delete(cabecera);
        doNothing().when(consolidacionRepository).flush();

        AdminDeleteConsolidacionResponse response = service.eliminarConsolidacion(
                91L,
                new AdminDeleteConsolidacionRequest("Error en datos base", "Eliminar"),
                principal);

        assertTrue(response.exito());
        assertEquals(150L, response.registrosEliminados());
        assertEquals(2, response.archivosEliminados());
        verify(notificacionConsolidacionService).notificarEliminacion(
                eq(cabecera),
                eq("Error en datos base"),
                eq(principal),
                eq(150L),
                eq(2),
                any(OffsetDateTime.class));
    }
}