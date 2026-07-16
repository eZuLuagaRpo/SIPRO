package com.bancolombia.sipro.validations.shared.utils;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica que el lector streaming conserve columnas vacías entre celdas dispersas.
 */
class XlsxStreamingReaderTest {

    @Test
    void shouldPreserveSparseColumnsWhenReadingFirstSheet() throws Exception {
        byte[] workbookBytes = buildWorkbook();
        List<List<String>> rows = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();

        XlsxStreamingReader.readFirstSheet(new ByteArrayInputStream(workbookBytes), (rowNumber, rowValues) -> {
            rowNumbers.add(rowNumber);
            rows.add(rowValues);
        });

        assertEquals(List.of(1, 2), rowNumbers);
        assertEquals(List.of("PRODUCTO", "", "MONEDA"), rows.get(0));
        assertEquals(List.of("CREDITO", "", "0"), rows.get(1));
    }

    /**
     * Genera un XLSX con columnas salteadas para probar el caso de celdas dispersas.
     */
    private byte[] buildWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("DATOS");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("PRODUCTO");
            header.createCell(2).setCellValue("MONEDA");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("CREDITO");
            row.createCell(2).setCellValue("0");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}