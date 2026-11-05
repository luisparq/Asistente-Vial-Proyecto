package com.avigem.diagnostics.infrastructure.persistence.adapter;

import com.avigem.diagnostics.domain.model.DiagnosticSummary;
import com.avigem.diagnostics.domain.port.out.SummaryRepository;
import com.avigem.diagnostics.infrastructure.persistence.mapper.SummaryMapper;
import com.avigem.diagnostics.infrastructure.persistence.repository.JpaSummaryRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SummaryRepositoryAdapter implements SummaryRepository {

    private final JpaSummaryRepository jpa;
    private final SummaryMapper mapper;

    public SummaryRepositoryAdapter(JpaSummaryRepository jpa, SummaryMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public DiagnosticSummary save(DiagnosticSummary summary) {
        return mapper.toDomain(jpa.save(mapper.toEntity(summary)));
    }

    @Override
    public Optional<DiagnosticSummary> findBySessionId(Long sessionId) {
        return jpa.findBySessionId(sessionId).map(mapper::toDomain);
    }
}
