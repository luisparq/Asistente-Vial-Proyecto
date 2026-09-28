# AsistenteVial — Etapa 3A

Aplicación Android del conductor, creada en Kotlin con Jetpack Compose y Material 3.
Application ID: `com.tecsup.asistente.conduccion`.

Etapa 3A verificada: compilación correcta, 10 pruebas unitarias y 15 pruebas
instrumentadas aprobadas; las 3 fases de persistencia con cierre de proceso
también aprobaron. Lint: 0 errores, 15 avisos detallados en
[ETAPA_3A.md](ETAPA_3A.md). No hay pruebas de conexión física ni mediciones.

Etapa 2 verificada: compilación correcta, 9 pruebas instrumentadas aprobadas y
3 fases adicionales aprobadas con cierre real del proceso entre ellas. Lint:
0 errores y 14 avisos de actualizaciones de dependencias. El inventario completo
de archivos y los resultados están en [ETAPA_2.md](ETAPA_2.md).

## Alcance

Sistema previsto: sensores/MPU6050 → ESP32 → BLE → Android → API REST/HTTPS →
Laravel → base de datos → dashboard. Android y Laravel son componentes del mismo
sistema; Laravel será API/backend y dashboard del administrador de flota.

La base incluye viajes y eventos manuales de prueba persistidos en Room.
Permite iniciar o continuar un recorrido, generar eventos SIMULATED, finalizar
y consultar el historial y el detalle. El recorrido activo se conserva al salir.
Etapa 3A añade soporte/estado BLE, permisos, escaneo, selección y preparación de
conexión. Diagnóstico muestra ausencia de mediciones. No implementa autenticación,
conexión GATT, sensores, GPS, algoritmos de detección ni API. No contiene UUID BLE,
endpoints, credenciales ni mediciones. No solicita permisos de red; solicita los
permisos BLE según la versión de Android solo desde la pantalla BLE. Los eventos
de viajes siguen siendo SIMULATED, no detecciones reales ni infracciones.

El alcance BLE, inventario de archivos y límites de validación están en
[ETAPA_3A.md](ETAPA_3A.md). La integración física 3B queda pendiente hasta que el
usuario confirme que dispone de ESP32 y MPU6050. El backend requiere autorización
separada y comenzará revisando el contrato con los modelos Android actuales.

## Organización

- `core/ui`: tema claro/oscuro del sistema y estructura compartida de pantallas.
- `data`: Room y repositorio local, acceso Android BLE y preparación GATT pendiente.
- `domain`: modelos/contratos de viajes, BLE y recepción futura de sensores.
- `feature`: splash, login, home, trip, ble, diagnostic, history y settings.
- `navigation`: rutas centralizadas y `AppNavHost`.

Hilt configura Application, Activity, ViewModels, repositorio, Room y Clock.
Los ViewModels exponen StateFlow a Compose mediante collectAsStateWithLifecycle;
las escrituras se ejecutan en viewModelScope y las consultas Room exponen Flow.

Flujo: Splash → Acceso → Inicio → iniciar/continuar recorrido → Viaje →
generar eventos simulados → finalizar → Historial → Detalle del recorrido.
Inicio también permite abrir BLE, Diagnóstico, Historial y Ajustes.
Splash y Acceso se retiran de la pila al avanzar. Las pantallas secundarias
permiten volver a Inicio; Ajustes permite volver a Acceso limpiando Inicio.
«Explorar aplicación» permite navegar sin representar un inicio de sesión real.

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
