package com.avigem.diagnostics.application.usecase;

import com.avigem.diagnostics.application.dto.RealBleBatchRequest;
import com.avigem.diagnostics.application.dto.RealBleBatchResponse;
import com.avigem.diagnostics.application.dto.SessionDetailResponse;
import com.avigem.diagnostics.application.dto.SessionResponse;
import com.avigem.diagnostics.application.port.in.DiagnosticsUseCase;
import com.avigem.diagnostics.domain.model.BusinessRuleException;
import com.avigem.diagnostics.domain.model.DataOrigin;
import com.avigem.diagnostics.domain.model.SessionResult;
import com.avigem.diagnostics.domain.model.SessionStatus;
import com.avigem.diagnostics.domain.port.out.DeviceRepository;
import com.avigem.diagnostics.domain.port.out.SessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas de integración de la ingesta real (REAL_BLE/REAL_MQTT) con H2 + Flyway.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RealBleIngestService - ingesta real, idempotencia y orígenes")
class RealBleIngestServiceTest {

    @Autowired private RealBleIngestService service;
    @Autowired private DiagnosticsUseCase useCase;
    @Autowired private DeviceRepository devices;
    @Autowired private SessionRepository sessions;

    private RealBleBatchRequest batch(String deviceCode, Long... sequences) {
        RealBleBatchRequest request = new RealBleBatchRequest();
        request.setDeviceCode(deviceCode);
        request.setStartedAt(Instant.now());
        List<RealBleBatchRequest.SampleItem> items = new ArrayList<>();
        for (Long sequence : sequences) {
            RealBleBatchRequest.SampleItem item = new RealBleBatchRequest.SampleItem();
            item.setSequence(sequence);
            item.setSampledAt(Instant.now());
            item.setAxRaw(100);
            item.setAyRaw(-200);
            item.setAzRaw(16384);
            item.setGxRaw(10);
            item.setGyRaw(-20);
            item.setGzRaw(30);
            items.add(item);
        }
        request.setSamples(items);
        return request;
    }

    @Test
    @DisplayName("Primera muestra MQTT crea sesión REAL_MQTT; el duplicado no se almacena")
    void mqttIngest_duplicateSequence_isIdempotent() {
        String clientId = UUID.randomUUID().toString();

        RealBleBatchResponse first = service.ingestMqtt(clientId, batch("AVIGEM-MQTT-001", 0L));
        assertThat(first.accepted()).isEqualTo(1);
        assertThat(first.duplicates()).isZero();
        assertThat(first.storedSamples()).isEqualTo(1);

        RealBleBatchResponse duplicate = service.ingestMqtt(clientId, batch("AVIGEM-MQTT-001", 0L));
        assertThat(duplicate.accepted()).isZero();
        assertThat(duplicate.duplicates()).isEqualTo(1);
        assertThat(duplicate.storedSamples()).isEqualTo(1);

        assertThat(service.recent(first.sessionId(), -1, 10)).hasSize(1);
    }

    @Test
    @DisplayName("Una sesión REAL_MQTT no acepta ingesta REAL_BLE (separación de orígenes)")
    void crossOriginIngest_isRejected() {
        String clientId = UUID.randomUUID().toString();
        RealBleBatchResponse mqtt = service.ingestMqtt(clientId, batch("AVIGEM-CROSS-001", 0L));

        assertThatThrownBy(() -> service.ingest(clientId, batch("AVIGEM-CROSS-001", 1L)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("REAL_BLE");

        assertThat(service.recent(mqtt.sessionId(), -1, 10)).hasSize(1);
    }

    @Test
    @DisplayName("deviceCode vacío se rechaza antes de crear dispositivo o sesión")
    void blankDeviceCode_isRejectedWithoutSideEffects() {
        String clientId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.ingestMqtt(clientId, batch("   ", 0L)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("deviceCode");

        assertThat(devices.findByDeviceCode("")).isEmpty();
        assertThat(sessions.findByClientSessionId(clientId)).isEmpty();
    }

    @Test
    @DisplayName("deviceCode distinto para el mismo clientSessionId se rechaza sin dispositivo huérfano")
    void deviceMismatch_isRejectedWithoutOrphanDevice() {
        String clientId = UUID.randomUUID().toString();
        service.ingestMqtt(clientId, batch("AVIGEM-MATCH-001", 0L));

        assertThatThrownBy(() -> service.ingestMqtt(clientId, batch("AVIGEM-MATCH-002", 1L)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("otro dispositivo");

        assertThat(devices.findByDeviceCode("AVIGEM-MATCH-002")).isEmpty();
    }

    @Test
    @DisplayName("Una sesión REAL_MQTT se puede completar con resumen y el segundo complete es idempotente")
    void completeRealMqtt_createsSummaryAndIsIdempotent() {
        String clientId = UUID.randomUUID().toString();
        RealBleBatchResponse ingested = service.ingestMqtt(
            clientId, batch("AVIGEM-MQTT-002", 0L, 1L));
        assertThat(ingested.storedSamples()).isEqualTo(2);

        SessionResponse completed = service.complete(clientId);
        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getResult()).isEqualTo(SessionResult.OBSERVED);
        assertThat(completed.getDataOrigin()).isEqualTo(DataOrigin.REAL_MQTT);

        SessionDetailResponse detail = useCase.getSessionDetail(ingested.sessionId());
        assertThat(detail.getDataOrigin()).isEqualTo(DataOrigin.REAL_MQTT);
        assertThat(detail.getSummary()).isNotNull();
        assertThat(detail.getSummary().getSampleCount()).isEqualTo(2);

        SessionResponse again = service.complete(clientId);
        assertThat(again.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(again.getResult()).isEqualTo(SessionResult.OBSERVED);
        assertThat(useCase.getSessionDetail(ingested.sessionId())
            .getSummary().getSampleCount()).isEqualTo(2);
    }
}
