# Firmware AVIGEM One

Abre `AsistenteVialImu.ino` en Arduino IDE con la placa ESP32 Dev Module y el core Espressif 3.3.12 usado por este proyecto. El sketch actual comunica IMU y GPS por BLE y controla el buzzer. El monitor serie utiliza 115200 baudios.

## Configuración MQTT

`MqttTelemetry.h` conserva el módulo MQTT, pero el sketch principal actual no lo incluye ni lo inicia. La prueba MQTT independiente está en [DiagnosticoMqttImu](../DiagnosticoMqttImu/README.md).

Para compilar código que incluya `MqttTelemetry.h`, instala PubSubClient y copia el ejemplo desde esta carpeta.

```powershell
Copy-Item -LiteralPath MqttSecrets.example.h -Destination MqttSecrets.h
```

Completa las credenciales localmente. `MqttSecrets.h` está ignorado por Git. Con TLS, configura la CA del broker, el puerto y el identificador de dispositivo según el ejemplo. El sketch independiente tiene su propia copia de configuración.

## Verificación física y memoria

El registro WHO_AM_I tiene dirección `0x75`. El sketch principal espera leer `0x68` para confirmar el MPU6050. Una dirección de registro y un dispositivo que responde por I2C no acreditan la identidad ni la calibración.

Al compilar la combinación que efectivamente se vaya a cargar, registra los bytes de programa y RAM que informa Arduino. La combinación simultánea BLE, Wi-Fi, MQTT y TLS requiere además medir heap libre durante conexión, reconexión y publicación. Esta revisión no compiló ni cargó firmware y no dispone de mediciones físicas de memoria ni de validación del sensor.
