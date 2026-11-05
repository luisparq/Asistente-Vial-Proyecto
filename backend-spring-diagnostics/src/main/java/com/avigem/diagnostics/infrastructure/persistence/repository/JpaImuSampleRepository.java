package com.avigem.diagnostics.infrastructure.persistence.repository;

import com.avigem.diagnostics.infrastructure.persistence.entity.ImuSampleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaImuSampleRepository extends JpaRepository<ImuSampleEntity, Long> {
    List<ImuSampleEntity> findBySessionId(Long sessionId);
    long countBySessionId(Long sessionId);
}
