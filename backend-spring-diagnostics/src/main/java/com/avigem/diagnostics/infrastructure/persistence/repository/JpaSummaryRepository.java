package com.avigem.diagnostics.infrastructure.persistence.repository;

import com.avigem.diagnostics.infrastructure.persistence.entity.DiagnosticSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaSummaryRepository extends JpaRepository<DiagnosticSummaryEntity, Long> {
    Optional<DiagnosticSummaryEntity> findBySessionId(Long sessionId);
}
