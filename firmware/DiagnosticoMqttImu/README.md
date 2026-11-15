# Diagnóstico MQTT en modo taller

Este sketch independiente lee la IMU del ESP32 y publica muestras crudas por Wi-Fi y MQTT. La telemetría es experimental y no valida el sensor.

1. Instala el soporte de placas ESP32 de Espressif y la biblioteca **PubSubClient** en Arduino IDE.
2. Desde esta carpeta, copia el archivo de ejemplo.

   ```powershell
   Copy-Item -LiteralPath MqttSecrets.example.h -Destination MqttSecrets.h
   ```

3. En `MqttSecrets.h`, configura el nombre y la clave de la red Wi-Fi, el host y puerto del broker, sus credenciales y el `DEVICE_CODE` del dispositivo. Mantén ese archivo fuera de Git.
4. Para un broker con TLS, usa `MQTT_USE_TLS = true`, configura su puerto TLS (habitualmente 8883) y copia la CA pública en `MQTT_CA_CERT`. Usa la configuración sin TLS solo en una red aislada de taller.
5. Abre `DiagnosticoMqttImu.ino`, selecciona la placa y el puerto, y carga el sketch. El monitor serie a 115200 baudios muestra el estado de conexión. Las muestras se publican en `avigem/devices/<DEVICE_CODE>/imu`.
