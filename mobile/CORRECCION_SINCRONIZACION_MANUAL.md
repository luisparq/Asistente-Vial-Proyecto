# Corrección de sincronización manual

## Comportamiento

El botón **Ajustes → Sincronizar** ejecuta `SyncEngine.syncNow()` en la coroutine del ViewModel. Ya no se limita a añadir un Worker. La petición HTTP utiliza el cliente y los recibos existentes; las pantallas siguen leyendo Room.

El ViewModel reserva el intento antes de lanzar la coroutine: las pulsaciones repetidas se ignoran mientras está ocupado. El botón queda deshabilitado con **Sincronizando…**. El motor singleton también impide dos intentos manuales simultáneos. Los leases transaccionales existentes coordinan el intento manual con los Workers automáticos.

El intento manual adelanta, una sola vez por pulsación, exclusivamente `RETRY_WAIT` con `errorCode = network_unavailable` de la cuenta actual. No adelanta errores HTTP, incluyendo `429` y `503`; conserva sus plazos y `Retry-After`. No desbloquea errores de autenticación, conflicto o validación, ni toma operaciones con lease vigente. La recuperación habitual de leases vencidos permanece en el motor existente.

No hay una consulta de disponibilidad del servidor previa al envío: con una sesión local válida, el propio intento HTTP determina si Laravel responde. Una conexión de red disponible no garantiza que el servidor sea accesible. Si el token resulta inválido, se conserva el tratamiento existente de `401`.

Al terminar se muestran operaciones confirmadas durante el intento, pendientes y bloqueadas. Una confirmación concurrente de un Worker puede incluirse en el contador, que se obtiene de Room. No es un contador de viajes: un viaje tiene operaciones de inicio y finalización, además de sus eventos.

Si quedan operaciones pendientes o el intento se interrumpe, se solicita respaldo con `enqueueUniqueWork("trip-sync-now", KEEP)`. Si ya existe trabajo pendiente, esta acción no añade otro eslabón. Si todo quedó confirmado, no crea un Worker vacío. La reconciliación periódica existente permanece.

Las escrituras locales conservan `APPEND_OR_REPLACE`: esto permite dejar un sucesor si una nueva operación llega mientras el Worker anterior termina. `finishTrip` guarda `COMPLETED` y su operación en la misma transacción y después llama al programador. No necesita un reinicio, aunque WorkManager puede diferir la ejecución automática. El intento manual evita esa espera. No se añadió sincronización permanente.

Se conserva el límite existente de 50 operaciones por ejecución del motor; cualquier resto queda pendiente y con respaldo automático. Los UUID, payloads y revisión de cada operación se reutilizan en los reintentos. Sin migración: esquema Room 2 y dependencias sin cambios.

## Archivos de esta corrección

Rutas relativas a `mobile/`:

- `app/src/main/java/com/tecsup/asistente/conduccion/data/local/SyncDao.kt`: consulta acotada para adelantar errores de red.
- `app/src/main/java/com/tecsup/asistente/conduccion/data/sync/SyncEngine.kt`: entrada manual, exclusión de intentos manuales y resultado leído de Room.
- `app/src/main/java/com/tecsup/asistente/conduccion/data/sync/SyncWorker.kt`: respaldo manual con `KEEP`, programación automática conservada.
- `app/src/main/java/com/tecsup/asistente/conduccion/domain/sync/SessionStore.kt`: método de respaldo en `SyncScheduler`.
- `app/src/main/java/com/tecsup/asistente/conduccion/feature/settings/SyncViewModel.kt`: envío inmediato, exclusión de pulsaciones, resultado y respaldo.
- `app/src/main/java/com/tecsup/asistente/conduccion/feature/settings/SettingsScreen.kt`: estado visual del botón.
- `app/src/androidTest/java/com/tecsup/asistente/conduccion/SyncIntegrationTest.kt`: recuperación de red, plazos/bloqueos/leases y finalización.
- `app/src/androidTest/java/com/tecsup/asistente/conduccion/ManualSyncScreenTest.kt`: botón real Compose, pulsaciones repetidas, contador y respaldo.
- `app/src/androidTest/java/com/tecsup/asistente/conduccion/LaravelEndToEndTest.kt`: ejercicio del intento manual contra Laravel real, incluyendo repetición idempotente.
- `CORRECCION_SINCRONIZACION_MANUAL.md`: esta documentación.

## Prueba manual en Android Studio

1. Abrir `D:\Proyectos\AsistenteVialInteligente\mobile`, seleccionar la variante `debug` y ejecutar la app en el emulador con **Run**. No desinstalar, borrar almacenamiento ni usar Wipe Data.
2. En una terminal PowerShell iniciar el servidor ya configurado:

   ```powershell
   Set-Location D:\Proyectos\AsistenteVialInteligente\backend
   & C:\xampp\php\php.exe artisan serve --host=127.0.0.1 --port=8000
   ```

3. En Android abrir **Inicio → Ajustes**. Mantener la URL y cuenta asociadas a los recorridos existentes. Para el emulador estándar el servidor local es `http://10.0.2.2:8000/`. Si ya utilizaste ese destino, usarlo de nuevo; cambiar el alias de servidor constituye otro destino para la cola. Iniciar/renovar sesión con tu cuenta TEST existente cuando sea necesario. No se necesitan credenciales nuevas.
4. Iniciar un recorrido, generar eventos **SIMULATED** y finalizarlo. Abrir **Ajustes → Sincronizar**. Durante el intento aparece **Sincronizando…** y no se pueden acumular pulsaciones. Al terminar deben disminuir los pendientes y aumentar los `ACKED`, siempre que Laravel acepte las operaciones. Si el Worker ya las envió, es correcto que el contador del intento indique cero.
5. Actualizar la vista de recorridos del dashboard con tu sesión existente de la misma empresa. Comprobar el recorrido `COMPLETED` y sus eventos `SIMULATED`. Pulsar de nuevo Sincronizar y actualizar: no debe aparecer otro recorrido ni duplicarse sus eventos.
6. Para comprobar recuperación, detener solo el proceso `artisan serve` de prueba con Ctrl+C, crear/finalizar otro recorrido y pulsar Sincronizar. Esperar el resultado; un fallo de conexión puede tardar hasta el timeout HTTP existente. Los registros deben permanecer en Historial y en la cola pendiente. Reiniciar el servidor con el mismo comando y pulsar Sincronizar: el reintento `network_unavailable` puede adelantarse sin reiniciar Android. Si el servidor respondió con `Retry-After`, se respeta ese plazo. Sin intervención manual, WorkManager conserva el reintento automático cuando corresponda.

## Verificación

Ejecutado en el emulador Android 14 disponible:

- `assembleDebug`, `assembleDebugAndroidTest`: compilación correcta.
- `testDebugUnitTest`: 24 pruebas aprobadas; incluye contrato, reintentos, fechas locales, perfil y BLE.
- `lintDebug`: 0 errores, 23 advertencias existentes.
- `connectedDebugAndroidTest` con las exclusiones indicadas abajo: 29 casos registrados, 28 aprobados y 1 omitido (Laravel real, opt-in). Incluye las cinco pruebas nuevas, navegación, Room, migración, sesión y BLE sin hardware.
- `scripts/verify-laravel-e2e.ps1`: 1 prueba HTTP real aprobada contra Laravel con SQLite TEST aislada. Comprobó ACTIVE, COMPLETED, SIMULATED, reapertura de Room, replay idempotente, rechazo de token revocado y renovación. La verificación directa de SQLite arrojó **1 viaje COMPLETED y 1 evento SIMULATED**, también después de repetir los envíos. Reportes: `app/build/reports/laravel-e2e/instrumentation.txt` y `sqlite-verification.json`.

Comandos Gradle usados desde `mobile` (con JDK 17 y el SDK local configurados):

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notClass=com.tecsup.asistente.conduccion.TripFlowTest,com.tecsup.asistente.conduccion.RestartPersistenceTest' --console=plain
.\scripts\verify-laravel-e2e.ps1
```

Se excluyeron expresamente los cuatro casos de `TripFlowTest`/`RestartPersistenceTest`: escriben en la base manual de la app. No se afirma que hayan sido ejecutados en esta corrección. Las demás pruebas de persistencia utilizan bases aisladas. La reapertura de Room se comprobó; esta ejecución no pretende comprobar un reinicio físico de la laptop.

Las cinco pruebas nuevas comprobaron:

1. Botón Compose → ViewModel → motor → HTTP mockeado, estado deshabilitado durante el envío, diez llamadas repetidas ignoradas y ningún Worker solicitado cuando todo queda ACKED. Una pulsación posterior no repite el HTTP.
2. HTTP 503 con `Retry-After`: datos conservados, mensaje pendiente y una sola solicitud de respaldo durante pulsaciones repetidas. Otro intento explícito no ignora el plazo. El programador se sustituye por un contador en esta prueba; la política de producción `KEEP` se verificó en el código, sin alterar la base real de WorkManager.
3. Servidor de prueba apagado (conexión rechazada) → `network_unavailable` → reapertura de Room → servidor disponible → intento manual antes del plazo: ACKED con la misma ruta, payload y UUID. Las respuestas de recuperación son mockeadas, no resultados experimentales de sensores.
4. Respeto de 429/503 con plazo, bloqueos 403/409/422 y cinco leases vigentes: ningún nuevo envío ni modificación de esas operaciones en el intento manual.
5. Inicio, evento y finalización programan respaldo; la cola contiene COMPLETED y el motor confirma las tres operaciones.

No se cambiaron el esquema, las dependencias, la API ni los datos manuales. Las pruebas nuevas utilizan Room y sesiones aisladas; no borran ni reasignan recorridos del usuario.
