package com.bancolombia.sipro.validations.service;

/**
 * Callback simple para reportar avance durante la validación de un archivo.
 */
public interface ValidationProgressListener {

    ValidationProgressListener NOOP = new ValidationProgressListener() {
    };

    default void onPhase(String phase, String message, int progressPercent) {
    }

    default void onTotalRowsEstimated(long totalRows) {
    }

    default void onRowsProcessed(long processedRows) {
    }
}