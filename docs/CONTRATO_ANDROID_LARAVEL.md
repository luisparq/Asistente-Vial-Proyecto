# Contrato propuesto Android ↔ Laravel

Fecha de auditoría original: 2026-09-28. **Contrato aprobado en sección 11, puntos 1–6, e implementado posteriormente.** Actualización de contexto: 2026-10-03.

Las secciones de evidencia conservan el estado histórico de Android al diseñar el contrato; no describen todas las funciones actuales. La implementación vigente incluye API Laravel, sincronización Room, dashboard/gestión de flota y BLE crudo validado físicamente en modo experimental con identidad del sensor no confirmada. Consultar [ESTADO_ACTUAL.md](ESTADO_ACTUAL.md), [integración](../mobile/ETAPA_INTEGRACION_LARAVEL.md) y [BLE](ETAPA_3B_BLE.md). Esta nota no modifica campos, endpoints ni decisiones aprobadas.

Este documento corresponde históricamente al paso 1 de backend. Su elaboración describió lo comprobado en Android y propuso la interoperabilidad, sin crear código ni tablas. Las implementaciones y validaciones posteriores están documentadas en las referencias anteriores; las descripciones originales se conservan como antecedente.

Sistema objetivo: `MPU6050 → ESP32 → BLE → Android → API REST/HTTPS → Laravel → base de datos → dashboard`.

Android y Laravel son componentes del mismo sistema. Android conserva el trabajo local del conductor; Laravel recibirá información y atenderá la administración de flota. Android nunca se conectará directamente a MySQL/MariaDB. Las futuras alertas inmediatas deberán resolverse localmente, sin depender de Internet. Actualmente no existe detección de maniobras implementada.

## 1. Evidencia y alcance de la revisión

El proyecto Android real está directamente en `mobile/`, no en `mobile/AsistenteVial/`. Base Git revisada: `3799e9afe1347de60ac6cd92e73d255cb6d44423`. El workspace estaba limpio antes de redactar este documento.

### 1.1 Fuentes de la implementación actual

Las rutas siguientes son relativas a este documento y constituyen la evidencia del inventario; las propuestas de secciones posteriores no deben confundirse con código existente.

- Dominio: [Trip.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/domain/model/Trip.kt), [DrivingEvent.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/domain/model/DrivingEvent.kt) y [TripRepository.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/domain/repository/TripRepository.kt).
- Persistencia: [TripEntity.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/data/local/TripEntity.kt), [DrivingEventEntity.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/data/local/DrivingEventEntity.kt), [TripDao.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/data/local/TripDao.kt), [AsistenteVialDatabase.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/data/local/AsistenteVialDatabase.kt) y [esquema Room versión 1](../mobile/app/schemas/com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase/1.json).
- Reglas y reloj: [RoomTripRepository.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/data/repository/RoomTripRepository.kt) y [StorageModule.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/core/di/StorageModule.kt).
- Flujo: [HomeViewModel.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/feature/home/HomeViewModel.kt), [TripViewModel.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/feature/trip/TripViewModel.kt), [HistoryViewModel.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/feature/history/HistoryViewModel.kt), [TripDetailViewModel.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/feature/history/TripDetailViewModel.kt), sus pantallas y [AppNavHost.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/navigation/AppNavHost.kt).
- Presentación de fechas y acceso: [TripComponents.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/core/ui/TripComponents.kt) y [LoginScreen.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/feature/login/LoginScreen.kt).
- Preparación física: [SensorDataSource.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/domain/diagnostic/SensorDataSource.kt), `PendingSensorDataSource.kt` (placeholder histórico eliminado), `PendingGattConnection.kt` (placeholder histórico eliminado) y [DiagnosticViewModel.kt](../mobile/app/src/main/java/com/tecsup/asistente/conduccion/feature/diagnostic/DiagnosticViewModel.kt).
- Pruebas existentes inspeccionadas: [RoomTripRepositoryTest.kt](../mobile/app/src/androidTest/java/com/tecsup/asistente/conduccion/RoomTripRepositoryTest.kt) y [TripFlowTest.kt](../mobile/app/src/androidTest/java/com/tecsup/asistente/conduccion/TripFlowTest.kt). Su lectura no equivale a una nueva ejecución.

### 1.2 Hechos encontrados

`Trip` tiene `id: Long`, `startedAt: Instant`, `endedAt: Instant?` y `status: TripStatus`. Los únicos estados son `ACTIVE` y `COMPLETED`.

`DrivingEvent` tiene `id: Long`, `tripId: Long`, `type: DrivingEventType`, `occurredAt: Instant` y `origin: DataOrigin`. Los tipos son `HARSH_BRAKING`, `HARSH_ACCELERATION` y `SHARP_TURN`. Los orígenes declarados son `SIMULATED` y `SENSOR`.

Room utiliza claves `Long` autogeneradas para ambas entidades. Guarda fechas como milisegundos desde Unix epoch en campos `Long`, y enums como sus nombres en campos `String`. La relación es uno a muchos: `driving_events.tripId → trips.id`, con índice y borrado en cascada. La base `asistente_vial.db` está en versión 1.

`TripEntity.activeSlot` tiene un índice único y admite `NULL`: el repositorio mantiene como máximo un recorrido activo **por base local**. Al finalizar, cambia a `COMPLETED`, escribe `endedAt` y libera ese campo. No es una restricción de un único viaje activo para toda una empresa.

`startOrResumeTrip()` usa una transacción: devuelve el activo si existe; de lo contrario crea uno. `addSimulatedEvent()` exige un viaje existente y activo, y fija siempre `SIMULATED`. `finishTrip()` solo cambia un viaje activo; repetir la finalización no cambia su fecha. No hay casos de uso para editar eventos, cambiar su viaje ni borrar recorridos.

Los ViewModels consumen Room mediante Flow/StateFlow. Home inicia o reanuda; Trip genera eventos manuales y finaliza; History y el detalle leen Room. La navegación al detalle usa el ID local `Long`. `TripSummary.eventCount` es un agregado calculado, no un campo persistido del viaje. Los eventos se ordenan por fecha y después por ID local.

Se inyecta `Clock.systemUTC()` y se usan `clock.millis()` e `Instant.ofEpochMilli()`. La interfaz presenta las fechas en la zona del dispositivo. No hay validación de `endedAt >= startedAt` ni del intervalo temporal de los eventos. El reloj del teléfono puede corregirse; usar UTC no lo convierte en un reloj exacto o monotónico. Esto es un riesgo permitido por el código, no evidencia de registros incorrectos existentes.

No hay usuario autenticado, empresa, conductor, vehículo ni dispositivo asociado a los viajes. Login permite explorar la aplicación. No hay cliente HTTP, permiso `INTERNET`, cola, identificadores globales, revisiones ni estados de sincronización.

`SENSOR` está reservado. La fuente pendiente expone estado `PENDING_HARDWARE` y un flujo vacío de bytes; no existe protocolo de mediciones ni generador de eventos físicos. La selección BLE no establece una conexión GATT real. Una dirección BLE observada no identifica de forma fiable un dispositivo de flota y no debe convertirse en identidad del ESP32.

## 2. Identificación local y remota propuesta

### 2.1 Conservar las claves locales

Mantener `Trip.id`, `DrivingEvent.id`, la FK local `tripId` y los argumentos de navegación tal como están. Sus valores solo son únicos dentro de esa base Room; enviar directamente estos números como identidad remota permitiría colisiones entre teléfonos.

Añadir en una futura migración tablas de metadatos de sincronización separadas de los modelos de dominio:

- Correspondencia de viaje: ID local → `client_trip_id` UUID v4 persistente.
- Correspondencia de evento: ID local → `client_event_id` UUID v4 persistente.
- Revisiones, propietario de sincronización y confirmaciones, según la sección 5.

Son UUID de **registros de aplicación**, no UUID BLE/GATT. No se propone ningún identificador de servicio o characteristic.

El cliente genera cada UUID una sola vez y lo guarda. El mismo UUID es el identificador público del recurso en la API; no hace falta asignar un segundo UUID remoto. Laravel podrá tener una clave interna adicional, pero Android no dependerá de ella. No se envían los IDs numéricos locales.

### 2.2 Duplicados y reintentos

- Unicidad remota de viaje: `(company_id, client_trip_id)`.
- Unicidad remota de evento: `(company_id, client_event_id)`, independiente del viaje para impedir reutilizar un evento bajo otro padre.
- Asociación remota: evento y viaje deben pertenecer a la misma empresa, respaldada por restricciones de base de datos además de autorización.
- Mismo ID y mismo contenido aceptado: confirmar el registro existente, sin insertar otro.
- Mismo ID con contenido incompatible: conflicto; nunca generar automáticamente un nuevo ID para ocultarlo.
- Nunca deduplicar por fecha, tipo o nombre: dos eventos diferentes pueden compartir esos valores.

La pérdida de una respuesta no cambia IDs ni contenido del reintento. La comprobación e inserción/actualización se harán atómicamente con transacciones y restricciones únicas. El método HTTP por sí solo no evita carreras en la base.

Se propone un solo escritor Android por viaje. La primera versión no soportará edición concurrente desde otro teléfono o desde el dashboard. Reinstalar y perder Room también pierde sus correspondencias: no se puede reconstruir identidad por aproximación. Una futura restauración/exportación deberá conservar estos metadatos; clonar una base para escribir desde dos dispositivos queda fuera de este contrato.

## 3. Campos del contrato

### 3.1 Trip

Campos actuales que viajarán:

- `startedAt` → `started_at`: fecha UTC obligatoria; inmutable después de la primera aceptación.
- `endedAt` → `ended_at`: fecha UTC o `null` explícito.
- `status` → `status`: exactamente `ACTIVE` o `COMPLETED`.
- `id`: se representa remotamente mediante su correspondencia `client_trip_id`, en la ruta y en las respuestas.

Campos exclusivamente locales: PK numérica `id` y `activeSlot`. El ID local no se sustituye con una respuesta remota. `TripSummary.eventCount` y `TripWithEvents` son proyecciones: se consultan o calculan, no se envían como hechos independientes.

Metadato nuevo de transporte: `client_revision`. Para este contrato v1, revisión `1` representa `ACTIVE` con `ended_at = null`, y revisión `2` representa `COMPLETED` con `ended_at` obligatorio. No es un tercer estado de recorrido. Las únicas transiciones de negocio siguen siendo las existentes. Si aparecen futuras correcciones o cancelaciones, habrá que ampliar expresamente el contrato.

Laravel añadirá metadatos propios necesarios para seguridad y trazabilidad: empresa propietaria, usuario que envía el registro, revisión aceptada y tiempos de recepción. No existen actualmente en `Trip`; no deben confundirse con conductor, fecha de conducción o datos de sensores.

No añadir origen al viaje: el origen ya pertenece a cada evento. Un viaje sin eventos no se clasificará arbitrariamente como simulado o físico.

### 3.2 DrivingEvent

Campos actuales que viajarán:

- `id`: correspondencia `client_event_id` en la ruta y respuesta.
- `tripId`: correspondencia `client_trip_id` del padre en la ruta y respuesta; nunca el número local.
- `type` → `type`: `HARSH_BRAKING`, `HARSH_ACCELERATION` o `SHARP_TURN`.
- `occurredAt` → `occurred_at`: fecha UTC obligatoria.
- `origin` → `origin`: conservar el significado explícito del enum.

Los eventos serán inmutables en esta primera versión. No se permite cambiar tipo, fecha, origen o padre después de aceptarlos. No necesitan revisión mutable propia; ID y contenido inmutable bastan para detectar repetición o conflicto.

`SIMULATED` significa **dato de prueba generado manualmente**. No demuestra una maniobra física ni una infracción legal. La primera integración solo admitirá altas con ese origen.

`SENSOR` permanecerá en el vocabulario reservado del contrato, pero las altas estarán deshabilitadas inicialmente con `422 origin_not_enabled`. Se habilitará expresamente cuando exista la integración y el productor de eventos correspondiente. Recibir bytes del MPU6050 no equivale por sí mismo a detectar una maniobra; esa futura transformación necesitará diseño y validación propios. El origen `SENSOR` tampoco certificará validez experimental o legal.

No se agregan velocidad, coordenadas, aceleración, giroscopio, severidad, puntuación o confianza: no están presentes en los modelos revisados.

### 3.3 Validación y compatibilidad

Los nombres de enums son sensibles a mayúsculas y deben conservarse. Android actual usa `valueOf`, que no tolera valores desconocidos. La API v1 no introducirá unilateralmente nuevos estados/tipos; un futuro lector remoto deberá tratar valores desconocidos sin insertarlos directamente en Room.

Se rechazan campos de escritura no admitidos, enums desconocidos, UUID mal formados, fechas inválidas y combinaciones estado/fecha incoherentes. No se aceptarán `company_id`, `user_id`, totales o asignaciones arbitrarias enviados por el cliente en estos cuerpos.

## 4. Fechas y reloj

Formato de intercambio propuesto: RFC 3339, perfil de ISO 8601, UTC con exactamente tres decimales y sufijo `Z`: `YYYY-MM-DDTHH:mm:ss.SSSZ`. Es una descripción de formato, no un resultado de prueba. La API serializará siempre esa forma canónica. Véase [RFC 3339, sección 5.6](https://www.rfc-editor.org/rfc/rfc3339.html#section-5.6).

Android conservará `Instant` en dominio y epoch **milisegundos** en Room. La conversión a texto sucederá en los DTO de red; no se enviarán las cadenas de fecha ya localizadas para pantalla. No se cambia el almacenamiento local a texto ni a segundos.

Laravel se configurará en UTC. Si se elige MySQL/MariaDB, se propone `DATETIME(3)` interpretado siempre como UTC, con conexión y serialización coherentes. La representación SQL no necesita ser texto ISO; debe preservar el mismo instante y precisión de milisegundos. Se validará el rango admitido por la base elegida y se evitarán serializadores que eliminen fracciones. La zona horaria solo se aplicará al mostrar los datos.

La fecha del teléfono es la fecha declarada por el origen. `first_received_at` y `last_changed_at`, generados por el servidor, sirven para trazabilidad de recepción; nunca reemplazan `occurred_at`, `started_at` o `ended_at`. Un reintento idéntico no cambia la primera recepción ni la última modificación efectiva.

**Política propuesta ante cambios del reloj:** conservar fechas sintácticamente válidas y representables aunque el final sea anterior al inicio o un evento quede fuera del intervalo. Calcular y mostrar indicadores de inconsistencia temporal, sin corregir silenciosamente ni inventar tiempos. Recalcular esos indicadores cuando llegue la finalización o un evento tardío. No mostrar duración negativa; marcarla como no calculable. Las fechas mal formadas o fuera del rango de almacenamiento sí se rechazan con `422`.

Esta política evita bloquear todos los datos de un recorrido por una corrección del reloj del teléfono. Necesita aprobación. No relaja las reglas estructurales: `ACTIVE` exige final nulo y `COMPLETED` exige final presente. Tampoco certifica que el reloj del teléfono sea correcto cuando no se detecta inconsistencia.

## 5. Sincronización propuesta

Flujo: `Room → metadatos + cola persistente → trabajador de sincronización → HTTPS/API → transacción remota → confirmación local`.

### 5.1 Operación sin Internet

Room seguirá siendo la fuente de datos de las pantallas existentes. Iniciar, generar eventos de prueba, finalizar y consultar historial no esperarán una petición HTTP. Un error remoto no deshará el viaje local. Las futuras alertas locales tampoco consultarán Laravel para decidir si deben emitirse.

La red pertenecerá a `data`, con contratos de repositorio y estados observables mediante Flow; Hilt suministrará las dependencias. Los ViewModels no harán peticiones directas ni convertirán `TripStatus` en un estado de red. Se propone WorkManager para trabajo persistente con conectividad requerida y recuperación tras reinicio, sujeto a configuración futura. Es la herramienta de Android para trabajo persistente; no garantiza ejecución inmediata. [Documentación oficial](https://developer.android.com/develop/background-work/background-tasks/persistent).

### 5.2 Escrituras y cola durables

En una futura migración aditiva de Room v1 a v2 se incorporarán correspondencias y cola, sin borrar viajes, cambiar sus claves ni usar migración destructiva. El número de versión deberá ajustarse si existe otra migración antes de implementar este paso.

Cada nueva mutación de negocio y su operación de cola se escribirán en la **misma transacción Room**:

1. Crear viaje: guardar viaje, UUID, propietario si está disponible, revisión 1 y operación de envío.
2. Crear evento: guardar evento, UUID, asociación al padre y operación inmutable.
3. Finalizar: guardar estado y final, revisión 2 y operación de envío. Repetir la finalización no genera una nueva revisión.

La cola necesita referencia a entidad y revisión, contenido congelado a enviar, dependencia del padre, propietario, estado, número de intentos, próxima oportunidad de ejecución y último error. Una marca de ocupación con vencimiento permite recuperar operaciones interrumpidas. Son metadatos técnicos propuestos; no campos de conducción. La identidad de operación puede derivarse de recurso y revisión, sin un segundo mecanismo obligatorio de idempotencia.

Estados propuestos de la cola: `PENDING`, `IN_FLIGHT`, `RETRY_WAIT`, `ACKED`, `BLOCKED_AUTH`, `BLOCKED_VALIDATION` y `BLOCKED_CONFLICT`. La falta de propietario se registra aparte como `UNASSIGNED` y bloquea la transmisión. Ninguno modifica `ACTIVE`/`COMPLETED`.

Un trabajador lógico procesará la cola. La programación se repetirá al iniciar sesión, recuperar conectividad, abrir la aplicación o solicitar sincronización manual. Una reconciliación al arranque cubrirá la interrupción entre confirmar Room y programar el trabajo. Las operaciones `IN_FLIGHT` cuyo plazo haya vencido vuelven a ser elegibles. Solo se marca `ACKED` al recibir y validar una confirmación de persistencia para el recurso y revisión correspondientes.

### 5.3 Orden y finalización

El viaje debe estar confirmado remotamente antes de enviar sus eventos. El orden entre eventos no altera su identidad; su presentación usa fechas y un criterio estable de desempate. El orden del ID local no se usará como orden global remoto; la API desempatará por `client_event_id`.

Se permiten viajes activos en el servidor para sincronización incremental. También se permite crear directamente un viaje `COMPLETED`, revisión 2, cuando se importe historial o todo el recorrido haya ocurrido sin Internet. La cola normal conserva sus operaciones; una importación de historial puede necesitar únicamente su estado final.

El servidor aceptará eventos tardíos de un viaje ya finalizado. Android exige que se creen mientras el viaje está activo, pero su llegada a la API puede ser posterior. La hora de recepción no debe usarse para rechazar esos eventos.

**Viaje finalizado no significa sincronización completa.** En Android se podrá indicar sincronizado cuando la revisión actual y todos sus eventos locales estén confirmados. El servidor no conoce cuántos eventos siguen pendientes en un teléfono; mostrará “eventos recibidos”, sin afirmar completitud. No se introduce un total declarado o sello de cierre de sincronización que hoy no existe.

### 5.4 Revisiones, respuestas perdidas y conflictos

- Revisión 1 seguida de revisión 2: actualizar a finalizado.
- Revisión 2 recibida antes de revisión 1: conservar finalizado; la revisión antigua no reabre el viaje.
- Misma revisión y mismos campos: confirmar sin mutar.
- Misma revisión con campos diferentes, cambio del inicio o final incompatible: `409` y revisión del conflicto.
- Revisión 1 tardía compatible con el inicio ya almacenado: responder `stale_ignored`, indicando la revisión actual 2.

Una respuesta identifica la revisión solicitada y la revisión actual. Confirmar una operación antigua nunca confirma una revisión local más nueva ni sobrescribe el dominio local. Si una restauración u otro escritor provoca divergencia de estado, se bloquea para conciliación; no se reabre el viaje remoto ni se fabrican IDs nuevos.

Timeouts, desconexiones, `408`, `429` y errores transitorios `5xx` permiten reintento del mismo contenido con espera exponencial, variación aleatoria y límite de frecuencia. Se respetará `Retry-After` cuando corresponda. No se borran operaciones por agotar un número de intentos.

`401` bloquea hasta recuperar autenticación del mismo propietario; `403` requiere resolver permisos. `409` requiere conciliación. `422` requiere corregir compatibilidad/validación, sin alterar silenciosamente datos originales. Un padre inexistente detiene sus eventos y exige verificar/reponer primero el viaje autorizado; no se reintenta indefinidamente el mismo evento aislado. Los errores permanentes de un recurso no deben bloquear viajes independientes.

### 5.5 Propiedad de datos históricos y cambios de cuenta

Los registros actuales no tienen propietario. La migración puede asignarles UUID, pero **no puede atribuirlos automáticamente a quien inicie sesión después**. Quedarán locales y sin asignar hasta una adopción explícita y autorizada. Se propone adoptar solo recorridos ya finalizados; un recorrido activo sin propietario se podrá terminar localmente antes de adoptarlo.

Los nuevos recorridos podrán capturar la empresa y usuario previamente autenticados y autorizados, incluso trabajando sin Internet, conservando esa vinculación. Sin contexto de identidad conocido seguirán siendo locales sin asignar. La API verificará nuevamente la autorización al recibirlos.

El propietario queda fijado al asignar el viaje; los eventos lo heredan. No se cambia la empresa de un recorrido activo. Una sesión de otra cuenta no transmite ni reasigna operaciones de la anterior. Cerrar sesión pausa sus envíos, sin borrar los datos pendientes. Si se revoca acceso, el trabajo local se conserva y el servidor deniega el envío; una futura política de recuperación deberá resolverlo explícitamente.

La primera versión sincroniza de Android hacia Laravel. No descargará recorridos remotos como viajes activos a Room: eso introduciría conflictos con su restricción local y requeriría una estrategia adicional de varios dispositivos.

## 6. API REST preliminar — propuesta, no disponible

Prefijo lógico `/api/v1`. No se define dominio, URL de despliegue, credencial ni endpoint ya existente. JSON UTF-8, HTTPS y autenticación obligatoria en todos los recursos. Versionar el contrato no obliga a seleccionar ahora una versión de Laravel.

### 6.1 Escritura idempotente

**`PUT /api/v1/trips/{client_trip_id}`**

Cuerpo completo obligatorio: `started_at`, `ended_at`, `status`, `client_revision`, con las reglas de secciones 3 y 5. El identificador está en la ruta. No acepta arrays de eventos ni propietario suministrado por el cliente.

Respuesta: `201` al crear; `200` al actualizar, repetir o ignorar una revisión antigua compatible. `data` contiene `client_trip_id`, los tres campos de negocio, `client_revision` vigente, `first_received_at` y `last_changed_at`. `sync` contiene `disposition` (`created`, `updated`, `already_applied` o `stale_ignored`), `acknowledged_revision` de la petición y `current_revision` remota. En `stale_ignored`, la confirmación significa que la operación antigua no necesita repetirse, no que su contenido sustituyó al vigente.

**`PUT /api/v1/trips/{client_trip_id}/events/{client_event_id}`**

Cuerpo completo obligatorio: `type`, `occurred_at`, `origin`. Padre existente y autorizado. Respuesta `201` al crear, `200` al repetir contenido idéntico; `data` incluye ambos identificadores, los campos del evento y `first_received_at`. `sync.disposition` es `created` o `already_applied`. Un contenido diferente o reutilización bajo otro padre autorizado produce `409`.

Se propone PUT porque su semántica es idempotente, respaldada aquí por IDs estables y reglas transaccionales. [RFC 9110, sección 9.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.2). Una respuesta `202` no contará como confirmación de persistencia. La API confirmará solo después del commit.

### 6.2 Consultas

- `GET /api/v1/me`: contexto autenticado de usuario, empresa activa y capacidades para vincular operaciones. No crea usuarios o empresas. El flujo de alta/inicio de sesión se diseñará aparte antes de implementar autenticación.
- `GET /api/v1/trips`: lista paginada de viajes autorizados; filtros opcionales `status`, `started_from` inclusivo y `started_before` exclusivo, con fechas UTC. Orden `started_at` descendente y `client_trip_id` como desempate.
- `GET /api/v1/trips/{client_trip_id}`: estado recibido, fechas, revisión, tiempos de recepción, conteo de eventos recibidos y advertencias temporales derivadas.
- `GET /api/v1/trips/{client_trip_id}/events`: eventos autorizados paginados; filtros opcionales `type`, `origin`, `occurred_from` inclusivo y `occurred_before` exclusivo. Orden `occurred_at` ascendente y `client_event_id` como desempate.

Las listas usarán `cursor` opaco, `limit` con máximo propuesto de 100 y respuesta `data` más `next_cursor` nulo al terminar. El cursor se vinculará a filtros y ámbito autorizado. La paginación no es una foto inmutable: una carga concurrente puede exigir refrescar la consulta. No se promete seguimiento en tiempo real.

No se proponen DELETE, reasignación de eventos ni edición de hechos desde el dashboard. El borrado en cascada local existente no implica una API de borrado remoto. Sin tal función, no hacen falta tombstones en este contrato inicial.

### 6.3 Errores

Formato propuesto: objeto `error` con `code` estable, `message` legible y `fields` cuando haya errores de validación. No incluir SQL, tokens ni detalles de otra empresa. El cliente decide recuperación por código y estado HTTP, nunca analizando el texto traducido.

- `400`: JSON o petición ilegible.
- `401`: autenticación ausente, expirada o inválida.
- `403`: falta de capacidad para la operación dentro del ámbito autorizado.
- `404`: recurso inexistente o fuera del ámbito visible; no revelar su existencia en otra empresa.
- `409`: conflicto de identidad, contenido o revisión.
- `422`: campos, fechas, enums o combinación de estado inválidos; incluye `origin_not_enabled`.
- `429`: límite de frecuencia; respetar `Retry-After`.
- `5xx`: fallo del servidor; reintentar con las mismas claves y contenido cuando sea transitorio.

Todas las escrituras validarán permisos y pertenencia antes de resolver repetición o conflicto. Saber un UUID no concede acceso. Un conflicto solo puede devolver información de un recurso que el solicitante tenga permiso para consultar.

## 7. Entidades futuras y aislamiento entre empresas

### 7.1 Mínimo necesario antes de aceptar datos remotos

Se necesitan identidad autenticada y un ámbito de empresa: se propone introducir `Company` y `User` al implementar la seguridad del backend, no ahora. El servidor derivará `company_id` del contexto autenticado y validará pertenencia; no confiará en campos o cabeceras libres del cliente.

Para la primera versión se propone una empresa activa por contexto/token. Una futura relación de membresías podrá permitir usuarios en varias empresas sin cambiar Trip o DrivingEvent, pero cada petición y operación pendiente conservará un único ámbito. Un UUID compartido accidentalmente entre empresas no mezcla sus registros.

`created_by_user_id` remoto identifica a quien origina/sube el registro autorizado; **no demuestra que sea el conductor físico**. La adopción de registros locales sin identidad exige una acción explícita; no reconstruye retrospectivamente quién conducía.

### 7.2 Entidades que deben esperar al flujo correspondiente

- `Driver`: perfil de conductor, separable de la cuenta `User`. No inferirlo del login actual. Su incorporación permitirá una asociación opcional y validada al viaje.
- `Vehicle`: vehículo real registrado/asignado. No deducirlo de un teléfono, nombre BLE ni recorrido.
- `Device`: dispositivo físico de adquisición, con identidad y asignación definidas cuando exista el hardware. No usar la dirección de escaneo BLE como identificador contractual persistente.

Las futuras asociaciones del viaje con conductor, vehículo o dispositivo deberán ser opcionales para el historial anterior y pertenecer a la misma empresa. No rellenar registros históricos con entidades ficticias. Las asignaciones deben conservar el contexto del viaje; cambiar posteriormente el vehículo de un dispositivo no debe reescribir viajes pasados. Si un viaje necesitara varios dispositivos, habrá que diseñar esa relación antes de asumir una FK única.

Estas entidades no justifican añadir ahora columnas vacías para completar el dashboard. Su contrato y ciclo de vida se aprobarán al implementar sus funciones.

### 7.3 Seguridad propuesta

- Propuesta de autenticación: tokens Bearer para Android mediante Laravel Sanctum y sesión con protección CSRF para dashboard. Sanctum documenta autenticación mediante tokens y sesiones; su elección y configuración siguen pendientes, sin seleccionar aquí versión ni credenciales. [Documentación oficial](https://laravel.com/framework/docs/13.x/sanctum).
- Políticas por recurso y capacidades: un usuario móvil podrá escribir/consultar sus viajes autorizados; la administración de flota podrá consultar los de su empresa. Ver todos los viajes de una empresa requiere capacidad explícita. Un token o rol por sí solo no sustituye la comprobación de pertenencia de cada objeto.
- Filtrar por empresa y autorización en consultas, bindings de rutas, agregados, exportaciones, trabajos en segundo plano y cachés. Aplicar restricciones compuestas para impedir relaciones entre empresas. No obtener un objeto global y filtrar únicamente su representación al final.
- Crear/asignar la empresa y membresías mediante un flujo administrativo autorizado; nunca aceptar que un cliente móvil elija libremente una empresa para elevar privilegios.
- Tokens con expiración/revocación y almacenamiento protegido en Android; no guardarlos en entidades de viajes, logs o repositorio. No desactivar validación TLS ni compartir credenciales de base de datos con Android.
- Al cambiar de cuenta, proteger también la visibilidad de datos locales asociados; los registros actuales sin propietario requieren una política explícita de adopción. Este aislamiento local no existe todavía y forma parte del trabajo futuro de autenticación.
- Validación estricta, límites de tamaño/frecuencia y errores sin filtraciones. La idempotencia no permite eludir permisos ni modificar datos ajenos.

## 8. Dashboard inicial honesto con los datos disponibles

Actualmente no existe base remota ni hay datos sincronizados. Tras implementar este contrato, el dashboard podrá mostrar **solo los registros efectivamente recibidos y autorizados**, aunque se hayan originado como pruebas:

- Lista y detalle de recorridos, inicio, final y estado declarado por Android.
- Duración entre fechas registradas si es coherente, etiquetada como duración del recorrido registrado; no prueba tiempo efectivo de conducción.
- Eventos recibidos, tipo preliminar, fecha y origen, con `SIMULATED — dato de prueba` visible.
- Conteos derivados por tipo, origen y período, sujetos a recepción parcial y a la calidad del reloj.
- Fechas de recepción y advertencias temporales derivadas, para distinguir antigüedad del dato y del envío.

Un `ACTIVE` remoto puede estar desactualizado por falta de conexión o porque el usuario no finalizó localmente. No equivale a “conduciendo ahora”. El servidor no conoce el tamaño de la cola offline, ni puede afirmar que recibió todos los eventos. Sin registros, se mostrará estado vacío; no se generarán ejemplos como si fueran datos reales.

Deben esperar: nombres/asignaciones de conductores y vehículos, inventario/estado físico de dispositivos, mapas, distancia, velocidad, ejes del MPU6050, alertas de sensores, detecciones validadas y cualquier análisis que necesite esas mediciones. Tampoco se justifican rankings de riesgo, multas, infracciones comprobadas, métricas experimentales o calidad de conducción a partir de eventos manuales.

No se necesitan campos nuevos de negocio para mostrar el historial básico. La empresa y el usuario aportan seguridad; no se añaden solo para decorar gráficos.

## 9. Adaptaciones necesarias e incompatibilidades encontradas

No se detecta necesidad de romper los modelos de dominio ni el flujo local. Sí faltan capacidades para interoperar:

1. Los IDs `Long` no son globales: requieren correspondencias persistentes, no sustitución de PK.
2. No hay propietario ni autenticación: impide enviar historial a una empresa sin una decisión explícita.
3. No hay cola, revisiones o confirmaciones: requiere migración Room aditiva y transacciones ampliadas.
4. Room usa epoch ms y la API propuesta texto UTC: exige un adaptador y pruebas de precisión, no cambiar las fechas de dominio.
5. El repositorio no valida orden temporal: un backend que rechazara toda anomalía cronológica podría bloquear registros hoy admisibles. Se propone conservarlos y señalarlos.
6. Los enums se convierten estrictamente: ampliar valores remotos sin coordinación rompería un futuro lector Android.
7. Solo existe un activo local: no convertir esa regla en restricción global de empresa ni importar activos remotos sin diseñar conciliación.
8. Faltan cliente HTTP, permiso de Internet, trabajador y manejo seguro de sesión: se incorporarán en una fase autorizada, manteniendo Hilt, Flow y separación de capas.
9. `SENSOR` existe como nombre, pero no como productor funcional: no habilitar eventos físicos por el mero hecho de aceptar el enum.

## 10. Verificación futura y comprobaciones de este paso

### 10.1 Criterios de aceptación para cuando se implemente

Estos son casos de prueba propuestos, **no resultados ejecutados**:

- Migrar una base v1 con viajes y eventos sin pérdida; conservar claves, relación y único activo local.
- Iniciar, generar eventos, finalizar y consultar Room sin Internet; cerrar/reabrir y recuperar cola y datos.
- Interrumpir entre commit local y programación, y durante una petición; recuperar trabajo sin duplicados.
- Repetir solicitudes tras perder la respuesta y ejecutar altas simultáneas del mismo ID: un único registro remoto.
- Reutilizar un ID con otro contenido o padre: conflicto sin modificar el original.
- Recibir revisión 2 antes de 1: no reabrir; confirmar una revisión antigua sin confirmar otra pendiente.
- Esperar al padre y aceptar eventos tardíos tras su finalización.
- Conservar UTC y milisegundos al convertir Room/API/SQL; representar correctamente cambios de zona y anomalías del reloj.
- Bloquear `SENSOR` inicialmente y valores desconocidos; mantener visible el origen de prueba.
- Probar aislamiento de empresas y de usuarios en cada escritura, lectura, agregado y trabajo; no filtrar existencia de recursos ajenos. Los fixtures de prueba se declararán como tales, no como entidades reales.
- Cerrar sesión/cambiar cuenta durante un envío: no reasignar cola ni exponer datos locales ajenos. Recuperar autorización sin perder operaciones.
- Mostrar conteos recibidos sin afirmar completitud ni convertir `ACTIVE` en presencia en tiempo real.
- Ejecutar compilación, pruebas de regresión de viajes/BLE y lint cuando se modifique Android; pruebas de contrato e integración cuando exista Laravel.

### 10.2 Trabajo realizado en este paso documental

Se inspeccionaron fuentes, esquema Room, flujo, preparación SENSOR/BLE y pruebas existentes, y se redactó este contrato. No se consultó una base de datos de usuario para extraer registros; no se afirma que exista un número concreto de viajes o eventos.

No se ejecutaron compilación, pruebas Android, lint, peticiones API ni pruebas físicas en este paso, porque solo se añadió documentación. Las verificaciones previas no se presentan como una nueva ejecución. La comprobación final de este cambio se limita a rutas de referencia, formato y alcance de archivos modificados.

## 11. Decisiones que requieren aprobación antes de implementar

1. Conservar PK locales y usar UUID persistentes por registro como identidad pública, con metadatos de sincronización separados.
2. Sincronización incremental unidireccional, PUT idempotente, revisiones 1/2 del viaje, eventos inmutables y aceptación de llegadas tardías. Sin edición remota ni descarga de activos en la primera versión.
3. UTC con precisión de milisegundos y conservación de anomalías cronológicas señaladas, frente a rechazarlas y bloquear su sincronización.
4. Empresa y usuario autenticado como mínimo previo a recibir datos; contexto de una empresa por token, capacidades de acceso y Sanctum como opción propuesta. La creación de cuentas, membresías y administración requiere un diseño de autenticación posterior.
5. Adopción explícita de historial local finalizado sin propietario; nunca atribuirlo automáticamente al próximo usuario que entre.
6. Dashboard inicial de recorridos/eventos recibidos, con pruebas claramente identificadas; posponer asignaciones de conductor/vehículo/dispositivo y mediciones hasta sus respectivas fases.

Orden recomendado tras aprobar: cerrar contrato y reglas de autenticación/propiedad; implementar seguridad y persistencia/API Laravel con pruebas de aislamiento e idempotencia; añadir migración y sincronización Android con regresión de Etapas 1–3A; construir el dashboard básico sobre la misma API y datos recibidos. La integración física 3B puede avanzar por separado cuando se confirme disponibilidad del hardware, sin alterar el significado de los eventos ya almacenados.

**Este paso termina en la propuesta. No autoriza ni inicia la implementación de Laravel, dashboard o cambios Android.**
