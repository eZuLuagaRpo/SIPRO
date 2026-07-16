package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidadoRegistroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * Prueba la reconstrucción del archivo CREFFSOS y su publicación en storage compartido.
 */
class CreffosConsolidationServiceTest {

    @Mock
    private SiproDetalleConsolidadoRegistroRepository consolidadoRegistroRepository;

    @Mock
    private CreffosParametricGenerator creffosParametricGenerator;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ParametroUnicoService parametroUnicoService;

    @Mock
    private ArchivosBloqueadosService archivosBloqueadosService;

        @Mock
        private FullIfrsBloqueadosConsolidacionService fullIfrsBloqueadosConsolidacionService;

    @Mock
    private ConciliacionArchivosBloqueadosService conciliacionArchivosBloqueadosService;

    private CreffosConsolidationService service;

    @BeforeEach
    void setUp() {
        service = new CreffosConsolidationService(
                consolidadoRegistroRepository,
                creffosParametricGenerator,
                fileStorageService,
                parametroUnicoService,
                archivosBloqueadosService,
                                fullIfrsBloqueadosConsolidacionService,
                conciliacionArchivosBloqueadosService
        );
    }

    @Test
    void deberiaGuardarArchivoEnStorageYPublicarRutaCompartida(@TempDir Path tempDir) throws Exception {
        LocalDate fechaCorte = LocalDate.of(2026, 3, 31);
        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setIdConsolidadoRegistro(1L);
        List<SiproDetalleConsolidadoRegistro> registros = List.of(registro);
        byte[] contenido = "contenido-creffsos".getBytes(StandardCharsets.UTF_8);

        when(consolidadoRegistroRepository.findByFechaCorteOrderByIdConsolidadoRegistroAsc(fechaCorte))
                .thenReturn(registros);
        when(creffosParametricGenerator.generate(fechaCorte, registros))
                .thenReturn(new CreffosParametricGenerator.GeneratedCreffosFile(
                        "CREFFSOS.csv",
                        "text/csv; charset=UTF-8",
                        contenido,
                        1,
                        "CSV",
                        "CREFFSOS.xlsx",
                        contenido
                ));
        when(parametroUnicoService.getString("CREFFSOS_RUTA_SALIDA", "")).thenReturn(tempDir.toString());

        CreffosConsolidationService.PublicationResult result = service.reconstruirCompleto(fechaCorte);

        verify(fileStorageService).storeBytes(
                eq(contenido),
                eq("consolidados/2026-03-31/CREFFSOS.csv"),
                eq("text/csv; charset=UTF-8")
        );
        assertTrue(result.generated());
        assertNull(result.sharedCopyWarning());

        assertEquals(
                "contenido-creffsos",
                Files.readString(tempDir.resolve("CREFFSOS.csv"), StandardCharsets.UTF_8)
        );
    }

    @Test
        void noDeberiaGenerarArchivoCuandoNoExistenRegistrosConsolidados() throws Exception {
        LocalDate fechaCorte = LocalDate.of(2026, 3, 31);
        when(consolidadoRegistroRepository.findByFechaCorteOrderByIdConsolidadoRegistroAsc(fechaCorte))
                .thenReturn(List.of());

                CreffosConsolidationService.PublicationResult result = service.reconstruirCompleto(fechaCorte);

        verify(creffosParametricGenerator, never()).generate(any(), any());
        verify(fileStorageService, never()).storeBytes(any(), any(), any());
                assertFalse(result.generated());
            }

            @Test
            void deberiaRetornarAdvertenciaCuandoRutaCompartidaNoEsAccesible(@TempDir Path tempDir) throws Exception {
                LocalDate fechaCorte = LocalDate.of(2026, 3, 31);
                SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
                registro.setIdConsolidadoRegistro(2L);
                List<SiproDetalleConsolidadoRegistro> registros = List.of(registro);
                byte[] contenido = "contenido-creffsos".getBytes(StandardCharsets.UTF_8);
                Path invalidTarget = Files.createTempFile(tempDir, "creffsos-share", ".tmp");

                when(consolidadoRegistroRepository.findByFechaCorteOrderByIdConsolidadoRegistroAsc(fechaCorte))
                        .thenReturn(registros);
                when(creffosParametricGenerator.generate(fechaCorte, registros))
                        .thenReturn(new CreffosParametricGenerator.GeneratedCreffosFile(
                                "CREFFSOS.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                contenido,
                                1,
                                "XLSX",
                                "CREFFSOS.xlsx",
                                contenido
                        ));
                when(parametroUnicoService.getString("CREFFSOS_RUTA_SALIDA", "")).thenReturn(invalidTarget.toString());

                CreffosConsolidationService.PublicationResult result = service.reconstruirCompleto(fechaCorte);

                assertTrue(result.generated());
                assertNotNull(result.sharedCopyWarning());
                assertTrue(result.sharedCopyWarning().contains("No se pudo copiar CREFFSOS a ruta compartida"));
    }
}