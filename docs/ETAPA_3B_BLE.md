# Etapa 3B — ESP32 + MPU6050 → BLE → Android

Implementación de telemetría cruda. **La Etapa 3B está bloqueada en la validación física del MPU6050.** El dispositivo responde por I2C en `0x68`, pero las pruebas actuales de `WHO_AM_I` (registro `0x75`) devuelven `0x75`, no el valor esperado `0x68`.

Este resultado no se considera una identidad válida: está prohibido aceptar `0x75` artificialmente, omitir/eliminar la validación `WHO_AM_I` o habilitar la telemetría SENSOR como si la comprobación hubiese pasado. El firmware conserva el bloqueo seguro y no publica muestras hasta confirmar `WHO_AM_I = 0x68`. Consultar `ESTADO_ACTUAL.md` antes de modificar esta etapa.

## Protocolo permanente

- Periférico/servidor: ESP32 Dev Module. Central/cliente: Android.
- Nombre anunciado: **AsistenteVial-IMU**.
- Servicio propio UUID v4: `73e8ca02-41d7-45bd-9d39-7187649746ca`.
- Característica propia IMU_RAW UUID v4: `e77a0a03-3641-40f2-87b7-f178622259cd`.
- Propiedad: NOTIFY, dirección ESP32 → Android.
- CCCD estándar: `00002902-0000-1000-8000-00805f9b34fb` (0x2902); Android escribe `01 00` para habilitar NOTIFY.
- Los dos UUID propios se generaron una vez para este proyecto y están registrados en el sketch y `TelemetryGattProfile.kt`. No deben regenerarse ni confundirse con los UUID de viajes/eventos.
- Una notificación contiene exactamente **12 bytes**. Seis enteros signed int16 little-endian, sin cabecera ni timestamp.

| Bytes | Eje |
|---|---|
| 0–1 | ax |
| 2–3 | ay |
| 4–5 | az |
| 6–7 | gx |
| 8–9 | gy |
| 10–11 | gz |

Cada entero se transmite con el byte bajo primero. Valores posibles: −32768…32767. Frecuencia inicial nominal: **4 Hz, una muestra cada 250 ms**; no es una métrica física verificada. La trama cabe en el MTU BLE mínimo, sin negociación adicional.

## Firmware

Archivo: `firmware/AsistenteVialImu/AsistenteVialImu.ino`. Compilación comprobada con Arduino CLI 1.5.1, core Espressif **3.3.12**, placa `esp32:esp32:esp32` y partición predeterminada. Utiliza Wire y BLE incluidas en el core; no necesita otra biblioteca MPU6050.

Conexiones conservadas: VCC → 3.3 V, GND → GND, SDA → GPIO21, SCL → GPIO22. I2C 100 kHz, dirección **0x68**. Inicializa el sensor, habilita sus seis ejes y fija ACCEL_CONFIG/GYRO_CONFIG a cero. Estos registros fijan rangos del hardware, pero la trama conserva cuentas crudas: no convierte unidades. No añade calibración, filtros de software ni umbrales. Los filtros internos del chip quedan en su configuración de reset.

Lee en bloque los registros 0x3B…0x48, omite temperatura y reorganiza los seis pares big-endian del MPU a little-endian para BLE. Solo notifica cuando existe cliente y suscripción. Ante error I2C omite la muestra y lo informa por Serial; no sustituye datos con ceros ni lecturas ficticias. Tras desconectar reanuda el anuncio. Imprime las lecturas crudas transmitidas a 115200 baudios.

## Android

Las interfaces existentes `BleConnection`, `BleRepository` y `SensorDataSource` continúan siendo los puntos de entrada. Se sustituyeron las implementaciones `PendingGattConnection`/`PendingSensorDataSource` por `GattBleConnection`/`RawSensorDataSource`, enlazadas con Hilt. `AndroidGattTransport` encapsula exclusivamente las llamadas del sistema; permite probar el ciclo mediante un doble sin radio.

Secuencia: seleccionar → `connectGatt(autoConnect=false, TRANSPORT_LE)` → descubrimiento → validar servicio, IMU_RAW, NOTIFY y CCCD → habilitar notificaciones locales → escribir CCCD → confirmar callback exitoso → CONNECTED. Hasta confirmar CCCD permanece CONNECTING. Android 13+ usa las APIs de valor explícito; Android 8–12 conserva las variantes correspondientes. Se mantienen los permisos BLE existentes por versión.

Solo se mantiene una sesión. Conectar varias veces mientras está activa no crea otra; seleccionar otro dispositivo o quitar selección cierra el GATT anterior. Un identificador interno de sesión descarta callbacks tardíos. Hay un timeout de 20 segundos para conexión/configuración; fallos, desconexión y pérdida de Bluetooth/permisos liberan GATT. La reconexión es manual.

`ImuRawDecoder` exige exactamente 12 bytes y decodifica signed little-endian. Diagnóstico muestra ax, ay, az, gx, gy y gz únicamente mientras recibe tramas válidas. Tras 2 segundos sin una muestra válida oculta valores anteriores y vuelve a esperar. Una trama inválida produce error; una trama válida posterior puede recuperar la recepción. Al desconectar elimina la última muestra. Estos plazos son decisiones de interfaz, no umbrales de conducción.

Los bytes y la última lectura viven en memoria. No crean `DrivingEvent`, no se guardan en Room ni se envían a Laravel. `DataOrigin.SENSOR` sigue reservado y deshabilitado para eventos. Los viajes/eventos SIMULATED y su sincronización mantienen su comportamiento. GPS, buzzer, calibración y detección quedan pendientes.

No existe servicio de ejecución permanente en segundo plano. La prueba debe realizarse con la app abierta; cierre de proceso requiere volver a seleccionar/conectar. El perfil inicial no añade bonding, cifrado BLE ni autenticación del periférico: reconocer el UUID no acredita la identidad del dispositivo. Esto debe resolverse antes de utilizarlo en producción.

## Prueba física personal (solo después de resolver el bloqueo)

1. Abrir en Arduino IDE `D:\Proyectos\AsistenteVialInteligente\firmware\AsistenteVialImu\AsistenteVialImu.ino`.
2. Seleccionar **ESP32 Dev Module**, core Espressif 3.3.12, partición predeterminada y el puerto que actualmente corresponda al ESP32 (**COM9** fue el de tu prueba anterior). Mantener los cuatro cables del MPU como arriba. Pulsar **Verificar** y después **Subir**. Si la placa pide BOOT durante la carga, mantener BOOT hasta que comience la escritura y soltarlo.
3. Abrir Serial Monitor a **115200**. Si no aparece el inicio, pulsar EN/reset. Mientras `WHO_AM_I` siga devolviendo `0x75`, el resultado correcto es que el firmware se detenga sin publicar BLE/RAW. No continuar con la prueba de BLE como si el sensor estuviera validado. Cuando una verificación legítima devuelva `0x68`, debe mostrar el sensor listo, nombre y ambos UUID; las líneas RAW empiezan después de que Android confirme NOTIFY.
4. En el celular físico activar opciones de desarrollador y depuración USB; conectarlo al equipo y aceptar su autorización. Abrir `D:\Proyectos\AsistenteVialInteligente\mobile` en Android Studio, elegir variante **debug**, seleccionar el **celular** como destino y pulsar **Run**. Actualizar la app instalada conservando sus datos; no desinstalar ni borrar almacenamiento. Para esta prueba BLE no hace falta iniciar Laravel ni tener Internet.
5. En la app pulsar **Explorar aplicación → Dispositivo BLE**. Conceder dispositivos cercanos en Android 12+, o ubicación para escaneo en Android 8–11. Activar Bluetooth; Android 8–11 también necesita el servicio de ubicación activado. Esto no introduce GPS en la app.
6. Pulsar **Iniciar escaneo**, localizar **AsistenteVial-IMU** y pulsar **Seleccionar dispositivo**. El escaneo dura hasta 10 segundos; puede repetirse. El firmware debe estar encendido y sin otra app conectada.
7. Pulsar **Conectar GATT**. Esperar **Conexión establecida · notificaciones habilitadas**. Serial debería informar conexión y NOTIFY habilitado. Si el servicio/CCCD falta, la app muestra el motivo; confirmar que se cargó este sketch con los mismos UUID.
8. Pulsar **Volver**, regresar a Inicio y abrir **Diagnóstico**. Salir de la pantalla BLE detiene el escaneo, pero conserva la conexión para Diagnóstico.
9. Deben aparecer **ax, ay, az, gx, gy, gz** como enteros positivos, negativos o cero según las lecturas reales. No se prescribe ningún valor esperado ni resultado experimental. La pantalla dice que son cuentas crudas, sin interpretación de maniobras.
10. Mantener la placa apoyada y mover/inclinar suavemente el MPU sin tirar de los cables. Comparar los cambios de las seis lecturas con Serial; no es una prueba de conducción. Mantener Diagnóstico abierto. Para desconectar, regresar a Dispositivo BLE y pulsar **Desconectar y quitar selección**; Diagnóstico debe dejar de mostrar lecturas vigentes.

Si queda esperando datos, comprobar en Serial si se habilitó NOTIFY y si hay errores de lectura. Si se desconecta, acercar los dispositivos y reconectar manualmente. Ninguna pantalla de esta etapa acredita detecciones ni infracciones.

## Archivos

Nuevos: sketch; `data/ble/AndroidGattTransport.kt`, `GattBleConnection.kt`, `GattTransport.kt`, `RawSensorDataSource.kt`, `TelemetryGattProfile.kt`; `domain/diagnostic/RawImuSample.kt`; pruebas `GattConnectionTest.kt`, `ImuRawTest.kt`, `DiagnosticRawUiTest.kt`; este documento.

Modificados en Android: `BleModule.kt`, `BleLabels.kt`, `AndroidBlePlatform.kt`, `DefaultBleRepository.kt`, `BleConnection.kt`, `BleModels.kt`, `SensorDataSource.kt`, `BleContent.kt`, `DiagnosticScreen.kt`, `DiagnosticViewModel.kt`, `strings.xml`, `BleRepositoryTest.kt`, `BleViewModelTest.kt`, `BleUiTest.kt`. Eliminados los dos placeholders Pending sustituidos por las implementaciones reales. No se modificaron Laravel, Room, autenticación, WorkManager ni sincronización.

## Verificación

Firmware compilado correctamente: **1.119.779 bytes (85%)** de programa; **42.708 bytes (13%)** de RAM estática. La validación física no está aprobada: I2C responde en `0x68`, pero `WHO_AM_I` devuelve `0x75`. No se realizó una validación física aprobada de radio/mediciones.

Verificación Android completada: `assembleDebug` y `assembleDebugAndroidTest` correctos; **31 pruebas unitarias aprobadas**; **29 pruebas instrumentadas aprobadas y 1 omitida** (Laravel HTTP real, opt-in), en emulador Android 14; `lintDebug`: **0 errores y 23 advertencias existentes**. Se comprobaron little-endian, extremos signed, longitudes inválidas, frescura de datos, perfil GATT incompleto, suscripción rechazada, timeout, desconexión, callbacks tardíos y pérdida de permisos/Bluetooth. También pasaron navegación, Room/migración, cola y sincronización existentes dentro de la suite seleccionada.

Las pruebas usan vectores TEST y transporte simulado; nunca se presentan como lecturas físicas. Se excluyeron los cuatro casos antiguos de TripFlowTest/RestartPersistenceTest que escriben en la base manual, para conservar recorridos existentes. No se ejecutó nuevamente la prueba HTTP real Laravel ni se afirma una prueba física ESP32 → celular.

Comando ejecutado desde `mobile`, con JDK 17 y SDK local configurados:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notClass=com.tecsup.asistente.conduccion.TripFlowTest,com.tecsup.asistente.conduccion.RestartPersistenceTest' --console=plain
```

Los resultados finales fueron exitosos después de corregir el orden de los comandos de selección/conexión detectado por una prueba de ViewModel. Además se comprobó automáticamente que los dos UUID son v4 y coinciden en sketch, Android y este documento.

Referencias técnicas utilizadas: [Android BluetoothGatt](https://developer.android.com/reference/android/bluetooth/BluetoothGatt), [Android BluetoothGattCallback](https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback), [BLE del core Espressif](https://github.com/espressif/arduino-esp32/tree/3.3.12/libraries/BLE), [mapa de registros MPU6050 del fabricante](https://invensense.tdk.com/wp-content/uploads/2015/02/MPU-6000-Register-Map1.pdf). Las API del firmware también se comprobaron en los encabezados del core 3.3.12 instalado.
