package com.avigem.diagnostics.infrastructure.persistence.mapper;

import com.avigem.diagnostics.domain.model.DiagnosticSummary;
import com.avigem.diagnostics.infrastructure.persistence.entity.DiagnosticSummaryEntity;
import org.springframework.stereotype.Component;

@Component
public class SummaryMapper {

    public DiagnosticSummary toDomain(DiagnosticSummaryEntity e) {
        DiagnosticSummary d = new DiagnosticSummary();
        d.setId(e.getId());
        d.setSessionId(e.getSessionId());
        d.setSampleCount(e.getSampleCount());
        d.setAxMin(e.getAxMin()); d.setAxMax(e.getAxMax());
        d.setAxAvg(e.getAxAvg()); d.setAxStd(e.getAxStd());
        d.setAyMin(e.getAyMin()); d.setAyMax(e.getAyMax());
        d.setAyAvg(e.getAyAvg()); d.setAyStd(e.getAyStd());
        d.setAzMin(e.getAzMin()); d.setAzMax(e.getAzMax());
        d.setAzAvg(e.getAzAvg()); d.setAzStd(e.getAzStd());
        d.setGxMin(e.getGxMin()); d.setGxMax(e.getGxMax());
        d.setGxAvg(e.getGxAvg()); d.setGxStd(e.getGxStd());
        d.setGyMin(e.getGyMin()); d.setGyMax(e.getGyMax());
        d.setGyAvg(e.getGyAvg()); d.setGyStd(e.getGyStd());
        d.setGzMin(e.getGzMin()); d.setGzMax(e.getGzMax());
        d.setGzAvg(e.getGzAvg()); d.setGzStd(e.getGzStd());
        d.setComputedAt(e.getComputedAt());
        return d;
    }

    public DiagnosticSummaryEntity toEntity(DiagnosticSummary d) {
        DiagnosticSummaryEntity e = new DiagnosticSummaryEntity();
        e.setId(d.getId());
        e.setSessionId(d.getSessionId());
        e.setSampleCount(d.getSampleCount());
        e.setAxMin(d.getAxMin()); e.setAxMax(d.getAxMax());
        e.setAxAvg(d.getAxAvg()); e.setAxStd(d.getAxStd());
        e.setAyMin(d.getAyMin()); e.setAyMax(d.getAyMax());
        e.setAyAvg(d.getAyAvg()); e.setAyStd(d.getAyStd());
        e.setAzMin(d.getAzMin()); e.setAzMax(d.getAzMax());
        e.setAzAvg(d.getAzAvg()); e.setAzStd(d.getAzStd());
        e.setGxMin(d.getGxMin()); e.setGxMax(d.getGxMax());
        e.setGxAvg(d.getGxAvg()); e.setGxStd(d.getGxStd());
        e.setGyMin(d.getGyMin()); e.setGyMax(d.getGyMax());
        e.setGyAvg(d.getGyAvg()); e.setGyStd(d.getGyStd());
        e.setGzMin(d.getGzMin()); e.setGzMax(d.getGzMax());
        e.setGzAvg(d.getGzAvg()); e.setGzStd(d.getGzStd());
        e.setComputedAt(d.getComputedAt());
        return e;
    }
}
