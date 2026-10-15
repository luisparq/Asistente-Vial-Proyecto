/*
 * DiagnosticoImu.ino
 *
 * Diagnostico de solo lectura para modulos IMU compatibles por I2C.
 * ESP32: SDA=21, SCL=22, 100 kHz, Monitor Serie=115200.
 *
 * No ejecuta reset, wake-up ni escribe registros de configuracion. La unica
 * escritura I2C es el byte de seleccion del registro que se desea leer.
 */
#include <Wire.h>

constexpr uint8_t SDA_PIN = 21;
constexpr uint8_t SCL_PIN = 22;
constexpr uint32_t I2C_CLOCK_HZ = 100000;
constexpr uint32_t SERIAL_BAUD = 115200;
constexpr uint8_t WHO_AM_I = 0x75;
constexpr uint8_t DATA_START = 0x3B;
constexpr size_t DATA_LENGTH = 14;
constexpr uint8_t ADDRESSES_TO_CHECK[] = {0x68, 0x69};
constexpr uint8_t WHO_READ_ATTEMPTS = 5;
constexpr uint8_t SAMPLE_ATTEMPTS = 3;

struct ReadResult {
  uint8_t txStatus = 255;
  uint8_t requested = 0;
  uint8_t returned = 0;
  uint8_t captured = 0;
  uint8_t bytes[DATA_LENGTH] = {};
};

const char *i2cStatusName(uint8_t status) {
  switch (status) {
    case 0: return "OK";
    case 1: return "DATA_TOO_LONG";
    case 2: return "NACK_ADDRESS";
    case 3: return "NACK_DATA";
    case 4: return "OTHER_ERROR";
    case 5: return "TIMEOUT";
    default: return "UNKNOWN";
  }
}

// stopAfterSelection=false uses the usual repeated-START transaction.
// true is intentionally also tested for WHO_AM_I as a compatibility check.
ReadResult readRegisters(uint8_t address, uint8_t reg, uint8_t length,
                         bool stopAfterSelection = false) {
  ReadResult result;
  result.requested = length;

  Wire.beginTransmission(address);
  Wire.write(reg);                         // Selects a register; does not configure it.
  result.txStatus = Wire.endTransmission(stopAfterSelection);
  if (result.txStatus != 0) return result;

  if (stopAfterSelection) delayMicroseconds(100);
  result.returned = Wire.requestFrom(address, length, static_cast<uint8_t>(true));
  while (Wire.available() && result.captured < DATA_LENGTH) {
    result.bytes[result.captured++] = static_cast<uint8_t>(Wire.read());
  }
  // Drain an unexpected excess so it cannot contaminate the next operation.
  while (Wire.available()) (void)Wire.read();
  return result;
}

bool complete(const ReadResult &result) {
  return result.txStatus == 0 && result.returned == result.requested &&
         result.captured == result.requested;
}

void printAddress(uint8_t address) {
  if (address < 0x10) Serial.print('0');
  Serial.print(address, HEX);
}

void printReadLine(const char *label, uint8_t address, uint8_t reg,
                   const char *method, const ReadResult &result) {
  Serial.print(label);
  Serial.print(" addr=0x"); printAddress(address);
  Serial.print(" reg=0x"); printAddress(reg);
  Serial.print(" method="); Serial.print(method);
  Serial.print(" tx_status="); Serial.print(result.txStatus);
  Serial.print('('); Serial.print(i2cStatusName(result.txStatus)); Serial.print(')');
  Serial.print(" requested="); Serial.print(result.requested);
  Serial.print(" returned="); Serial.print(result.returned);
  Serial.print(" captured="); Serial.print(result.captured);
  if (complete(result)) {
    Serial.print(" value=0x"); printAddress(result.bytes[0]);
  }
  Serial.println();
}

bool isPresent(uint8_t address) {
  Wire.beginTransmission(address);
  const uint8_t status = Wire.endTransmission(true);
  Serial.print("I2C_CHECK addr=0x"); printAddress(address);
  Serial.print(" status="); Serial.print(status);
  Serial.print('('); Serial.print(i2cStatusName(status)); Serial.println(')');
  return status == 0;
}

void scanI2c() {
  Serial.println("I2C_SCAN_BEGIN");
  uint8_t found = 0;
  for (uint8_t address = 0x03; address <= 0x77; ++address) {
    Wire.beginTransmission(address);
    const uint8_t status = Wire.endTransmission(true);
    if (status == 0) {
      Serial.print("I2C_FOUND addr=0x"); printAddress(address); Serial.println();
      ++found;
    } else if (status == 4 || status == 5) {
      Serial.print("I2C_SCAN_ERROR addr=0x"); printAddress(address);
      Serial.print(" status="); Serial.print(status);
      Serial.print('('); Serial.print(i2cStatusName(status)); Serial.println(')');
    }
  }
  Serial.print("I2C_SCAN_END found="); Serial.println(found);
}

int16_t be16(const uint8_t *data) {
  return static_cast<int16_t>((static_cast<uint16_t>(data[0]) << 8) | data[1]);
}

float accelLsbPerG(uint8_t accelConfig) {
  switch ((accelConfig >> 3) & 0x03) {
    case 0: return 16384.0f; // +/-2 g
    case 1: return 8192.0f;  // +/-4 g
    case 2: return 4096.0f;  // +/-8 g
    default: return 2048.0f; // +/-16 g
  }
}

float gyroLsbPerDps(uint8_t gyroConfig) {
  switch ((gyroConfig >> 3) & 0x03) {
    case 0: return 131.0f;  // +/-250 dps
    case 1: return 65.5f;   // +/-500 dps
    case 2: return 32.8f;   // +/-1000 dps
    default: return 16.4f;  // +/-2000 dps
  }
}

uint8_t fullScale(uint8_t config) {
  return (config >> 3) & 0x03;
}

void printScale(uint8_t accelConfig, uint8_t gyroConfig) {
  const uint8_t accelFs = fullScale(accelConfig);
  const uint8_t gyroFs = fullScale(gyroConfig);
  const int accelRangeG = 2 << accelFs;
  const int gyroRangeDps = 250 << gyroFs;
  Serial.print("SCALE accel_config=0x"); printAddress(accelConfig);
  Serial.print(" afs_sel="); Serial.print(accelFs);
  Serial.print(" range=+/-"); Serial.print(accelRangeG);
  Serial.print("g lsb_per_g="); Serial.print(accelLsbPerG(accelConfig), 1);
  Serial.print(" gyro_config=0x"); printAddress(gyroConfig);
  Serial.print(" fs_sel="); Serial.print(gyroFs);
  Serial.print(" range=+/-"); Serial.print(gyroRangeDps);
  Serial.print("dps lsb_per_dps="); Serial.println(gyroLsbPerDps(gyroConfig), 1);
}

bool printRegister(uint8_t address, uint8_t reg, const char *name, uint8_t &value) {
  const ReadResult result = readRegisters(address, reg, 1);
  printReadLine(name, address, reg, "REPEATED_START", result);
  if (!complete(result)) return false;
  value = result.bytes[0];
  return true;
}

void printMeasurements(uint8_t address, uint8_t accelConfig, uint8_t gyroConfig) {
  const float accelScale = accelLsbPerG(accelConfig);
  const float gyroScale = gyroLsbPerDps(gyroConfig);
  for (uint8_t attempt = 1; attempt <= SAMPLE_ATTEMPTS; ++attempt) {
    const ReadResult result = readRegisters(address, DATA_START, DATA_LENGTH);
    Serial.print("RAW_READ n="); Serial.print(attempt);
    Serial.print(" addr=0x"); printAddress(address);
    Serial.print(" reg=0x3B tx_status="); Serial.print(result.txStatus);
    Serial.print('('); Serial.print(i2cStatusName(result.txStatus)); Serial.print(')');
    Serial.print(" requested=14 returned="); Serial.print(result.returned);
    Serial.print(" captured="); Serial.println(result.captured);
    if (!complete(result)) continue;

    const int16_t ax = be16(&result.bytes[0]);
    const int16_t ay = be16(&result.bytes[2]);
    const int16_t az = be16(&result.bytes[4]);
    const int16_t temp = be16(&result.bytes[6]);
    const int16_t gx = be16(&result.bytes[8]);
    const int16_t gy = be16(&result.bytes[10]);
    const int16_t gz = be16(&result.bytes[12]);
    Serial.printf("RAW n=%u ax=%d ay=%d az=%d temp=%d gx=%d gy=%d gz=%d\n",
                  attempt, ax, ay, az, temp, gx, gy, gz);
    Serial.printf("CONVERTED n=%u ax_g=%.4f ay_g=%.4f az_g=%.4f gx_dps=%.3f gy_dps=%.3f gz_dps=%.3f\n",
                  attempt, ax / accelScale, ay / accelScale, az / accelScale,
                  gx / gyroScale, gy / gyroScale, gz / gyroScale);
    delay(250);
  }
}

void diagnoseAddress(uint8_t address) {
  Serial.print("TARGET_BEGIN addr=0x"); printAddress(address); Serial.println();
  if (!isPresent(address)) {
    Serial.println("TARGET_END no responde");
    return;
  }

  for (uint8_t attempt = 1; attempt <= WHO_READ_ATTEMPTS; ++attempt) {
    const bool stopAfterSelection = (attempt % 2 == 0);
    const ReadResult result = readRegisters(address, WHO_AM_I, 1, stopAfterSelection);
    Serial.print("WHO_ATTEMPT n="); Serial.print(attempt); Serial.print(' ');
    printReadLine("WHO_AM_I", address, WHO_AM_I,
                  stopAfterSelection ? "STOP" : "REPEATED_START", result);
    delay(20);
  }

  uint8_t pwrMgmt1 = 0, pwrMgmt2 = 0, config = 0, gyroConfig = 0, accelConfig = 0;
  const bool power1Ok = printRegister(address, 0x6B, "PWR_MGMT_1", pwrMgmt1);
  const bool power2Ok = printRegister(address, 0x6C, "PWR_MGMT_2", pwrMgmt2);
  (void)power2Ok;
  (void)printRegister(address, 0x19, "SMPLRT_DIV", config);
  (void)printRegister(address, 0x1A, "CONFIG", config);
  const bool gyroOk = printRegister(address, 0x1B, "GYRO_CONFIG", gyroConfig);
  const bool accelOk = printRegister(address, 0x1C, "ACCEL_CONFIG", accelConfig);
  (void)printRegister(address, 0x23, "FIFO_EN", config);
  (void)printRegister(address, 0x6A, "USER_CTRL", config);

  if (power1Ok && (pwrMgmt1 & 0x40)) {
    Serial.println("WARNING PWR_MGMT_1.SLEEP=1: este sketch no despierta el sensor; las muestras pueden no ser evaluables.");
  }
  if (gyroOk && accelOk) {
    printScale(accelConfig, gyroConfig);
    printMeasurements(address, accelConfig, gyroConfig);
  } else {
    Serial.println("MEASUREMENTS_SKIPPED: no se pudo leer GYRO_CONFIG y/o ACCEL_CONFIG completos.");
  }
  Serial.print("TARGET_END addr=0x"); printAddress(address); Serial.println();
}

void setup() {
  Serial.begin(SERIAL_BAUD);
  delay(400);
  Serial.println();
  Serial.println("=== IMU_DIAGNOSTIC_READ_ONLY_BEGIN ===");
  Serial.println("INFO no reset, no wake, no cambios de escala; solo seleccion y lectura de registros.");
  Wire.begin(SDA_PIN, SCL_PIN, I2C_CLOCK_HZ);
  Wire.setTimeOut(50);
  scanI2c();
  for (uint8_t address : ADDRESSES_TO_CHECK) diagnoseAddress(address);
  Serial.println("=== IMU_DIAGNOSTIC_READ_ONLY_END ===");
  Serial.println("Copie todo el bloque delimitado por BEGIN y END.");
}

void loop() {
  delay(1000); // El diagnostico corre una vez para que el Monitor Serie sea facil de copiar.
}
