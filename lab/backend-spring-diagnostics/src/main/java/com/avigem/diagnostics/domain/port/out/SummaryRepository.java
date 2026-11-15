package com.avigem.diagnostics.domain.port.out;

import com.avigem.diagnostics.domain.model.DiagnosticSummary;
import java.util.Optional;

/**
 * Puerto de salida: persistencia de resúmenes estadísticos.
 */
public interface SummaryRepository {
    DiagnosticSummary save(DiagnosticSummary summary);
    Optional<DiagnosticSummary> findBySessionId(Long sessionId);
}
