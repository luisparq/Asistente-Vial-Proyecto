package com.avigem.diagnostics.domain.model;

import java.time.Instant;

/**
 * Modelo de dominio: Sesión de diagnóstico.
 */
public class DiagnosticSession {
    private Long id;
    private Long deviceId;
    private SessionStatus status;
    private SessionResult result;
    private String notes;
    private String cancellationReason;
    private DataOrigin dataOrigin = DataOrigin.TEST_DATA;
    private String clientSessionId;
    private Instant startedAt;
    private Instant finishedAt;

    public DiagnosticSession() {}

    public DiagnosticSession(Long id, Long deviceId, SessionStatus status,
                              SessionResult result, String notes,
                              Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.deviceId = deviceId;
        this.status = status;
        this.result = result;
        this.notes = notes;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public DiagnosticSession(Long id, Long deviceId, SessionStatus status,
                              SessionResult result, String notes,
                              String cancellationReason, DataOrigin dataOrigin,
                              Instant startedAt, Instant finishedAt) {
        this(id, deviceId, status, result, notes, startedAt, finishedAt);
        this.cancellationReason = cancellationReason;
        this.dataOrigin = dataOrigin != null ? dataOrigin : DataOrigin.TEST_DATA;
    }

    public boolean isInProgress() {
        return SessionStatus.IN_PROGRESS.equals(this.status);
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public SessionResult getResult() { return result; }
    public void setResult(SessionResult result) { this.result = result; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public DataOrigin getDataOrigin() { return dataOrigin; }
    public void setDataOrigin(DataOrigin dataOrigin) { this.dataOrigin = dataOrigin; }

    public String getClientSessionId() { return clientSessionId; }
    public void setClientSessionId(String clientSessionId) { this.clientSessionId = clientSessionId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
