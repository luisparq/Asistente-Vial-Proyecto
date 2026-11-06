/* AVIGEM One: prueba aislada IMU -> Wi-Fi -> MQTT -> Diagnostics.
 * No modifica el firmware BLE principal. Datos RAW experimentales, 4 Hz.
 * Requiere la librería PubSubClient y un MqttSecrets.h local.
 */
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <driver/i2c_master.h>
#include <esp_system.h>
#include "MqttSecrets.h"

constexpr uint8_t MPU_ADDRESS = 0x68;
constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;
constexpr uint32_t SAMPLE_PERIOD_MS = 250;
constexpr int I2C_TIMEOUT_MS = 50;

WiFiClient plainNetwork;
WiFiClientSecure secureNetwork;
Client &networkClient() {
  return MQTT_USE_TLS ? static_cast<Client &>(secureNetwork) : static_cast<Client &>(plainNetwork);
}
PubSubClient mqtt(networkClient());
i2c_master_bus_handle_t imuBus = nullptr;
i2c_master_dev_handle_t imuDevice = nullptr;
char topic[96];
char sessionId[37];
uint64_t sequenceNumber = 0;
uint32_t lastSample = 0;
uint32_t nextReconnect = 0;

bool readRegisters(uint8_t reg, uint8_t *destination, size_t size) {
  return i2c_master_transmit_receive(imuDevice, &reg, 1, destination, size, I2C_TIMEOUT_MS) == ESP_OK;
}

bool writeRegister(uint8_t reg, uint8_t value) {
  const uint8_t command[2] = {reg, value};
  return i2c_master_transmit(imuDevice, command, sizeof(command), I2C_TIMEOUT_MS) == ESP_OK;
}

bool initializeImu() {
  i2c_master_bus_config_t bus = {};
  bus.i2c_port = I2C_NUM_0;
  bus.sda_io_num = gpio_num_t(SDA_PIN);
  bus.scl_io_num = gpio_num_t(SCL_PIN);
  bus.clk_source = I2C_CLK_SRC_DEFAULT;
  bus.glitch_ignore_cnt = 7;
  bus.flags.enable_internal_pullup = true;
  if (i2c_new_master_bus(&bus, &imuBus) != ESP_OK) return false;

  i2c_device_config_t device = {};
  device.dev_addr_length = I2C_ADDR_BIT_LEN_7;
  device.device_address = MPU_ADDRESS;
  device.scl_speed_hz = 100000;
  if (i2c_master_bus_add_device(imuBus, &device, &imuDevice) != ESP_OK) return false;
  if (i2c_master_probe(imuBus, MPU_ADDRESS, I2C_TIMEOUT_MS) != ESP_OK) return false;

  if (!writeRegister(0x6B, 0x01) || !writeRegister(0x6C, 0x00) ||
      !writeRegister(0x1B, 0x00) || !writeRegister(0x1C, 0x00)) return false;
  delay(100);

  uint8_t raw[14];
  if (!readRegisters(0x3B, raw, sizeof(raw))) return false;
  uint8_t identity = 0;
  if (readRegisters(0x75, &identity, 1)) {
    Serial.printf("WHO_AM_I leído = 0x%02X\n", identity);
    if (identity != 0x68) Serial.println("ADVERTENCIA: identidad del sensor no confirmada.");
  }
  return true;
}

bool readAxes(int16_t (&axes)[6]) {
  uint8_t raw[14];
  if (!readRegisters(0x3B, raw, sizeof(raw))) return false;
  const uint8_t offsets[6] = {0, 2, 4, 8, 10, 12};
  for (size_t i = 0; i < 6; ++i) {
    axes[i] = int16_t((uint16_t(raw[offsets[i]]) << 8) | raw[offsets[i] + 1]);
  }
  return true;
}

void makeSessionId() {
  uint32_t a = esp_random(), b = esp_random(), c = esp_random(), d = esp_random();
  snprintf(sessionId, sizeof(sessionId), "%08lx-%04lx-4%03lx-a%03lx-%08lx%04lx",
           (unsigned long)a, (unsigned long)(b & 0xffff),
           (unsigned long)(c & 0xfff), (unsigned long)(d & 0xfff),
           (unsigned long)b, (unsigned long)(c & 0xffff));
}

void connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print('.'); }
  Serial.printf(" conectado: %s\n", WiFi.localIP().toString().c_str());
}

void reconnectMqtt() {
  if (mqtt.connected() || millis() < nextReconnect) return;
  nextReconnect = millis() + 5000;
  String clientId = String("avigem-one-") + String((uint32_t)ESP.getEfuseMac(), HEX);
  bool ok = mqtt.connect(clientId.c_str(), MQTT_USERNAME, MQTT_PASSWORD);
  Serial.println(ok ? "MQTT conectado." : "MQTT no disponible; nuevo intento en 5 s.");
}

void publishAxes(const int16_t (&v)[6]) {
  char payload[320];
  snprintf(payload, sizeof(payload),
    "{\"sessionId\":\"%s\",\"deviceCode\":\"%s\",\"sequence\":%llu,"
    "\"axRaw\":%d,\"ayRaw\":%d,\"azRaw\":%d,\"gxRaw\":%d,\"gyRaw\":%d,\"gzRaw\":%d}",
    sessionId, DEVICE_CODE, (unsigned long long)sequenceNumber,
    v[0], v[1], v[2], v[3], v[4], v[5]);
  if (mqtt.publish(topic, payload, false)) {
    Serial.printf("MQTT #%llu ax=%d ay=%d az=%d gx=%d gy=%d gz=%d\n",
      (unsigned long long)sequenceNumber, v[0], v[1], v[2], v[3], v[4], v[5]);
    ++sequenceNumber;
  } else Serial.println("MQTT: muestra no publicada; no se inventan sustitutos.");
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("AVIGEM One - Diagnóstico MQTT experimental");
  if (!initializeImu()) {
    Serial.println("ERROR IMU: no se publicarán datos.");
    while (true) delay(1000);
  }
  makeSessionId();
  snprintf(topic, sizeof(topic), "avigem/devices/%s/imu", DEVICE_CODE);
  connectWifi();
  if (MQTT_USE_TLS) {
    if (strlen(MQTT_CA_CERT) == 0) {
      Serial.println("ERROR MQTT TLS: falta MQTT_CA_CERT.");
      while (true) delay(1000);
    }
    secureNetwork.setCACert(MQTT_CA_CERT);
  }
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setBufferSize(384);
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) connectWifi();
  reconnectMqtt();
  mqtt.loop();
  const uint32_t now = millis();
  if (mqtt.connected() && uint32_t(now - lastSample) >= SAMPLE_PERIOD_MS) {
    lastSample = now;
    int16_t axes[6];
    if (readAxes(axes)) publishAxes(axes);
    else Serial.println("ERROR IMU: muestra omitida.");
  }
  delay(1);
}
