/* Etapa 3B: MPU6050 -> BLE IMU_RAW. GPS UART independiente; buzzer manual y control BLE.
 * Arduino ESP32 Dev Module, core Espressif 3.3.12.
 * Protocolo permanente: ../../docs/ETAPA_3B_BLE.md
 */
#include <driver/i2c_master.h>
#include <esp_err.h>
#include <cstring>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <atomic>
#include "GpsTelemetry.h"
#include "ManualBuzzer.h"

constexpr char DEVICE_NAME[] = "AsistenteVial-IMU";
constexpr char SERVICE_UUID[] = "73e8ca02-41d7-45bd-9d39-7187649746ca";
constexpr char IMU_RAW_UUID[] = "e77a0a03-3641-40f2-87b7-f178622259cd";
constexpr char GPS_UUID[] = "af1b5aad-b30b-47b2-a8a5-64f152f6510e";
constexpr char BUZZER_UUID[] = "b84c0001-5120-4a8f-b98a-2415d86121a9";
constexpr uint8_t MPU_ADDRESS = 0x68;
constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;
constexpr uint32_t SAMPLE_PERIOD_MS = 250;
constexpr size_t FRAME_SIZE = 12;

BLECharacteristic *imuRaw = nullptr;
BLE2902 *cccd = nullptr;
BLECharacteristic *gpsTelemetry = nullptr;
BLE2902 *gpsCccd = nullptr;
BLECharacteristic *buzzerCharacteristic = nullptr;
BLE2902 *buzzerCccd = nullptr;
std::atomic_bool pendingBuzzerDone{false};
uint32_t lastGps = 0;
std::atomic_bool connected{false};
std::atomic_bool subscribed{false};
std::atomic_bool restartAdvertising{false};
uint32_t lastSample = 0;
i2c_master_bus_handle_t imuBus = nullptr;
i2c_master_dev_handle_t imuDevice = nullptr;
constexpr int I2C_TIMEOUT_MS = 50;
// Selected after a complete raw-block read; identity does not override this mode.
bool stopBeforeRegisterRead = false;
bool sensorIdentityConfirmed = false;

bool initializeI2c() {
  // Own this bus directly: do not initialize Wire or another I2C driver on it.
  i2c_master_bus_config_t busConfig = {};
  busConfig.i2c_port = I2C_NUM_0;
  busConfig.sda_io_num = gpio_num_t(SDA_PIN);
  busConfig.scl_io_num = gpio_num_t(SCL_PIN);
  busConfig.clk_source = I2C_CLK_SRC_DEFAULT;
  busConfig.glitch_ignore_cnt = 7;
  busConfig.flags.enable_internal_pullup = true;
  esp_err_t result = i2c_new_master_bus(&busConfig, &imuBus);
  if (result != ESP_OK) {
    Serial.printf("I2C ERROR inicio bus SDA=21 SCL=22: %s\n", esp_err_to_name(result));
    return false;
  }
  i2c_device_config_t deviceConfig = {};
  deviceConfig.dev_addr_length = I2C_ADDR_BIT_LEN_7;
  deviceConfig.device_address = MPU_ADDRESS;
  deviceConfig.scl_speed_hz = 100000;
  result = i2c_master_bus_add_device(imuBus, &deviceConfig, &imuDevice);
  if (result != ESP_OK) {
    Serial.printf("I2C ERROR registro dispositivo 0x68: %s\n", esp_err_to_name(result));
    i2c_del_master_bus(imuBus);
    imuBus = nullptr;
    return false;
  }
  Serial.println("I2C: controlador nativo ESP-IDF, 100 kHz.");
  return true;
}

bool writeRegister(uint8_t reg, uint8_t value) {
  const uint8_t command[2] = {reg, value};
  const esp_err_t result = i2c_master_transmit(imuDevice, command, sizeof(command), I2C_TIMEOUT_MS);
  if (result != ESP_OK) {
    Serial.printf("I2C ERROR escritura: reg=0x%02X valor=0x%02X: %s\n",
                  reg, value, esp_err_to_name(result));
    return false;
  }
  return true;
}

bool readRegistersWithTransaction(uint8_t reg, uint8_t *destination, size_t size, bool selectionStop) {
  uint8_t received[14]; // Separate RX storage; never copy the register address into it.
  if (destination == nullptr || size == 0 || size > sizeof(received)) return false;
  esp_err_t result;
  if (selectionStop) {
    result = i2c_master_transmit(imuDevice, &reg, 1, I2C_TIMEOUT_MS);
    if (result != ESP_OK) {
      Serial.printf("I2C ERROR seleccion reg=0x%02X (STOP): %s\n", reg, esp_err_to_name(result));
      return false;
    }
    delayMicroseconds(100);
    result = i2c_master_receive(imuDevice, received, size, I2C_TIMEOUT_MS);
  } else {
    result = i2c_master_transmit_receive(imuDevice, &reg, 1, received, size, I2C_TIMEOUT_MS);
  }
  if (result != ESP_OK) {
    Serial.printf("I2C ERROR lectura reg=0x%02X (%s): %s\n", reg,
                  selectionStop ? "STOP" : "repeated START", esp_err_to_name(result));
    return false;
  }
  // Only a successful, complete driver transaction updates the caller's buffer.
  memcpy(destination, received, size);
  return true;
}

bool readRegisters(uint8_t reg, uint8_t *destination, size_t size) {
  return readRegistersWithTransaction(reg, destination, size, stopBeforeRegisterRead);
}

bool verifyMpuIdentity() {
  bool identityReceived = false;
  uint8_t lastIdentity = 0;
  // Native combined transfer first; separate STOP transfer as compatibility fallback.
  for (int attempt = 0; attempt < 2; ++attempt) {
    const bool selectionStop = attempt == 1;
    Serial.printf("MPU INIT: WHO_AM_I con %s.\n", selectionStop ? "STOP" : "repeated START");
    uint8_t identity = 0;
    if (readRegistersWithTransaction(0x75, &identity, 1, selectionStop)) {
      identityReceived = true;
      lastIdentity = identity;
      Serial.printf("WHO_AM_I leído = 0x%02X\n", identity);
      if (identity == 0x68) {
        sensorIdentityConfirmed = true;
        Serial.println("MPU INIT: identidad MPU6050 confirmada.");
        return true;
      }
      Serial.println("MPU INIT: identidad recibida distinta de 0x68.");
    } else {
      Serial.println("MPU INIT: WHO_AM_I no recibido; fallo de transaccion I2C.");
    }
    if (attempt == 0) {
      Serial.println("MPU INIT: se probara una segunda lectura con STOP.");
      delay(2);
    }
  }
  if (identityReceived) {
    Serial.printf("ADVERTENCIA: WHO_AM_I anomalo = 0x%02X\n", lastIdentity);
    Serial.println("Modo experimental: identidad del sensor no confirmada");
    Serial.println("Solo mediciones crudas para pruebas controladas; no son detecciones validadas.");
    return true; // Communication checked, NOT a valid MPU6050 identification.
  }
  Serial.println("MPU INIT ERROR: WHO_AM_I no pudo leerse; no se publicaran muestras.");
  return false;
}

bool initializeMpu() {
  const esp_err_t probe = i2c_master_probe(imuBus, MPU_ADDRESS, I2C_TIMEOUT_MS);
  if (probe != ESP_OK) {
    Serial.printf("I2C ERROR: dispositivo 0x68 no responde: %s\n", esp_err_to_name(probe));
    return false;
  }
  Serial.println("I2C device 0x68 OK");
  // Reset, wake with X gyro PLL, all six axes enabled. No software filtering.
  Serial.println("MPU INIT: reset PWR_MGMT_1 (0x6B <- 0x80).");
  if (!writeRegister(0x6B, 0x80)) {
    Serial.println("MPU INIT ERROR: escritura del reset.");
    return false;
  }
  // An address ACK does not mean reset has finished. Check its self-clearing bit.
  delay(100);
  bool resetComplete = false;
  const uint32_t resetCheckStarted = millis();
  do {
    uint8_t power = 0;
    if (readRegisters(0x6B, &power, 1) && (power & 0x80) == 0) {
      Serial.printf("MPU INIT: reset terminado, PWR_MGMT_1=0x%02X.\n", power);
      resetComplete = true;
      break;
    }
    delay(20);
  } while (uint32_t(millis() - resetCheckStarted) < 500);
  if (!resetComplete) {
    Serial.println("MPU INIT ERROR: reset no confirmado (lectura fallida o bit DEVICE_RESET activo).");
    return false;
  }
  Serial.println("MPU INIT: wake/PLL PWR_MGMT_1 (0x6B <- 0x01).");
  if (!writeRegister(0x6B, 0x01)) {
    Serial.println("MPU INIT ERROR: wake/PLL.");
    return false;
  }
  delay(100);
  const uint8_t configRegisters[] = {0x6C, 0x1B, 0x1C};
  for (uint8_t reg : configRegisters) {
    Serial.printf("MPU INIT: configuracion 0x%02X <- 0x00.\n", reg);
    if (!writeRegister(reg, 0x00)) {
      Serial.printf("MPU INIT ERROR: configuracion del registro 0x%02X.\n", reg);
      return false;
    }
  }
  // Require a complete 14-byte transfer containing all six axes, before identity.
  uint8_t rawRegisters[14];
  if (!readRegisters(0x3B, rawRegisters, sizeof(rawRegisters))) {
    if (!readRegistersWithTransaction(0x3B, rawRegisters, sizeof(rawRegisters), true)) {
      Serial.println("MPU INIT ERROR: bloque crudo de seis ejes no recibido.");
      return false;
    }
    stopBeforeRegisterRead = true;
  }
  Serial.printf("MPU INIT: bloque crudo de seis ejes recibido (%s).\n",
                stopBeforeRegisterRead ? "STOP" : "repeated START");
  if (!verifyMpuIdentity()) return false;
  Serial.println("MPU INIT: inicializacion completada.");
  return true;
}

bool readFrame(uint8_t (&frame)[FRAME_SIZE]) {
  // 0x3B..0x48: acceleration, temperature, gyro. Registers are big-endian.
  uint8_t registers[14];
  if (!readRegisters(0x3B, registers, sizeof(registers))) return false;
  const uint8_t offsets[6] = {0, 2, 4, 8, 10, 12}; // omit temperature
  for (size_t axis = 0; axis < 6; ++axis) {
    frame[axis * 2] = registers[offsets[axis] + 1];
    frame[axis * 2 + 1] = registers[offsets[axis]];
  }
  return true;
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *) override {
    subscribed.store(false);
    cccd->setNotifications(false);
    gpsCccd->setNotifications(false);
    if (buzzerCccd) buzzerCccd->setNotifications(false);
    pendingBuzzerDone.store(false);
    connected.store(true);
    Serial.println("BLE conectado; esperando suscripcion CCCD.");
  }
  void onDisconnect(BLEServer *) override {
    connected.store(false);
    subscribed.store(false);
    pendingBuzzerDone.store(false);
    restartAdvertising.store(true);
    Serial.println("BLE desconectado; se reanudara el anuncio.");
  }
};

class BuzzerCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    Serial.println("BLE WRITE: orden de activacion de buzzer recibida de Android.");
    if (ManualBuzzer::trigger()) {
      pendingBuzzerDone.store(true);
    } else {
      Serial.println("BLE WRITE: buzzer ocupado o en cooldown; no activado.");
    }
  }
};

class BuzzerCccdCallbacks : public BLEDescriptorCallbacks {
  void onWrite(BLEDescriptor *) override {
    const bool enabled = buzzerCccd != nullptr && buzzerCccd->getNotifications();
    Serial.println(enabled ? "NOTIFY habilitado: BUZZER control/confirmacion." : "NOTIFY deshabilitado: BUZZER.");
  }
};

class SubscriptionCallbacks : public BLEDescriptorCallbacks {
  void onWrite(BLEDescriptor *) override {
    const bool enabled = cccd->getNotifications();
    subscribed.store(enabled);
    Serial.println(enabled ? "NOTIFY habilitado: IMU_RAW, 12 bytes, 4 Hz." : "NOTIFY deshabilitado.");
  }
};

void setup() {
  Serial.begin(115200);
  ManualBuzzer::begin();
  delay(300);
  Serial.println("Asistente Vial - Etapa 3B - solo mediciones crudas.");
  if (!initializeI2c()) {
    while (true) delay(1000);
  }
  if (!initializeMpu()) {
    Serial.println("ERROR MPU6050: no se publicaran muestras. Revisar conexion y reiniciar.");
    while (true) delay(1000);
  }
  Serial.println(sensorIdentityConfirmed
      ? "MPU6050 listo: identidad confirmada, 0x68, SDA=21, SCL=22."
      : "Dispositivo I2C listo en modo experimental: identidad NO confirmada, 0x68, SDA=21, SCL=22.");
  GpsTelemetry::begin();
  BLEDevice::init(DEVICE_NAME);
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  BLEService *service = server->createService(SERVICE_UUID);
  imuRaw = service->createCharacteristic(IMU_RAW_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  cccd = new BLE2902(); // Standard 0x2902, not a third custom UUID.
  cccd->setCallbacks(new SubscriptionCallbacks());
  imuRaw->addDescriptor(cccd);
  gpsTelemetry = service->createCharacteristic(GPS_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  gpsCccd = new BLE2902();
  gpsTelemetry->addDescriptor(gpsCccd);
  buzzerCharacteristic = service->createCharacteristic(
      BUZZER_UUID,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR | BLECharacteristic::PROPERTY_NOTIFY);
  buzzerCharacteristic->setCallbacks(new BuzzerCallbacks());
  buzzerCccd = new BLE2902();
  buzzerCccd->setCallbacks(new BuzzerCccdCallbacks());
  buzzerCharacteristic->addDescriptor(buzzerCccd);
  service->start();
  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  // 128-bit service in advertising packet; complete name in scan response.
  BLEAdvertisementData scanResponse;
  scanResponse.setName(DEVICE_NAME);
  advertising->setScanResponseData(scanResponse);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.printf("Anunciando %s\nServicio: %s\nIMU_RAW: %s\n", DEVICE_NAME, SERVICE_UUID, IMU_RAW_UUID);
}

void loop() {
  ManualBuzzer::poll();
  if (ManualBuzzer::checkCompleted()) {
    Serial.println("Pulso terminado: GPIO4 LOW.");
    if (pendingBuzzerDone.exchange(false)) {
      if (connected.load() && buzzerCharacteristic != nullptr && buzzerCccd != nullptr && buzzerCccd->getNotifications()) {
        const char confirmation[] = "BUZZER_DONE";
        buzzerCharacteristic->setValue((uint8_t *)confirmation, strlen(confirmation));
        buzzerCharacteristic->notify();
        Serial.println("BLE NOTIFY: BUZZER_DONE enviado a Android.");
      }
    }
  }
  GpsTelemetry::poll();
  if (restartAdvertising.exchange(false)) {
    delay(250); // Let the stack finish disconnecting before advertising again.
    BLEDevice::startAdvertising();
  }
  const uint32_t now = millis();
  if (connected.load() && subscribed.load() && uint32_t(now - lastSample) >= SAMPLE_PERIOD_MS) {
    lastSample = now; // No burst/catch-up of stale samples.
    uint8_t frame[FRAME_SIZE];
    if (readFrame(frame)) {
      if (connected.load() && subscribed.load()) {
        imuRaw->setValue(frame, sizeof(frame));
        imuRaw->notify();
        int16_t raw[6];
        for (size_t i = 0; i < 6; ++i) raw[i] = int16_t(uint16_t(frame[i * 2]) | (uint16_t(frame[i * 2 + 1]) << 8));
        Serial.printf("RAW ax=%d ay=%d az=%d gx=%d gy=%d gz=%d\n", raw[0], raw[1], raw[2], raw[3], raw[4], raw[5]);
      }
    } else Serial.println("ERROR lectura MPU6050: muestra omitida, no se envian valores sustitutos.");
  }
  if (uint32_t(now - lastGps) >= 1000) {
    lastGps = now;
    uint8_t gpsFrame[20]; GpsTelemetry::frame(gpsFrame);
    Serial.printf("GPS estado=%u sat=%s edad_ms=%lu\n", gpsFrame[1], (gpsFrame[2]&2) ? "disponibles" : "no disponibles",
                  (unsigned long)(uint32_t(gpsFrame[16]) | uint32_t(gpsFrame[17])<<8 | uint32_t(gpsFrame[18])<<16 | uint32_t(gpsFrame[19])<<24));
    if (gpsFrame[1] == 2) {
      Serial.printf("GPS posicion lat=%.7f lon=%.7f UTC_seg=%lu UTC_disponible=%u\n",
                    GpsTelemetry::latitude, GpsTelemetry::longitude, (unsigned long)GpsTelemetry::fixUtc, unsigned(gpsFrame[2]&1));
    }
    if (connected.load() && gpsCccd->getNotifications()) {
      gpsTelemetry->setValue(gpsFrame,sizeof(gpsFrame)); gpsTelemetry->notify();
    }
  }
  delay(1);
}
