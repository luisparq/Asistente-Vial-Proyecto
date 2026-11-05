package com.avigem.diagnostics.application.usecase;

import com.avigem.diagnostics.application.dto.AddSamplesRequest;
import com.avigem.diagnostics.application.dto.DeviceResponse;
import com.avigem.diagnostics.application.dto.RegisterDeviceRequest;
import com.avigem.diagnostics.application.dto.SessionDetailResponse;
import com.avigem.diagnostics.application.dto.SessionResponse;
import com.avigem.diagnostics.application.dto.StartSessionRequest;
import com.avigem.diagnostics.application.port.in.DiagnosticsUseCase;
import com.avigem.diagnostics.domain.model.AxisStats;
import com.avigem.diagnostics.domain.model.BusinessRuleException;
import com.avigem.diagnostics.domain.model.DataOrigin;
import com.avigem.diagnostics.domain.model.Device;
import com.avigem.diagnostics.domain.model.DiagnosticSession;
import com.avigem.diagnostics.domain.model.DiagnosticSummary;
import com.avigem.diagnostics.domain.model.DuplicateResourceException;
import com.avigem.diagnostics.domain.model.ImuSample;
import com.avigem.diagnostics.domain.model.ImuStatsCalculator;
import com.avigem.diagnostics.domain.model.PageQuery;
import com.avigem.diagnostics.domain.model.PageResult;
import com.avigem.diagnostics.domain.model.ResourceNotFoundException;
import com.avigem.diagnostics.domain.model.SessionResult;
import com.avigem.diagnostics.domain.model.SessionStatus;
import com.avigem.diagnostics.domain.port.out.DeviceRepository;
import com.avigem.diagnostics.domain.port.out.ImuSampleRepository;
import com.avigem.diagnostics.domain.port.out.SessionRepository;
import com.avigem.diagnostics.domain.port.out.SummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implementación de los casos de uso del módulo de diagnóstico.
 */
@Service
@Transactional
public class DiagnosticsService implements DiagnosticsUseCase {

    private final DeviceRepository deviceRepo;
    private final SessionRepository sessionRepo;
    private final ImuSampleRepository sampleRepo;
    private final SummaryRepository summaryRepo;

    public DiagnosticsService(DeviceRepository deviceRepo,
                               SessionRepository sessionRepo,
                               ImuSampleRepository sampleRepo,
                               SummaryRepository summaryRepo) {
        this.deviceRepo = deviceRepo;
        this.sessionRepo = sessionRepo;
        this.sampleRepo = sampleRepo;
        this.summaryRepo = summaryRepo;
    }

    // ── Dispositivos ──────────────────────────────────────────────────────────

    @Override
    public DeviceResponse registerDevice(RegisterDeviceRequest request) {
        String deviceCode = normalizeDeviceCode(request);

        if (deviceRepo.existsByDeviceCode(deviceCode)) {
            throw new DuplicateResourceException(
                "Ya existe un dispositivo con código: " + deviceCode);
        }

        Device device = new Device();
        device.setDeviceCode(deviceCode);
        device.setDescription(request.getDescription());
        device.setRegisteredAt(Instant.now());

        Device saved = deviceRepo.save(device);
        return DeviceResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceResponse getDevice(Long deviceId) {
        return deviceRepo.findById(deviceId)
            .map(DeviceResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException("Dispositivo no encontrado: " + deviceId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DeviceResponse> getDevices(PageQuery query) {
        return deviceRepo.findAll(query).map(DeviceResponse::from);
    }

    // ── Sesiones ──────────────────────────────────────────────────────────────

    @Override
    public SessionResponse startSession(Long deviceId, StartSessionRequest request) {
        deviceRepo.findById(deviceId).orElseThrow(() ->
            new ResourceNotFoundException("Dispositivo no encontrado: " + deviceId));

        DiagnosticSession session = new DiagnosticSession();
        session.setDeviceId(deviceId);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setResult(SessionResult.PENDING);
        session.setDataOrigin(DataOrigin.TEST_DATA);
        session.setNotes(request != null ? request.getNotes() : null);
        session.setStartedAt(Instant.now());

        return SessionResponse.from(sessionRepo.save(session));
    }

    @Override
    public SessionResponse addSamples(Long sessionId, AddSamplesRequest request) {
        DiagnosticSession session = findActiveSession(sessionId);

        if (request == null || request.getSamples() == null || request.getSamples().isEmpty()) {
            throw new BusinessRuleException("El lote debe contener al menos una muestra");
        }
        for (AddSamplesRequest.SampleItem item : request.getSamples()) {
            if (item == null) {
                throw new BusinessRuleException("El lote contiene una muestra nula");
            }
        }

        Instant now = Instant.now();
        List<ImuSample> samples = request.getSamples().stream()
            .map(item -> {
                ImuSample s = new ImuSample();
                s.setSessionId(sessionId);
                s.setAxRaw(requireRaw(item.getAxRaw(), "axRaw"));
                s.setAyRaw(requireRaw(item.getAyRaw(), "ayRaw"));
                s.setAzRaw(requireRaw(item.getAzRaw(), "azRaw"));
                s.setGxRaw(requireRaw(item.getGxRaw(), "gxRaw"));
                s.setGyRaw(requireRaw(item.getGyRaw(), "gyRaw"));
                s.setGzRaw(requireRaw(item.getGzRaw(), "gzRaw"));
                s.setSampledAt(item.getSampledAt() != null ? item.getSampledAt() : now);
                return s;
            })
            .toList();

        sampleRepo.saveAll(samples);
        return SessionResponse.from(session);
    }

    @Override
    public SessionResponse completeSession(Long sessionId) {
        DiagnosticSession session = findActiveSession(sessionId);

        List<ImuSample> samples = sampleRepo.findBySessionId(sessionId);

        session.setStatus(SessionStatus.COMPLETED);
        session.setDataOrigin(DataOrigin.TEST_DATA);

        if (samples.isEmpty()) {
            // Sin muestras no hay datos suficientes para evaluar la sesión:
            // no se crea DiagnosticSummary.
            session.setResult(SessionResult.NOT_EVALUATED);
        } else {
            // Una sesión TEST_DATA solo verifica el flujo de software; nunca se
            // clasifica como PASSED ni como medición física real.
            DiagnosticSummary summary = buildSummary(sessionId, samples);
            summaryRepo.save(summary);
            session.setResult(SessionResult.OBSERVED);
        }

        session.setFinishedAt(Instant.now());

        return SessionResponse.from(sessionRepo.save(session));
    }

    @Override
    public SessionResponse cancelSession(Long sessionId, String reason) {
        DiagnosticSession session = findActiveSession(sessionId);
        String cancellationReason = normalizeCancellationReason(reason);

        session.setStatus(SessionStatus.CANCELLED);
        session.setResult(SessionResult.NOT_EVALUATED);
        session.setCancellationReason(cancellationReason);
        session.setFinishedAt(Instant.now());

        // No se sobrescriben las notas originales de la sesión.
        return SessionResponse.from(sessionRepo.save(session));
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SessionDetailResponse getSessionDetail(Long sessionId) {
        DiagnosticSession session = sessionRepo.findById(sessionId).orElseThrow(() ->
            new ResourceNotFoundException("Sesión no encontrada: " + sessionId));

        DiagnosticSummary summary = summaryRepo.findBySessionId(sessionId).orElse(null);
        return SessionDetailResponse.from(session, summary);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SessionResponse> getSessionHistory(Long deviceId, PageQuery query) {
        deviceRepo.findById(deviceId).orElseThrow(() ->
            new ResourceNotFoundException("Dispositivo no encontrado: " + deviceId));

        return sessionRepo.findByDeviceId(deviceId, query)
            .map(SessionResponse::from);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String normalizeDeviceCode(RegisterDeviceRequest request) {
        if (request == null || request.getDeviceCode() == null) {
            throw new BusinessRuleException("deviceCode es obligatorio");
        }
        String deviceCode = request.getDeviceCode().trim();
        if (deviceCode.isEmpty()) {
            throw new BusinessRuleException("deviceCode no puede estar vacío");
        }
        return deviceCode;
    }

    private String normalizeCancellationReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new BusinessRuleException("reason no puede superar 500 caracteres");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private DiagnosticSession findActiveSession(Long sessionId) {
        DiagnosticSession session = sessionRepo.findById(sessionId).orElseThrow(() ->
            new ResourceNotFoundException("Sesión no encontrada: " + sessionId));

        if (!session.isInProgress()) {
            throw new BusinessRuleException(
                "La sesión " + sessionId + " no está EN PROGRESO (estado actual: " +
                session.getStatus() + ")");
        }
        return session;
    }

    private DiagnosticSummary buildSummary(Long sessionId, List<ImuSample> samples) {
        DiagnosticSummary sum = new DiagnosticSummary();
        sum.setSessionId(sessionId);
        sum.setSampleCount(samples.size());

        AxisStats ax = ImuStatsCalculator.computeAxis(samples, s -> s.getAxRaw());
        AxisStats ay = ImuStatsCalculator.computeAxis(samples, s -> s.getAyRaw());
        AxisStats az = ImuStatsCalculator.computeAxis(samples, s -> s.getAzRaw());
        AxisStats gx = ImuStatsCalculator.computeAxis(samples, s -> s.getGxRaw());
        AxisStats gy = ImuStatsCalculator.computeAxis(samples, s -> s.getGyRaw());
        AxisStats gz = ImuStatsCalculator.computeAxis(samples, s -> s.getGzRaw());

        sum.setAxMin(ax.getMin()); sum.setAxMax(ax.getMax());
        sum.setAxAvg(ax.getAvg()); sum.setAxStd(ax.getStd());

        sum.setAyMin(ay.getMin()); sum.setAyMax(ay.getMax());
        sum.setAyAvg(ay.getAvg()); sum.setAyStd(ay.getStd());

        sum.setAzMin(az.getMin()); sum.setAzMax(az.getMax());
        sum.setAzAvg(az.getAvg()); sum.setAzStd(az.getStd());

        sum.setGxMin(gx.getMin()); sum.setGxMax(gx.getMax());
        sum.setGxAvg(gx.getAvg()); sum.setGxStd(gx.getStd());

        sum.setGyMin(gy.getMin()); sum.setGyMax(gy.getMax());
        sum.setGyAvg(gy.getAvg()); sum.setGyStd(gy.getStd());

        sum.setGzMin(gz.getMin()); sum.setGzMax(gz.getMax());
        sum.setGzAvg(gz.getAvg()); sum.setGzStd(gz.getStd());

        sum.setComputedAt(Instant.now());
        return sum;
    }

    /**
     * Convierte Integer a short conservando el rango int16 sin silencio de overflow.
     * Rechaza nulos y valores fuera del rango.
     */
    private short requireRaw(Integer value, String field) {
        if (value == null) {
            throw new BusinessRuleException(field + " es obligatorio");
        }
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new BusinessRuleException(
                "Valor RAW fuera de rango int16 en " + field + ": " + value +
                " (válido: " + Short.MIN_VALUE + " .. " + Short.MAX_VALUE + ")");
        }
        return (short) value.intValue();
    }
}
