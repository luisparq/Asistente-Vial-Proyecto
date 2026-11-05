package com.avigem.diagnostics.domain.model;

/**
 * Consulta de paginación independiente de frameworks.
 */
public record PageQuery(int page, int size, String sortBy, boolean ascending) {

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page no puede ser negativo");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size debe ser mayor que cero");
        }
        if (sortBy == null || sortBy.isBlank()) {
            throw new IllegalArgumentException("sortBy es obligatorio");
        }
    }
}
