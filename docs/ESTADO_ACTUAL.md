# Estado actual del proyecto

Resumen histórico: **2026-10-03**, con ampliación 3C posterior. La actualización vigente (GPS, Room 3, mapas, administración y buzzer) está en [CHECKPOINT_2026_10_10.md](CHECKPOINT_2026_10_10.md); prevalece sobre las funciones pendientes de este resumen. La evidencia física procede del reporte del responsable del proyecto.

## Arquitectura y flujos actuales

Objetivo: `IMU/MPU6050 → ESP32 → BLE → Android → API REST/HTTPS → Laravel → base de datos → dashboard`.

Android es la interfaz del conductor y Laravel es API/backend y administración de flota del mismo sistema. No hay conexión Android directa a una base SQL remota.

Actualmente:

1. **Hardware:** módulo IMU → I2C → ESP32 → GATT/NOTIFY → Diagnóstico Android. Seis cuentas crudas int16, solo en memoria; sin eventos, Room ni envío a Laravel.
2. **Viajes de prueba:** Android → Room → outbox → SyncEngine → API Sanctum → SQLite → dashboard. Únicamente eventos `SIMULATED`.

La unión entre mediciones y eventos de conducción está pendiente. Las futuras alertas inmediatas deben funcionar localmente sin depender de Internet; todavía no existe algoritmo de detección.

## Android implementado

Proyecto directamente en `mobile/`; applicationId `com.tecsup.asistente.conduccion`, versión declarada 0.4.0. Kotlin/Compose/Material 3, Hilt, Navigation, Coroutines y ViewModel/StateFlow; organización `core`, `data`, `domain`, `feature`, `navigation`.

- Pantallas de acceso/exploración, inicio, viaje, historial/detalle, BLE, diagnóstico, ajustes y perfil propio.
- Viajes ACTIVE/COMPLETED y eventos manuales HARSH_BRAKING/HARSH_ACCELERATION/SHARP_TURN, siempre SIMULATED. No son infracciones ni maniobras detectadas.
- Room 2.7.2, **esquema 2**, migración aditiva 1→2. Conserva IDs Long, datos y relaciones; añade `trip_sync`, `event_sync`, `sync_outbox` y UUID persistentes.
- Historial y viajes activos leídos desde Room. Fechas epoch milisegundos/Instant UTC; interfaz mediante `ZoneId.systemDefault()`, sin cambiar timestamps almacenados.
- BLE: soporte/estado, permisos por versión, escaneo/listado sin duplicados, selección, GATT real, descubrimiento, CCCD y NOTIFY. Diagnóstico decodifica exactamente 12 bytes y descarta lecturas caducadas al desconectar o dejar de recibir.
- Perfil propio desde `GET /api/v1/me/driver`: contacto, vehículo y metadatos documentales en memoria; consulta requiere servidor y no altera Room/cola offline.

## Offline, sesión y sincronización

Inicio/finalización de viaje y generación de eventos no requieren Internet. Datos de negocio y operación congelada de cola se guardan en la misma transacción. UUID de viaje/evento se genera una vez y se reutiliza. El propietario de sincronización incluye servidor, empresa y usuario; los viajes sin propietario no se adjudican ni envían automáticamente. Ajustes permite adopción explícita de recorridos finalizados sin asignar.

Autenticación `POST /api/v1/auth/token`, comprobación `GET /api/v1/me` y revocación `DELETE /api/v1/auth/token`. Token/contexto cifrados AES-GCM con Android Keystore; no se persiste contraseña. HTTP local permitido solo en variante debug; release exige HTTPS.

`SyncViewModel.sync()` llama inmediatamente a `SyncEngine.syncNow()`, evita pulsaciones concurrentes y muestra confirmadas, pendientes y bloqueadas. Adelanta exclusivamente reintentos `network_unavailable`; no ignora Retry-After ni fuerza IN_FLIGHT/bloqueos. `drain()` reutiliza la misma cola para envío manual y Worker, con reclamación por lease.

WorkManager requiere conectividad; inicio/evento/finalización programan `trip-sync-now` con APPEND_OR_REPLACE. El respaldo manual usa KEEP para no acumular trabajos con pulsaciones repetidas; reconciliación periódica cada 15 minutos y al iniciar proceso. Android puede diferir Workers: no se promete ejecución automática instantánea.

Estados: PENDING, IN_FLIGHT, ACKED, RETRY_WAIT, BLOCKED_AUTH, BLOCKED_CONFLICT y BLOCKED_VALIDATION. Leases de dos minutos recuperan operaciones interrumpidas; reintentos conservan UUID/payload. 401/403 bloquean autenticación; 409/404 conflicto; 422 validación; 408/429/5xx y fallos de red permiten espera/reintento, respetando Retry-After. Los eventos esperan confirmación de su padre. Room no se borra por errores de red.

## Laravel, API y dashboard implementados

Laravel 12/Sanctum 4; las fases documentadas usaron Laravel 12.69.2, Sanctum 4.3.3 y PHP 8.2.12. Base local SQLite. Versiones reproducibles de dependencias en `backend/composer.lock`; PHP requiere al menos 8.2. No se ha desplegado producción.

Modelos actuales: Company, User, Trip, DrivingEvent, Driver, Vehicle, VehicleAssignment, EmergencyContact, DocumentType, DriverDocument, AccessInvitation y AdministrativeAudit. `Device` no implementado.

API `/api/v1`:

- POST/DELETE `/auth/token`, GET `/me`, GET `/me/driver`.
- PUT `/trips/{client_trip_id}`; GET `/trips` y `/trips/{client_trip_id}`.
- PUT `/trips/{client_trip_id}/events/{client_event_id}`; GET `/trips/{client_trip_id}/events`.

UUID públicos por empresa, revisión 1 ACTIVE / 2 COMPLETED, eventos inmutables, conflictos 409, revisión antigua coherente `stale_ignored`. Validación de fechas RFC3339 UTC con milisegundos y respuestas verificadas por Android antes de ACKED. Escritura `SENSOR` rechazada con 422 `origin_not_enabled`. El contexto de empresa procede de la cuenta/token; no se acepta una empresa arbitraria del payload. Recursos y consultas aplican aislamiento y permisos.

Dashboard Blade + CSS local: login web separado de Sanctum, resumen, recorridos/detalles, eventos, conteos/analíticas y ajustes. Muestra registros recibidos, no cola offline ni presencia en tiempo real. No modifica viajes/eventos. Gestión de flota: conductores, vehículos, asignaciones con historial, documentos privados/revisión y acceso mediante códigos de un uso. Área `/platform` para administración global de empresas/cuentas/auditoría, separada del dashboard de cada empresa. Roles member/driver/fleet_admin/platform_admin; plataforma no obtiene tokens Android.

`trips.driver_id` y `vehicle_id` son nullable; no se rellenaron viajes legacy ni se infiere vehículo al recibir datos offline. El contrato de sincronización aún no transmite ese contexto. La revisión documental VERIFIED es administrativa, no certificación legal.

## Etapa 3B: transmisión cruda validada físicamente

**Prueba realizada y reportada por el responsable el 2026-10-03:** ESP32, módulo IMU y teléfono Android real. Flujo observado `IMU → I2C → ESP32 → BLE GATT/NOTIFY → Android`:

- Respuesta I2C estable en `0x68`, inicialización y lectura del bloque de seis ejes.
- `WHO_AM_I = 0x75` permanece anómalo; identidad del módulo NO confirmada.
- Continuación con advertencia y modo experimental explícito; anuncio AsistenteVial-IMU.
- Detección Android, conexión GATT real y NOTIFY habilitado.
- Transmisión/recepción del frame IMU_RAW de 12 bytes a 4 Hz según lo reportado; configuración nominal 250 ms, sin medición independiente de jitter o pérdidas.
- ax/ay/az/gx/gy/gz cambian al mover controladamente el circuito y se observan en Android y Serial.

Esto valida **el transporte físico de cuentas crudas** para esa prueba; no identifica válidamente un MPU6050, no certifica precisión/conversión/calibración y no valida maniobras peligrosas. No se inventan muestras numéricas, tasas de acierto ni resultados de conducción.

Firmware: `firmware/AsistenteVialImu/AsistenteVialImu.ino`, ESP32 Dev Module/core 3.3.12, controlador I2C ESP-IDF nativo; SDA GPIO21, SCL GPIO22, dirección 0x68. Comprueba comunicación, reset/wake/configuración y lectura completa del bloque antes de consultar identidad. Un valor distinto de 0x68 permite solo modo experimental explícito; fallos de comunicación/configuración/bloque o imposibilidad de leer identidad detienen el arranque BLE. Muestras fallidas durante transmisión se omiten.

Protocolo conservado:

- Nombre `AsistenteVial-IMU`.
- Servicio `73e8ca02-41d7-45bd-9d39-7187649746ca`.
- IMU_RAW `e77a0a03-3641-40f2-87b7-f178622259cd`, NOTIFY con CCCD estándar.
- 12 bytes: ax, ay, az, gx, gy, gz signed int16 little-endian; 4 Hz nominales.

El frame no informa a Android del modo experimental: advertencia y limitación quedan en Serial/documentación. Conexión BLE en primer plano, reconexión manual; no se implementó bonding/cifrado/autenticación del periférico. El sketch independiente WHO_AM_I se conserva como antecedente, sin exigir nuevas pruebas aisladas para continuar.

## Pendientes y reglas para cambios

**Etapa 3C — software preparado, validación física pendiente:** pipeline nominal RAW → conversión → offsets/filtro opcionales → detector de candidatos en memoria. Detector, filtro y calibración deshabilitados por defecto; no se generan eventos SENSOR. Ver [preparación 3C](ETAPA_3C_PROCESAMIENTO.md). La conversión software no confirma la identidad del módulo ni valida maniobras. Etapa 4 mantiene su numeración original (backend desarrollado anticipadamente).

- Resolver/identificar la anomalía del módulo; **jamás aceptar 0x75 como identidad válida** ni ocultar el modo experimental.
- Calibración y validación física de conversión/criterios; productor de eventos SENSOR, asociación con viajes/Room y envío a Laravel mediante contrato revisado.
- GPS, buzzer, Device y contexto conductor/vehículo de viajes; pruebas controladas posteriores.
- Seguridad BLE y preparación/despliegue de producción.
- No inventar datos, métricas, credenciales o evidencia. SIMULATED es prueba; SENSOR aún no representa detecciones físicas validadas.
- Conservar datos locales/remotos, contratos, UUID, frame y revisiones. Revisar implementación real antes de editar; migraciones aditivas y verificación incremental. No resetear Room/SQLite ni firmware sin necesidad.

## Referencias y colaboración

Leer [contrato](CONTRATO_ANDROID_LARAVEL.md), [BLE](ETAPA_3B_BLE.md), [integración](../mobile/ETAPA_INTEGRACION_LARAVEL.md), [corrección manual](../mobile/CORRECCION_SINCRONIZACION_MANUAL.md), [API](../backend/ETAPA_BACKEND_API.md), [dashboard](../backend/ETAPA_DASHBOARD_V1.md), [flota](../backend/ETAPA_GESTION_FLOTA_V1.md) y [perfil](../mobile/ETAPA_PERFIL_CONDUCTOR.md).

Los documentos de etapas conservan resultados históricos de sus ejecuciones; el contrato original contiene una auditoría de una versión anterior. No interpretar sus descripciones históricas como ausencia de funciones actuales. Esta actualización de estado no ejecuta nuevamente pruebas Android/backend ni una prueba física por parte del agente.

Versionar solo fuentes, esquemas, contratos y fixtures de prueba. No subir `.env`, claves/tokens, SQLite/Room, documentos privados, logs, builds, dependencias ni configuración local. Revisar git status/diff, archivos rastreados y .gitignore antes del commit; no borrar trabajo existente ni hacer force push.
