#pragma once
#include <Arduino.h>
#include <HardwareSerial.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
// NMEA parser reused from DiagnosticoNeo6m; no configuration commands sent.
namespace GpsTelemetry {
constexpr uint32_t FRESH_MS = 5000;
HardwareSerial uart(2);
char sentence[128]; size_t used = 0;
bool received = false, nmea = false, fix = false, everFix = false;
bool utcValid = false, fixUtcValid = false, satsValid = false;
uint32_t lastByte = 0, lastNmea = 0, lastFix = 0, lastSats = 0;
uint32_t utc = 0, fixUtc = 0;
uint8_t sats = 0;
double latitude = 0, longitude = 0;
int hexDigit(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  return -1;
}
bool number(const char *s, double &value) {
  if (!*s) return false;
  char *end; value = strtod(s, &end); return *end == 0 && isfinite(value);
}
bool coordinate(const char *s, const char *hemisphere, bool lat, double &value) {
  double raw;
  if (!number(s, raw) || raw < 0 || strlen(hemisphere) != 1) return false;
  double degrees = floor(raw / 100), minutes = raw - degrees * 100;
  double limit = lat ? 90 : 180;
  if (minutes >= 60 || degrees > limit || (degrees == limit && minutes != 0)) return false;
  char positive = lat ? 'N' : 'E', negative = lat ? 'S' : 'W';
  if (*hemisphere != positive && *hemisphere != negative) return false;
  value = (degrees + minutes / 60) * (*hemisphere == negative ? -1 : 1); return true;
}
void readUtc(const char *s) {
  utcValid = false;
  if (strlen(s) < 6) return;
  for (int i = 0; i < 6; i++) if (s[i] < '0' || s[i] > '9') return;
  int h = (s[0]-'0')*10+s[1]-'0', m = (s[2]-'0')*10+s[3]-'0', sec = (s[4]-'0')*10+s[5]-'0';
  if (h > 23 || m > 59 || sec > 59) return;
  utc = h*3600+m*60+sec; utcValid = true;
}
void parse() {
  char *star = strchr(sentence, '*');
  if (!star || strlen(star+1) != 2 || hexDigit(star[1]) < 0 || hexDigit(star[2]) < 0) return;
  uint8_t checksum = 0;
  for (char *p = sentence+1; p < star; p++) checksum ^= uint8_t(*p);
  if (checksum != ((hexDigit(star[1])<<4)|hexDigit(star[2]))) return;
  nmea = true; lastNmea = millis(); *star = 0;
  char *fields[20]; int count = 1; fields[0] = sentence+1;
  for (char *p=sentence+1; *p; p++) if (*p == ',') {
    *p=0; if (count < 20) fields[count++]=p+1; else return;
  }
  if (strlen(fields[0]) != 5) return;
  const char *type = fields[0]+2; bool allowed; int latField, lonField;
  if (strcmp(type,"GGA") == 0 && count >= 10) {
    readUtc(fields[1]); double quality, satellites;
    allowed = number(fields[6],quality) && (quality==1 || quality==2);
    satsValid = number(fields[7],satellites) && satellites>=0 && satellites<=99 && floor(satellites)==satellites;
    if (satsValid) { sats=uint8_t(satellites); lastSats=millis(); }
    latField=2; lonField=4;
  } else if (strcmp(type,"RMC") == 0 && count >= 10) {
    readUtc(fields[1]); allowed = strcmp(fields[2],"A") == 0;
    // Only autonomous / differential mode; reject simulated or estimated fixes.
    if (count>12 && *fields[12] && strcmp(fields[12],"A") && strcmp(fields[12],"D")) allowed=false;
    latField=3; lonField=5;
  } else return;
  double lat, lon;
  fix=allowed && coordinate(fields[latField],fields[latField+1],true,lat) && coordinate(fields[lonField],fields[lonField+1],false,lon);
  if (fix) { latitude=lat; longitude=lon; lastFix=millis(); everFix=true; fixUtcValid=utcValid; fixUtc=utc; }
}
void begin() { uart.begin(9600,SERIAL_8N1,16,17); Serial.println("GPS UART2 RX=16 TX=17 9600; esperando NMEA real."); }
void poll() {
  // Bounded work per loop; never wait for UART bytes or a satellite fix.
  for (int budget=0; budget<256 && uart.available(); budget++) {
    char c=char(uart.read()); received=true; lastByte=millis();
    if (c=='$') { used=0; sentence[used++]=c; }
    else if (c=='\n') { if (used) { sentence[used]=0; parse(); used=0; } }
    else if (c!='\r' && used) { if (used<sizeof(sentence)-1) sentence[used++]=c; else used=0; }
  }
}
void put32(uint8_t *p,uint32_t value) { for (int i=0;i<4;i++) p[i]=uint8_t(value>>(8*i)); }
void frame(uint8_t (&out)[20]) {
  memset(out,0,sizeof(out)); const uint32_t now=millis();
  uint32_t age=everFix ? uint32_t(now-lastFix) : UINT32_MAX;
  uint8_t state;
  if (!received || uint32_t(now-lastByte)>FRESH_MS) state=0;
  else if (!nmea || uint32_t(now-lastNmea)>FRESH_MS) state=1;
  else if (fix && age<=FRESH_MS) state=2;
  else state=everFix && age>FRESH_MS ? 3 : 1;
  out[0]=1; out[1]=state;
  if (satsValid && uint32_t(now-lastSats)<=FRESH_MS) { out[2]|=2; out[3]=sats; }
  if (state==2) {
    put32(out+4,uint32_t(int32_t(llround(latitude*1e7))));
    put32(out+8,uint32_t(int32_t(llround(longitude*1e7))));
    if (fixUtcValid) { out[2]|=1; put32(out+12,fixUtc); }
  }
  put32(out+16,age);
}
} // namespace
