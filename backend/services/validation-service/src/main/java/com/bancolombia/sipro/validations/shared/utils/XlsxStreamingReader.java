package com.bancolombia.sipro.validations.shared.utils;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.ParserConfigurationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidad para leer archivos XLSX grandes en streaming sin cargar toda la hoja en memoria.
 */
public final class XlsxStreamingReader {

    private XlsxStreamingReader() {
    }

    /**
     * Recibe cada fila leída de la primera hoja como una lista de valores ya formateados.
     */
    @FunctionalInterface
    public interface RowConsumer {
        void accept(int rowNumber, List<String> rowValues);
    }

    /**
     * Recorre la primera hoja del archivo y entrega cada fila al consumidor indicado.
     */
    public static void readFirstSheet(InputStream inputStream, RowConsumer rowConsumer) throws IOException {
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            ReadOnlySharedStringsTable sharedStrings = new ReadOnlySharedStringsTable(opcPackage);
            XSSFReader reader = new XSSFReader(opcPackage);
            StylesTable styles = reader.getStylesTable();

            try (InputStream sheetStream = getFirstSheetStream(reader)) {
                if (sheetStream == null) {
                    return;
                }

                XMLReader parser = XMLHelper.newXMLReader();
                StreamingSheetHandler sheetHandler = new StreamingSheetHandler(rowConsumer);
                parser.setContentHandler(new XSSFSheetXMLHandler(
                        styles,
                        null,
                        sharedStrings,
                        sheetHandler,
                        new DataFormatter(),
                        false));
                parser.parse(new InputSource(sheetStream));
            }
        } catch (OpenXML4JException | SAXException | ParserConfigurationException e) {
            throw new IOException("Error procesando XLSX en streaming: " + e.getMessage(), e);
        }
    }

    /**
     * Estima cuántas filas de datos tiene la primera hoja a partir de su dimensión declarada.
     */
    public static Long estimateFirstSheetDataRowCount(InputStream inputStream) throws IOException {
        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader reader = new XSSFReader(opcPackage);

            try (InputStream sheetStream = getFirstSheetStream(reader)) {
                if (sheetStream == null) {
                    return 0L;
                }

                XMLReader parser = XMLHelper.newXMLReader();
                DimensionHandler handler = new DimensionHandler();
                parser.setContentHandler(handler);

                try {
                    parser.parse(new InputSource(sheetStream));
                } catch (StopParsingException ignored) {
                }

                return handler.getEstimatedDataRowCount();
            }
        } catch (OpenXML4JException | SAXException | ParserConfigurationException e) {
            throw new IOException("Error estimando filas del XLSX: " + e.getMessage(), e);
        }
    }

    private static InputStream getFirstSheetStream(XSSFReader reader) throws IOException {
        try {
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            return sheets.hasNext() ? sheets.next() : null;
        } catch (OpenXML4JException e) {
            throw new IOException("No fue posible abrir la primera hoja del XLSX", e);
        }
    }

    private static final class StreamingSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final RowConsumer rowConsumer;
        private List<String> currentRow = List.of();

        private StreamingSheetHandler(RowConsumer rowConsumer) {
            this.rowConsumer = rowConsumer;
        }

        @Override
        public void startRow(int rowNum) {
            currentRow = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            rowConsumer.accept(rowNum + 1, List.copyOf(currentRow));
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int targetColumn = currentRow.size();
            if (cellReference != null) {
                targetColumn = new CellReference(cellReference).getCol();
            }

            while (currentRow.size() < targetColumn) {
                currentRow.add("");
            }

            currentRow.add(formattedValue != null ? formattedValue.trim() : "");
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
        }
    }

    private static final class DimensionHandler extends DefaultHandler {

        private Long estimatedDataRowCount;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if ("dimension".equals(localName) || "dimension".equals(qName)) {
                String ref = attributes.getValue("ref");
                estimatedDataRowCount = parseDataRowCount(ref);
                throw StopParsingException.INSTANCE;
            }

            if ("sheetData".equals(localName) || "sheetData".equals(qName)) {
                throw StopParsingException.INSTANCE;
            }
        }

        private long getEstimatedDataRowCount() {
            return estimatedDataRowCount != null ? estimatedDataRowCount : 0L;
        }

        private long parseDataRowCount(String ref) {
            if (ref == null || ref.isBlank()) {
                return 0L;
            }

            String lastReference = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
            int lastRowIndex = new CellReference(lastReference).getRow();
            return Math.max(0, lastRowIndex);
        }
    }

    private static final class StopParsingException extends SAXException {

        private static final StopParsingException INSTANCE = new StopParsingException();

        private StopParsingException() {
            super("STOP_PARSING");
        }
    }
}