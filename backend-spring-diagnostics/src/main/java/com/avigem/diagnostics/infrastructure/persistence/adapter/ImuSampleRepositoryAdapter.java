package com.avigem.diagnostics.infrastructure.persistence.adapter;

import com.avigem.diagnostics.domain.model.ImuSample;
import com.avigem.diagnostics.domain.port.out.ImuSampleRepository;
import com.avigem.diagnostics.infrastructure.persistence.mapper.ImuSampleMapper;
import com.avigem.diagnostics.infrastructure.persistence.repository.JpaImuSampleRepository;
import org.springframework.stereotype.Component;

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
}
