package com.bancolombia.sipro.validations.application.dto;

/**
 * Respuesta del panel admin al eliminar una consolidación completa.
 */
public record AdminDeleteConsolidacionResponse(
        boolean exito,
        String mensaje,
        long registrosEliminados,
        int archivosEliminados) {
}