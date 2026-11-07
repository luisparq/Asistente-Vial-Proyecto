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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(prefix = "diagnostics.mqtt", name = "enabled", havingValue = "true")
public class MqttTelemetrySubscriber implements MqttCallbackExtended {
    private static final Logger log = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);
    private static final Pattern TELEMETRY_TOPIC =
        Pattern.compile("^[^/]+/devices/([^/]+)/imu$");
    private static final long RECONNECT_DELAY_MS = 5_000L;

    private final MqttProperties properties;
    private final ObjectMapper json;
    private final RealBleIngestService ingestService;
    private volatile MqttClient client;
    private volatile boolean closing;
    private Thread connectThread;

    public MqttTelemetrySubscriber(MqttProperties properties, ObjectMapper json,
                                   RealBleIngestService ingestService) {
        this.properties = properties;
        this.json = json;
        this.ingestService = ingestService;
    }

    /**
     * La conexión inicial se hace fuera del arranque de Spring: un broker caído
     * no debe impedir que la API HTTP arranque. Se reintenta cada 5 segundos.
     */
    @PostConstruct
    void start() {
        connectThread = new Thread(this::connectWithRetry, "mqtt-telemetry-subscriber");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void connectWithRetry() {
        while (!closing) {
            MqttClient candidate = null;
            try {
                candidate = new MqttClient(
                    properties.getBrokerUri(), properties.getClientId(), new MemoryPersistence());
                candidate.setCallback(this);
                client = candidate;

                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                options.setConnectionTimeout(10);
                options.setKeepAliveInterval(30);
                if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
                    options.setUserName(properties.getUsername());
                    options.setPassword(properties.getPassword().toCharArray());
                }
                candidate.connect(options);
                return;
            } catch (MqttException ex) {
                log.warn("MQTT no disponible ({}); reintento en {} ms: {}",
                    properties.getBrokerUri(), RECONNECT_DELAY_MS, ex.getMessage());
                closeQuietly(candidate);
                client = null;
            }

            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        MqttClient current = client;
        if (current == null) return;
        try {
            current.subscribe(properties.getTopic(), Math.max(0, Math.min(2, properties.getQos())));
            log.info("MQTT conectado; suscrito a {}", properties.getTopic());
        } catch (MqttException ex) {
            log.error("MQTT no pudo suscribirse", ex);
        }
    }

    @Override public void connectionLost(Throwable cause) {
        String detail = cause != null && cause.getMessage() != null
            ? cause.getMessage() : "sin detalle";
        log.warn("MQTT desconectado; se intentará reconexión automática: {}", detail);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String topicDeviceCode = deviceCodeFromTopic(topic);
            if (topicDeviceCode == null) {
                log.warn("Mensaje MQTT rechazado: tópico no reconocido {}", topic);
                return;
            }

            MqttImuPayload payload = json.readValue(
                new String(message.getPayload(), StandardCharsets.UTF_8), MqttImuPayload.class);
            if (payload == null) {
                log.warn("Mensaje MQTT rechazado en {}: payload vacío", topic);
                return;
            }
            if (payload.sessionId() == null || payload.sessionId().isBlank()) {
                log.warn("Mensaje MQTT rechazado en {}: sessionId vacío", topic);
                return;
            }
            if (payload.deviceCode() == null || payload.deviceCode().isBlank()) {
                log.warn("Mensaje MQTT rechazado en {}: deviceCode vacío", topic);
                return;
            }
            if (!topicDeviceCode.equals(payload.deviceCode().trim())) {
                log.warn("Mensaje MQTT rechazado en {}: deviceCode del payload ({}) no coincide con el tópico",
                    topic, payload.deviceCode());
                return;
            }

            RealBleBatchRequest request = toRequest(payload);
            ingestService.ingestMqtt(payload.sessionId(), request);
        } catch (Exception ex) {
            log.warn("Mensaje MQTT IMU rechazado en {}: {}", topic, ex.getMessage());
        }
    }

    static String deviceCodeFromTopic(String topic) {
        if (topic == null) return null;
        Matcher matcher = TELEMETRY_TOPIC.matcher(topic);
        return matcher.matches() ? matcher.group(1) : null;
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
        closing = true;
        Thread thread = connectThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        MqttClient current = client;
        if (current == null) return;
        try {
            if (current.isConnected()) current.disconnect();
            current.close();
        } catch (MqttException ex) {
            log.debug("MQTT cierre incompleto", ex);
        }
    }

    private void closeQuietly(MqttClient candidate) {
        if (candidate == null) return;
        try {
            if (candidate.isConnected()) candidate.disconnect();
            candidate.close();
        } catch (MqttException ex) {
            log.debug("MQTT cierre incompleto tras conexión fallida", ex);
        }
    }
}
