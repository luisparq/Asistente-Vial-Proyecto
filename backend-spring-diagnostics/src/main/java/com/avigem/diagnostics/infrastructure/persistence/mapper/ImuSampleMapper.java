package com.avigem.diagnostics.infrastructure.persistence.mapper;

import com.avigem.diagnostics.domain.model.ImuSample;
import com.avigem.diagnostics.infrastructure.persistence.entity.ImuSampleEntity;
import org.springframework.stereotype.Component;

@Component
public class ImuSampleMapper {

    public ImuSample toDomain(ImuSampleEntity e) {
        return new ImuSample(
            e.getId(), e.getSessionId(),
            e.getAxRaw(), e.getAyRaw(), e.getAzRaw(),
            e.getGxRaw(), e.getGyRaw(), e.getGzRaw(),
            e.getSampledAt()
        );
    }

    public ImuSampleEntity toEntity(ImuSample d) {
        ImuSampleEntity e = new ImuSampleEntity();
        e.setId(d.getId());
        e.setSessionId(d.getSessionId());
        e.setAxRaw(d.getAxRaw());
        e.setAyRaw(d.getAyRaw());
        e.setAzRaw(d.getAzRaw());
        e.setGxRaw(d.getGxRaw());
        e.setGyRaw(d.getGyRaw());
        e.setGzRaw(d.getGzRaw());
        e.setSampledAt(d.getSampledAt());
        return e;
    }
}
