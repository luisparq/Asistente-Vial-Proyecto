# Backend — Laravel, persistencia y API REST inicial

Fecha: 2026-09-28. Implementación local del servidor, sin sincronización Android ni dashboard completo.

## 1. Alcance autorizado y decisiones conservadas

El usuario aprobó los puntos 1–6 de la sección 11 de [CONTRATO_ANDROID_LARAVEL.md](../docs/CONTRATO_ANDROID_LARAVEL.md). Ese documento conserva la propuesta original como referencia; la aprobación y esta implementación son posteriores.

Se mantienen: IDs locales Android sin cambios y UUID de registros para intercambio; sincronización futura unidireccional e incremental con PUT idempotente; revisiones 1/2; eventos inmutables; aceptación de llegadas tardías; UTC y milisegundos; anomalías temporales conservadas y señaladas; aislamiento por empresa y usuario; adopción futura explícita del historial sin propietario; dashboard limitado posteriormente a datos recibidos. No se implementó ninguna parte de la cola/adopción Android.

Se revisaron estructura, contrato, PHP, extensiones y Composer antes de escribir. `backend/` contenía únicamente `.gitkeep`. Se creó el proyecto Laravel dentro de esta carpeta. El contrato ya estaba sin seguimiento Git al comenzar y se conservó. No se modificaron `mobile/` ni `firmware/`.

## 2. Entorno utilizado

- PHP CLI **8.2.12**, `C:\xampp\php\php.exe`.
- Composer **2.10.1**.
- Laravel Framework **12.69.2** y Sanctum **4.3.3**, fijados en [composer.lock](composer.lock).
- PHPUnit **11.5.56** y Laravel Pint **1.30.4**.
- SQLite **3.39.2**, mediante PDO SQLite, con claves foráneas habilitadas y espera de bloqueo de 5 segundos.
- Base local: `backend/database/database.sqlite`, ignorada por Git. UTC en Laravel; fechas de negocio guardadas con milisegundos mediante formato de modelo `Y-m-d H:i:s.v`.

Se eligió Laravel 12 por compatibilidad con PHP 8.2 disponible. Laravel 13 requiere PHP 8.3 como mínimo. Esta selección no implica que se haya actualizado la instalación global de PHP. Referencias oficiales: [requisitos Laravel 12](https://laravel.com/framework/docs/12.x/deployment) y [versiones Laravel](https://laravel.com/framework/docs/releases).

La base local quedó migrada y **sin empresas, usuarios, tokens, viajes o eventos**. Los fixtures se crean exclusivamente durante las pruebas y se identifican como `TEST ONLY`; no representan personas, empresas o maniobras reales.

No se instaló un servidor MySQL/MariaDB, ni se modificó XAMPP globalmente. El SQL se expresa mediante migraciones Laravel, pero **solo SQLite fue ejecutado y verificado** en esta fase.

## 3. Modelo y migraciones

Se ejecutaron siete migraciones:

1. [0000_12_31_000000_create_companies_table.php](database/migrations/0000_12_31_000000_create_companies_table.php): empresa con nombre e identidad interna.
2. [0001_01_01_000000_create_users_table.php](database/migrations/0001_01_01_000000_create_users_table.php): cuenta, empresa obligatoria, rol, contraseña hasheada; tablas auxiliares de sesión y recuperación del esqueleto Laravel.
3. [0001_01_01_000001_create_cache_table.php](database/migrations/0001_01_01_000001_create_cache_table.php): infraestructura estándar de caché; la configuración local usa archivos.
4. [0001_01_01_000002_create_jobs_table.php](database/migrations/0001_01_01_000002_create_jobs_table.php): infraestructura estándar de trabajos; esta API persiste sincrónicamente y no usa una cola para confirmar escrituras.
5. [2026_09_28_000001_create_personal_access_tokens_table.php](database/migrations/2026_09_28_000001_create_personal_access_tokens_table.php): Sanctum, hash del token, capacidades, expiración y empresa fijada al emitirlo.
6. [2026_09_28_000002_create_trips_table.php](database/migrations/2026_09_28_000002_create_trips_table.php): empresa, creador, UUID del cliente, fechas, estado, revisión y tiempos de recepción/modificación.
7. [2026_09_28_000003_create_driving_events_table.php](database/migrations/2026_09_28_000003_create_driving_events_table.php): empresa, viaje, UUID del evento, tipo, origen, fecha y primera recepción.

Restricciones únicas `(company_id, client_trip_id)` y `(company_id, client_event_id)`. Claves foráneas compuestas impiden asociar un viaje con un usuario de otra empresa o un evento con un viaje de otra empresa. No hay borrado remoto en este contrato; las referencias se restringen en vez de ofrecer cascadas destructivas.

Los modelos están en [app/Models](app/Models): `Company`, `User`, `Trip`, `DrivingEvent` y el modelo técnico `PersonalAccessToken`. No se crearon `Driver`, `Vehicle` o `Device`. Un usuario creador no se presenta como conductor físico.

## 4. Autenticación y autorización implementadas

Sanctum funciona exclusivamente con Bearer en esta fase; no se activó autenticación de SPA/sesión para un dashboard. Véase [documentación Sanctum](https://laravel.com/framework/docs/12.x/sanctum).

- `POST /api/v1/auth/token`: recibe únicamente `email` y `password`. Responde `201` con `data.token_type`, `data.access_token` y `data.expires_at`. Credenciales inválidas producen `401 invalid_credentials`; no se revela si la cuenta existe. La respuesta lleva `Cache-Control: no-store`.
- `DELETE /api/v1/auth/token`: autenticado; revoca el token usado y responde `204`.
- Tokens con vencimiento de 30 días; Sanctum persiste su hash. Cada token queda vinculado a la empresa al crearlo. Cambiar la empresa del usuario invalida el contexto de un token anterior.
- `member`: `trips:read` y `trips:write`, solo para sus viajes.
- `fleet_admin`: añade `trips:read-company`, para consultar viajes de su empresa. No puede modificar viajes de otros usuarios: se conserva un solo escritor por viaje.
- Los permisos efectivos son la intersección de capacidades actuales de la cuenta y capacidades del token. Un rol desconocido no concede permisos de viajes.
- Recursos ajenos al ámbito visible responden `404`. Un administrador que puede leer un viaje ajeno pero intenta escribirlo recibe `403`.
- Login: límite de 20 intentos/minuto por IP y 5 por cuenta; API autenticada: 120 peticiones/minuto por usuario. Las respuestas `429` preservan `Retry-After`.
- JSON de hasta 16 KiB, validación estricta, errores sin SQL/stack trace y HTTPS requerido cuando `APP_ENV=production`.

Alta inicial: [ProvisionAccount.php](app/Console/Commands/ProvisionAccount.php), comando `php artisan account:provision`. Solicita datos reales al operador, contraseña oculta de al menos 12 caracteres y confirmación; sin credenciales predeterminadas ni registro público. `--company-id` permite añadir una cuenta a una empresa existente. No se ejecutó para crear identidades reales. [DatabaseSeeder.php](database/seeders/DatabaseSeeder.php) es deliberadamente vacío.

Estos dos endpoints de token completan el flujo de autenticación necesario para usar los seis recursos acordados. No se creó gestión web de cuentas, membresías múltiples, recuperación de contraseña ni autoinscripción pública.

## 5. Recursos del contrato

Todos requieren token válido y contexto de empresa:

- **GET `/api/v1/me`**: usuario, empresa y capacidades efectivas; no devuelve contraseña o token.
- **PUT `/api/v1/trips/{client_trip_id}`**: cuerpo completo `started_at`, `ended_at`, `status`, `client_revision`.
- **PUT `/api/v1/trips/{client_trip_id}/events/{client_event_id}`**: cuerpo completo `type`, `occurred_at`, `origin`.
- **GET `/api/v1/trips`**: filtros `status`, `started_from` inclusivo, `started_before` exclusivo, `limit` y `cursor`.
- **GET `/api/v1/trips/{client_trip_id}`**: datos recibidos, revisión, fechas de recepción, `received_event_count` y `temporal_issues`.
- **GET `/api/v1/trips/{client_trip_id}/events`**: filtros `type`, `origin`, `occurred_from` inclusivo, `occurred_before` exclusivo, `limit` y `cursor`.

UUID v4 requerido; se normaliza a minúsculas. Los IDs son de registros, no de GATT. Empresa y creador se derivan de la autenticación, nunca de un cuerpo suministrado por Android. Se rechazan campos adicionales y parámetros de consulta en escrituras.

Fechas canónicas `YYYY-MM-DDTHH:mm:ss.SSSZ`; rango admitido 1000–9999. No se aceptan fechas inexistentes, cadenas localizadas, segundos sin fracciones o desplazamientos distintos de la representación canónica. El dominio Android `Instant`/Room epoch ms continúa intacto.

Viajes: `ACTIVE`, revisión entera 1 y final explícitamente `null`; `COMPLETED`, revisión entera 2 y final obligatorio. La cadena vacía no equivale a `null`. Enums sensibles a mayúsculas.

Eventos: `HARSH_BRAKING`, `HARSH_ACCELERATION`, `SHARP_TURN`; escrituras únicamente `SIMULATED`. `SENSOR` pertenece al vocabulario pero produce `422 origin_not_enabled`. Consultar `origin=SENSOR` es válido y devuelve una lista vacía mientras no existan datos de ese origen; no habilita su creación.

Paginación: 25 elementos por defecto, máximo 100. Cursor cifrado y autenticado, vinculado a identidad, empresa, capacidad de lectura de empresa, ruta y filtros. Orden de viajes por inicio descendente y UUID ascendente; eventos por fecha ascendente y UUID ascendente. El cursor usa valores SQL con milisegundos, evitando pérdidas por serialización de Carbon. No es una instantánea inmutable frente a escrituras concurrentes.

Respuestas de escritura según contrato: `201 created`, `200 updated`, `200 already_applied`, `200 stale_ignored`. Los viajes incluyen `sync.acknowledged_revision` y `sync.current_revision`. Los eventos incluyen su padre y primera recepción.

Errores: `error.code`, `error.message` y `error.fields` cuando corresponde. Se implementaron `400`, `401`, `403`, `404`, `409`, `422`, `429` y errores internos genéricos; también `413` para tamaño excesivo y `415` para contenido distinto de JSON. No se redirige a un login HTML cuando falta `Accept`.

## 6. Persistencia, idempotencia y temporalidad

[TripSync.php](app/Services/TripSync.php) realiza las escrituras en transacciones. Para esta primera versión se serializa la ingestión por empresa adquiriendo un bloqueo de escritura sobre su fila; funciona también en SQLite, donde `SELECT FOR UPDATE` no protege una creación concurrente. Los índices únicos y las FK respaldan las invariantes. Se permite reintentar transacciones por concurrencia hasta cinco veces; la confirmación HTTP solo se devuelve después del commit.

Mismo UUID y contenido: sin nuevo registro ni cambio de primera recepción. Inicio inmutable; final inmutable una vez aceptada la revisión 2. Mismo número de revisión con contenido diferente: `409`. Una revisión 1 compatible posterior a la 2 responde `stale_ignored` y no reabre el viaje. Se puede crear directamente un viaje completado.

Los eventos son inmutables, no pueden cambiar de padre y pueden llegar después de finalizado el recorrido. El padre debe existir primero en el ámbito autorizado. Los UUID de otra empresa no dan acceso a sus recursos: la unicidad está deliberadamente delimitada por empresa, por lo que el mismo UUID puede existir independientemente en dos empresas.

Se conservan anomalías del reloj y se calculan al consultar: `end_before_start`, `event_before_start`, `event_after_end`. Se vuelven a evaluar usando los datos actualmente recibidos, incluso después de eventos tardíos. No se corrigen fechas ni se calculan duraciones negativas. Un viaje finalizado no significa que todos sus eventos hayan llegado; el conteo se llama `received_event_count` y no se expone un indicador remoto de sincronización completa.

El bloqueo por empresa prioriza sencillez y corrección. Su capacidad bajo carga de flotas grandes no se midió; antes de ampliar concurrencia se deberá evaluar una estrategia más granular manteniendo las mismas garantías. No se presentan métricas de rendimiento.

## 7. Verificaciones realmente ejecutadas

Resultado final de `php artisan test --compact --log-junit storage/framework/testing/api-results.xml`: **26 pruebas aprobadas, 212 aserciones, sin fallos**. El reporte JUnit generado queda en `storage/framework/testing/api-results.xml` y está ignorado por Git.

Archivos y cobertura:

- [ApiContractTest.php](tests/Feature/ApiContractTest.php): autenticación real Sanctum mediante tokens persistidos; login, expiración, token inválido, revocación y empresa del token; creación/idempotencia; transición ACTIVE → COMPLETED; revisión antigua; conflictos; eventos simulados y tardíos; rechazo SENSOR; huérfanos; aislamiento entre empresas y usuarios; lectura administrativa sin escritura ajena; capacidades; UUID y normalización; campos desconocidos; fechas y revisiones; anomalías del reloj; consultas, filtros y cursores; FK entre empresas; JSON malformado, tamaño, rate limiting y error sin cabecera Accept; HTTPS en producción; seeder vacío.
- [ConcurrentPersistenceTest.php](tests/Feature/ConcurrentPersistenceTest.php) y [api_worker.php](tests/Support/api_worker.php): archivo SQLite temporal exclusivo. Cuatro procesos PHP enviaron el mismo viaje simultáneamente y devolvieron un `201` y tres `200`; otros cuatro hicieron lo mismo con el evento. Quedó un viaje y un evento. Procesos nuevos finalizaron y consultaron el recorrido/eventos, verificando persistencia entre procesos y milisegundos.
- [ProvisionAccountTest.php](tests/Feature/ProvisionAccountTest.php): alta administrativa interactiva con identidad explícita de prueba, contraseña hasheada y sin emitir token.
- [ExampleTest.php](tests/Feature/ExampleTest.php): disponibilidad de la raíz JSON y `/up` sin frontend. Se eliminó la prueba trivial de ejemplo que solo verificaba `true`.

Otras comprobaciones ejecutadas:

- `php artisan migrate --no-interaction`: siete migraciones completadas; `migrate:status` confirmó todas aplicadas.
- `php vendor/bin/pint --test`: aprobado después de aplicar formato.
- `php -l` en 60 archivos PHP del proyecto: cero errores de sintaxis en esa ejecución; los cambios posteriores de protección de pruebas también se cargaron y ejecutaron en la batería final.
- `composer validate --strict`: manifiesto válido.
- `composer check-platform-reqs`: requisitos satisfechos.
- `composer audit --locked`: no encontró avisos de vulnerabilidades de dependencias en la consulta realizada. Esto no es una auditoría de la instalación global de PHP ni una certificación de seguridad.
- Servidor HTTP real temporal en loopback: raíz `200`, `/api/v1/me` sin token `401`; se detuvo al finalizar la comprobación. Las pruebas de contrato restantes ejecutan el kernel HTTP de Laravel; no se simularon respuestas exitosas.
- `php -d extension=intl artisan db:show --counts --no-interaction`: SQLite 3.39.2, siete registros de migración y cero registros de negocio, usuarios, empresas o tokens en la base local.

Durante las primeras ejecuciones fallaron casos de fecha final vacía, cursores con precisión temporal y autenticación sin `Accept`. Fueron corregidos y la batería final se volvió a ejecutar. No se presentan esas ejecuciones iniciales como aprobadas.

## 8. Problemas de entorno resueltos y límites

- La descarga inicial de Composer no tenía acceso a red en el entorno restringido. Se reintentó mediante el mecanismo de permisos disponible y se instalaron dependencias oficiales; el lock permite repetir la instalación.
- `db:show --counts` necesitó `intl`, instalada pero deshabilitada en el PHP disponible. Se ejecutó con `php -d extension=intl` para ese diagnóstico, sin modificar `php.ini`. La API y sus pruebas no requieren esa extensión adicional.
- Se conservaron con `.gitignore` las carpetas vacías de ejecución de Laravel, manteniendo fuera de Git logs, sesiones, cachés, resultados, `.env` y la base SQLite.
- Las pruebas bloquean la ejecución si la configuración está cacheada, para no migrar accidentalmente una base local con una configuración ajena a pruebas. Ejecutar `php artisan config:clear` antes de la batería.
- No se probó MySQL/MariaDB, infraestructura TLS, servidor de producción, proxies, Android sincronizando ni hardware. Para producción habrá que seleccionar el motor y despliegue, actualizar el parche de PHP disponible y ejecutar las verificaciones en ese entorno. No se considera esta ejecución un despliegue.

## 9. Archivos relevantes y uso

Arranque e instalación: [README.md](README.md), [composer.json](composer.json), [composer.lock](composer.lock), [.env.example](.env.example), [bootstrap/app.php](bootstrap/app.php), [config/database.php](config/database.php), [config/sanctum.php](config/sanctum.php).

Rutas: [routes/api.php](routes/api.php). Controladores en `app/Http/Controllers`; validadores en `app/Http/Requests`; límites de entrada, contexto de empresa y UUID en `app/Http/Middleware`. [TripAccess.php](app/Services/TripAccess.php) centraliza el acceso a recorridos y [TripSync.php](app/Services/TripSync.php) las transacciones. `app/Support` contiene serialización, errores, fechas y cursores. `app/Domain` conserva los enums del contrato.

No se requiere Node/Vite. La raíz es un descriptor JSON y `/up` verifica el arranque. Se retiraron los archivos de frontend de bienvenida del esqueleto, sin construir un dashboard.

## 10. Pendiente para fases autorizadas posteriores

1. Suministrar datos reales de empresa y cuentas al operador cuando se quiera usar el servidor; no se generaron identidades de demostración.
2. Sincronización Android: migración Room, UUID persistentes, cola, reintentos, vinculación de propietario y adopción explícita de historial. No iniciada.
3. Dashboard y flujo de gestión de cuentas, conductores/vehículos/dispositivos cuando corresponda. No iniciados.
4. Etapa 3B y detección física, exclusivamente después de confirmar hardware y definir/validar su protocolo. SENSOR sigue deshabilitado.
5. Selección y verificación de infraestructura de producción, incluidos HTTPS y motor de base de datos.

**La fase termina en el servidor y API verificados localmente. No se continúa con Android ni dashboard sin autorización.**
