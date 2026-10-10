// Prueba manual aislada: buzzer activo (+) GPIO4, (-) GND.
// No utiliza IMU, GPS ni BLE. No emite sonidos al arrancar.
#include <Arduino.h>

constexpr uint8_t BUZZER_PIN = 4;
constexpr uint32_t PULSE_MS = 300;
constexpr uint32_t COOLDOWN_MS = 2000;
bool sounding = false;
bool used = false;
uint32_t startedAt = 0;
char command[16];
size_t commandLength = 0;
bool overflow = false;

void stopBuzzer() {
  digitalWrite(BUZZER_PIN, LOW);
  sounding = false;
}

void executeCommand() {
  command[commandLength] = '\0';
  if (!overflow && strcmp(command, "STOP") == 0) {
    stopBuzzer();
    Serial.println("Buzzer detenido.");
  } else if (!overflow && strcmp(command, "PITAR") == 0) {
    const uint32_t now = millis();
    if (sounding || (used && uint32_t(now - startedAt) < COOLDOWN_MS)) {
      Serial.println("Espera 2 segundos entre pruebas.");
    } else {
      startedAt = now;
      used = true;
      sounding = true;
      digitalWrite(BUZZER_PIN, HIGH);
      Serial.println("Prueba manual: GPIO4 HIGH durante 300 ms.");
    }
  } else if (commandLength || overflow) {
    Serial.println("Comandos: PITAR / STOP (con Nueva linea).");
  }
  commandLength = 0;
  overflow = false;
}

void setup() {
  digitalWrite(BUZZER_PIN, LOW);
  pinMode(BUZZER_PIN, OUTPUT);
  stopBuzzer();
  Serial.begin(115200);
  Serial.println("Diagnostico buzzer activo GPIO4. Inicialmente apagado.");
  Serial.println("Enviar PITAR: pulso de 300 ms. STOP: apagar inmediatamente.");
}

void loop() {
  if (sounding && uint32_t(millis() - startedAt) >= PULSE_MS) {
    stopBuzzer();
    Serial.println("Pulso terminado: GPIO4 LOW.");
  }
  // Lectura acotada para no retrasar el apagado automatico.
  for (uint8_t n = 0; n < 32 && Serial.available(); ++n) {
    const char c = char(Serial.read());
    if (c == '\n') executeCommand();
    else if (c != '\r') {
      if (commandLength < sizeof(command) - 1) command[commandLength++] = c;
      else overflow = true;
    }
  }
}
