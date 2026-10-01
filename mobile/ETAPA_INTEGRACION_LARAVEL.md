# Integración Android ↔ Laravel

## Alcance

Se implementa Room → cola persistente → API Laravel → SQLite, con eventos `SIMULATED`. Room sigue siendo la fuente de las pantallas de viajes e historial. No se implementan dashboard, firmware, detección, Driver/Vehicle/Device ni generación SENSOR.

Se revisaron el contrato aprobado, los controladores/servicios/respuestas de Laravel y Room antes de modificar Android. El backend conserva su implementación. Los helpers de verificación crean datos `TEST ONLY` en una base distinta de la base local del backend.

## Persistencia y compatibilidad

El esquema Room pasa de **1 a 2**; la librería Room sigue en **2.7.2**. La migración es aditiva, sin fallback destructivo:

- `trips` y `driving_events` conservan columnas, claves Long, relación y datos.
- `trip_sync`: UUID `clientTripId`, referencia local, revisión y propietario de sincronización.
- `event_sync`: UUID `clientEventId` y referencia local.
- `sync_outbox`: identidad de operación, padre, ruta, contenido congelado, estado, intentos, próxima ejecución, lease y error.

La migración asigna UUID v4 una sola vez a cada registro existente. No son UUID GATT. El historial anterior queda **sin propietario** y no se envía al iniciar sesión. En Ajustes se puede seleccionar un recorrido finalizado y confirmar explícitamente la empresa, usuario y servidor destinatarios. Los recorridos activos sin propietario deben finalizarse primero. Nunca se reatribuye automáticamente el historial.

Cada alta de viaje, evento y finalización escribe su operación de cola dentro de la misma transacción Room. Inicio: revisión 1; final: revisión 2. Repetir la finalización no crea revisiones adicionales. Los nuevos viajes capturan el propietario conocido, aunque no haya Internet; sin sesión conocida quedan sin asignar.

La identidad de propietario incluye URL base normalizada, empresa y usuario. No se reutilizan credenciales actuales para operaciones de otra cuenta/empresa/servidor. Al cambiar de cuenta se filtra el historial local asociado; el historial sin asignar sigue disponible para uso local/adopción. No se cambia de propietario durante un viaje activo y se pide finalizar antes de cerrar sesión. Los tokens vencidos mantienen la identidad local para seguir trabajando offline y renovar después.

## Red y contrato

Librerías añadidas:

- OkHttp **4.12.0**, sin logging de credenciales, sin redirecciones automáticas y con límite de llamada de 30 segundos.
- Kotlin serialization JSON **1.8.0**, con plugin Kotlin **2.1.20**.
- WorkManager **2.10.1** y Hilt Work/compiler **1.2.0**.
- Pruebas: MockWebServer **4.12.0** y Room testing **2.7.2**.

Se añadió `INTERNET`. Los DTO de red están separados de los modelos de dominio. Las fechas se convierten de epoch milisegundos a UTC `YYYY-MM-DDTHH:mm:ss.SSSZ`, conservando el `null` explícito del final activo y los nombres de enums. Solo se construyen operaciones de eventos cuyo origen sea `SIMULATED`.

El envío utiliza los PUT aprobados. No descarga recorridos remotos a Room. Antes de marcar una operación `ACKED`, comprueba UUID, padre, campos, revisión y disposición de la respuesta. Un 200 con contenido incorrecto no se acepta como confirmación. Un `stale_ignored` solo confirma una revisión antigua cuando el estado remoto es coherente con el final local; nunca reabre ni sobrescribe el viaje local.

## Sesión

Inicio de sesión con `POST /api/v1/auth/token` y comprobación posterior `GET /api/v1/me`. Se requiere capacidad `trips:write`. El cierre intenta revocar con `DELETE /api/v1/auth/token` y elimina la sesión local. Si no hay Internet, la revocación remota puede no completarse; el token del servidor mantiene su expiración de Sanctum.

El token y su contexto se cifran con **AES-GCM**, clave en **Android Keystore**, y se guardan en preferencias privadas cifradas. No se guardan contraseñas, tokens en Room ni tokens en URLs/logs. La contraseña del formulario se borra al iniciar la operación. Las copias de seguridad de la aplicación continúan deshabilitadas. Un fallo de descifrado elimina la sesión inaccesible y exige volver a autenticarse.

Los endpoints y credenciales no se codifican como datos de una empresa real. La API existente conserva Sanctum y el aislamiento del servidor.

## Cola y recuperación

WorkManager usa conectividad requerida, trabajo inmediato único y reconciliación periódica de 15 minutos. Al abrir el proceso se vuelve a programar el envío; esto cubre una interrupción entre confirmar Room y programar WorkManager. Android puede diferir la ejecución por restricciones del sistema: no se promete envío instantáneo.

Las operaciones tienen lease de 2 minutos y un token de reclamación. Los workers concurrentes no reclaman la misma operación; los leases vencidos se recuperan. El intento usa siempre el mismo UUID y contenido. No se borran datos al agotar intentos. Si se pierde la respuesta o se detiene el proceso, el reintento idempotente no debe duplicar el registro remoto.

Un evento espera a que alguna operación de su viaje esté confirmada. La finalización puede llegar antes que los eventos, conforme al contrato. `COMPLETED` es un estado del viaje; `ACKED` es confirmación de una operación de cola, no prueba de conducción física.

Estados y respuestas:

- `PENDING`: listo para intentar cuando corresponda al propietario autenticado.
- `IN_FLIGHT`: operación reclamada temporalmente.
- `ACKED`: confirmación validada.
- `RETRY_WAIT`: desconexión, timeout, HTTP 408/429 o 5xx. Espera exponencial con variación aleatoria, base 30 segundos, tramo exponencial limitado a 6 horas; respeta `Retry-After` en segundos o fecha HTTP.
- `BLOCKED_AUTH`: 401/403. Un 401 invalida el token local y detiene nuevos envíos; renovar la misma identidad reactiva sus operaciones, sin reasignarlas.
- `BLOCKED_CONFLICT`: 409, padre inexistente/404 o confirmación incompatible. Requiere conciliación; no se inventan nuevos IDs para ocultarlo.
- `BLOCKED_VALIDATION`: 422 y otros errores permanentes. Se conservan los datos; no hay corrección o descarte automático.

Las dependencias de un padre bloqueado permanecen pendientes. El estado sin asignar se representa en los metadatos de propietario y en Ajustes; no se confunde con el estado del recorrido. Las operaciones de otros viajes pueden continuar.

## Configuración manual

1. En `backend`, comprobar `.env`, migraciones y ejecutar `php artisan serve --host=127.0.0.1 --port=8000`.
2. Crear una cuenta con datos proporcionados por el operador mediante `php artisan account:provision`. No existen cuentas reales predeterminadas. Si se usa una cuenta manual de desarrollo, nombrarla explícitamente TEST y no presentarla como información real.
3. Instalar el APK debug. Entrar con “Explorar aplicación”, abrir Inicio → Ajustes e indicar URL, email y contraseña. Iniciar sesión.
4. Volver a Inicio, iniciar un recorrido, generar un evento simulado y finalizar. En Ajustes consultar la cola o pulsar Sincronizar. El historial anterior exige confirmación de asignación por recorrido.
5. Comprobar los registros mediante la API autenticada o la base Laravel; nunca mediante acceso directo de Android a SQLite/MySQL.

URLs de desarrollo:

- Emulador estándar: `http://10.0.2.2:8000/`, alias especial del host, no IP personal.
- Emulador o teléfono de prueba por USB: ejecutar `adb reverse tcp:8000 tcp:8000` y usar `http://127.0.0.1:8000/`.
- Teléfono por LAN: indicar la dirección privada real del equipo y configurar el servidor/firewall para ese acceso. No se incluye una IP personal en el proyecto.
- HTTPS: usar la URL real con certificado válido.

La variante debug admite HTTP exclusivamente para localhost/direcciones IPv4 privadas mediante la validación del cliente. La excepción de manifest existe solo en `src/debug`. **Release prohíbe HTTP**, conserva validación TLS normal y no incluye una URL de producción inventada. La URL puede introducirse en Ajustes; el valor inicial debug se puede cambiar al compilar con `-PapiBaseUrl=http://127.0.0.1:8000/`.

Mantener la misma URL para los viajes ya asociados: otro alias se trata como otro destino por seguridad. No hay transferencia automática de cola entre servidores. No desactivar certificados para resolver errores de HTTPS.

## Archivos relevantes

- `app/build.gradle.kts`, `build.gradle.kts`, manifest principal y debug: dependencias, variantes y WorkManager.
- `data/network`: DTO, cliente Laravel y sesión cifrada.
- `domain/sync`: contrato de sesión y programación.
- `data/local/SyncEntities.kt`, `SyncDao.kt`, `SyncMigration.kt`, `OutboxWriter.kt`, `AsistenteVialDatabase.kt` y esquema `schemas/.../2.json`.
- `data/repository/RoomTripRepository.kt`: operaciones locales y cola atómica, filtrado por propietario.
- `data/sync`: política de reintentos, motor, autenticación/adopción, worker y programador.
- `core/di/SyncModule.kt`, `StorageModule.kt`, `AsistenteVialApplication.kt`: Hilt e inicialización.
- `feature/settings`: acceso, estado, asignación explícita y sincronización. Textos de inicio actualizados.
- `ContractSyncTest`, `SyncIntegrationTest`, `SecureSessionTest`, `SyncRestartPersistenceTest`, `LaravelEndToEndTest` y scripts de verificación.
- Pruebas anteriores: se actualiza la expectativa de `INTERNET` en el test de manifest y se registra la migración en las pruebas que abren la base de la aplicación. No se cambian las reglas BLE.

## Cómo repetir las comprobaciones

Con JDK 17 y el SDK/emulador disponibles, dentro de `mobile`:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest connectedDebugAndroidTest lintDebug assembleRelease
.\scripts\verify-process-restart.ps1
.\scripts\verify-sync-restart.ps1
.\scripts\verify-laravel-e2e.ps1
```

La prueba Laravel real es opt-in: la batería general la omite si no recibe argumentos de conexión de prueba. Su script instala los APK con `-r`, crea una SQLite exclusiva `backend/storage/framework/testing/android-e2e-*.sqlite`, crea empresa/usuario TEST, abre un servidor temporal en loopback y usa `adb reverse`. Las credenciales temporales no se imprimen ni versionan y su archivo se elimina al finalizar. Se detiene el servidor y se retira el reverse; la SQLite TEST se conserva para inspección. No se borra ni se siembra la base de desarrollo del backend.

Esta prueba usa Android instrumentado con Room, el repositorio, DTO, cliente y motor reales. No es una prueba física ni un ensayo con sensores. Los tests `SyncIntegrationTest` usan respuestas HTTP de MockWebServer y deben distinguirse de Laravel real. Las pruebas de interfaz existentes cubren la navegación local de viajes y BLE.

## Resultados de ejecución

Verificaciones ejecutadas el 28–29 de septiembre de 2026, con JDK 17 y emulador Android 14 (API 34):

- `assembleDebug`, `assembleDebugAndroidTest` y `assembleRelease`: correctos. Release genera un APK sin firma de distribución.
- `testDebugUnitTest`: **14 pruebas aprobadas**, incluidas las regresiones BLE y serialización/política de reintentos.
- `connectedDebugAndroidTest`: XML con **26 casos, 25 aprobados, 1 omitido y 0 fallos**. El omitido es el caso Laravel opt-in, ejecutado separadamente. Incluye migración 1→2 con datos conservados, UUID, cola offline, reintentos, respuestas 401/403/409/422/429, recibos inválidos, aislamiento local, adopción explícita, cifrado de sesión y navegación previa.
- `lintDebug`: **0 errores, 23 advertencias**. Principalmente versiones nuevas de dependencias, recomendaciones de preferencias y atributo BLE dependiente de API. La advertencia del proveedor se comprobó contra el manifest fusionado: `InitializationProvider` tiene `exported=false`. El guardado síncrono de sesión se mantiene para confirmar persistencia antes de publicar el estado.
- Laravel `php artisan test --compact`: **26 pruebas aprobadas, 212 aserciones**. No se modificó el código del servidor.

Informes locales: `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/debug/` y `app/build/reports/lint-results-debug.html`.

- `verify-laravel-e2e.ps1`: **1 prueba HTTP real aprobada**. Android creó un viaje ACTIVE, añadió un evento SIMULATED, finalizó el viaje y sincronizó. Reabrió Room y repitió los envíos con los mismos IDs. Laravel/SQLite conservaron exactamente **1 viaje COMPLETED y 1 evento SIMULATED**. También se verificó revocación de token, respuesta 401 y recuperación al reautenticar.
  - UUID de viaje TEST: `a2c791e5-49f3-4203-8a5b-0e62f55924a1`.
  - UUID de evento TEST: `d372ff31-5c38-4619-b601-5200fb95012b`.
  - Evidencia: `app/build/reports/laravel-e2e/instrumentation.txt` y `sqlite-verification.json`. El archivo temporal de credenciales fue eliminado.

La prueba HTTP real invoca el motor de sincronización desde instrumentación para hacer determinista la verificación. No mide los tiempos de ejecución automática de WorkManager bajo Doze, restricciones del fabricante o redes móviles. Las pruebas de reintentos/errores HTTP adicionales usan MockWebServer. No se realizaron pruebas físicas BLE/ESP32/MPU6050 ni se habilitó SENSOR.

- `verify-process-restart.ps1`: **3 fases aprobadas**, con detención real del proceso entre fases. Se conservaron el viaje activo y sus eventos, se finalizó al reabrir y se consultó el historial persistido después de otro cierre.
- `verify-sync-restart.ps1`: **2 fases aprobadas**, con detención real del proceso. Se conservaron UUID y contenidos de las tres operaciones (inicio, evento, final), y se recuperó una operación cuyo lease había vencido.

Evidencia adicional: `app/build/reports/process-restart/` y `app/build/reports/sync-process-restart/`. Los scripts no usan `pm clear` ni desinstalan la aplicación. Las cinco fases son ejecuciones adicionales de casos presentes en la batería general, esta vez separadas por cierres reales.

La fase queda finalizada dentro de este alcance. No quedan bloqueos de compilación o pruebas ejecutadas. Para uso manual hace falta iniciar el servidor y proporcionar una identidad local autorizada (o explícitamente TEST); no se dejó una contraseña de acceso predeterminada. Dashboard, sincronización de descarga, conciliación asistida de conflictos y pruebas con hardware quedan fuera de esta fase.
