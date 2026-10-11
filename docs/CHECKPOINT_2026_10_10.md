# Checkpoint funcional — 10/10/2026

Actualiza los resúmenes históricos del 03/10. Android, Laravel y ESP32 pertenecen al mismo sistema.

## Avances incluidos
- GPS NEO-6M UART2 (RX16/TX17) integrado sin cambiar IMU I2C21/22. GPS BLE independiente, 20 bytes a 1 Hz; IMU_RAW conserva UUID, 12 bytes little-endian y 4 Hz.
- Usuario confirmó recepción física simultánea IMU/GPS en Android y un evento SIMULATED con coordenadas reales recibido en Laravel. No implica maniobra detectada ni precisión certificada.
- Room biblioteca 2.7.2, esquema **3**; migración 2→3 aditiva y seis campos GPS nullable. Contrato/DTO/outbox/API preservan ubicación e idempotencia. Eventos antiguos sin ubicación siguen compatibles.
- Laravel: campos GPS y presentación empresarial; MapLibre GL JS 5.17.0 con marcadores/calor y aislamiento. OpenFreeMap configurable, atribución conservada; intensidad significa concentración de registros, no riesgo demostrado.
- Android: «Mi mapa» opcional, MapLibre Native 13.6.1; posición actual válida y eventos SIMULATED del recorrido activo. Prueba física específica del mapa pendiente.
- 3C: conversión nominal, calibración experimental de 30 s, offsets/filtro opcionales y detector probado con datos sintéticos TEST_ONLY. Sin offsets permanentes ni eventos SENSOR. Identidad WHO_AM_I=0x75 y observación aproximada de 0.943 g siguen sin explicación validada.
- Administración ya existente: /admin para platform_admin sin empresa, /dashboard para fleet_admin empresarial y activación del conductor desde Android. No se modifican cuentas ni datos para este checkpoint.

## Buzzer manual integrado
DiagnosticoBuzzer compiló y **emitió sonido según confirmación física del usuario**, con (+) GPIO4 / (-) GND en protoboard. Se conserva el sketch independiente.

Firmware principal añade ManualBuzzer.h: apagado por defecto; Serial 115200 con Nueva línea; PITAR activa 300 ms y STOP apaga. Intervalo mínimo de 2 s, entrada acotada y temporizador ESP-IDF de un disparo para apagar sin depender de delays existentes del loop. Sin alertas automáticas, comandos BLE ni cambios de protocolo.

**La integración en el firmware principal requiere todavía prueba física.** Cargar AsistenteVialImu.ino, conectar Android, comprobar IMU/GPS y enviar PITAR/STOP desde Serial; no debe haber sonido al arrancar. Confirmar que continúa la telemetría durante el pitido.

## Evidencia y límites
Los resultados históricos se consultan en ETAPA_3C_PROCESAMIENTO.md, CONTRATO_GPS_BLE.md, CONTRATO_ANDROID_LARAVEL.md y MAPA_INCIDENCIAS.md. Última integración del mapa Android: compilación, 14 unitarias seleccionadas y 3 instrumentadas de navegación aprobadas. Estos conteos no representan una suite completa ejecutada hoy.

Este checkpoint compila únicamente el firmware principal tras integrar el buzzer; no repite Android/Laravel ni afirma una nueva validación física. Sin habilitación SENSOR, calibración física, alertas automáticas, reconexión automática garantizada o despliegue productivo. No se versionan bases, credenciales, documentos privados ni builds.

Resultado ejecutado: Arduino CLI, `esp32:esp32:esp32`, core 3.3.12; compilación correcta del principal integrado. Programa: 1 118 911 bytes (85%); variables globales: 41 692 bytes (12%). No se cargó desde este entorno ni se comprobó sonido junto con IMU/GPS/BLE.
