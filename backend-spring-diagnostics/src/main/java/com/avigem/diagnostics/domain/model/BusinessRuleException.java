package com.avigem.diagnostics.domain.model;

/**
 * Excepción lanzada cuando una operación viola reglas de negocio.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
