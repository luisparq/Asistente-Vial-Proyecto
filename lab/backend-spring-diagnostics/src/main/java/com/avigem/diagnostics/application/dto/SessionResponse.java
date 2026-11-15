package com.avigem.diagnostics.application.dto;

import com.avigem.diagnostics.domain.model.DataOrigin;
import com.avigem.diagnostics.domain.model.DiagnosticSession;
import com.avigem.diagnostics.domain.model.SessionResult;
import com.avigem.diagnostics.domain.model.SessionStatus;

import java.time.Instant;

/**
 * Respuesta de sesión (vista resumida).
 */
public class SessionResponse {
    private Long id;
    private Long deviceId;
    private SessionStatus status;
    private SessionResult result;
    private DataOrigin dataOrigin;
    private String clientSessionId;
    private String notes;
    private String cancellationReason;
    private String resultNote;
    private Instant startedAt;
    private Instant finishedAt;

    public SessionResponse() {}

    public static SessionResponse from(DiagnosticSession s) {
        SessionResponse r = new SessionResponse();
        r.id = s.getId();
        r.deviceId = s.getDeviceId();
        r.status = s.getStatus();
        r.result = s.getResult();
        r.dataOrigin = s.getDataOrigin();
        r.clientSessionId = s.getClientSessionId();
        r.notes = s.getNotes();
        r.cancellationReason = s.getCancellationReason();
        r.resultNote = resultNote(s);
        r.startedAt = s.getStartedAt();
        r.finishedAt = s.getFinishedAt();
        return r;
    }

    static String resultNote(DiagnosticSession s) {
        if (SessionStatus.COMPLETED.equals(s.getStatus())) {
            if (SessionResult.OBSERVED.equals(s.getResult()) && DataOrigin.TEST_DATA.equals(s.getDataOrigin())) {
                return "Solo se verificó el flujo de software con TEST_DATA; no representa una medición física real.";
            }
            if (SessionResult.OBSERVED.equals(s.getResult()) && DataOrigin.REAL_BLE.equals(s.getDataOrigin())) {
                return "Muestras recibidas desde Android/BLE; identidad, escala y calibración física aún no confirmadas.";
            }
            if (SessionResult.OBSERVED.equals(s.getResult()) && DataOrigin.REAL_MQTT.equals(s.getDataOrigin())) {
                return "Muestras recibidas directamente desde ESP32/MQTT; identidad, escala y calibración física aún no confirmadas.";
            }
            if (SessionResult.NOT_EVALUATED.equals(s.getResult())) {
                return "Sin datos suficientes para evaluar la sesión";
            }
        }
        return null;
    }

    public Long getId() { return id; }
    public Long getDeviceId() { return deviceId; }
    public SessionStatus getStatus() { return status; }
    public SessionResult getResult() { return result; }
    public DataOrigin getDataOrigin() { return dataOrigin; }
    public String getClientSessionId() { return clientSessionId; }
    public String getNotes() { return notes; }
    public String getCancellationReason() { return cancellationReason; }
    public String getResultNote() { return resultNote; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
