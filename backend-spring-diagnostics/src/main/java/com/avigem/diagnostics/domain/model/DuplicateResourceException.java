package com.avigem.diagnostics.domain.model;

/**
 * Excepción lanzada cuando se intenta crear un recurso que ya existe.
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
