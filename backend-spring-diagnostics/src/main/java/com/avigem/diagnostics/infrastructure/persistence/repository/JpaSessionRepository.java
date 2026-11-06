package com.avigem.diagnostics.infrastructure.persistence.repository;

import com.avigem.diagnostics.infrastructure.persistence.entity.DiagnosticSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JpaSessionRepository extends JpaRepository<DiagnosticSessionEntity, Long> {
    Page<DiagnosticSessionEntity> findByDeviceId(Long deviceId, Pageable pageable);
    Optional<DiagnosticSessionEntity> findByClientSessionId(String clientSessionId);
}
