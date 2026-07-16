package com.bancolombia.sipro.validations.application.dto;

import java.util.List;
import java.util.Map;

/**
 * Resultado de ejecutar una consulta SQL restringida desde administración.
 */
public record AdminSqlExecuteResponse(
        boolean exito,
        String tipoOperacion,
        String mensaje,
        Integer filasAfectadas,
        List<String> columnas,
        List<Map<String, Object>> filas,
        String sqlEjecutada,
        boolean resultadoTruncado) {

    public static AdminSqlExecuteResponse failure(String tipoOperacion, String mensaje) {
        return new AdminSqlExecuteResponse(false, tipoOperacion, mensaje, null, List.of(), List.of(), null, false);
    }
}