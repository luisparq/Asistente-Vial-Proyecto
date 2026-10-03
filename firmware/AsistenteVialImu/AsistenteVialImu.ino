/* Etapa 3B: MPU6050 -> BLE IMU_RAW. No eventos, GPS, buzzer ni detección.
 * Arduino ESP32 Dev Module, core Espressif 3.3.12.
 * Protocolo permanente: ../../docs/ETAPA_3B_BLE.md
 */
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <atomic>

constexpr char DEVICE_NAME[] = "AsistenteVial-IMU";
constexpr char SERVICE_UUID[] = "73e8ca02-41d7-45bd-9d39-7187649746ca";
constexpr char IMU_RAW_UUID[] = "e77a0a03-3641-40f2-87b7-f178622259cd";
constexpr uint8_t MPU_ADDRESS = 0x68;
constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;
constexpr uint32_t SAMPLE_PERIOD_MS = 250;
constexpr size_t FRAME_SIZE = 12;

BLECharacteristic *imuRaw = nullptr;
BLE2902 *cccd = nullptr;
std::atomic_bool connected{false};
std::atomic_bool subscribed{false};
std::atomic_bool restartAdvertising{false};
uint32_t lastSample = 0;
// Selected only after a successful WHO_AM_I check; also used for raw samples.
bool stopBeforeRegisterRead = true;

bool writeRegister(uint8_t reg, uint8_t value) {
  Wire.beginTransmission(MPU_ADDRESS);
  Wire.write(reg);
  Wire.write(value);
  const uint8_t result = Wire.endTransmission(true);
  if (result != 0) {
    Serial.printf("I2C ERROR escritura: direccion=0x%02X registro=0x%02X valor=0x%02X endTransmission=%u\n",
                  MPU_ADDRESS, reg, value, unsigned(result));
    return false;
  }
  return true;
}

bool readRegistersWithTransaction(uint8_t reg, uint8_t *destination, size_t size, bool selectionStop) {
  // Discard any unread RX bytes before starting a new register transaction.
  while (Wire.available() > 0) Wire.read();
  Wire.beginTransmission(MPU_ADDRESS);
  // This byte selects the register; it is never assigned to destination.
  if (Wire.write(reg) != 1) {
    Wire.endTransmission(true);
    Serial.printf("I2C ERROR: no se pudo encolar registro 0x%02X.\n", reg);
    return false;
  }
  const uint8_t result = Wire.endTransmission(selectionStop);
  if (result != 0) {
    Serial.printf("I2C ERROR seleccion de registro: reg=0x%02X endTransmission=%u\n", reg, unsigned(result));
    return false;
  }
  if (selectionStop) delayMicroseconds(100);
  // requestFrom returns a BYTE COUNT. Only Wire.read returns register content.
  const size_t received = Wire.requestFrom(MPU_ADDRESS, size, true);
  if (received != size || size_t(Wire.available()) < size) {
    Serial.printf("I2C ERROR lectura: reg=0x%02X esperados=%u recibidos=%u disponibles=%d\n",
                  reg, unsigned(size), unsigned(received), Wire.available());
    while (Wire.available()) Wire.read();
    return false;
  }
  for (size_t i = 0; i < size; ++i) {
    const int value = Wire.read();
    if (value < 0) {
      Serial.printf("I2C ERROR buffer de lectura: reg=0x%02X byte=%u\n", reg, unsigned(i));
      return false;
    }
    destination[i] = uint8_t(value);
  }
  return true;
}

bool readRegisters(uint8_t reg, uint8_t *destination, size_t size) {
  return readRegistersWithTransaction(reg, destination, size, stopBeforeRegisterRead);
}

bool verifyMpuIdentity() {
  // First try a separate write/read with STOP; then repeated START if needed.
  for (int attempt = 0; attempt < 2; ++attempt) {
    const bool selectionStop = attempt == 0;
    Serial.printf("MPU INIT: WHO_AM_I con %s.\n", selectionStop ? "STOP" : "repeated START");
    uint8_t identity = 0;
    if (readRegistersWithTransaction(0x75, &identity, 1, selectionStop)) {
      Serial.printf("WHO_AM_I leído = 0x%02X\n", identity);
      if (identity == 0x68) {
        stopBeforeRegisterRead = selectionStop;
        Serial.printf("MPU INIT: identidad confirmada; lecturas con %s.\n", selectionStop ? "STOP" : "repeated START");
        return true;
      }
      Serial.println("MPU INIT: identidad recibida distinta de 0x68.");
    } else {
      Serial.println("MPU INIT: WHO_AM_I no recibido; fallo de transaccion I2C.");
    }
    if (attempt == 0) {
      Serial.println("MPU INIT: se probara una segunda lectura con repeated START.");
      delay(2);
    }
  }
  Serial.println("MPU INIT ERROR: ninguna de las dos transacciones confirma WHO_AM_I=0x68. No se acepta 0x75 ni se publicaran muestras.");
  return false;
}

bool initializeMpu() {
  Wire.beginTransmission(MPU_ADDRESS);
  const uint8_t probe = Wire.endTransmission(true);
  if (probe != 0) {
    Serial.printf("I2C ERROR: dispositivo 0x68 no responde; endTransmission=%u\n", unsigned(probe));
    return false;
  }
  Serial.println("I2C device 0x68 OK");
  // Verify identity before reset/configuration. No unrelated device is configured.
  if (!verifyMpuIdentity()) return false;
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
    connected.store(true);
    Serial.println("BLE conectado; esperando suscripcion CCCD.");
  }
  void onDisconnect(BLEServer *) override {
    connected.store(false);
    subscribed.store(false);
    restartAdvertising.store(true);
    Serial.println("BLE desconectado; se reanudara el anuncio.");
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
  delay(300);
  Serial.println("Asistente Vial - Etapa 3B - solo mediciones crudas.");
  if (!Wire.begin(SDA_PIN, SCL_PIN, 100000)) {
    Serial.println("I2C ERROR: Wire.begin fallo (SDA=21, SCL=22, 100 kHz).");
    while (true) delay(1000);
  }
  Wire.setTimeOut(50);
  if (!initializeMpu()) {
    Serial.println("ERROR MPU6050: no se publicaran muestras. Revisar conexion y reiniciar.");
    while (true) delay(1000);
  }
  Serial.println("MPU6050 listo: 0x68, SDA=21, SCL=22.");
  BLEDevice::init(DEVICE_NAME);
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  BLEService *service = server->createService(SERVICE_UUID);
  imuRaw = service->createCharacteristic(IMU_RAW_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  cccd = new BLE2902(); // Standard 0x2902, not a third custom UUID.
  cccd->setCallbacks(new SubscriptionCallbacks());
  imuRaw->addDescriptor(cccd);
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
  delay(1);
}
