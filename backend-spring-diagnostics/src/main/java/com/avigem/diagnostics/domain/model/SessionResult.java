package com.avigem.diagnostics.domain.model;

/**
 * Resultado técnico de la sesión.
 * Los datos son TEST_DATA en esta etapa; no representan mediciones físicas reales.
 */
public enum SessionResult {
    PENDING,
    PASSED,
    OBSERVED,
    FAILED,
    NOT_EVALUATED
}
