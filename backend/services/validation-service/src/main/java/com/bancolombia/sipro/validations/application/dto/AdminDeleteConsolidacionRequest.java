package com.bancolombia.sipro.validations.application.dto;

/**
 * Solicitud del panel admin para eliminar una consolidación completa.
 */
public record AdminDeleteConsolidacionRequest(
        String motivo,
        String confirmacion) {
}