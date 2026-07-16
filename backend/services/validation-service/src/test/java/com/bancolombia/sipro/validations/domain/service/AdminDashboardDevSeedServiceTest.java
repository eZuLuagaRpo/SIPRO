package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.AdminDashboardResponse;
import com.bancolombia.sipro.validations.domain.model.Producto;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.UsuarioPersona;
import com.bancolombia.sipro.validations.infrastructure.repository.ProductoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioPersonaRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardDevSeedServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private SiproDetalleCargaPlanillasRepository planillaRepository;

    @Mock
    private UsuarioPersonaRepository usuarioPersonaRepository;

    @Mock
    private ProductoRepository productoRepository;

    private AdminDashboardDevSeedService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardDevSeedService(
                new TestFileStorageService(tempDir),
                planillaRepository,
                usuarioPersonaRepository,
                productoRepository);
    }

    @Test
    void deberiaCompletarPeriodosDummyEnOrdenDescendente() {
        List<LocalDate> periodos = service.completarPeriodosDisponibles(List.of(LocalDate.of(2026, 2, 28)));

        assertEquals(List.of(
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 2, 28)
        ), periodos);
    }

    @Test
    void deberiaSembrarArchivosAConsolidarEnRutaAprobados() throws Exception {
        List<AdminDashboardResponse.ArchivoEstado> archivos =
                service.completarArchivosAConsolidar(LocalDate.of(2026, 5, 31), List.of());

        assertEquals(5, archivos.size());

        Path archivoEsperado = tempDir.resolve("aprobados/2026-05-31/SIM_APROBADO_2026-05-31_01.xlsx");
        assertTrue(Files.exists(archivoEsperado));
        assertWorkbookValido(archivoEsperado);
    }

    @Test
    void deberiaSembrarArchivosConsolidadosEnRutaConsolidados() throws Exception {
        List<AdminDashboardResponse.ArchivoEstado> archivos =
                service.completarArchivosConsolidados(LocalDate.of(2026, 4, 30), List.of());

        assertEquals(5, archivos.size());

        Path archivoEsperado = tempDir.resolve("consolidados/2026-04-30/SIM_CONSOLIDADO_2026-04-30_01.xlsx");
        assertTrue(Files.exists(archivoEsperado));
        assertWorkbookValido(archivoEsperado);
    }

    @Test
    void deberiaSembrarPlanillasDummyConUsuarioYProductoBase() {
        UsuarioPersona usuario = new UsuarioPersona();
        usuario.setIdUsuario(3L);
        usuario.setNombres("Junior");
        usuario.setApellidos("Ortiz");
        usuario.setCorreo("junior@bancolombia.com");
        usuario.setUsuario("junortiz");

        Producto producto = new Producto();
        producto.setIdProducto(11L);
        producto.setTitulo("Producto demo");

        when(usuarioPersonaRepository.findAll()).thenReturn(List.of(usuario));
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of(producto));
        when(planillaRepository.findByFechaCorteInformacionAndEstadoPlanillaAndActivoTrue(
                LocalDate.of(2026, 5, 31), "APROBADO")).thenReturn(List.of());
        when(planillaRepository.findByFechaCorteInformacionAndEstadoPlanillaAndActivoTrue(
                LocalDate.of(2026, 4, 30), "APROBADO")).thenReturn(List.of());
        when(planillaRepository.findByFechaCorteInformacionAndEstadoPlanillaAndActivoTrue(
                LocalDate.of(2026, 3, 31), "APROBADO")).thenReturn(List.of());

        service.ensureDemoData();

        verify(planillaRepository, times(3)).saveAll(anyList());
    }

    @Test
    void noDeberiaRomperSiNoExisteUsuarioOProductoBase() {
        when(usuarioPersonaRepository.findAll()).thenReturn(List.of());
        when(productoRepository.findAllByOrderByTituloAsc()).thenReturn(List.of());

        service.ensureDemoData();

        verify(planillaRepository, never()).saveAll(anyList());
    }

    @Test
    void deberiaInactivarPlanillasYEliminarArchivosDummyCuandoSePurgan() throws Exception {
        service.completarArchivosAConsolidar(LocalDate.of(2026, 5, 31), List.of());
        service.completarArchivosConsolidados(LocalDate.of(2026, 5, 31), List.of());

        SiproDetalleCargaPlanillas planillaDummy = new SiproDetalleCargaPlanillas();
        planillaDummy.setActivo(true);
        planillaDummy.setArchivoUid("SIM-2026-05-31-1");
        planillaDummy.setRutaArchivoAlmacenamiento("aprobados/2026-05-31/SIM_APROBADO_2026-05-31_01.xlsx");

        when(planillaRepository.findByActivoTrueAndArchivoUidStartingWith("SIM-"))
                .thenReturn(List.of(planillaDummy));

        service.purgeDemoData();

        assertFalse(planillaDummy.getActivo());
        assertNotNull(planillaDummy.getFechaInactivacion());
        assertFalse(Files.exists(tempDir.resolve("aprobados/2026-05-31/SIM_APROBADO_2026-05-31_01.xlsx")));
        assertFalse(Files.exists(tempDir.resolve("consolidados/2026-05-31/SIM_CONSOLIDADO_2026-05-31_01.xlsx")));
        verify(planillaRepository).saveAll(anyList());
    }

    private void assertWorkbookValido(Path archivo) throws IOException {
        try (InputStream inputStream = Files.newInputStream(archivo); XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Datos", workbook.getSheetName(0));
        }
    }

    private static final class TestFileStorageService implements FileStorageService {

        private final Path baseDir;

        private TestFileStorageService(Path baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public String store(MultipartFile file, String subDirectory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String store(MultipartFile file, String subDirectory, String customFileName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(String path) {
            try {
                return Files.deleteIfExists(getAbsolutePath(path));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String move(String currentPath, String newSubDirectory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path getAbsolutePath(String relativePath) {
            return baseDir.resolve(relativePath.replace("/", java.io.File.separator)).normalize();
        }

        @Override
        public InputStream openStream(String relativePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getFileAsBytes(String relativePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String storeBytes(byte[] content, String key, String contentType) throws IOException {
            Path path = getAbsolutePath(key);
            Files.createDirectories(path.getParent());
            Files.write(path, content);
            return key;
        }

        @Override
        public List<String> listObjects(String prefix) {
            throw new UnsupportedOperationException();
        }
    }
}