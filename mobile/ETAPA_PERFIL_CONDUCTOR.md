# Mi perfil del conductor — integración Laravel

## IMPLEMENTADO

Se conserva Kotlin/Compose/Material 3, Hilt, ViewModel/StateFlow, OkHttp/serialización y SessionStore cifrado existentes. Nueva entrada **Mi perfil** desde Inicio. No hay bibliotecas nuevas, cambios de versión/schema Room (**2**, biblioteca **2.7.2**), migraciones Android, modificaciones BLE, hardware ni cambios en la cola WorkManager.

`GET /api/v1/me/driver` usa el mismo servidor/token Sanctum configurado en Ajustes. No recibe IDs de otros usuarios. `/api/v1/me`, viajes/eventos y sus UUID/revisiones permanecen iguales.

Capas nuevas:

- `domain/profile/DriverProfile.kt`: modelos de perfil/contacto/documento/vehículo, estados e interfaz de repositorio.
- `data/network/DriverProfileDto.kt`: DTO separado y conversión explícita; fecha de vencimiento `LocalDate`, porque es fecha calendario y no un timestamp UTC.
- `data/repository/LaravelDriverProfileRepository.kt`: consulta HTTP, adaptación de errores y cambio de sesión. Usa LaravelApi existente.
- `core/di/ProfileModule.kt`: vínculo Hilt de interfaz/implementación.
- `feature/profile/ProfileViewModel.kt` y `ProfileScreen.kt`: StateFlow, ciclo de vida, vista y actualización manual.
- Cambios acotados en HomeScreen, AppRoute, AppNavHost y strings.xml.

Contenido: nombre/email/estado, contacto propio, vehículo actual y metadatos de documentos (tipo, revisión, vencimiento). VERIFIED se presenta como revisión administrativa; no validación legal. Ningún resultado de sensores ni datos personales de relleno.

Estados: cargando, sin sesión, sin perfil registrado (404), sin conexión, sesión inválida/vencida (401), acceso denegado (403), fallo de servidor y perfil disponible. Elementos opcionales muestran “Sin vehículo asignado”, “Sin documentos registrados” y “Sin contacto de emergencia registrado”. Botón para volver a Ajustes cuando corresponde y Actualizar perfil para reintentar.

### Privacidad y offline

No se persisten contacto/documentos/perfil en Room, preferencias ni caché HTTP. La respuesta solo vive en memoria mientras se observa la pantalla; StateFlow borra su replay al dejar de estar suscrito. Un cambio de cuenta/token cancela el flujo y descarta respuestas de la sesión anterior. No se registran tokens ni datos del perfil en logs. No se descarga el archivo documental.

Consultar perfil requiere servidor. Esta limitación no afecta iniciar/finalizar viajes, eventos SIMULATED, historial, Room ni cola offline. Un error de perfil no borra recorridos ni modifica la sesión/cola de sincronización por su cuenta. Las fechas de viajes continúan persistidas/sincronizadas en UTC y presentadas en zona local del dispositivo.

## PROBADO

Ejecutado el 30/09/2026 con JDK 17, Android SDK 35 y emulador Android 14:

- `assembleDebug`, `assembleDebugAndroidTest`: correctos.
- **24 pruebas unitarias aprobadas**, incluyendo 7 nuevas de DTO/repositorio/ViewModel. Cubren Bearer y ruta propia, campos vacíos, fecha calendario, 401/403/404/429/500, JSON incorrecto, ausencia de sesión/token, servidor no disponible y limpieza del estado del ViewModel.
- Instrumentación: **24 casos, 23 aprobados, 0 errores/fallos, 1 omitido** (LaravelEndToEndTest opt-in). Incluye dos nuevas pruebas visuales de perfil vacío/offline/sesión expirada, navegación a Mi perfil y pruebas existentes de Room, migración, UUID, sincronización con MockWebServer, sesión segura y BLE preparado.
- El E2E omitido por defecto se ejecutó **por separado contra Laravel real**: **1 prueba aprobada**, con cuenta del nuevo rol `driver` en la ejecución final. Emulador → HTTP local → Sanctum → perfil/contacto/vehículo TEST → Trip ACTIVE/COMPLETED → evento SIMULATED → reenvío idempotente → reapertura Room → 401/reautenticación. Verificación SQLite: exactamente 1 viaje COMPLETED y 1 evento SIMULATED.
- `lintDebug`: **0 errores, 23 advertencias**; no se presentan advertencias como fallos ni se ocultan.
- Backend final: **68 pruebas / 520 aserciones**, incluidas regresiones API y Dashboard V1.

Los fixtures HTTP/browser solo existen en SQLite aisladas bajo `backend/storage/framework/testing`. Los tests de repositorio usan bases Room de prueba. La prueba real de perfil no es un mock; las unitarias de red y SyncIntegrationTest sí utilizan MockWebServer.

Los cuatro casos de `TripFlowTest` y `RestartPersistenceTest` se conservaron pero se excluyeron de esta ejecución porque escriben en la base de la app instalada que contiene datos manuales. No se desinstaló ni limpió la app; instalación de APK con `-r`. Persistencia y flujo ACTIVE→COMPLETED se verificaron con pruebas aisladas y HTTP real. No se afirma haber probado hardware.

Comandos desde `D:\Proyectos\AsistenteVialInteligente\mobile`:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:ANDROID_HOME='C:\Users\USUARIO\AppData\Local\Android\Sdk'
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notClass=com.tecsup.asistente.conduccion.TripFlowTest,com.tecsup.asistente.conduccion.RestartPersistenceTest' --console=plain
.\scripts\verify-laravel-e2e.ps1
```

Evidencia generada: `app/build/reports/tests/testDebugUnitTest/`, `app/build/outputs/androidTest-results/connected/debug/`, `app/build/reports/lint-results-debug.html`, `app/build/reports/laravel-e2e/instrumentation.txt` y `sqlite-verification.json`. El script elimina el archivo temporal de credenciales al acabar y detiene el servidor TEST.

Tests nuevos: `DriverProfileTest.kt`, `ProfileViewModelTest.kt`, `ProfileScreenTest.kt`. Ampliados `NavigationTest.kt`, `LaravelEndToEndTest.kt` y fixture `scripts/laravel-e2e-fixture.php` para perfil/contacto/vehículo exclusivamente TEST.

## Prueba manual

1. Sigue `../backend/ETAPA_GESTION_FLOTA_V1.md`: registra conductor, contacto/vehículo/documentación opcionales y activa su contraseña en `/access`.
2. Mantén Laravel local funcionando en puerto 8000.
3. En el emulador abre **Inicio → Ajustes**. En **URL base del servidor** introduce `http://10.0.2.2:8000/`; en **Email** y **Contraseña**, las credenciales que tú activaste. Pulsa **Iniciar / renovar sesión**. No introduces un token manualmente.
4. Si cambias de cuenta, finaliza primero cualquier viaje activo según las reglas de sesión existentes. No reasignes viajes legacy como efecto de consultar perfil.
5. Inicio → **Mi perfil**. Verifica datos del conductor, contacto, documentos y vehículo. Los campos no registrados deben aparecer vacíos, nunca con contenido de muestra.
6. Cambia un dato permitido en dashboard; **Actualizar perfil** vuelve a consultarlo. Desconecta el servidor y actualiza: se presenta indisponibilidad de conexión, sin impedir viajes offline.
7. Con Luis TEST legacy, la ausencia de Driver produce mensaje de perfil no registrado. No se creó uno automáticamente ni se alteraron sus viajes o credenciales.

Un teléfono físico necesita una URL alcanzable de tu red, no `10.0.2.2`. Se conserva la restricción HTTP a debug/local; producción requiere HTTPS.

## PENDIENTE / límites

La pantalla no permite editar el perfil ni descargar archivos; los datos son administrados por la empresa. No hay caché persistente de información personal. La verificación visual real en emulador usó estados UI TEST; la consulta HTTP real se verificó además con el repositorio y la app instrumentada. No se afirma validación física en teléfono o sensores.

## FUTURO

Diseñar contexto conductor/vehículo capturado al iniciar viaje mediante ampliación compatible del contrato. BLE físico, SENSOR, GPS, buzzer, telemetría y detección siguen pendientes y fuera de esta entrega.
