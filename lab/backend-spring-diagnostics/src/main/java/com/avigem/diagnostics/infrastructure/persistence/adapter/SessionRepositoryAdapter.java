package com.avigem.diagnostics.infrastructure.persistence.adapter;

import com.avigem.diagnostics.domain.model.DiagnosticSession;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;
import com.avigem.diagnostics.domain.port.out.SessionRepository;
import com.avigem.diagnostics.infrastructure.persistence.mapper.SessionMapper;
import com.avigem.diagnostics.infrastructure.persistence.repository.JpaSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SessionRepositoryAdapter implements SessionRepository {

    private final JpaSessionRepository jpa;
    private final SessionMapper mapper;

    public SessionRepositoryAdapter(JpaSessionRepository jpa, SessionMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public DiagnosticSession save(DiagnosticSession session) {
        return mapper.toDomain(jpa.save(mapper.toEntity(session)));
    }

    @Override
    public Optional<DiagnosticSession> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<DiagnosticSession> findByClientSessionId(String clientSessionId) {
        return jpa.findByClientSessionId(clientSessionId).map(mapper::toDomain);
    }

    @Override
    public PageResult<DiagnosticSession> findByDeviceId(Long deviceId, PageQuery query) {
        Page<DiagnosticSession> page = jpa.findByDeviceId(deviceId, toPageable(query))
            .map(mapper::toDomain);
        return new PageResult<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages());
    }

    private Pageable toPageable(PageQuery query) {
        Sort sort = query.ascending()
            ? Sort.by(query.sortBy()).ascending()
            : Sort.by(query.sortBy()).descending();
        return PageRequest.of(query.page(), query.size(), sort);
    }
}
