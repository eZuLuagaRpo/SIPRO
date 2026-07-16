package com.bancolombia.sipro.validations.shared.exceptions;

/**
 * Excepción usada para errores de negocio que el usuario puede corregir o atender.
 */
public class BusinessException extends RuntimeException {
    public BusinessException() {
        super();
    }

    public BusinessException(String message) {
        super(message);
    }
}
