package com.avigem.diagnostics.application.port.in;

import com.avigem.diagnostics.application.dto.AddSamplesRequest;
import com.avigem.diagnostics.application.dto.DeviceResponse;
import com.avigem.diagnostics.application.dto.RegisterDeviceRequest;
import com.avigem.diagnostics.application.dto.SessionDetailResponse;
import com.avigem.diagnostics.application.dto.SessionResponse;
import com.avigem.diagnostics.application.dto.StartSessionRequest;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;

/**
 * Puerto de entrada: casos de uso del módulo de diagnóstico.
 */
public interface DiagnosticsUseCase {

    // ── Dispositivos ───────────────────────────────────────────────────────────
    DeviceResponse registerDevice(RegisterDeviceRequest request);

    DeviceResponse getDevice(Long deviceId);

    PageResult<DeviceResponse> getDevices(PageQuery query);

    // ── Sesiones ───────────────────────────────────────────────────────────────
    SessionResponse startSession(Long deviceId, StartSessionRequest request);

    SessionResponse addSamples(Long sessionId, AddSamplesRequest request);

    SessionResponse completeSession(Long sessionId);

    SessionResponse cancelSession(Long sessionId, String reason);

    // ── Consultas ──────────────────────────────────────────────────────────────
    SessionDetailResponse getSessionDetail(Long sessionId);

    PageResult<SessionResponse> getSessionHistory(Long deviceId, PageQuery query);
}
