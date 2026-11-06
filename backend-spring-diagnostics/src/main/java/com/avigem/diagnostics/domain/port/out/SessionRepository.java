package com.avigem.diagnostics.domain.port.out;

import com.avigem.diagnostics.domain.model.DiagnosticSession;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;

import java.util.Optional;

/**
 * Puerto de salida: persistencia de sesiones.
 */
public interface SessionRepository {
    DiagnosticSession save(DiagnosticSession session);
    Optional<DiagnosticSession> findById(Long id);
    Optional<DiagnosticSession> findByClientSessionId(String clientSessionId);
    PageResult<DiagnosticSession> findByDeviceId(Long deviceId, PageQuery query);
}
