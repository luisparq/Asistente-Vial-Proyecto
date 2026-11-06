package com.avigem.diagnostics.infrastructure.mqtt;

import com.avigem.diagnostics.application.dto.RealBleBatchRequest;
import com.avigem.diagnostics.application.usecase.RealBleIngestService;
import com.avigem.diagnostics.config.MqttProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(prefix = "diagnostics.mqtt", name = "enabled", havingValue = "true")
public class MqttTelemetrySubscriber implements MqttCallbackExtended {
    private static final Logger log = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);
    private final MqttProperties properties;
    private final ObjectMapper json;
    private final RealBleIngestService ingestService;
    private MqttClient client;

    public MqttTelemetrySubscriber(MqttProperties properties, ObjectMapper json,
                                   RealBleIngestService ingestService) {
        this.properties = properties;
        this.json = json;
        this.ingestService = ingestService;
    }

    @PostConstruct
    void connect() throws MqttException {
        client = new MqttClient(properties.getBrokerUri(), properties.getClientId(), new MemoryPersistence());
        client.setCallback(this);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername());
            options.setPassword(properties.getPassword().toCharArray());
        }
        client.connect(options);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe(properties.getTopic(), Math.max(0, Math.min(2, properties.getQos())));
            log.info("MQTT conectado; suscrito a {}", properties.getTopic());
        } catch (MqttException ex) {
            log.error("MQTT no pudo suscribirse", ex);
        }
    }

    @Override public void connectionLost(Throwable cause) {
        log.warn("MQTT desconectado; se intentará reconexión automática: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            MqttImuPayload payload = json.readValue(
                new String(message.getPayload(), StandardCharsets.UTF_8), MqttImuPayload.class);
            RealBleBatchRequest request = toRequest(payload);
            ingestService.ingestMqtt(payload.sessionId(), request);
        } catch (Exception ex) {
            log.warn("Mensaje MQTT IMU rechazado en {}: {}", topic, ex.getMessage());
        }
    }

    private RealBleBatchRequest toRequest(MqttImuPayload p) {
        RealBleBatchRequest.SampleItem item = new RealBleBatchRequest.SampleItem();
        item.setSequence(p.sequence());
        item.setSampledAt(Instant.now()); // El frame actual no contiene fecha UTC.
        item.setAxRaw(p.axRaw()); item.setAyRaw(p.ayRaw()); item.setAzRaw(p.azRaw());
        item.setGxRaw(p.gxRaw()); item.setGyRaw(p.gyRaw()); item.setGzRaw(p.gzRaw());
        RealBleBatchRequest request = new RealBleBatchRequest();
        request.setDeviceCode(p.deviceCode());
        request.setStartedAt(Instant.now());
        request.setSamples(List.of(item));
        return request;
    }

    @Override public void deliveryComplete(IMqttDeliveryToken token) { }

    @PreDestroy
    void disconnect() {
        if (client == null) return;
        try { if (client.isConnected()) client.disconnect(); client.close(); }
        catch (MqttException ex) { log.debug("MQTT cierre incompleto", ex); }
    }
}
