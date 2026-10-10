# GPS NEO-6M → ESP32 → BLE → Android

## Estado y alcance (2026-10-09)
Integración de software. El usuario confirmó NMEA real en interiores, todavía sin fix. Posición válida y recepción conjunta GPS + IMU en Android requieren prueba física exterior; no se afirman resultados. WHO_AM_I = 0x75 sigue siendo una anomalía; identidad IMU no confirmada. Detector experimental sin validación física; no se habilitan eventos SENSOR.

## Transporte
Se conserva AsistenteVial-IMU, servicio `73e8ca02-41d7-45bd-9d39-7187649746ca`, IMU_RAW `e77a0a03-3641-40f2-87b7-f178622259cd`, 12 bytes little-endian a 4 Hz.
Nueva característica propia GPS: `af1b5aad-b30b-47b2-a8a5-64f152f6510e`, NOTIFY + CCCD estándar, 20 bytes a 1 Hz. UUID definido para esta integración, no un servicio estándar del NEO-6M. Cabe en el payload de MTU 23; no requiere negociar MTU. Android utiliza la misma sesión GATT: primero completa CCCD de IMU, después suscribe GPS opcional. GPS ausente/rechazado no bloquea IMU.

Frame versión 1, offsets de bytes:
- 0: versión uint8 = 1.
- 1: estado uint8: 0 sin recepción UART, 1 sin fix (incluye bytes sin NMEA válida), 2 posición válida, 3 posición caducada.
- 2: flags uint8: bit 0 UTC disponible; bit 1 satélites disponibles; demás bits reservados = 0.
- 3: cantidad de satélites usados GGA uint8, 0–99; significativo solo con bit 1.
- 4–7: latitud int32 little-endian, grados × 10^7; significativo solo en estado 2.
- 8–11: longitud int32 little-endian, grados × 10^7; significativo solo en estado 2.
- 12–15: uint32 little-endian, segundos desde medianoche UTC asociados a la posición; solo estado 2 con bit 0. Es hora del día, NO timestamp de fecha completa. No se inventa fecha ni se usa para sincronización.
- 16–19: antigüedad uint32 little-endian en ms desde la recepción de la última frase con fix válido en ESP32; 0xffffffff si nunca hubo fix. No acredita latencia interna del receptor ni BLE.

Sin posición válida, coordenadas y UTC se envían como bytes cero no significativos; Android los expone como null, nunca como una ubicación 0/0. Satélites pueden estar disponibles sin fix. No se transmiten coordenadas anteriores en estado caducado.

## Validez y pérdida de datos
UART2 9600 8N1: GPS TX → GPIO16; GPS RX → GPIO17; GND común. IMU GPIO21/22 y GPIO4 reservado no se cambian. Lectura UART acotada a 256 bytes por loop; no espera fix ni envía comandos al GPS. Parser adaptado del diagnóstico existente: checksum NMEA, campos vacíos, hemisferios y rangos; GGA calidad 1/2 o RMC A con modo autónomo/diferencial (si existe). Rechaza posiciones simuladas/estimadas.

Ventana de frescura de desarrollo: 5000 ms, no un criterio físicamente validado. Sin bytes durante esa ventana: estado 0. Bytes sin NMEA reciente: estado 1. Fix explícitamente inválido: estado 1; última posición envejecida con NMEA reciente: estado 3. Solo estado 2 con edad ≤5000 ms permite coordenadas/UTC. Android elimina la posición al vencer la ventana restante incluso sin nuevos frames; sin telemetría no válida reciente muestra esperando. Una desconexión borra GPS y RAW de memoria. Un frame GPS inválido no altera IMU.

No persistencia GPS, rutas, mapas, Room/API ni eventos nuevos en esta fase. No se agregaron dependencias.

## Prueba física pendiente
1. Cargar `firmware/AsistenteVialImu/AsistenteVialImu.ino` en el ESP32 utilizado, abrir Serial a 115200. GPS continúa a 9600. Conservar cableado y comprobar que VCC del módulo GPS admite la alimentación empleada.
2. Instalar APK actualizado desde Android Studio; Dispositivo → escanear → AsistenteVial-IMU → conectar → Diagnóstico.
3. Confirmar RAW de seis ejes y sección GPS sin fix mientras no exista posición. No esperar coordenadas ficticias en interiores.
4. Llevar antena a cielo despejado y esperar adquisición real. Si se obtiene fix, comparar coordenadas, hora UTC y estados Serial/Android. Verificar que IMU continúa recibiéndose.
5. Desconectar BLE: deben desaparecer coordenadas y RAW; reconectar recupera solo datos recibidos nuevos. Para probar pérdida de UART, desconectar con alimentación apagada el TX GPS y volver a arrancar; debe indicar sin recepción, manteniendo IMU. No hacer conexiones en caliente para esta prueba.

## Decisión futura de mapas (sin implementar)
- Android: MapLibre Native, integrado en Compose mediante el mecanismo apropiado (por ejemplo AndroidView o wrapper compatible); mapa del conductor opcional, monitoreo independiente de mantenerlo abierto.
- Laravel: MapLibre GL JS + capa de calor en fase posterior.
- Cartografía OpenStreetMap a través de proveedor configurable con atribución y condiciones del proveedor. No se fija un servidor público ni se presupone permiso para descargas masivas/offline.
Referencias: https://maplibre.org/projects/native/ ; https://maplibre.org/maplibre-compose/ ; https://maplibre.org/maplibre-gl-js/docs/ ; https://operations.osmfoundation.org/policies/tiles/

## Archivos de esta integración
- Firmware: `firmware/AsistenteVialImu/AsistenteVialImu.ino`, nuevo `GpsTelemetry.h` en la misma carpeta. El sketch de diagnóstico independiente no se modificó.
- Android, bajo `mobile/app/src/main/java/com/tecsup/asistente/conduccion/`: `data/ble/TelemetryGattProfile.kt`, `GattTransport.kt`, `AndroidGattTransport.kt`, `GattBleConnection.kt`, `RawSensorDataSource.kt`; `domain/diagnostic/SensorDataSource.kt`, nuevo `GpsTelemetry.kt`; `feature/diagnostic/DiagnosticViewModel.kt`, `DiagnosticScreen.kt`.
- Pruebas: nuevo `mobile/app/src/test/java/com/tecsup/asistente/conduccion/GpsTelemetryTest.kt`.
- Documentación: este contrato y referencia en `docs/ETAPA_3B_BLE.md`. Los cambios previos de Etapa 3C se conservaron.

## Verificación ejecutada
Android: `assembleDebug` y `testDebugUnitTest` filtrado a GpsTelemetryTest (6), ImuRawTest (4), GattConnectionTest (4), BleViewModelTest (1), CalibrationFlowTest (5): 20 pruebas, 0 fallos/errores. Se repitieron después de ajustar el manejo de excepciones de suscripción GPS; BUILD SUCCESSFUL. Frames de pruebas son sintéticos TEST, no mediciones físicas. No se ejecutaron pruebas instrumentadas ni se cargó firmware al ESP32 desde este entorno.

Firmware: Arduino CLI, FQBN `esp32:esp32:esp32`, core Espressif 3.3.12: compilación correcta; 1 115 587 bytes de programa (85%) y 41 636 bytes de variables globales (12%). Sin carga ni prueba física desde este entorno.
