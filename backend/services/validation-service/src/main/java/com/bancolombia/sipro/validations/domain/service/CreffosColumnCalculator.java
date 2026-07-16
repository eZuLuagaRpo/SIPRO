package com.bancolombia.sipro.validations.domain.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Calcula y agrega las columnas adicionales requeridas por el CREFFSOS consolidado.
 * <p>
 * Las columnas calculadas se agregan al final de cada fila, después de las columnas
 * originales del Excel fuente. Las reglas de negocio se basan en valores de columnas
 * de referencia existentes en los datos originales.
 * </p>
 *
 * <h3>Grupos de reglas:</h3>
 * <ul>
 *   <li>A) OFICINA → GTECUENTA, ZONA</li>
 *   <li>B) MODALIDAD → ESTADOCR</li>
 *   <li>C) DIAVCTOFIN → ANOULTPAGO..PORCRDSCTO</li>
 *   <li>D) Cadena provisiones (INTCTASORD → SALDOMES → ... → GARCREDITO)</li>
 *   <li>E) CLASEGTIA_INSUMO → DSTECONOM</li>
 *   <li>F) NIT,MODALIDAD,BQ_INSUMO → CLASIFCPUC, CLASIFCUSA</li>
 *   <li>G) CTAPUC → PLAZO, cadenas VALORUSU/FECHAUSU/INDICUSU/TASAUSU</li>
 * </ul>
 */
@Component
public class CreffosColumnCalculator {

    /**
     * Lista ordenada de columnas calculadas que se agregarán al CREFFSOS.
     * El orden importa porque algunas columnas dependen de otras calculadas previamente.
     */
    public static final List<String> COLUMNAS_CALCULADAS = Collections.unmodifiableList(Arrays.asList(
            "GTECUENTA", "ZONA", "ESTADOCR",
            "ANOULTPAGO", "MESULTPAGO", "DIAULTPAGO",
            "TASAINTVIG", "TASAINTMRA", "TASAREDESC", "PORCRDSCTO",
            "SALDOMES",
            "PROVCAPANT", "PROVCAPACT", "PROVINTANT", "PROVINTACT",
            "PROVOTRANT", "PROVOTRACT", "PROVCAPUSA", "PROVINTUSA",
            "GARANTIA", "GARCREDITO",
            "DSTECONOM",
            "CLASIFCPUC", "CLASIFCUSA",
            "PLAZO",
            "VALORUSU1", "VALORUSU2", "VALORUSU3", "VALORUSU4",
            "FECHAUSU1", "FECHAUSU2",
            "INDICUSU3", "INDICUSU4",
            "TASAUSU1"
    ));

    /**
     * Calcula los valores de todas las columnas agregadas para una fila dada.
     *
     * @param row                Fila de datos con valores originales
     * @param headerIndexMap     Mapa de nombre de columna → índice en la fila
     * @param calculatedStartIdx Índice donde empiezan las columnas calculadas
     * @return Mapa con nombre de columna calculada → valor calculado
     */
    public Map<String, Object> calculateRow(Row row, Map<String, Integer> headerIndexMap, int calculatedStartIdx) {
        Map<String, Object> calculated = new LinkedHashMap<>();

        // Función auxiliar para leer valor de una columna original O calculada previamente
        // Primero busca en las calculadas (por si es cadena), luego en la fila original
        java.util.function.Function<String, String> getVal = colName -> {
            // Si ya fue calculada, usar ese valor
            if (calculated.containsKey(colName)) {
                Object val = calculated.get(colName);
                return val == null ? "" : val.toString();
            }
            // Buscar en la fila original
            Integer idx = headerIndexMap.get(colName);
            if (idx == null) return "";
            return getCellValueAsString(row, idx);
        };

        // ═══════════════════════════════════════════════════════════════
        // A) Columnas que dependen de OFICINA
        // ═══════════════════════════════════════════════════════════════
        String oficina = getVal.apply("OFICINA");
        calculated.put("GTECUENTA", oficina.isEmpty() ? "" : oficina);
        calculated.put("ZONA", oficina.isEmpty() ? "" : "1");

        // ═══════════════════════════════════════════════════════════════
        // B) Columnas que dependen de MODALIDAD
        // ═══════════════════════════════════════════════════════════════
        String modalidad = getVal.apply("MODALIDAD");
        calculated.put("ESTADOCR", modalidad.isEmpty() ? "" : "0");

        // ═══════════════════════════════════════════════════════════════
        // C) Columnas que dependen de DIAVCTOFIN
        // ═══════════════════════════════════════════════════════════════
        String diavctofin = getVal.apply("DIAVCTOFIN");
        calculated.put("ANOULTPAGO", diavctofin.isEmpty() ? "" : "0");
        calculated.put("MESULTPAGO", diavctofin.isEmpty() ? "" : "0");
        calculated.put("DIAULTPAGO", diavctofin.isEmpty() ? "" : "0");
        calculated.put("TASAINTVIG", diavctofin.isEmpty() ? "" : "0");
        calculated.put("TASAINTMRA", diavctofin.isEmpty() ? "" : "0");
        calculated.put("TASAREDESC", diavctofin.isEmpty() ? "" : "0");
        calculated.put("PORCRDSCTO", diavctofin.isEmpty() ? "" : "0");

        // ═══════════════════════════════════════════════════════════════
        // D) Cadena de provisiones
        //    SALDOMES ← INTCTASORD
        //    PROVCAPANT ← SALDOMES ... GARCREDITO ← GARANTIA
        // ═══════════════════════════════════════════════════════════════
        String intctasord = getVal.apply("INTCTASORD");
        calculated.put("SALDOMES", intctasord.isEmpty() ? "" : "0");

        // Cada una depende de la anterior en la cadena. Usamos el valor calculado.
        String[] cadenaProvisiones = {
                "PROVCAPANT", "PROVCAPACT", "PROVINTANT", "PROVINTACT",
                "PROVOTRANT", "PROVOTRACT", "PROVCAPUSA", "PROVINTUSA",
                "GARANTIA", "GARCREDITO"
        };
        String[] cadenaReferencias = {
                "SALDOMES", "PROVCAPANT", "PROVCAPACT", "PROVINTANT",
                "PROVINTACT", "PROVOTRANT", "PROVOTRACT", "PROVCAPUSA",
                "PROVINTUSA", "GARANTIA"
        };

        for (int i = 0; i < cadenaProvisiones.length; i++) {
            String refVal = getVal.apply(cadenaReferencias[i]);
            calculated.put(cadenaProvisiones[i], refVal.isEmpty() ? "" : "0");
        }

        // ═══════════════════════════════════════════════════════════════
        // E) DSTECONOM depende de CLASEGTIA_INSUMO (o CLASEGTIA)
        // ═══════════════════════════════════════════════════════════════
        String clasegtia = getVal.apply("CLASEGTIA_INSUMO");
        if (clasegtia.isEmpty()) {
            clasegtia = getVal.apply("CLASEGTIA"); // fallback a nombre alternativo
        }
        calculated.put("DSTECONOM", clasegtia.isEmpty() ? "" : "000260");

        // ═══════════════════════════════════════════════════════════════
        // F) CLASIFCPUC → lógica condicional real
        //    - NIT vacío → ""
        //    - MODALIDAD = "HIP" → 3
        //    - BQ_INSUMO in {3,7,8} → 1
        //    - MODALIDAD = "DSC" → 1
        //    - Otro caso → 2
        // ═══════════════════════════════════════════════════════════════
        String nit = getVal.apply("NIT");
        String bqInsumo = getVal.apply("BQ_INSUMO");
        if (bqInsumo.isEmpty()) {
            bqInsumo = getVal.apply("BQ"); // fallback
        }

        String clasifcpuc;
        if (nit.isEmpty()) {
            clasifcpuc = "";
        } else if ("HIP".equalsIgnoreCase(modalidad.trim())) {
            clasifcpuc = "3";
        } else {
            // Verificar BQ_INSUMO
            String bqTrimmed = bqInsumo.trim();
            if ("3".equals(bqTrimmed) || "7".equals(bqTrimmed) || "8".equals(bqTrimmed)) {
                clasifcpuc = "1";
            } else if ("DSC".equalsIgnoreCase(modalidad.trim())) {
                clasifcpuc = "1";
            } else {
                clasifcpuc = "2";
            }
        }
        calculated.put("CLASIFCPUC", clasifcpuc);
        calculated.put("CLASIFCUSA", clasifcpuc.isEmpty() ? "" : clasifcpuc);

        // ═══════════════════════════════════════════════════════════════
        // G) PLAZO y cadenas VALORUSU/FECHAUSU/INDICUSU/TASAUSU
        // ═══════════════════════════════════════════════════════════════
        String ctapuc = getVal.apply("CTAPUC");
        calculated.put("PLAZO", ctapuc.isEmpty() ? "" : "0");

        // Cadena VALORUSU: PLAZO → VALORUSU1 → ... → VALORUSU4
        String[] cadenaValorUsu = {"VALORUSU1", "VALORUSU2", "VALORUSU3", "VALORUSU4"};
        String[] refValorUsu = {"PLAZO", "VALORUSU1", "VALORUSU2", "VALORUSU3"};
        for (int i = 0; i < cadenaValorUsu.length; i++) {
            String refV = getVal.apply(refValorUsu[i]);
            calculated.put(cadenaValorUsu[i], refV.isEmpty() ? "" : "0");
        }

        // FECHAUSU1 depende de VALORUSU4, FECHAUSU2 depende de FECHAUSU1
        String valorusu4 = getVal.apply("VALORUSU4");
        calculated.put("FECHAUSU1", valorusu4.isEmpty() ? "" : "0");
        String fechausu1 = getVal.apply("FECHAUSU1");
        calculated.put("FECHAUSU2", fechausu1.isEmpty() ? "" : "0");

        // INDICUSU3 depende de CTAPUC, INDICUSU4 depende de INDICUSU3
        calculated.put("INDICUSU3", ctapuc.isEmpty() ? "" : "0");
        String indicusu3 = getVal.apply("INDICUSU3");
        calculated.put("INDICUSU4", indicusu3.isEmpty() ? "" : "0");

        // TASAUSU1 depende de INDICUSU4
        String indicusu4 = getVal.apply("INDICUSU4");
        calculated.put("TASAUSU1", indicusu4.isEmpty() ? "" : "0");

        return calculated;
    }

    /**
     * Obtiene el valor de una celda como String, manejando todos los tipos de celda.
     */
    private String getCellValueAsString(Row row, int cellIndex) {
        if (row == null) return "";
        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue() != null ? cell.getStringCellValue().trim() : "";
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue() != null
                            ? cell.getLocalDateTimeCellValue().toString()
                            : "";
                }
                double numVal = cell.getNumericCellValue();
                // Devolver como entero si no tiene decimales
                if (numVal == Math.floor(numVal) && !Double.isInfinite(numVal)) {
                    return String.valueOf((long) numVal);
                }
                return String.valueOf(numVal);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return "";
                    }
                }
            case BLANK:
            case _NONE:
            case ERROR:
            default:
                return "";
        }
    }
}
