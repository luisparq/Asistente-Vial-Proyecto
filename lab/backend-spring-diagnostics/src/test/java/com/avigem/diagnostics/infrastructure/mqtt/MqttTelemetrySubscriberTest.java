package com.avigem.diagnostics.infrastructure.mqtt;

import com.avigem.diagnostics.application.dto.RealBleBatchRequest;
import com.avigem.diagnostics.application.usecase.RealBleIngestService;
import com.avigem.diagnostics.config.MqttProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MqttTelemetrySubscriber - suscripción y procesamiento de telemetría")
class MqttTelemetrySubscriberTest {

    private MqttProperties properties;
    private ObjectMapper objectMapper;
    @Mock private RealBleIngestService ingestService;
    @Mock private MqttClient mqttClient;

    private MqttTelemetrySubscriber subscriber;

    @BeforeEach
    void setUp() {
        properties = new MqttProperties();
        properties.setEnabled(true);
        properties.setBrokerUri("tcp://127.0.0.1:1883");
        properties.setClientId("avigem-diagnostics-test");
        properties.setTopic("avigem/devices/+/imu");
        properties.setQos(1);

        objectMapper = new ObjectMapper();
        subscriber = new MqttTelemetrySubscriber(properties, objectMapper, ingestService);
        ReflectionTestUtils.setField(subscriber, "client", mqttClient);
    }

    @Test
    @DisplayName("connectComplete suscribe al tópico avigem/devices/+/imu con el QoS configurado")
    void connectComplete_subscribesToWildcardTopic() throws MqttException {
        subscriber.connectComplete(false, "tcp://127.0.0.1:1883");

        verify(mqttClient, times(1)).subscribe("avigem/devices/+/imu", 1);
    }

    @Test
    @DisplayName("messageArrived deserializa payload MQTT e invoca ingestMqtt con sessionId y batch request")
    void messageArrived_ingestsTelemetryPayload() {
        String jsonPayload = "{\"sessionId\":\"a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d\",\"deviceCode\":\"AVIGEM-ONE-001\",\"sequence\":42,\"axRaw\":120,\"ayRaw\":-340,\"azRaw\":16400,\"gxRaw\":15,\"gyRaw\":-25,\"gzRaw\":3}";

        MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
        subscriber.messageArrived("avigem/devices/AVIGEM-ONE-001/imu", message);

        ArgumentCaptor<RealBleBatchRequest> requestCaptor = ArgumentCaptor.forClass(RealBleBatchRequest.class);
        verify(ingestService, times(1)).ingestMqtt(eq("a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d"), requestCaptor.capture());

        RealBleBatchRequest captured = requestCaptor.getValue();
        assertThat(captured.getDeviceCode()).isEqualTo("AVIGEM-ONE-001");
        assertThat(captured.getSamples()).hasSize(1);

        RealBleBatchRequest.SampleItem sample = captured.getSamples().get(0);
        assertThat(sample.getSequence()).isEqualTo(42L);
        assertThat(sample.getAxRaw()).isEqualTo(120);
        assertThat(sample.getAyRaw()).isEqualTo(-340);
        assertThat(sample.getAzRaw()).isEqualTo(16400);
        assertThat(sample.getGxRaw()).isEqualTo(15);
        assertThat(sample.getGyRaw()).isEqualTo(-25);
        assertThat(sample.getGzRaw()).isEqualTo(3);
    }

    @Test
    @DisplayName("messageArrived con payload JSON inválido no lanza excepción ni llama a ingestMqtt")
    void messageArrived_invalidJson_doesNotThrow() {
        MqttMessage badMessage = new MqttMessage("INVALID_JSON_PAYLOAD".getBytes(StandardCharsets.UTF_8));
        subscriber.messageArrived("avigem/devices/AVIGEM-ONE-001/imu", badMessage);

        verify(ingestService, never()).ingestMqtt(any(), any());
    }

    @Test
    @DisplayName("messageArrived rechaza deviceCode del payload distinto al del tópico")
    void messageArrived_deviceMismatch_isRejected() {
        String jsonPayload = "{\"sessionId\":\"a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d\",\"deviceCode\":\"AVIGEM-ONE-002\",\"sequence\":42,\"axRaw\":120,\"ayRaw\":-340,\"azRaw\":16400,\"gxRaw\":15,\"gyRaw\":-25,\"gzRaw\":3}";
        MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));

        subscriber.messageArrived("avigem/devices/AVIGEM-ONE-001/imu", message);

        verify(ingestService, never()).ingestMqtt(any(), any());
    }

    @Test
    @DisplayName("messageArrived rechaza deviceCode vacío")
    void messageArrived_blankDeviceCode_isRejected() {
        String jsonPayload = "{\"sessionId\":\"a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d\",\"deviceCode\":\"  \",\"sequence\":42,\"axRaw\":120,\"ayRaw\":-340,\"azRaw\":16400,\"gxRaw\":15,\"gyRaw\":-25,\"gzRaw\":3}";
        MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));

        subscriber.messageArrived("avigem/devices/AVIGEM-ONE-001/imu", message);

        verify(ingestService, never()).ingestMqtt(any(), any());
    }

    @Test
    @DisplayName("messageArrived rechaza sessionId ausente")
    void messageArrived_missingSessionId_isRejected() {
        String jsonPayload = "{\"deviceCode\":\"AVIGEM-ONE-001\",\"sequence\":42,\"axRaw\":120,\"ayRaw\":-340,\"azRaw\":16400,\"gxRaw\":15,\"gyRaw\":-25,\"gzRaw\":3}";
        MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));

        subscriber.messageArrived("avigem/devices/AVIGEM-ONE-001/imu", message);

        verify(ingestService, never()).ingestMqtt(any(), any());
    }

    @Test
    @DisplayName("messageArrived rechaza tópicos que no cumplen avigem/devices/{code}/imu")
    void messageArrived_unknownTopic_isRejected() {
        String jsonPayload = "{\"sessionId\":\"a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d\",\"deviceCode\":\"AVIGEM-ONE-001\",\"sequence\":42,\"axRaw\":120,\"ayRaw\":-340,\"azRaw\":16400,\"gxRaw\":15,\"gyRaw\":-25,\"gzRaw\":3}";
        MqttMessage message = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));

        subscriber.messageArrived("otro/topico/imu", message);

        verify(ingestService, never()).ingestMqtt(any(), any());
    }
}
