package com.avigem.diagnostics.infrastructure.persistence.repository;

import com.avigem.diagnostics.infrastructure.persistence.entity.ImuSampleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaImuSampleRepository extends JpaRepository<ImuSampleEntity, Long> {
    List<ImuSampleEntity> findBySessionId(Long sessionId);
    long countBySessionId(Long sessionId);
    @Query("select s.sequenceNumber from ImuSampleEntity s where s.sessionId = :sessionId and s.sequenceNumber in :sequences")
    List<Long> findExistingSequences(@Param("sessionId") Long sessionId, @Param("sequences") List<Long> sequences);
    List<ImuSampleEntity> findBySessionIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
        Long sessionId, Long afterSequence, Pageable pageable);
    List<ImuSampleEntity> findBySessionIdAndSequenceNumberIsNotNullOrderBySequenceNumberDesc(
        Long sessionId, Pageable pageable);
}
