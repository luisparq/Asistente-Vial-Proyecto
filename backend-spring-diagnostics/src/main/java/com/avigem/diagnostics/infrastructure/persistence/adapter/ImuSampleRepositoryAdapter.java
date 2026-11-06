package com.avigem.diagnostics.infrastructure.persistence.adapter;

import com.avigem.diagnostics.domain.model.ImuSample;
import com.avigem.diagnostics.domain.port.out.ImuSampleRepository;
import com.avigem.diagnostics.infrastructure.persistence.mapper.ImuSampleMapper;
import com.avigem.diagnostics.infrastructure.persistence.entity.ImuSampleEntity;
import com.avigem.diagnostics.infrastructure.persistence.repository.JpaImuSampleRepository;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ImuSampleRepositoryAdapter implements ImuSampleRepository {

    private final JpaImuSampleRepository jpa;
    private final ImuSampleMapper mapper;

    public ImuSampleRepositoryAdapter(JpaImuSampleRepository jpa, ImuSampleMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<ImuSample> saveAll(List<ImuSample> samples) {
        return jpa.saveAll(samples.stream().map(mapper::toEntity).collect(Collectors.toList()))
            .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ImuSample> findBySessionId(Long sessionId) {
        return jpa.findBySessionId(sessionId).stream()
            .map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countBySessionId(Long sessionId) {
        return jpa.countBySessionId(sessionId);
    }

    @Override
    public List<Long> findSequenceNumbers(Long sessionId, List<Long> sequenceNumbers) {
        if (sequenceNumbers.isEmpty()) return List.of();
        return jpa.findExistingSequences(sessionId, sequenceNumbers);
    }

    @Override
    public List<ImuSample> findRecentBySessionId(Long sessionId, Long afterSequence, int limit) {
        List<ImuSampleEntity> entities = afterSequence < 0
            ? jpa.findBySessionIdAndSequenceNumberIsNotNullOrderBySequenceNumberDesc(sessionId, PageRequest.of(0, limit))
            : jpa.findBySessionIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                sessionId, afterSequence, PageRequest.of(0, limit));
        if (afterSequence < 0) java.util.Collections.reverse(entities);
        return entities.stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
