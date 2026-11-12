package com.bancolombia.sipro.validations.application.dto;

import java.util.List;
import java.time.Instant;

/**
 * Lote incremental de logs recientes para el panel de administrador.
 */
public record AdminLogStreamResponse(
        long latestId,
        long cursorId,
        long totalBuffered,
        List<LogItem> items,
        String instanceId,
        Instant capturadoEn,
        long oldestId,
        boolean entriesLost) {

    public record LogItem(
            long id,
            String timestamp,
            String level,
            String logger,
            String thread,
            String scope,
            String message,
            String executionId,
            String detalleError) {
    }
}