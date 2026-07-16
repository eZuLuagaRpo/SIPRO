package com.bancolombia.sipro.validations.application.usecase;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.domain.service.PlanillaNotificationService;
import com.bancolombia.sipro.validations.domain.service.ExcelMetadataService;
import com.bancolombia.sipro.validations.domain.service.VentanaCargaService;
import com.bancolombia.sipro.validations.domain.service.ConsolidacionPlanillasService;
import com.bancolombia.sipro.validations.domain.service.FileStorageService;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanillaUseCaseCopyToSharedFolderTest {

    @TempDir
    Path tempDir;

    // Mocks necesarios por el constructor (seguimos el patrón del repo)
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
    void copiarArchivosFullIfrs_aprobado_debeEscribirEnRaizCarpetaCompartida() throws Exception {
        // Preparación
        String rutaXlsx = "aprobados/2026-06-30/uuid__IFRS_20260630_PROD.xlsx";
        String rutaCtrl  = "aprobados/2026-06-30/uuid__CTRL_IFRS_20260630.txt";
        String nombreArchivo = "IFRS_20260630_PROD.xlsx";

        byte[] xlsxBytes = "contenido-xlsx-simulado".getBytes(StandardCharsets.UTF_8);
        byte[] ctrlBytes  = "0".getBytes(StandardCharsets.UTF_8);

        // Parametro apunta a la carpeta temporal (raíz)
        when(parametroUnicoService.getString("IFRS_PLANILLAS_RUTA_SALIDA", "")).thenReturn(tempDir.toString());

        // fileStorageService devuelve los bytes que deben terminar escritos en la carpeta compartida
        when(fileStorageService.getFileAsBytes(rutaXlsx)).thenReturn(xlsxBytes);
        when(fileStorageService.getFileAsBytes(rutaCtrl)).thenReturn(ctrlBytes);

        // Invocar el método privado copiarACarpertaCompartidaFullIfrs por reflexión (firma actual: 3 params)
        Method copiarMethod = PlanillaUseCase.class.getDeclaredMethod(
                "copiarACarpertaCompartidaFullIfrs", String.class, String.class, String.class);
        copiarMethod.setAccessible(true);

        copiarMethod.invoke(useCase, rutaXlsx, rutaCtrl, nombreArchivo);

        // Verificaciones: los archivos deben existir en la raíz de la carpeta compartida (tempDir) con el contenido esperado.
        Path targetDir = tempDir; // ahora la raíz
        assertTrue(Files.exists(targetDir), "La carpeta compartida raíz debería existir (tempDir)");

        Path targetXlsx = targetDir.resolve(nombreArchivo);
        assertTrue(Files.exists(targetXlsx), "El xlsx aprobado debe haberse copiado con el nombre esperado en la raíz");
        assertArrayEquals(xlsxBytes, Files.readAllBytes(targetXlsx));

        // El archivo de control usa extraerNombreLimpio(rutaCtrl) para decidir el nombre
        // con la ruta "uuid__CTRL_IFRS_20260630.txt" el nombre final será "CTRL_IFRS_20260630.txt"
        Path targetCtrl = targetDir.resolve("CTRL_IFRS_20260630.txt");
        assertTrue(Files.exists(targetCtrl), "El control aprobado debe haberse copiado con el nombre extraído en la raíz");
        assertArrayEquals(ctrlBytes, Files.readAllBytes(targetCtrl));
    }
}