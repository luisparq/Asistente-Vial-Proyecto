package com.avigem.diagnostics.infrastructure.web.controller;

import com.avigem.diagnostics.application.dto.AddSamplesRequest;
import com.avigem.diagnostics.application.dto.DeviceResponse;
import com.avigem.diagnostics.application.dto.RegisterDeviceRequest;
import com.avigem.diagnostics.application.dto.SessionDetailResponse;
import com.avigem.diagnostics.application.dto.SessionResponse;
import com.avigem.diagnostics.application.dto.StartSessionRequest;
import com.avigem.diagnostics.application.port.in.DiagnosticsUseCase;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST de AVIGEM Diagnostics.
 * Base: /api/v1/diagnostics
 */
@RestController
@RequestMapping("/api/v1/diagnostics")
public class DiagnosticsController {

    private final DiagnosticsUseCase useCase;

    public DiagnosticsController(DiagnosticsUseCase useCase) {
        this.useCase = useCase;
    }

    // ── Dispositivos ───────────────────────────────────────────────────────────

    /**
     * POST /api/v1/diagnostics/devices
     * Registrar un nuevo dispositivo.
     */
    @PostMapping("/devices")
    public ResponseEntity<DeviceResponse> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(useCase.registerDevice(request));
    }

    /**
     * GET /api/v1/diagnostics/devices
     * Listar dispositivos con paginación.
     */
    @GetMapping("/devices")
    public ResponseEntity<Page<DeviceResponse>> getDevices(
            @PageableDefault(size = 20, sort = "registeredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResult<DeviceResponse> result = useCase.getDevices(toPageQuery(pageable, "registeredAt"));
        return ResponseEntity.ok(toPage(result, pageable));
    }

    /**
     * GET /api/v1/diagnostics/devices/{deviceId}
     * Obtener un dispositivo por id.
     */
    @GetMapping("/devices/{deviceId}")
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable Long deviceId) {
        return ResponseEntity.ok(useCase.getDevice(deviceId));
    }

    // ── Sesiones ───────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/diagnostics/devices/{deviceId}/sessions
     * Iniciar una sesión de diagnóstico.
     */
    @PostMapping("/devices/{deviceId}/sessions")
    public ResponseEntity<SessionResponse> startSession(
            @PathVariable Long deviceId,
            @Valid @RequestBody(required = false) StartSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(useCase.startSession(deviceId, request != null ? request : new StartSessionRequest()));
    }

    /**
     * POST /api/v1/diagnostics/sessions/{sessionId}/samples
     * Ingresar un lote de muestras IMU (TEST_DATA).
     */
    @PostMapping("/sessions/{sessionId}/samples")
    public ResponseEntity<SessionResponse> addSamples(
            @PathVariable Long sessionId,
            @Valid @RequestBody AddSamplesRequest request) {
        return ResponseEntity.ok(useCase.addSamples(sessionId, request));
    }

    /**
     * POST /api/v1/diagnostics/sessions/{sessionId}/complete
     * Completar una sesión y calcular resumen estadístico.
     */
    @PostMapping("/sessions/{sessionId}/complete")
    public ResponseEntity<SessionResponse> completeSession(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(useCase.completeSession(sessionId));
    }

    /**
     * POST /api/v1/diagnostics/sessions/{sessionId}/cancel
     * Cancelar una sesión en progreso.
     */
    @PostMapping("/sessions/{sessionId}/cancel")
    public ResponseEntity<SessionResponse> cancelSession(
            @PathVariable Long sessionId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(useCase.cancelSession(sessionId, reason));
    }

    // ── Consultas ──────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/diagnostics/sessions/{sessionId}
     * Detalle de sesión con resumen estadístico.
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionDetailResponse> getSessionDetail(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(useCase.getSessionDetail(sessionId));
    }

    /**
     * GET /api/v1/diagnostics/devices/{deviceId}/sessions
     * Historial paginado de sesiones de un dispositivo.
     */
    @GetMapping("/devices/{deviceId}/sessions")
    public ResponseEntity<Page<SessionResponse>> getSessionHistory(
            @PathVariable Long deviceId,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResult<SessionResponse> result = useCase.getSessionHistory(
            deviceId, toPageQuery(pageable, "startedAt"));
        return ResponseEntity.ok(toPage(result, pageable));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PageQuery toPageQuery(Pageable pageable, String defaultSort) {
        Sort.Order order = pageable.getSort().stream()
            .findFirst()
            .orElse(Sort.Order.desc(defaultSort));
        return new PageQuery(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            order.getProperty(),
            order.isAscending());
    }

    private <T> Page<T> toPage(PageResult<T> result, Pageable pageable) {
        Pageable effective = PageRequest.of(result.page(), result.size(), pageable.getSort());
        return new PageImpl<>(result.content(), effective, result.totalElements());
    }
}
