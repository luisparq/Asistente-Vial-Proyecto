#pragma once
#include <Arduino.h>
#include <esp_timer.h>
#include <atomic>

// Manual diagnostic only. No BLE commands, automatic alerts or SENSOR events.
// Same GPIO, pulse and cooldown as DiagnosticoBuzzer.
namespace ManualBuzzer {
constexpr uint8_t PIN = 4;
constexpr uint32_t PULSE_MS = 300;
constexpr uint32_t COOLDOWN_MS = 2000;
esp_timer_handle_t timer = nullptr;
std::atomic_bool sounding{false};
std::atomic_bool completed{false};
bool used = false;
uint32_t startedAt = 0;
char command[16];
size_t length = 0;
bool overflow = false;

void finish(void *) {
  digitalWrite(PIN, LOW);
  sounding.store(false);
  completed.store(true);
}

void stop() {
  if (timer) esp_timer_stop(timer);
  digitalWrite(PIN, LOW);
  sounding.store(false);
  completed.store(false);
}

void begin() {
  digitalWrite(PIN, LOW);
  pinMode(PIN, OUTPUT);
  esp_timer_create_args_t args = {};
  args.callback = finish;
  args.name = "manual-buzzer";
  if (esp_timer_create(&args, &timer) != ESP_OK) {
    timer = nullptr;
    Serial.println("Buzzer: temporizador no disponible; prueba deshabilitada.");
    return;
  }
  Serial.println("Buzzer GPIO4 apagado. PITAR: 300 ms; STOP: apagar. Nueva linea.");
}

void execute() {
  command[length] = '\0';
  if (!overflow && strcmp(command, "STOP") == 0) {
    stop();
    Serial.println("Buzzer detenido.");
  } else if (!overflow && strcmp(command, "PITAR") == 0) {
    const uint32_t now = millis();
    if (!timer) Serial.println("Buzzer: prueba deshabilitada.");
    else if (sounding.load() || (used && uint32_t(now - startedAt) < COOLDOWN_MS)) {
      Serial.println("Espera 2 segundos entre pruebas.");
    } else {
      startedAt = now;
      used = true;
      completed.store(false);
      sounding.store(true);
      digitalWrite(PIN, HIGH);
      // Independent one-shot: even existing I2C/advertising delays cannot hold
      // the output HIGH waiting for the next loop iteration.
      if (esp_timer_start_once(timer, PULSE_MS * 1000ULL) != ESP_OK) {
        stop();
        Serial.println("Buzzer: error de temporizador; salida apagada.");
      } else Serial.println("Prueba manual: GPIO4 HIGH durante 300 ms.");
    }
  } else if (length || overflow) {
    Serial.println("Comandos buzzer: PITAR / STOP (con Nueva linea).");
  }
  length = 0;
  overflow = false;
}

void poll() {
  if (completed.exchange(false)) Serial.println("Pulso terminado: GPIO4 LOW.");
  // Bounded input processing; no wait for newline and no delay.
  for (uint8_t n = 0; n < 32 && Serial.available(); ++n) {
    const char c = char(Serial.read());
    if (c == '\n') execute();
    else if (c != '\r') {
      if (length < sizeof(command) - 1) command[length++] = c;
      else overflow = true;
    }
  }
}
}
