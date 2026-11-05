package com.avigem.diagnostics.infrastructure.persistence.mapper;

import com.avigem.diagnostics.domain.model.DiagnosticSession;
import com.avigem.diagnostics.infrastructure.persistence.entity.DiagnosticSessionEntity;
import org.springframework.stereotype.Component;

@Component
public class SessionMapper {

    public DiagnosticSession toDomain(DiagnosticSessionEntity e) {
        DiagnosticSession d = new DiagnosticSession(
            e.getId(), e.getDeviceId(), e.getStatus(), e.getResult(),
            e.getNotes(), e.getStartedAt(), e.getFinishedAt()
        );
        d.setCancellationReason(e.getCancellationReason());
        d.setDataOrigin(e.getDataOrigin());
        return d;
    }

    public DiagnosticSessionEntity toEntity(DiagnosticSession d) {
        DiagnosticSessionEntity e = new DiagnosticSessionEntity();
        e.setId(d.getId());
        e.setDeviceId(d.getDeviceId());
        e.setStatus(d.getStatus());
        e.setResult(d.getResult());
        e.setNotes(d.getNotes());
        e.setCancellationReason(d.getCancellationReason());
        e.setDataOrigin(d.getDataOrigin());
        e.setStartedAt(d.getStartedAt());
        e.setFinishedAt(d.getFinishedAt());
        return e;
    }
}
