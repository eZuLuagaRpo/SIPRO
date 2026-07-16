package com.bancolombia.sipro.validations.application.dto;

/**
 * Solicitud de ejecución SQL desde el panel de administrador.
 */
public record AdminSqlExecuteRequest(
        String tipoOperacion,
        String sql,
        String justificacion) {
}