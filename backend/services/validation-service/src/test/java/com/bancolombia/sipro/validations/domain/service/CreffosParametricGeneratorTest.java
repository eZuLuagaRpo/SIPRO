package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.CreffosColumnDefinition;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import com.bancolombia.sipro.validations.infrastructure.lz.LzJdbcService;
import com.bancolombia.sipro.validations.infrastructure.repository.CreffosParametroColumnasRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * Comprueba la generación paramétrica de archivos CREFFSOS en CSV y XLSX.
 */
class CreffosParametricGeneratorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private CreffosParametroColumnasRepository parametroColumnasRepository;

    @Mock
    private ParametroUnicoService parametroUnicoService;

    @Mock
    private LzJdbcService lzJdbcService;

    private CreffosParametricGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CreffosParametricGenerator(parametroColumnasRepository, parametroUnicoService, lzJdbcService);
    }

    @Test
    void deberiaGenerarCsvParametricoYPersistirConsecutivo() {
        List<CreffosColumnDefinition> definiciones = List.of(
                definition("NIT", 1, "INTEGER", "DIRECTO", "copiarDirecto", null,
                        Map.of("sourceField", "nit")),
                definition("DOCUMENTO", 2, "INTEGER", "SECUENCIA", "resolverDocumentoConsecutivo", null,
                        Map.of(
                                "sourceField", "documento",
                                "useSequenceWhenNullOrZero", true,
                                "initialSequenceParam", "CREFFSOS_DOCUMENTO_CONSECUTIVO_INICIAL",
                                "currentSequenceParam", "CREFFSOS_DOCUMENTO_CONSECUTIVO_ACTUAL",
                                "increment", 1
                        )),
                definition("INDICUSU1", 3, "STRING", "CONSTANTE", "asignarConstante", "   ",
                        Map.of("value", "   ")),
                definition("CTAPUC", 4, "INTEGER", "LOOKUP", "resolverCuentaBankvision", null,
                        Map.of(
                                "sourceField", "ctapuc",
                                "lookupTable", "public.sipro_parametros_homologacion_colgaap",
                                "lookupKey", "cuenta_sap",
                                "lookupValue", "cuenta_bv",
                                "ifNotFound", "NULL"
                        )),
                definition("CLASIFCPUC", 5, "INTEGER", "REGLA", "resolverClasificacionCpuc", null,
                        Map.of(
                                "modalidadHip", "HIP",
                                "modalidadDsc", "DSC",
                                "tiposClasificacionUno", List.of("FS003", "FS007", "FS008"),
                                "defaultValue", 2
                        )),
                definition("CALIFICPUC", 6, "STRING", "LOOKUP", "resolverCalificacionDesdeCenie", "A",
                        Map.of(
                                "sourceField", "nit",
                                "defaultValue", "A"
                        ))
        );

        when(parametroColumnasRepository.findActiveDefinitions()).thenReturn(definiciones);
        when(parametroUnicoService.getString("CREFFSOS_FORMATO_SALIDA", "XLSX")).thenReturn("csv");
        when(parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", "CREFFSOS.csv"))
                .thenReturn("salida_parametrica");
        when(parametroUnicoService.getString("CREFFSOS_INCLUIR_ENCABEZADO", "true")).thenReturn("false");
        when(parametroUnicoService.getString("CREFFSOS_HOJA_XLSX", "CREFFSOS")).thenReturn("CREFFSOS");
        when(parametroUnicoService.reserveSequenceRange(
                "CREFFSOS_DOCUMENTO_CONSECUTIVO_ACTUAL",
                "CREFFSOS_DOCUMENTO_CONSECUTIVO_INICIAL",
                1,
                1
        )).thenReturn(new ParametroUnicoService.SequenceReservation(true, 101L, 101L, 1));
        when(parametroColumnasRepository.findLookupValues(
                org.mockito.ArgumentMatchers.eq("public.sipro_parametros_homologacion_colgaap"),
                org.mockito.ArgumentMatchers.eq("cuenta_sap"),
                org.mockito.ArgumentMatchers.eq("cuenta_bv"),
                argThat((Collection<String> keys) -> keys.size() == 1 && keys.contains("123"))
        )).thenReturn(Map.of("123", "456"));
        when(lzJdbcService.isEnabled()).thenReturn(false);

        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setNit(900L);
        registro.setDocumento(null);
        registro.setCtapuc(123L);
        registro.setModalidad("HIP");
        registro.setTipoId("FS003");
        registro.setSaldo(new BigDecimal("10.50"));

        CreffosParametricGenerator.GeneratedCreffosFile generated = generator.generate(
                LocalDate.of(2026, 3, 31),
                List.of(registro)
        );

        assertEquals("salida_parametrica.csv", generated.fileName());
        assertEquals("text/csv; charset=UTF-8", generated.contentType());
        assertEquals("CSV", generated.format());
        assertEquals(1, generated.rowCount());

        String content = new String(generated.content(), StandardCharsets.UTF_8).stripTrailing();
        assertEquals("900;101;   ;456;3;A", content);
        verify(parametroUnicoService).reserveSequenceRange(
                "CREFFSOS_DOCUMENTO_CONSECUTIVO_ACTUAL",
                "CREFFSOS_DOCUMENTO_CONSECUTIVO_INICIAL",
                1,
                1
        );
    }

    @Test
    void deberiaConsultarCeniePriorizandoUltimaIngestionCuandoLaReglaLoPide() {
        List<CreffosColumnDefinition> definiciones = List.of(
                definition("DOCUMENTO", 1, "INTEGER", "DIRECTO", "copiarDirecto", null,
                        Map.of("sourceField", "documento")),
                definition("CLASEGTIA", 2, "STRING", "LOOKUP", "resolverClaseGarantiaDesdeCenie", "N",
                        Map.of(
                                "sourceField", "documento",
                                "lookupSchema", "s_productos",
                                "lookupTable", "bvnc_visionry_cenie",
                                "lookupKey", "ceac21",
                                "lookupValue", "cein21",
                                "defaultValue", "N",
                                "filters", Map.of(
                                        "cest21NotIn", List.of("01", "02"),
                                        "cetr21", 1,
                                        "ceap21Trim", "C",
                                        "ultimaFechaIngestion", true,
                                        "latestOrderBy", List.of("period_year", "period_month", "ingestion_run_id")
                                )
                        ))
        );

        when(parametroColumnasRepository.findActiveDefinitions()).thenReturn(definiciones);
        when(parametroUnicoService.getString("CREFFSOS_FORMATO_SALIDA", "XLSX")).thenReturn("csv");
        when(parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", "CREFFSOS.csv"))
                .thenReturn("cenie.csv");
        when(parametroUnicoService.getString("CREFFSOS_INCLUIR_ENCABEZADO", "true")).thenReturn("false");
        when(parametroUnicoService.getString("CREFFSOS_HOJA_XLSX", "CREFFSOS")).thenReturn("CREFFSOS");
        when(lzJdbcService.isEnabled()).thenReturn(true);
        when(lzJdbcService.executeQuery(argThat(sql -> sql.contains("ROW_NUMBER() OVER")
                && sql.contains("period_year DESC")
                && sql.contains("ingestion_run_id DESC"))))
                .thenReturn(List.of(Map.of("lookup_key", "456", "lookup_value", "G")));

        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setDocumento(456L);

        CreffosParametricGenerator.GeneratedCreffosFile generated = generator.generate(
                LocalDate.of(2026, 3, 31),
                List.of(registro)
        );

        String content = new String(generated.content(), StandardCharsets.UTF_8).stripTrailing();
        assertEquals("456;G", content);
    }

    @Test
    void deberiaGenerarXlsxConEncabezadoCuandoFormatoConfiguradoNoEsValido() throws Exception {
        List<CreffosColumnDefinition> definiciones = List.of(
                definition("NIT", 1, "INTEGER", "DIRECTO", "copiarDirecto", null,
                        Map.of("sourceField", "nit")),
                definition("MONEDA", 2, "INTEGER", "DIRECTO", "copiarDirecto", null,
                        Map.of("sourceField", "moneda"))
        );

        when(parametroColumnasRepository.findActiveDefinitions()).thenReturn(definiciones);
        when(parametroUnicoService.getString("CREFFSOS_FORMATO_SALIDA", "XLSX")).thenReturn("json");
        when(parametroUnicoService.getString("CREFFSOS_NOMBRE_ARCHIVO_SALIDA", "CREFFSOS.xlsx"))
                .thenReturn("reporte_final.tmp");
        when(parametroUnicoService.getString("CREFFSOS_INCLUIR_ENCABEZADO", "true")).thenReturn("true");
        when(parametroUnicoService.getString("CREFFSOS_HOJA_XLSX", "CREFFSOS")).thenReturn("SALIDA");

        SiproDetalleConsolidadoRegistro registro = new SiproDetalleConsolidadoRegistro();
        registro.setNit(77L);
        registro.setMoneda(1);

        CreffosParametricGenerator.GeneratedCreffosFile generated = generator.generate(
                LocalDate.of(2026, 3, 31),
                List.of(registro)
        );

        assertEquals("reporte_final.xlsx", generated.fileName());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", generated.contentType());
        assertEquals("XLSX", generated.format());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(generated.content()))) {
            assertNotNull(workbook.getSheet("SALIDA"));
            assertEquals("NIT", workbook.getSheet("SALIDA").getRow(0).getCell(0).getStringCellValue());
            assertEquals("MONEDA", workbook.getSheet("SALIDA").getRow(0).getCell(1).getStringCellValue());
            assertEquals("77", workbook.getSheet("SALIDA").getRow(1).getCell(0).getStringCellValue());
            assertEquals("1", workbook.getSheet("SALIDA").getRow(1).getCell(1).getStringCellValue());
        }

        assertTrue(generated.content().length > 0);
    }

        /**
         * Crea una definición paramétrica acotada para simular columnas de salida en los tests.
         */
    private CreffosColumnDefinition definition(String nombre,
                                               int orden,
                                               String tipoDato,
                                               String origenDato,
                                               String funcionJava,
                                               String valorConstante,
                                               Map<String, Object> parametros) {
        return new CreffosColumnDefinition(
                nombre,
                orden,
                tipoDato,
                null,
                null,
                null,
                null,
                origenDato,
                null,
                null,
                null,
                valorConstante,
                funcionJava,
                null,
                OBJECT_MAPPER.valueToTree(parametros),
                true,
                true,
                true,
                (short) 1,
                "",
                ""
        );
    }
}