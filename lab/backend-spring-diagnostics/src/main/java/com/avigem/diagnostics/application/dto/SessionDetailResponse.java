package com.avigem.diagnostics.application.dto;

import com.avigem.diagnostics.domain.model.DataOrigin;
import com.avigem.diagnostics.domain.model.DiagnosticSession;
import com.avigem.diagnostics.domain.model.DiagnosticSummary;
import com.avigem.diagnostics.domain.model.SessionResult;
import com.avigem.diagnostics.domain.model.SessionStatus;

import java.time.Instant;

/**
 * Respuesta detallada de sesión incluyendo estadísticas calculadas.
 */
public class SessionDetailResponse {
    private Long id;
    private Long deviceId;
    private SessionStatus status;
    private SessionResult result;
    private DataOrigin dataOrigin;
    private String notes;
    private String cancellationReason;
    private String resultNote;
    private Instant startedAt;
    private Instant finishedAt;
    private SummaryDto summary;

    public SessionDetailResponse() {}

    public static SessionDetailResponse from(DiagnosticSession s, DiagnosticSummary sum) {
        SessionDetailResponse r = new SessionDetailResponse();
        r.id = s.getId();
        r.deviceId = s.getDeviceId();
        r.status = s.getStatus();
        r.result = s.getResult();
        r.dataOrigin = s.getDataOrigin();
        r.notes = s.getNotes();
        r.cancellationReason = s.getCancellationReason();
        r.resultNote = SessionResponse.resultNote(s);
        r.startedAt = s.getStartedAt();
        r.finishedAt = s.getFinishedAt();
        if (sum != null) {
            r.summary = SummaryDto.from(sum);
        }
        return r;
    }

    public Long getId() { return id; }
    public Long getDeviceId() { return deviceId; }
    public SessionStatus getStatus() { return status; }
    public SessionResult getResult() { return result; }
    public DataOrigin getDataOrigin() { return dataOrigin; }
    public String getNotes() { return notes; }
    public String getCancellationReason() { return cancellationReason; }
    public String getResultNote() { return resultNote; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public SummaryDto getSummary() { return summary; }

    // ── Summary ───────────────────────────────────────────────────────────────

    public static class SummaryDto {
        private int sampleCount;
        private AxisStats ax;
        private AxisStats ay;
        private AxisStats az;
        private AxisStats gx;
        private AxisStats gy;
        private AxisStats gz;
        private Instant computedAt;

        public static SummaryDto from(DiagnosticSummary s) {
            SummaryDto d = new SummaryDto();
            d.sampleCount = s.getSampleCount();
            d.ax = new AxisStats(s.getAxMin(), s.getAxMax(), s.getAxAvg(), s.getAxStd());
            d.ay = new AxisStats(s.getAyMin(), s.getAyMax(), s.getAyAvg(), s.getAyStd());
            d.az = new AxisStats(s.getAzMin(), s.getAzMax(), s.getAzAvg(), s.getAzStd());
            d.gx = new AxisStats(s.getGxMin(), s.getGxMax(), s.getGxAvg(), s.getGxStd());
            d.gy = new AxisStats(s.getGyMin(), s.getGyMax(), s.getGyAvg(), s.getGyStd());
            d.gz = new AxisStats(s.getGzMin(), s.getGzMax(), s.getGzAvg(), s.getGzStd());
            d.computedAt = s.getComputedAt();
            return d;
        }

        public int getSampleCount() { return sampleCount; }
        public AxisStats getAx() { return ax; }
        public AxisStats getAy() { return ay; }
        public AxisStats getAz() { return az; }
        public AxisStats getGx() { return gx; }
        public AxisStats getGy() { return gy; }
        public AxisStats getGz() { return gz; }
        public Instant getComputedAt() { return computedAt; }
    }

    public static class AxisStats {
        private Short min;
        private Short max;
        private Double avg;
        private Double std;

        public AxisStats() {}

        public AxisStats(Short min, Short max, Double avg, Double std) {
            this.min = min;
            this.max = max;
            this.avg = avg;
            this.std = std;
        }

        public Short getMin() { return min; }
        public Short getMax() { return max; }
        public Double getAvg() { return avg; }
        public Double getStd() { return std; }
    }
}
