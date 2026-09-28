# Etapa 3A — Preparación BLE Android sin ESP32/MPU6050

Proyecto: `D:\Proyectos\AsistenteVialInteligente\mobile`. Se conserva la
arquitectura de las Etapas 1 y 2. No se modifican firmware, backend ni dashboard.

## Implementación

- BLE es opcional en el manifest: la aplicación y los viajes locales siguen
  disponibles en dispositivos sin radio BLE.
- AndroidBlePlatform consulta soporte, adaptador, permisos, Bluetooth activado
  y, en Android 8–11, el servicio de ubicación necesario para escanear.
- Android 12+ solicita BLUETOOTH_SCAN y BLUETOOTH_CONNECT. Android 8–11 solicita
  ACCESS_FINE_LOCATION y declara los permisos Bluetooth heredados hasta API 30.
  No se obtiene GPS ni ubicación en segundo plano. No se solicita ADVERTISE.
- Se declara neverForLocation en Android 12+: no se deduce ubicación de anuncios.
  Android puede filtrar ciertos beacons con esta opción; no se ha definido ni
  inventado un filtro de servicios del ESP32.
- Los permisos se solicitan por acción del usuario en BLE, nunca al abrir Inicio.
  Se muestra explicación y, si se deniegan, se ofrece reintento y acceso a ajustes
  para los casos en que Android ya no vuelve a mostrar el diálogo.
- Bluetooth se activa mediante la solicitud del sistema. El estado se vuelve a
  consultar al volver de permisos/ajustes, al entrar a la pantalla y al recibir
  cambios de Bluetooth/ubicación. Los broadcasts disparan una consulta real;
  no se utilizan sus extras como evidencia del estado.
- Escaneo de hasta 10 segundos, parada manual, parada al seleccionar/preparar,
  al salir de BLE o al pasar la Activity a segundo plano. No hay escaneo en un
  servicio de fondo ni reinicio automático en bucle.
- Se conservan resultados de anuncios recibidos durante el escaneo y se deduplican
  por dirección. Un anuncio puede actualizar el nombre de una fila existente.
  La dirección es un identificador de esa sesión, no una identidad permanente:
  un periférico que rote su dirección puede aparecer como otro dispositivo.
- Los callbacks tardíos de una sesión detenida se ignoran. Errores de Android,
  pérdida de permisos y apagado del adaptador tienen estados explícitos. La
  captura de SecurityException cubre revocación entre comprobación y llamada.
- ViewModels exponen StateFlow para BLE y Diagnóstico. El repositorio y la
  selección se comparten mediante Hilt, sin dependencias de la interfaz en la
  capa de acceso a Bluetooth.

## Selección, conexión y diagnóstico

Seleccionar solo acepta direcciones de la lista descubierta. La selección no
equivale a conexión ni identifica automáticamente al ESP32. Preparar conexión
detiene el escaneo y pasa a AWAITING_GATT_PROFILE, con explicación visible.
La selección vive en memoria y se pierde al terminar el proceso. No se guardan
direcciones BLE en Room ni se intenta reconectar automáticamente al abrir la app.

BleConnection define el punto de sustitución para 3B. PendingGattConnection no
llama a connectGatt, no usa UUID, no descubre servicios, no lee/escribe ni habilita
notificaciones. Los estados CONNECTING/CONNECTED/DISCONNECTED/FAILED se reservan
para el transporte real y no se emiten en la implementación de 3A.

DiagnosticScreen muestra selección, estado de preparación y ausencia de mediciones.
SensorDataSource reserva un flujo de bytes sin definir su formato. Su implementación
actual devuelve un flujo vacío y PENDING_HARDWARE; no genera lecturas, ejes, unidades
ni valores de muestra. La decodificación de las notificaciones se definirá con el
protocolo real. Ninguno de estos componentes crea DrivingEvent de origen SENSOR.

Los viajes, eventos SIMULATED, esquema Room, historial y pruebas de Etapa 2 se
conservan. No hay nuevas dependencias de producción. Solo se añade
kotlinx-coroutines-test 1.10.2 a las pruebas unitarias para controlar tiempo,
callbacks y StateFlow de manera determinista.

## Archivos creados

Dentro de `app/src/main/java/com/tecsup/asistente/conduccion/`:

- `domain/ble/BleModels.kt`: dispositivos, disponibilidad, escaneo y conexión.
- `domain/ble/BlePlatform.kt`: interfaz del acceso al sistema y sesión de escaneo.
- `domain/ble/BleRepository.kt`: contrato de coordinación observable.
- `domain/ble/BleConnection.kt`: contrato de preparación/transporte futuro.
- `domain/diagnostic/SensorDataSource.kt`: estado y flujo futuro, sin protocolo.
- `core/permissions/BlePermissionPolicy.kt`: permisos según API.
- `core/di/BleModule.kt`: enlaces Hilt y scope BLE serializado en Main.
- `core/ui/BleLabels.kt`: textos de estados compartidos entre pantallas.
- `data/ble/AndroidBlePlatform.kt`: permisos, disponibilidad, receiver y scanner.
- `data/ble/DefaultBleRepository.kt`: coordinación, deduplicación y tiempo límite.
- `data/ble/PendingGattConnection.kt`: selección y estado pendiente de 3B.
- `data/ble/PendingSensorDataSource.kt`: ausencia explícita de datos.
- `feature/ble/BleViewModel.kt`: estado y acciones de BLE.
- `feature/ble/BleContent.kt`: lista y controles de interfaz.
- `feature/diagnostic/DiagnosticViewModel.kt`: selección y recepción observable.

Pruebas bajo `app/src/test/java/com/tecsup/asistente/conduccion/`:

- `BlePermissionPolicyTest.kt`.
- `BleRepositoryTest.kt`.
- `BleViewModelTest.kt`.

Pruebas bajo `app/src/androidTest/java/com/tecsup/asistente/conduccion/`:

- `AndroidBlePlatformTest.kt`.
- `BleUiTest.kt`.

Documentación: `ETAPA_3A.md` (este archivo).

## Archivos modificados

- `app/build.gradle.kts`: versión 0.3.0/código 3 y dependencia exclusiva de pruebas.
- `app/src/main/AndroidManifest.xml`: permisos por versión y BLE opcional.
- `app/src/main/res/values/strings.xml`: estados, instrucciones y límites de 3A.
- `feature/ble/BleScreen.kt`: estado, permisos, acciones del sistema y lifecycle.
- `feature/diagnostic/DiagnosticScreen.kt`: estado futuro sin mediciones.
- `data/README.md`, `domain/README.md` (bajo el paquete Kotlin): responsabilidades BLE.
- `README.md`: alcance actual y comandos de verificación.

## Repetir las verificaciones sin hardware

Desde `mobile`, usando JDK 17, SDK 35 y un emulador disponible:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --console=plain
.\scripts\verify-process-restart.ps1 -Serial emulator-5554
```

Las pruebas BLE de lógica y UI usan dobles exclusivos de los source sets de
pruebas: sus nombres/direcciones son fixtures, no dispositivos físicos ni UUID.
La prueba de plataforma consulta el entorno Android real disponible y, si no
permite escanear, comprueba el rechazo seguro; no fuerza una radio ficticia.
Las pruebas antiguas de Room y navegación se ejecutan sobre la misma aplicación.

## Pendiente de 3B y límites de validación

Solo cuando el usuario confirme disponer del ESP32 y MPU6050 se trabajará en
firmware y en el servicio/characteristics GATT real: UUID, formato de mensajes,
configuración del sensor, suscripción a notificaciones, decodificación y pruebas
de comunicación completa. La conexión física, la recepción de mediciones y su
comportamiento ante desconexiones no se consideran probados en esta etapa.

El escaneo de radio y los diálogos/permisos deben validarse también en teléfonos
físicos y distintas versiones/OEM. Las pruebas de política por API y los dobles
de interfaz no sustituyen esa validación. No se deduce que un escaneo vacío
signifique ausencia de periféricos o que un dispositivo sea nuestro ESP32.

No se implementan detección de maniobras, GPS, Laravel, endpoints ni dashboard.
Los eventos simulados existentes continúan siendo datos de prueba y no
infracciones legales. No se declaran pruebas físicas ni resultados de sensores.

## Resultados reales — 28 de septiembre de 2026

Entorno: JDK 17, Gradle 8.11.1, SDK 35 y emulador Medium_Phone con Android 14
(API 34), ya disponible al iniciar esta etapa. No se inició ni cerró ese emulador.

- Ejecución conjunta de assembleDebug, testDebugUnitTest,
  connectedDebugAndroidTest y lintDebug: BUILD SUCCESSFUL.
- APK generado en `app/build/outputs/apk/debug/app-debug.apk`, versión 0.3.0.
- 10 pruebas unitarias aprobadas, 0 fallos/errores/omitidas: 2 de permisos por API,
  7 del repositorio BLE (incluido error síncrono, callbacks tardíos, revocación,
  apagado, deduplicación, timeout y selección), y 1 de ViewModels/Diagnóstico.
- 15 pruebas instrumentadas aprobadas, 0 fallos/errores/omitidas: las 9 existentes
  de Etapa 2 más 2 de entorno/manifest Android y 4 de interfaz/lifecycle BLE.
  Las nuevas pruebas de interfaz verifican selección pendiente sin conexión,
  parada al pasar a segundo plano, ausencia de reinicio automático, controles
  de permisos/ajustes y dispositivo sin BLE. Emplean un repositorio de prueba.
- El script existente verify-process-restart.ps1 volvió a ejecutar sus 3 fases,
  todas OK (1 test), con cierres reales del proceso entre fases y sin borrar
  datos. Confirmó conservación del viaje activo y del historial finalizado.
- Lint: 0 errores y 15 avisos. 14 son actualizaciones de dependencias disponibles;
  el restante indica que usesPermissionFlags solo tiene efecto en API 31+.
  Es intencional: neverForLocation aplica a Android 12+, mientras versiones
  anteriores usan el permiso de ubicación limitado a API 30. No se ocultó este
  aviso ni se deshabilitó lint.

Se corrigió un import de la prueba de Compose. Un intento de lint se interrumpió
durante el traslado de un archivo nuevo; se repitió con la estructura estable y
terminó correctamente. Los resultados anteriores corresponden a la ejecución
final, después de estas correcciones.

Informes: `app/build/reports/tests/testDebugUnitTest/index.html`,
`app/build/reports/androidTests/connected/debug/index.html`,
`app/build/reports/lint-results-debug.html` y
`app/build/reports/process-restart/`.

No se ejecutaron conexiones físicas, ensayos con ESP32/MPU6050 ni pruebas de
recepción real de anuncios/mediciones. La política para API 26–30 se verificó
mediante pruebas unitarias, no con un teléfono de esas versiones. Los modelos
Trip/DrivingEvent y el esquema Room de Etapa 2 no se modificaron; backend y
firmware permanecen vacíos. El trabajo se detiene en 3A.

Referencias oficiales utilizadas: [permisos Bluetooth](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
y [escaneo BLE](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices).
