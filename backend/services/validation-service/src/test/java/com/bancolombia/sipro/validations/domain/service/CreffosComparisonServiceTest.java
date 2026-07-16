package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.CreffosColumnDefinition;
import com.bancolombia.sipro.validations.infrastructure.repository.CreffosParametroColumnasRepository;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * Valida la comparación entre el consolidado interno y el archivo CREFFSOS publicado.
 */
class CreffosComparisonServiceTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ParametroUnicoService parametroUnicoService;

    @Mock
    private CreffosParametroColumnasRepository parametroColumnasRepository;

    private CreffosComparisonService service;

    @BeforeEach
    void setUp() {
        service = new CreffosComparisonService(fileStorageService, parametroUnicoService, parametroColumnasRepository);
    }

    @Test
    void deberiaCompararArchivoXlsxEnStreamingSinDiferencias() throws Exception {
        LocalDate fechaCorte = LocalDate.of(2026, 3, 31);
        String storageKey = "consolidados/2026-03-31/CREFFSOS.xlsx";

        when(parametroColumnasRepository.findActiveDefinitions()).thenReturn(buildDefinitions("NIT", "VLRINIOBL", "SALDO"));
        when(parametroUnicoService.getString("CREFFSOS_FORMATO_SALIDA", "XLSX")).thenReturn("XLSX");
        when(parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", "CREFFSOS.xlsx")).thenReturn("CREFFSOS.xlsx");
        when(parametroUnicoService.getString("CREFFSOS_INCLUIR_ENCABEZADO", "true")).thenReturn("true");
        when(fileStorageService.listObjects("consolidados/2026-03-31/")).thenReturn(List.of(storageKey));
        when(fileStorageService.openStream(storageKey)).thenReturn(new ByteArrayInputStream(buildWorkbookBytes()));

        CreffosComparisonService.ComparisonSnapshot snapshot = service.comparar(
                fechaCorte,
                2L,
                new BigDecimal("300.00")
        );

        assertTrue(snapshot.archivo().isEncontrado());
        assertEquals("CONSISTENTE", snapshot.archivo().getEstado());
        assertEquals(2L, snapshot.archivo().getCantidadRegistros());
        assertEquals(3, snapshot.archivo().getCantidadColumnasArchivo());
        assertEquals(0, snapshot.archivo().getTotalVlrIniObl().compareTo(new BigDecimal("300.00")));
        assertFalse(snapshot.tieneDiferencias());
    }

    @Test
    void deberiaCargarDetalleArchivoParaConciliacion() throws Exception {
        LocalDate fechaCorte = LocalDate.of(2026, 3, 31);
        String storageKey = "consolidados/2026-03-31/CREFFSOS.xlsx";

        when(parametroColumnasRepository.findActiveDefinitions()).thenReturn(buildDefinitions("NIT", "VLRINIOBL", "SALDO"));
        when(parametroUnicoService.getString("CREFFSOS_FORMATO_SALIDA", "XLSX")).thenReturn("XLSX");
        when(parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", "CREFFSOS.xlsx")).thenReturn("CREFFSOS.xlsx");
        when(parametroUnicoService.getString("CREFFSOS_INCLUIR_ENCABEZADO", "true")).thenReturn("true");
        when(fileStorageService.listObjects("consolidados/2026-03-31/")).thenReturn(List.of(storageKey));
        when(fileStorageService.openStream(storageKey)).thenReturn(new ByteArrayInputStream(buildWorkbookBytes()));

        CreffosComparisonService.DetailedCreffosFile detail = service.cargarDetalleArchivo(fechaCorte);

        assertTrue(detail.encontrado());
        assertEquals("OK", detail.estado());
        assertEquals(2L, detail.cantidadRegistros());
        assertEquals("9001", detail.rows().get(0).value("NIT"));
        assertEquals("100.00", detail.rows().get(0).value("VLRINIOBL"));
    }

    @Test
    void deberiaMarcarDiferenciasCuandoElCsvNoCoincide() throws Exception {
        LocalDate fechaCorte = LocalDate.of(2026, 3, 31);
        String storageKey = "consolidados/2026-03-31/CREFFSOS.csv";
        byte[] content = "9001;120.00\n9002;100.00\n".getBytes(StandardCharsets.UTF_8);

        when(parametroColumnasRepository.findActiveDefinitions()).thenReturn(buildDefinitions("NIT", "VLRINIOBL"));
        when(parametroUnicoService.getString("CREFFSOS_FORMATO_SALIDA", "XLSX")).thenReturn("CSV");
        when(parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", "CREFFSOS.csv")).thenReturn("CREFFSOS.csv");
        when(parametroUnicoService.getString("CREFFSOS_INCLUIR_ENCABEZADO", "true")).thenReturn("false");
        when(fileStorageService.listObjects("consolidados/2026-03-31/")).thenReturn(List.of(storageKey));
        when(fileStorageService.openStream(storageKey)).thenReturn(new ByteArrayInputStream(content));

        CreffosComparisonService.ComparisonSnapshot snapshot = service.comparar(
                fechaCorte,
                2L,
                new BigDecimal("300.00")
        );

        assertTrue(snapshot.archivo().isEncontrado());
        assertEquals("CON_DIFERENCIAS", snapshot.archivo().getEstado());
        assertTrue(snapshot.tieneDiferencias());
        assertEquals(0, snapshot.metricas().stream()
                .filter(metrica -> "REGISTROS".equals(metrica.getCodigo()))
                .findFirst()
                .orElseThrow()
                .getDiferencia()
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, snapshot.metricas().stream()
                .filter(metrica -> "VLRINIOBL".equals(metrica.getCodigo()))
                .findFirst()
                .orElseThrow()
                .getDiferencia()
            .compareTo(new BigDecimal("80.00")));
    }

    /**
     * Construye la definición mínima de columnas requerida por cada escenario de comparación.
     */
    private List<CreffosColumnDefinition> buildDefinitions(String... columns) {
        return java.util.stream.IntStream.range(0, columns.length)
                .mapToObj(index -> new CreffosColumnDefinition(
                        columns[index],
                        index + 1,
                        "STRING",
                        null,
                        null,
                        null,
                        null,
                        "DIRECTO",
                        null,
                        null,
                        null,
                        null,
                        "copiarDirecto",
                        null,
                        MissingNode.getInstance(),
                        true,
                        true,
                        true,
                        (short) 1,
                        null,
                        null
                ))
                .toList();
    }

    /**
     * Genera un XLSX pequeño para probar la lectura en streaming del archivo consolidado.
     */
    private byte[] buildWorkbookBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("CREFFSOS");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("NIT");
            header.createCell(1).setCellValue("VLRINIOBL");
            header.createCell(2).setCellValue("SALDO");

            Row firstRow = sheet.createRow(1);
            firstRow.createCell(0).setCellValue("9001");
            firstRow.createCell(1).setCellValue("100.00");
            firstRow.createCell(2).setCellValue("90.00");

            Row secondRow = sheet.createRow(2);
            secondRow.createCell(0).setCellValue("9002");
            secondRow.createCell(1).setCellValue("200.00");
            secondRow.createCell(2).setCellValue("180.00");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}