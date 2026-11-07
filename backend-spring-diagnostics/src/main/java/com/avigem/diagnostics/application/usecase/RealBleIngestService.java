package com.avigem.diagnostics.application.usecase;

import com.avigem.diagnostics.application.dto.ImuSampleResponse;
import com.avigem.diagnostics.application.dto.RealBleBatchRequest;
import com.avigem.diagnostics.application.dto.RealBleBatchResponse;
import com.avigem.diagnostics.application.dto.SessionResponse;
import com.avigem.diagnostics.domain.model.*;
import com.avigem.diagnostics.domain.port.out.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class RealBleIngestService {
    private final DeviceRepository devices;
    private final SessionRepository sessions;
    private final ImuSampleRepository samples;
    private final SummaryRepository summaries;

    public RealBleIngestService(DeviceRepository devices, SessionRepository sessions,
                                ImuSampleRepository samples, SummaryRepository summaries) {
        this.devices = devices;
        this.sessions = sessions;
        this.samples = samples;
        this.summaries = summaries;
    }

    public RealBleBatchResponse ingest(String clientSessionId, RealBleBatchRequest request) {
        return ingest(clientSessionId, request, DataOrigin.REAL_BLE,
            "Registrado automáticamente por Android para REAL_BLE",
            "Captura técnica enviada por Android; sin validación física");
    }

    public RealBleBatchResponse ingestMqtt(String clientSessionId, RealBleBatchRequest request) {
        return ingest(clientSessionId, request, DataOrigin.REAL_MQTT,
            "Registrado automáticamente por MQTT para REAL_MQTT",
            "Captura técnica enviada directamente por ESP32 vía MQTT; sin validación física");
    }

    private RealBleBatchResponse ingest(String clientSessionId, RealBleBatchRequest request,
                                        DataOrigin origin, String deviceDescription, String notes) {
        // Validar el payload antes de cualquier efecto lateral (crear dispositivo/sesión).
        String clientId = requireUuid(clientSessionId);
        String deviceCode = requireDeviceCode(request);
        List<RealBleBatchRequest.SampleItem> requested = requireSamples(request);

        DiagnosticSession existingSession = sessions.findByClientSessionId(clientId).orElse(null);
        DiagnosticSession session;
        Device device;
        if (existingSession != null) {
            // Primero validar origen/estado y el dispositivo de la sesión existente.
            if (!origin.equals(existingSession.getDataOrigin()) || !existingSession.isInProgress()) {
                throw new BusinessRuleException(
                    "La sesión " + origin + " no está disponible para recibir muestras");
            }
            device = devices.findById(existingSession.getDeviceId()).orElseThrow(() ->
                new ResourceNotFoundException(
                    "Dispositivo de la sesión no encontrado: " + existingSession.getDeviceId()));
            if (!device.getDeviceCode().equals(deviceCode)) {
                throw new BusinessRuleException(
                    "clientSessionId ya pertenece a otro dispositivo");
            }
            session = existingSession;
        } else {
            device = devices.findByDeviceCode(deviceCode).orElseGet(() -> {
                Device created = new Device();
                created.setDeviceCode(deviceCode);
                created.setDescription(deviceDescription);
                created.setRegisteredAt(Instant.now());
                return devices.save(created);
            });
            DiagnosticSession created = new DiagnosticSession();
            created.setDeviceId(device.getId());
            created.setClientSessionId(clientId);
            created.setStatus(SessionStatus.IN_PROGRESS);
            created.setResult(SessionResult.PENDING);
            created.setDataOrigin(origin);
            created.setNotes(notes);
            created.setStartedAt(request.getStartedAt() != null ? request.getStartedAt() : Instant.now());
            session = sessions.save(created);
        }

        List<Long> requestedSequences = requested.stream()
            .map(RealBleBatchRequest.SampleItem::getSequence).toList();
        if (new HashSet<>(requestedSequences).size() != requestedSequences.size()) {
            throw new BusinessRuleException("El lote contiene secuencias repetidas");
        }
        Set<Long> existing = new HashSet<>(samples.findSequenceNumbers(session.getId(), requestedSequences));
        List<ImuSample> fresh = requested.stream()
            .filter(item -> !existing.contains(item.getSequence()))
            .map(item -> toDomain(session.getId(), item))
            .toList();
        if (!fresh.isEmpty()) samples.saveAll(fresh);
        return new RealBleBatchResponse(session.getId(), clientId, fresh.size(), existing.size(),
            samples.countBySessionId(session.getId()));
    }

    public SessionResponse complete(String clientSessionId) {
        DiagnosticSession session = sessions.findByClientSessionId(requireUuid(clientSessionId))
            .orElseThrow(() -> new ResourceNotFoundException("Sesión real no encontrada"));
        if (!DataOrigin.REAL_BLE.equals(session.getDataOrigin())
                && !DataOrigin.REAL_MQTT.equals(session.getDataOrigin())) {
            throw new BusinessRuleException("El origen de la sesión no es REAL_BLE/REAL_MQTT");
        }
        if (!session.isInProgress()) return SessionResponse.from(session);
        List<ImuSample> stored = samples.findBySessionId(session.getId());
        session.setStatus(SessionStatus.COMPLETED);
        session.setResult(stored.isEmpty() ? SessionResult.NOT_EVALUATED : SessionResult.OBSERVED);
        session.setFinishedAt(Instant.now());
        if (!stored.isEmpty()) summaries.save(buildSummary(session.getId(), stored));
        return SessionResponse.from(sessions.save(session));
    }

    @Transactional(readOnly = true)
    public List<ImuSampleResponse> recent(Long sessionId, long afterSequence, int limit) {
        DiagnosticSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada: " + sessionId));
        if (DataOrigin.TEST_DATA.equals(session.getDataOrigin())) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return samples.findRecentBySessionId(sessionId, afterSequence, safeLimit).stream()
            .map(ImuSampleResponse::from).toList();
    }

    private ImuSample toDomain(Long sessionId, RealBleBatchRequest.SampleItem item) {
        ImuSample sample = new ImuSample();
        sample.setSessionId(sessionId);
        sample.setSequenceNumber(item.getSequence());
        sample.setSampledAt(item.getSampledAt() != null ? item.getSampledAt() : Instant.now());
        sample.setAxRaw(raw(item.getAxRaw(), "axRaw"));
        sample.setAyRaw(raw(item.getAyRaw(), "ayRaw"));
        sample.setAzRaw(raw(item.getAzRaw(), "azRaw"));
        sample.setGxRaw(raw(item.getGxRaw(), "gxRaw"));
        sample.setGyRaw(raw(item.getGyRaw(), "gyRaw"));
        sample.setGzRaw(raw(item.getGzRaw(), "gzRaw"));
        return sample;
    }

    private String requireDeviceCode(RealBleBatchRequest request) {
        if (request == null || request.getDeviceCode() == null) {
            throw new BusinessRuleException("deviceCode es obligatorio");
        }
        String deviceCode = request.getDeviceCode().trim();
        if (deviceCode.isEmpty() || deviceCode.length() > 64) {
            throw new BusinessRuleException("deviceCode debe tener entre 1 y 64 caracteres");
        }
        return deviceCode;
    }

    private List<RealBleBatchRequest.SampleItem> requireSamples(RealBleBatchRequest request) {
        List<RealBleBatchRequest.SampleItem> batch = request.getSamples();
        if (batch == null || batch.isEmpty()) {
            throw new BusinessRuleException("El lote debe contener al menos una muestra");
        }
        if (batch.size() > 200) {
            throw new BusinessRuleException("No se pueden enviar más de 200 muestras por lote");
        }
        for (RealBleBatchRequest.SampleItem item : batch) {
            if (item == null) {
                throw new BusinessRuleException("El lote contiene una muestra nula");
            }
            if (item.getSequence() == null || item.getSequence() < 0) {
                throw new BusinessRuleException("sequence debe ser un entero no negativo");
            }
        }
        return batch;
    }

    private short raw(Integer value, String field) {
        if (value == null || value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new BusinessRuleException(field + " debe estar en rango int16");
        }
        return value.shortValue();
    }

    private String requireUuid(String value) {
        try { return UUID.fromString(value).toString(); }
        catch (Exception ex) { throw new BusinessRuleException("clientSessionId debe ser UUID"); }
    }

    private DiagnosticSummary buildSummary(Long sessionId, List<ImuSample> stored) {
        DiagnosticSummary sum = new DiagnosticSummary();
        sum.setSessionId(sessionId); sum.setSampleCount(stored.size());
        setAxes(sum,
            ImuStatsCalculator.computeAxis(stored, ImuSample::getAxRaw),
            ImuStatsCalculator.computeAxis(stored, ImuSample::getAyRaw),
            ImuStatsCalculator.computeAxis(stored, ImuSample::getAzRaw),
            ImuStatsCalculator.computeAxis(stored, ImuSample::getGxRaw),
            ImuStatsCalculator.computeAxis(stored, ImuSample::getGyRaw),
            ImuStatsCalculator.computeAxis(stored, ImuSample::getGzRaw));
        sum.setComputedAt(Instant.now());
        return sum;
    }

    private void setAxes(DiagnosticSummary s, AxisStats ax, AxisStats ay, AxisStats az,
                         AxisStats gx, AxisStats gy, AxisStats gz) {
        s.setAxMin(ax.getMin()); s.setAxMax(ax.getMax()); s.setAxAvg(ax.getAvg()); s.setAxStd(ax.getStd());
        s.setAyMin(ay.getMin()); s.setAyMax(ay.getMax()); s.setAyAvg(ay.getAvg()); s.setAyStd(ay.getStd());
        s.setAzMin(az.getMin()); s.setAzMax(az.getMax()); s.setAzAvg(az.getAvg()); s.setAzStd(az.getStd());
        s.setGxMin(gx.getMin()); s.setGxMax(gx.getMax()); s.setGxAvg(gx.getAvg()); s.setGxStd(gx.getStd());
        s.setGyMin(gy.getMin()); s.setGyMax(gy.getMax()); s.setGyAvg(gy.getAvg()); s.setGyStd(gy.getStd());
        s.setGzMin(gz.getMin()); s.setGzMax(gz.getMax()); s.setGzAvg(gz.getAvg()); s.setGzStd(gz.getStd());
    }
}
