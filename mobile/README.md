# AsistenteVial — Android

Aplicación del conductor en Kotlin, Jetpack Compose y Material 3. Application ID: `com.tecsup.asistente.conduccion`; versión declarada 0.4.0. Estado actualizado: 2026-10-03. Consultar primero [estado general](../docs/ESTADO_ACTUAL.md) y [contrato Android ↔ Laravel](../docs/CONTRATO_ANDROID_LARAVEL.md).

## Implementado

- Arquitectura `core`, `data`, `domain`, `feature`, `navigation`; Hilt, ViewModel/StateFlow, Coroutines y Navigation Compose.
- Iniciar/reanudar viaje → eventos manuales SIMULATED → finalizar → historial/detalle desde Room, sin depender de Internet.
- Room biblioteca 2.7.2, esquema 2 y migración aditiva; UUID persistentes, propietario y outbox transaccional.
- OkHttp/DTO de contrato, autenticación Sanctum desde Ajustes y sesión cifrada con Android Keystore.
- **Sincronizar** llama directamente a SyncEngine; evita pulsaciones simultáneas, muestra resultado y conserva WorkManager como respaldo offline. Respeta Retry-After y bloqueos; solo adelanta reintentos network_unavailable.
- Perfil propio consultado en Laravel, en memoria; servidor no disponible no bloquea viajes locales.
- Soporte/permisos/escaneo BLE, selección y conexión GATT real; Diagnóstico recibe cuentas crudas ax/ay/az/gx/gy/gz.

**Etapa 3B físicamente validada para transporte crudo**, según prueba reportada por el responsable con ESP32, IMU y teléfono Android real el 2026-10-03. `WHO_AM_I = 0x75` continúa anómalo: identidad no confirmada y firmware en modo experimental explícito. Los cambios de seis ejes fueron observados en Android y Serial al mover el módulo.

Las cuentas BLE viven solo en memoria: no generan DrivingEvent, no se guardan en Room ni se envían a Laravel. SIMULATED es prueba; SENSOR no está habilitado como productor de eventos ni representa detecciones físicas validadas. Conversión/calibración, detección, GPS y buzzer siguen pendientes.

Nombre, UUID, frame little-endian de 12 bytes y 4 Hz se conservan según [protocolo BLE](../docs/ETAPA_3B_BLE.md). No cambiarlos arbitrariamente. Hilt enlaza GattBleConnection/RawSensorDataSource; no hay un servicio BLE permanente en segundo plano.

## Configuración y guías

En Ajustes introducir URL del servidor y credenciales suministradas por el operador. Debug usa por defecto `http://10.0.2.2:8000/` para emulador, configurable con la propiedad Gradle `apiBaseUrl`; un teléfono requiere una dirección alcanzable del servidor. HTTP permitido solo en debug/local, HTTPS para producción. No hay cuentas predeterminadas.

Guías: [Etapa 2](ETAPA_2.md), [3A](ETAPA_3A.md), [integración Laravel](ETAPA_INTEGRACION_LARAVEL.md), [sincronización manual](CORRECCION_SINCRONIZACION_MANUAL.md), [perfil](ETAPA_PERFIL_CONDUCTOR.md) y [3B física](../docs/ETAPA_3B_BLE.md). Sus resultados de pruebas son históricos y no se ejecutaron nuevamente por actualizar este README.

## Abrir y compilar

Abrir esta carpeta `mobile` en Android Studio. Requisitos: JDK 17, Android SDK
Platform 35 y Build Tools 35.0.0. Android mínimo: API 26; compile/target: API 35.
Gradle 8.11.1 se descarga mediante el wrapper con checksum SHA-256.
AGP 8.9.2, Kotlin/Compose compiler 2.1.20 y KSP 2.1.20-1.0.32 están fijados.

Configurar `sdk.dir` en `local.properties` con la ruta del SDK local. Este archivo
no se versiona. Usar `JAVA_HOME` con JDK 17 al compilar desde terminal.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Informe lint: `app/build/reports/lint-results-debug.html`.

Con un emulador o dispositivo Android API 26+ conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Las pruebas instrumentadas cubren Room, recorrido completo, navegación y
recreación de Activity. Compilar las pruebas no equivale a ejecutarlas.
Para comprobar la muerte de proceso, con ambos APK compilados y un emulador:

```powershell
.\scripts\verify-process-restart.ps1 -Serial emulator-5554
```

Este script instala con `-r`, ejecuta tres fases y fuerza cierres entre ellas sin
borrar la base. Verifica viaje activo y viaje finalizado contra una instantánea
de IDs, fechas, estados, tipos y orígenes. Deja registros de prueba SIMULATED en
el emulador. Las pruebas del repositorio usan archivos de base exclusivos que
se eliminan al terminar. Usar un emulador de pruebas.

## Registro histórico: verificación de Etapa 1 — 28 de septiembre de 2026

- `:app:assembleDebug`: BUILD SUCCESSFUL; APK de depuración generado.
- `:app:assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- `:app:connectedDebugAndroidTest`: ejecutado en Medium_Phone, Android 14
  (API 34): 1 prueba, 0 fallos, 0 errores, 0 omitidas. Se verificó nuevamente
  después de añadir el icono y las reglas de extracción para Android 12+.
- Tras completar las reglas de copia de seguridad para Android anterior a 12,
  se repitieron compilación y lint correctamente. Este último ajuste de recursos
  no tuvo una nueva ejecución instrumentada.
- Se instalaron Platform 35 y Build Tools 35.0.0, ausentes inicialmente.
- El emulador iniciado para la comprobación se cerró al finalizar las pruebas.

Los avisos de actualizaciones disponibles no se ocultan: se conservan versiones
fijadas y compatibles en esta base. No se verificaron dispositivos físicos,
servicios remotos ni hardware; estos últimos quedan fuera de la Etapa 1.

Referencias de configuración: [compatibilidad de AGP y Gradle](https://developer.android.com/build/releases/about-agp)
y [configuración de Hilt](https://dagger.dev/hilt/gradle-setup.html).
