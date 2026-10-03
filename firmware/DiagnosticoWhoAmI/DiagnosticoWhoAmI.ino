// Prueba aislada: lee 0x75 en 0x68. No escribe ni reinicia el sensor.
#include <Wire.h>

constexpr uint8_t DEVICE_ADDRESS = 0x68;
constexpr uint8_t WHO_AM_I_REGISTER = 0x75;

void setup() {
  Serial.begin(115200);
  delay(300);
  if (!Wire.begin(21, 22, 100000)) {
    Serial.println("ERROR: no se pudo iniciar I2C.");
    while (true) delay(1000);
  }
  Wire.setTimeOut(50);
}

void loop() {
  Wire.beginTransmission(DEVICE_ADDRESS);
  Wire.write(WHO_AM_I_REGISTER); // Solo selecciona el registro a leer.
  const uint8_t status = Wire.endTransmission(false);
  if (status != 0) {
    Serial.printf("ERROR I2C: endTransmission=%u\n", unsigned(status));
  } else {
    const size_t received = Wire.requestFrom(DEVICE_ADDRESS, size_t(1), true);
    if (received == 1 && Wire.available() == 1) {
      const int value = Wire.read();
      if (value >= 0) Serial.printf("0x%02X\n", unsigned(value));
      else Serial.println("ERROR: no hay byte recibido.");
    } else {
      Serial.printf("ERROR: recibidos=%u, disponibles=%d\n", unsigned(received), Wire.available());
      while (Wire.available()) Wire.read();
    }
  }
  delay(1000);
}
