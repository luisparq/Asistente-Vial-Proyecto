#pragma once

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <esp_system.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/task.h>
#include "MqttSecrets.h"

// Wi-Fi y MQTT viven en su propia tarea: connect()/publish() pueden bloquear segundos
// sin detener IMU, GPS, BLE ni buzzer. PubSubClient no es thread-safe: solo lo usa esta tarea.
namespace MqttTelemetry {

constexpr UBaseType_t QUEUE_LENGTH = 8;  // 2 s a 4 Hz; si se llena, la muestra se descarta.
constexpr uint32_t TASK_STACK_BYTES = 10240;  // Margen para el handshake TLS.
constexpr UBaseType_t TASK_PRIORITY = 1;
constexpr BaseType_t TASK_CORE = 0;  // loop() corre en el núcleo 1.
constexpr TickType_t IDLE_WAIT = pdMS_TO_TICKS(50);
constexpr uint32_t WIFI_ATTEMPT_MS = 30000;  // Con BLE activo, conectar tarda más; no interrumpir antes.

struct RawSample {
  int16_t axes[6];
};

WiFiClient plainNetwork;
WiFiClientSecure secureNetwork;

Client &networkClient() {
  return MQTT_USE_TLS ? static_cast<Client &>(secureNetwork) : static_cast<Client &>(plainNetwork);
}

PubSubClient mqtt(networkClient());
QueueHandle_t samples = nullptr;
char topic[96];
char sessionId[37];
uint64_t sequenceNumber = 0;
uint32_t nextWifiAttempt = 0;
uint32_t nextMqttAttempt = 0;
bool wifiReported = false;

void makeSessionId() {
  const uint32_t a = esp_random(), b = esp_random(), c = esp_random(), d = esp_random();
  snprintf(sessionId, sizeof(sessionId), "%08lx-%04lx-4%03lx-a%03lx-%08lx%04lx",
           (unsigned long)a, (unsigned long)(b & 0xffff),
           (unsigned long)(c & 0xfff), (unsigned long)(d & 0xfff),
           (unsigned long)b, (unsigned long)(c & 0xffff));
}

void reportWifiDisconnect(WiFiEvent_t, WiFiEventInfo_t info) {
  // Solo al cambiar: el driver repite el evento en cada reintento. 201 = red no encontrada.
  static uint8_t lastReason = 0;
  if (info.wifi_sta_disconnected.reason == lastReason) return;
  lastReason = info.wifi_sta_disconnected.reason;
  Serial.printf("Wi-Fi desconectado: motivo %u\n", unsigned(lastReason));
}

void startWifiAttempt(uint32_t now) {
  nextWifiAttempt = now + WIFI_ATTEMPT_MS;
  wifiReported = false;
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.println("Wi-Fi: conexion iniciada en tarea propia.");
}

void maintainConnection() {
  const uint32_t now = millis();
  if (WiFi.status() != WL_CONNECTED) {
    wifiReported = false;
    if (mqtt.connected()) mqtt.disconnect();
    if (int32_t(now - nextWifiAttempt) >= 0) startWifiAttempt(now);
    return;
  }
  if (!wifiReported) {
    wifiReported = true;
    Serial.printf("Wi-Fi conectado: %s\n", WiFi.localIP().toString().c_str());
  }
  if (!mqtt.connected()) {
    if (int32_t(now - nextMqttAttempt) < 0) return;
    nextMqttAttempt = now + 5000;
    const String clientId = String("avigem-one-") + String((uint32_t)ESP.getEfuseMac(), HEX);
    const bool ok = mqtt.connect(clientId.c_str(), MQTT_USERNAME, MQTT_PASSWORD);
    Serial.println(ok ? "MQTT conectado; Diagnostics recibira IMU_RAW." :
                        "MQTT no disponible; nuevo intento en 5 s.");
    return;
  }
  mqtt.loop();
}

void publish(const RawSample &sample) {
  char payload[320];
  snprintf(payload, sizeof(payload),
    "{\"sessionId\":\"%s\",\"deviceCode\":\"%s\",\"sequence\":%llu,"
    "\"axRaw\":%d,\"ayRaw\":%d,\"azRaw\":%d,\"gxRaw\":%d,\"gyRaw\":%d,\"gzRaw\":%d}",
    sessionId, DEVICE_CODE, (unsigned long long)sequenceNumber,
    sample.axes[0], sample.axes[1], sample.axes[2], sample.axes[3], sample.axes[4], sample.axes[5]);
  if (!mqtt.publish(topic, payload, false)) {
    Serial.println("MQTT: muestra no publicada; no se inventan sustitutos.");
    return;
  }
  ++sequenceNumber;
}

void run(void *) {
  WiFi.onEvent(reportWifiDisconnect, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);
  startWifiAttempt(millis());
  RawSample sample;
  for (;;) {
    maintainConnection();
    if (!mqtt.connected()) {
      xQueueReset(samples);  // Nunca publicar muestras antiguas al reconectar.
      vTaskDelay(IDLE_WAIT);
    } else if (xQueueReceive(samples, &sample, IDLE_WAIT) == pdTRUE) {
      publish(sample);
    }
  }
}

void begin() {
  makeSessionId();
  snprintf(topic, sizeof(topic), "avigem/devices/%s/imu", DEVICE_CODE);
  if (MQTT_USE_TLS) {
    if (strlen(MQTT_CA_CERT) == 0) {
      Serial.println("MQTT deshabilitado: falta MQTT_CA_CERT para TLS.");
      return;
    }
    secureNetwork.setCACert(MQTT_CA_CERT);
  }
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setBufferSize(384);
  mqtt.setSocketTimeout(1);
  mqtt.setKeepAlive(20);
  QueueHandle_t queue = xQueueCreate(QUEUE_LENGTH, sizeof(RawSample));
  if (queue == nullptr) {
    Serial.println("MQTT deshabilitado: sin memoria para la cola.");
    return;
  }
  samples = queue;
  if (xTaskCreatePinnedToCore(run, "mqtt-telemetry", TASK_STACK_BYTES, nullptr, TASK_PRIORITY, nullptr, TASK_CORE) != pdPASS) {
    samples = nullptr;
    vQueueDelete(queue);
    Serial.println("MQTT deshabilitado: no se pudo crear la tarea.");
    return;
  }
  Serial.printf("MQTT preparado en tarea propia: topic=%s\n", topic);
}

// Llamada desde loop(): nunca espera. Si la cola esta llena, descarta la muestra.
bool publishRaw(const int16_t (&axes)[6]) {
  if (samples == nullptr) return false;
  RawSample sample;
  memcpy(sample.axes, axes, sizeof(sample.axes));
  return xQueueSend(samples, &sample, 0) == pdTRUE;
}

}  // namespace MqttTelemetry
