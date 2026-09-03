package com.bancolombia.sipro.validations.application.dto;

import java.math.BigDecimal;

/**
 * Resultado de la agregación GROUP BY por producto para el Resumen Consolidado.
 * Evita cargar todos los registros individuales en memoria Java.
 */
public record ProductoAgregadoDto(
        Long idProductoOrigen,
        String productoOrigen,
        Long cantidadRegistros,
        BigDecimal totalVlriniobl,
        Long registrosObservados) {
}