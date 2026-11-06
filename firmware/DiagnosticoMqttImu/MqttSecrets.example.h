#pragma once

// Copiar como MqttSecrets.h y completar localmente. No subir credenciales a Git.
constexpr char WIFI_SSID[] = "REEMPLAZAR_WIFI";
constexpr char WIFI_PASSWORD[] = "REEMPLAZAR_PASSWORD_WIFI";
constexpr char MQTT_HOST[] = "192.168.1.100";
constexpr uint16_t MQTT_PORT = 1883; // Solo laboratorio local. En VPS usar TLS/8883.
constexpr bool MQTT_USE_TLS = false;
// Para TLS, pega aquí la CA pública que firma el certificado del broker.
constexpr char MQTT_CA_CERT[] = "";
constexpr char MQTT_USERNAME[] = "avigem-one";
constexpr char MQTT_PASSWORD[] = "REEMPLAZAR_PASSWORD_MQTT";
constexpr char DEVICE_CODE[] = "AVIGEM-ONE-001";
