#pragma once

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <esp_system.h>
#include <esp_random.h>
#include <ctype.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/task.h>
#if __has_include("MqttSecrets.h")
#include "MqttSecrets.h"
#else
#include "MqttSecrets.example.h"
#endif
#include "VehicleControl.h"

// Wi-Fi y MQTT viven en su propia tarea: connect()/publish() pueden bloquear segundos
// sin detener IMU, GPS, BLE ni buzzer. PubSubClient no es thread-safe: solo lo usa esta tarea.
namespace MqttTelemetry {

constexpr UBaseType_t QUEUE_LENGTH = 32;  // 1.6 s a 20 Hz; si se llena, la muestra se descarta.
constexpr uint32_t TASK_STACK_BYTES = 10240;  // Margen para el handshake TLS.
constexpr UBaseType_t TASK_PRIORITY = 1;
constexpr BaseType_t TASK_CORE = 0;  // loop() corre en el núcleo 1.
constexpr TickType_t IDLE_WAIT = pdMS_TO_TICKS(50);
constexpr uint32_t WIFI_ATTEMPT_MS = 30000;  // Con BLE activo, conectar tarda más; no interrumpir antes.

struct RawSample {
  int16_t axes[6];
  uint32_t sequence; uint16_t version; uint8_t event, range, flags;
  float accel,brake,turn; char ack[37]; char status[48];
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
uint32_t sequenceNumber = 0;
struct Command {char id[37];char op[20];};
QueueHandle_t commands=nullptr;
char commandTopic[96];
void receiveCommand(char *receivedTopic,uint8_t *payload,unsigned int length){
  if(strcmp(receivedTopic,commandTopic)!=0 || length<38 || length>=60)return;
  char text[64];memcpy(text,payload,length);text[length]=0;
  if(text[36]!='|')return;
  for(int i=0;i<36;i++){if(i==8||i==13||i==18||i==23){if(text[i]!='-')return;}else if(!isxdigit((unsigned char)text[i]))return;}
  Command c={};memcpy(c.id,text,36);strlcpy(c.op,text+37,sizeof(c.op));
  xQueueSend(commands,&c,0);
}
void pollCommands(){Command c;if(commands && xQueueReceive(commands,&c,0)==pdTRUE){
  if(strcmp(c.op,"buzzer")==0){if(!VehicleControl::duplicateCommand(c.id))ManualBuzzer::trigger();}
  else VehicleControl::command(c.id,c.op,millis());
}}
uint32_t nextWifiAttempt = 0;
uint32_t nextMqttAttempt = 0;
bool wifiReported = false;

void makeSessionId() {
  uint8_t bytes[16];
  esp_fill_random(bytes,sizeof(bytes));
  bytes[6]=(bytes[6]&0x0f)|0x40;
  bytes[8]=(bytes[8]&0x3f)|0x80;
  static constexpr char hex[]="0123456789abcdef";
  size_t out=0;
  for(size_t i=0;i<16;i++){
    if(i==4 || i==6 || i==8 || i==10)sessionId[out++]='-';
    sessionId[out++]=hex[bytes[i]>>4];sessionId[out++]=hex[bytes[i]&0x0f];
  }
  sessionId[out]=0;
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
    if(ok)mqtt.subscribe(commandTopic,1);
    Serial.println(ok ? "MQTT conectado; Diagnostics recibira IMU_RAW." :
                        "MQTT no disponible; nuevo intento en 5 s.");
    return;
  }
  mqtt.loop();
}

void publish(const RawSample &sample) {
  char payload[900];
  snprintf(payload,sizeof(payload),
    "{\"protocol\":2,\"sessionId\":\"%s\",\"deviceCode\":\"%s\",\"sequence\":%lu,"
    "\"axRaw\":%d,\"ayRaw\":%d,\"azRaw\":%d,\"gxRaw\":%d,\"gyRaw\":%d,\"gzRaw\":%d,"
    "\"profileVersion\":%u,\"event\":%u,\"range\":%u,\"flags\":%u,"
    "\"accelerationLimit\":%.6f,\"brakingLimit\":%.6f,\"turnLimit\":%.6f,\"ack\":\"%s\",\"calibrationStatus\":\"%s\"}",
    sessionId,DEVICE_CODE,(unsigned long)sample.sequence,
    sample.axes[0],sample.axes[1],sample.axes[2],sample.axes[3],sample.axes[4],sample.axes[5],
    sample.version,sample.event,sample.range,sample.flags,sample.accel,sample.brake,sample.turn,sample.ack,sample.status);
  if (!mqtt.publish(topic, payload, false)) {
    Serial.println("MQTT: muestra no publicada; no se inventan sustitutos.");
    return;
  }

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
  if(!sessionId[0])makeSessionId();
  commands=xQueueCreate(8,sizeof(Command));
  snprintf(commandTopic,sizeof(commandTopic),"avigem/devices/%s/control",DEVICE_CODE);
  if(!commands)return;
  snprintf(topic, sizeof(topic), "avigem/devices/%s/imu", DEVICE_CODE);
  if (MQTT_USE_TLS) {
    if (strlen(MQTT_CA_CERT) == 0) {
      Serial.println("MQTT deshabilitado: falta MQTT_CA_CERT para TLS.");
      return;
    }
    secureNetwork.setCACert(MQTT_CA_CERT);
  }
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setBufferSize(1024);
  mqtt.setCallback(receiveCommand);
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
RawSample makeSample(const int16_t (&axes)[6]) {
  RawSample sample={};memcpy(sample.axes,axes,sizeof(sample.axes));sample.sequence=sequenceNumber++;
  sample.version=VehicleControl::profile.version;sample.event=VehicleControl::event;sample.range=VehicleControl::range;sample.flags=VehicleControl::flags();
  sample.accel=VehicleControl::profile.accel;sample.brake=VehicleControl::profile.brake;sample.turn=VehicleControl::profile.turn;
  strlcpy(sample.ack,VehicleControl::acknowledged,37);strlcpy(sample.status,VehicleControl::status,48);return sample;
}
bool publishSample(const RawSample &sample){return samples && xQueueSend(samples,&sample,0)==pdTRUE;}
}  // namespace MqttTelemetry
