# AVIGEM One: detección autónoma y dos caminos de telemetría

Este firmware mide IMU a una frecuencia objetivo de 20 Hz, recibe GPS por UART y
mantiene BLE y Wi-Fi/MQTT en paralelo. El ESP32 calcula la detección y ejecuta su
buzzer; el celular y Laravel no participan en esa decisión. Los límites se
aprenden durante pruebas guiadas desde Vialigent y se guardan con Preferences/NVS.

## Compilación

ESP32 Dev Module, core Espressif 3.3.12, bibliotecas PubSubClient 2.8 y
ESP_SSLClient 3.1.3, y partición
**Huge APP (3 MB No OTA/1 MB SPIFFS)** de un ESP32 de 4 MB. La partición predeterminada
de 1.2 MB no alcanza para la combinación BLE + Wi-Fi/MQTT/TLS + calibración.

```powershell
& 'C:\Users\USUARIO\AppData\Local\Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe' compile --jobs 1 --fqbn 'esp32:esp32:esp32:PartitionScheme=huge_app' --build-path 'D:\Proyectos\AsistenteVialInteligente\firmware\.build-autonomous-verified' 'D:\Proyectos\AsistenteVialInteligente\firmware\AsistenteVialImu'
```

El monitor serie usa 115200 baudios. La generación de binarios no equivale a haber
cargado el firmware ni haber validado físicamente detección, radio, GPS o buzzer.

## Credenciales y broker

Copia `MqttSecrets.example.h` como `MqttSecrets.h` y completa las credenciales
localmente; ese archivo está ignorado por Git. Si no existe, el ejemplo permite
compilar, pero sus valores REEMPLAZAR no establecen conectividad real.

El `DEVICE_CODE` debe coincidir con el código asociado al dispositivo en Laravel.
El broker debe permitir al dispositivo publicar en `avigem/devices/CODIGO/imu`
y suscribirse a `avigem/devices/CODIGO/control`. El consumidor de Laravel necesita
leer IMU y publicar control. Los comandos no se publican con retain. TLS usa la CA
configurada; no se desactiva su validación. El ejemplo de clase admite MQTT sin TLS,
pero no se considera un despliegue seguro de producción.

Wi-Fi/MQTT usan una tarea y colas propias. Un fallo de red no detiene la medición,
BLE ni detección. La cola MQTT tiene 32 muestras (aproximadamente 1.6 s a 20 Hz),
no es un almacén durable y descarta datos cuando no hay conexión o capacidad.
Por tanto no se promete recuperar todas las muestras/eventos después de una
interrupción de MQTT; el reenvío persistente del celular es un segundo camino.

## Calibración y persistencia

1. **rest**: motor apagado y sensor fijo durante 30 s. Se verifica variabilidad
   nominal ≤0.02 g / ≤1 °/s por eje y gravedad entre 0.7 y 1.3 g. Se rechazan
   saturación, interrupciones y falta de muestras.
2. **acceleration**: aceleración suave en línea recta, sin frenar ni girar en la
   misma lección. Su dirección horizontal coherente define el sentido de avance.
3. **braking**: frenadas suaves y rectas.
4. **turn**: giros normales hacia ambos lados.
5. **finish** termina una maniobra después de al menos 3 s; el reposo siempre
   requiere los 30 s. Las maniobras terminan a los 60 s como máximo. Un fallo pide
   repetir y no inventa un límite.

La persistencia comprueba la escritura NVS. PROFILE_APPLIED solo se reporta cuando
el perfil completo y los niveles normales se guardaron correctamente. Se conservan
reposo, bias gyro, dirección de avance, normales medidos, límites y versión. Al
reiniciar, se recupera ese perfil y su estado habilitado/deshabilitado. Repetir solo
una lección conserva los valores de las otras. Repetir reposo o aceleración obliga
a reaprender las demás maniobras. Si cambia de carro o montaje, repite todo.

Los límites iniciales son 1.8 veces el percentil 95 normal, con mínimos 0.12 g y
10 °/s. Son criterios experimentales, no estándares de seguridad vial. La resta
del reposo no compensa dinámicamente cabeceo, balanceo, pendiente o vibración;
se requiere validación física con el montaje real antes de atribuir peligro.

La detección exige al menos 3 muestras y 300 ms sobre límite y aplica 3 s entre
avisos. Ante simultaneidad se prioriza giro, luego aceleración, luego frenada.
El buzzer usa su temporizador limitado de 300 ms y un cooldown propio. Una lectura
I2C fallida o saturada limpia la evidencia para no unir muestras inválidas.

## Identidad compartida y BLE v2

Se genera un UUID de sesión en cada arranque y un contador uint32 por muestra,
antes de copiarla a MQTT o BLE. Los dos caminos conservan `DEVICE_CODE`, UUID de
sesión y secuencia. Las dos vistas Laravel consultan el mismo registro.

- Servicio: `73e8ca02-41d7-45bd-9d39-7187649746ca`.
- IMU_RAW anterior: `e77a0a03-3641-40f2-87b7-f178622259cd`, 12 bytes, compatible.
- Identidad de solo lectura: `e77a0a04-3641-40f2-87b7-f178622259cd`, JSON con
  `deviceCode` y `sessionId`.
- Telemetría relay: `e77a0a05-3641-40f2-87b7-f178622259cd`, 20 bytes little-endian.

Formato relay: seis int16 (12 bytes), secuencia uint32 (4), versión uint16 (2),
byte con rango en nibble alto y evento en nibble bajo (1), flags (1).
Rango: 0 normal, 1 aceleración, 2 frenada, 3 giro, 4 sin señal clasificable/perfil
activo. Evento 1/2/3 se emite solo en la muestra que completa la evidencia.
Flags: bit 0 perfil listo, bit 1 detección habilitada, bits 2.. paso de aprendizaje
(0 ninguno, 1 reposo, 2 aceleración, 3 frenada, 4 giros).

MQTT reporta además límites, estado de calibración y último comando reconocido.
Los comandos son `UUID|operacion`; se conservan las 16 identidades recientes en RAM
para que un reintento no repita el aprendizaje ni el pulso del buzzer.

## Límites de verificación

WHO_AM_I se lee en el registro 0x75; se espera 0x68 para confirmar MPU6050. Una
respuesta diferente mantiene explícitamente el modo experimental. Compilar o
recibir bytes no valida identidad, precisión ni detección vehicular. Se debe
verificar heap real durante TLS/reconexiones y medir la frecuencia real de datos.
