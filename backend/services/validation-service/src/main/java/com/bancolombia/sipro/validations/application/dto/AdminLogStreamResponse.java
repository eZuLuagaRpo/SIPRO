package com.bancolombia.sipro.validations.application.dto;

import java.util.List;

/**
 * Lote incremental de logs recientes para el panel de administrador.
 */
public record AdminLogStreamResponse(
        long latestId,
        long cursorId,
        long totalBuffered,
        List<LogItem> items) {

    public record LogItem(
            long id,
            String timestamp,
            String level,
            String logger,
            String thread,
            String scope,
            String message) {
    }
}