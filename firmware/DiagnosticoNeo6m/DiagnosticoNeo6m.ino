/* Prueba UART aislada NEO-6M. No IMU, BLE, eventos ni datos simulados.
 * Conexion reportada: GPS TX -> GPIO16; GPS RX -> GPIO17; GND comun.
 * GPS TX -> ESP32 RX; GPS RX -> ESP32 TX (opcional, no enviamos comandos).
 */
#include <Arduino.h>
#include <HardwareSerial.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

constexpr int GPS_RX_PIN = 16; // Conectado al TX del GPS.
constexpr int GPS_TX_PIN = 17; // Conectado al RX del GPS; no enviamos comandos.
constexpr uint32_t GPS_BAUD = 9600; // Valor de fabrica; puede haber sido reconfigurado.
constexpr uint32_t FRESH_MS = 5000;
HardwareSerial gps(2);
char sentence[128];
size_t used = 0;
uint32_t bytesReceived = 0, validSentences = 0, badSentences = 0;
uint32_t lastByte = 0, lastNmea = 0, lastFix = 0, lastUtc = 0, lastSatellites = 0, lastReport = 0;
bool started = false, hasFix = false, hasUtc = false, hasSatellites = false;
double latitude, longitude; // Nunca se imprimen antes de recibir una posicion valida.
int satellites;
char utc[9];

int hexDigit(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  return -1;
}

bool number(const char *text, double &value) {
  if (!*text) return false;
  char *end;
  value = strtod(text, &end);
  return *end == '\0' && isfinite(value);
}

bool coordinate(const char *text, const char *hemisphere, bool lat, double &value) {
  double raw;
  if (!number(text, raw) || raw < 0 || strlen(hemisphere) != 1) return false;
  const double degrees = floor(raw / 100.0);
  const double minutes = raw - degrees * 100.0;
  const double limit = lat ? 90 : 180;
  if (minutes >= 60 || degrees > limit || (degrees == limit && minutes != 0)) return false;
  const char positive = lat ? 'N' : 'E', negative = lat ? 'S' : 'W';
  if (*hemisphere != positive && *hemisphere != negative) return false;
  value = (degrees + minutes / 60.0) * (*hemisphere == negative ? -1 : 1);
  return true;
}

void readUtc(const char *text) {
  if (strlen(text) < 6) return;
  for (int i = 0; i < 6; ++i) if (text[i] < '0' || text[i] > '9') return;
  const int hh = (text[0] - '0') * 10 + text[1] - '0';
  const int mm = (text[2] - '0') * 10 + text[3] - '0';
  const int ss = (text[4] - '0') * 10 + text[5] - '0';
  if (hh > 23 || mm > 59 || ss > 60) return;
  snprintf(utc, sizeof(utc), "%02d:%02d:%02d", hh, mm, ss);
  hasUtc = true; lastUtc = millis();
}

void parseSentence() {
  char *star = strchr(sentence, '*');
  if (!star || strlen(star + 1) != 2 || hexDigit(star[1]) < 0 || hexDigit(star[2]) < 0) {
    badSentences++; return;
  }
  uint8_t checksum = 0;
  for (char *p = sentence + 1; p < star; ++p) checksum ^= uint8_t(*p);
  if (checksum != ((hexDigit(star[1]) << 4) | hexDigit(star[2]))) { badSentences++; return; }
  validSentences++; lastNmea = millis();
  Serial.printf("NMEA: %s\n", sentence); // Datos recibidos, nunca frases sinteticas.
  *star = '\0';
  char *fields[20];
  int count = 1;
  fields[0] = sentence + 1;
  for (char *p = sentence + 1; *p; ++p) {
    if (*p == ',') { *p = '\0'; if (count < 20) fields[count++] = p + 1; else return; }
  }
  if (strlen(fields[0]) != 5) return;
  const char *type = fields[0] + 2; // GP/GN talker + GGA/RMC.
  bool positionAllowed;
  int latField, lonField;
  if (strcmp(type, "GGA") == 0 && count >= 10) {
    readUtc(fields[1]);
    double quality, sats;
    positionAllowed = number(fields[6], quality) && (quality == 1 || quality == 2);
    if (number(fields[7], sats) && sats >= 0 && sats <= 99 && floor(sats) == sats) {
      satellites = int(sats); hasSatellites = true; lastSatellites = millis();
    } else hasSatellites = false;
    latField = 2; lonField = 4;
  } else if (strcmp(type, "RMC") == 0 && count >= 10) {
    readUtc(fields[1]);
    positionAllowed = strcmp(fields[2], "A") == 0;
    // Mode N (invalid) or E (estimated/dead reckoning) is not a GPS position fix.
    if (count > 12 && (strcmp(fields[12], "N") == 0 || strcmp(fields[12], "E") == 0)) positionAllowed = false;
    latField = 3; lonField = 5;
  } else return;
  double lat, lon;
  hasFix = positionAllowed && coordinate(fields[latField], fields[latField + 1], true, lat) &&
    coordinate(fields[lonField], fields[lonField + 1], false, lon);
  if (hasFix) { latitude = lat; longitude = lon; lastFix = millis(); }
}

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("Diagnostico GPS aislado. Firmware principal/IMU/BLE no se ejecutan en esta prueba.");
  if (GPS_RX_PIN < 0) {
    Serial.println("PENDIENTE: confirma el GPIO conectado al TX del GPS y configura GPS_RX_PIN. UART no iniciada.");
    return;
  }
  // Protect the existing I2C pins and USB/UART0; further board-specific availability must be confirmed.
  if (GPS_RX_PIN == 21 || GPS_RX_PIN == 22 || GPS_RX_PIN == 1 || GPS_RX_PIN == 3 ||
      GPS_TX_PIN == 21 || GPS_TX_PIN == 22 || GPS_TX_PIN == 1 || GPS_TX_PIN == 3 || GPS_TX_PIN == GPS_RX_PIN) {
    Serial.println("ERROR: GPIO en conflicto con IMU/Serial o RX y TX iguales. UART no iniciada.");
    return;
  }
  gps.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
  started = true;
  Serial.printf("UART2 RX=%d TX=%d baud=%lu. Esperando datos GPS reales.\n", GPS_RX_PIN, GPS_TX_PIN, (unsigned long)GPS_BAUD);
}

void loop() {
  if (!started) { delay(100); return; }
  while (gps.available()) {
    const char c = char(gps.read());
    bytesReceived++; lastByte = millis();
    if (c == '$') { used = 0; sentence[used++] = c; }
    else if (c == '\n') {
      if (used) { sentence[used] = '\0'; parseSentence(); used = 0; }
    } else if (c != '\r' && used) {
      if (used < sizeof(sentence) - 1) sentence[used++] = c;
      else { used = 0; badSentences++; }
    }
  }
  const uint32_t now = millis();
  if (now - lastReport >= 2000) {
    lastReport = now;
    if (!bytesReceived || now - lastByte > FRESH_MS) {
      Serial.println("SIN RECEPCION UART: no demuestra falta de senal satelital; revisar cableado, alimentacion y baud.");
    } else if (!validSentences || now - lastNmea > FRESH_MS) {
      Serial.println("BYTES RECIBIDOS, SIN NMEA VALIDA: comprobar baud/protocolo/checksum.");
    } else if (!hasFix || now - lastFix > FRESH_MS) {
      Serial.println("GPS SIN POSICION VALIDA: NMEA recibida, sin fix satelital valido o fix caducado.");
    } else {
      Serial.printf("UBICACION VALIDA: lat=%.7f lon=%.7f\n", latitude, longitude);
    }
    const bool recentNmea = validSentences && now - lastNmea <= FRESH_MS;
    if (recentNmea && hasUtc && now - lastUtc <= FRESH_MS) Serial.printf("UTC reportada por GPS: %s (sin fix no se certifica su exactitud)\n", utc);
    else Serial.println("UTC: no disponible actualmente.");
    if (recentNmea && hasSatellites && now - lastSatellites <= FRESH_MS) Serial.printf("Satelites usados (GGA): %d\n", satellites);
    else Serial.println("Satelites usados: no disponibles actualmente.");
    Serial.printf("Bytes=%lu NMEA checksum OK=%lu invalidas=%lu\n", (unsigned long)bytesReceived,
      (unsigned long)validSentences, (unsigned long)badSentences);
  }
  delay(1);
}
