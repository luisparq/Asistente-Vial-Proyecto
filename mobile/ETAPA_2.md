# Etapa 2 — Viajes, eventos simulados y persistencia local

El proyecto real continúa en `D:\Proyectos\AsistenteVialInteligente\mobile`.
No existía `mobile/AsistenteVial`, por lo que no se movió ni duplicó la aplicación.

## Comportamiento implementado

Desde Inicio, Iniciar recorrido crea un viaje en Room antes de navegar. Si ya
existe uno activo, aparece Continuar recorrido y se recupera el mismo ID.
Salir, volver al acceso o cerrar la app no finaliza automáticamente el recorrido.

Viaje muestra el estado, identificador, inicio y contador confirmado desde Room.
Los tres botones manuales generan HARSH_BRAKING, HARSH_ACCELERATION o SHARP_TURN,
siempre con origen SIMULATED. Los textos identifican expresamente datos de prueba,
sin presentar detecciones reales ni infracciones. No se producen mediciones.

Finalizar guarda fecha de fin y COMPLETED antes de abrir Historial. Historial
consulta Room, muestra viajes activos y finalizados y permite abrir su detalle.
El detalle recupera los eventos asociados con sus tipos, fechas y orígenes. Las
listas se componen de forma diferida con LazyColumn; no hay listas de datos
prellenadas. La lista de tipos de botones es configuración, no datos de viajes.

## Persistencia e integridad

Base `asistente_vial.db`, versión 1. Las fechas se guardan como milisegundos Unix
y se exponen como Instant; la interfaz usa la zona horaria local. Los IDs Long
son generados por SQLite. Un índice único nullable permite como máximo un viaje
activo. Cada evento tiene una clave foránea e índice sobre tripId.

Inicio/reanudación, generación y finalización se ejecutan en transacciones.
Finalizar es idempotente; no altera una fecha de cierre existente. El repositorio
rechaza eventos sobre viajes inexistentes o finalizados. El origen SENSOR se
reserva en el modelo; no existe una operación que genere datos de sensores.

Hilt proporciona la base singleton, el repositorio y Clock. Las pantallas usan
ViewModels, StateFlow y collectAsStateWithLifecycle. Room maneja las consultas
asíncronas; no se habilita acceso a la base en el hilo principal. Se exporta el
esquema y no se configura fallback destructivo para futuras migraciones.

Únicas nuevas dependencias: Room runtime/ktx y su compilador KSP, versión 2.7.2.
Se conserva el resto del entorno verificado. No se implementó la Etapa 3.

## Archivos creados

Rutas Kotlin relativas a `app/src/main/java/com/tecsup/asistente/conduccion/`:

- `domain/model/Trip.kt`: Trip, estados, resumen y detalle con eventos.
- `domain/model/DrivingEvent.kt`: evento, tipos y origen.
- `domain/repository/TripRepository.kt`: contrato del repositorio.
- `data/local/TripEntity.kt`: entidad de viajes e índice de activo.
- `data/local/DrivingEventEntity.kt`: entidad de eventos y clave foránea.
- `data/local/TripDao.kt`: escrituras y consultas observables/transaccionales.
- `data/local/AsistenteVialDatabase.kt`: base Room.
- `data/repository/RoomTripRepository.kt`: reglas de escritura y mapeos.
- `core/di/StorageModule.kt`: provisión Hilt y enlace de repositorio.
- `core/ui/LoadState.kt`: estados de lectura.
- `core/ui/TripComponents.kt`: presentación compartida de viajes y eventos.
- `feature/home/HomeViewModel.kt`: inicio y reanudación.
- `feature/trip/TripViewModel.kt`: simulación y finalización.
- `feature/history/HistoryViewModel.kt`: lista persistida.
- `feature/history/TripDetailViewModel.kt`: consulta por tripId.
- `feature/history/TripDetailScreen.kt`: detalle y eventos.

Otros archivos creados, relativos a `mobile`:

- `app/schemas/com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase/1.json`.
- `app/src/androidTest/java/com/tecsup/asistente/conduccion/RoomTripRepositoryTest.kt`.
- `app/src/androidTest/java/com/tecsup/asistente/conduccion/TripFlowTest.kt`.
- `app/src/androidTest/java/com/tecsup/asistente/conduccion/RestartPersistenceTest.kt`.
- `app/src/androidTest/java/com/tecsup/asistente/conduccion/TestUiHelpers.kt`.
- `scripts/verify-process-restart.ps1`.
- `ETAPA_2.md` (este documento).

## Archivos modificados

- `app/build.gradle.kts`: Room/KSP, exportación de esquema y versión 0.2.0 (código 2).
- `app/src/main/res/values/strings.xml`: textos de la etapa y advertencias de simulación.
- `README.md`: alcance y comandos actualizados, conservando el registro de Etapa 1.
- `app/src/androidTest/java/com/tecsup/asistente/conduccion/NavigationTest.kt`: adapta
  navegación al flujo nuevo; el viaje ahora se comprueba en TripFlowTest.

En `app/src/main/java/com/tecsup/asistente/conduccion/`:

- `core/ui/ScreenLayout.kt`: permite listas con desplazamiento propio.
- `feature/home/HomeScreen.kt`: iniciar/continuar desde estado persistido.
- `feature/trip/TripScreen.kt`: viaje activo, botones de prueba y finalización.
- `feature/history/HistoryScreen.kt`: historial observable y acceso al detalle.
- `navigation/AppNavHost.kt`: cierre hacia historial y ruta de detalle tipada Long.
- `navigation/AppRoute.kt`: incorpora la ruta de detalle.
- `data/README.md` y `domain/README.md`: responsabilidades actuales.

## Cómo repetir las comprobaciones

Con JDK 17, SDK 35 y un emulador Android conectado, desde `mobile`:

```powershell
.\gradlew.bat :app:assembleDebug :app:connectedDebugAndroidTest :app:lintDebug --console=plain
.\scripts\verify-process-restart.ps1 -Serial emulator-5554
```

Las pruebas de repositorio crean y eliminan sus propias bases. Las pruebas de
interfaz y reinicio dejan viajes de prueba SIMULATED en la base de la aplicación
del emulador. El script no borra datos, no desinstala la aplicación ni reinicia
el dispositivo: fuerza el cierre del proceso y vuelve a abrirlo entre fases.

Informes generados:

- `app/build/reports/androidTests/connected/debug/index.html`.
- `app/build/reports/lint-results-debug.html`.
- `app/build/reports/process-restart/`: resultado y confirmación de proceso detenido
  para cada fase de la prueba de reapertura.

## Resultados reales — 28 de septiembre de 2026

Entorno: JDK 17, Gradle 8.11.1, AGP 8.9.2, SDK 35 y emulador Medium_Phone con
Android 14 (API 34). APK generado: `app/build/outputs/apk/debug/app-debug.apk`.

- Compilación de aplicación y pruebas: BUILD SUCCESSFUL.
- Ejecución final conjunta de assembleDebug, connectedDebugAndroidTest y
  lintDebug: BUILD SUCCESSFUL; 9 pruebas, 0 fallos, 0 errores y 0 omitidas.
- RoomTripRepositoryTest: 4 pruebas aprobadas. Cubren creación, fechas,
  persistencia de los tres tipos SIMULATED, lectura Flow, finalización,
  reapertura del archivo, 12 inicios concurrentes con un único ID, cierre
  idempotente, rechazo de eventos posteriores al cierre y clave foránea.
- TripFlowTest: 1 prueba aprobada. Crear/continuar desde Inicio, generar los
  tres eventos con botones, consultar lo persistido, recrear Activity, finalizar,
  abrir historial y detalle desde Room, y volver a Inicio.
- NavigationTest: 1 prueba aprobada. BLE, Diagnóstico, Historial, Ajustes y
  retorno al acceso; viaje y detalle se cubren en TripFlowTest.
- RestartPersistenceTest: sus 3 fases aprobaron en la suite y se repitieron con
  `verify-process-restart.ps1`, esta vez deteniendo el proceso y abriendo la app
  entre fases. Las 3 ejecuciones adicionales devolvieron OK (1 test).
  Se verificó recuperación del viaje activo y, después de finalizarlo y volver
  a cerrar/abrir, recuperación del historial con los mismos IDs, fechas,
  estado, tipos y orígenes. No se borraron datos entre esas fases.
- Lint final: 0 errores, 14 avisos GradleDependency de actualizaciones
  disponibles. No se suprimieron avisos ni se modificaron versiones ajenas a Room.

Durante la implementación se corrigieron un error de sintaxis del DAO y una
firma de prueba que JUnit exigía sin retorno. La suite completa se volvió a
ejecutar después de corregirlos y produjo los resultados anteriores.

No se verificaron dispositivos físicos ni reinicio del sistema operativo. Sí se
verificaron recreación de Activity, cierre/reapertura de archivo Room y muerte
real del proceso de la aplicación. El alcance no incluye BLE, ESP32, MPU6050,
GPS, Laravel, API REST ni algoritmos de detección. Las carpetas backend,
firmware, docs y design permanecieron vacías.

Referencias: [Room](https://developer.android.com/jetpack/androidx/releases/room)
y [pruebas de bases Room](https://developer.android.com/training/data-storage/room/testing-db).
