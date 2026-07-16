package com.bancolombia.sipro.validations.shared.exceptions;

/**
 * Excepción usada para fallas técnicas o de infraestructura dentro de la aplicación.
 */
public class TechnicalException extends RuntimeException {
    public TechnicalException() {
        super();
    }

    public TechnicalException(String message) {
        super(message);
    }
}
