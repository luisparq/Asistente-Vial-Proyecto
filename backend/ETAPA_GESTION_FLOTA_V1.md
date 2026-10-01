# Gestión de flota V1

## Auditoría y decisiones previas a migración

Baseline ejecutado: **46 pruebas, 354 aserciones, todas aprobadas**. Se revisaron migraciones, modelos, relaciones compuestas, middleware, guard web, Sanctum Bearer-only, controladores, servicios, tests, documentación, contrato y modelos/autenticación/Room Android. Se conserva la arquitectura existente.

### Modelo propuesto

- `users`: sigue siendo la única cuenta de autenticación. `member` conserva capacidades y datos legacy; nuevo `driver` identifica cuentas con perfil operativo. `fleet_admin` permanece. `platform_admin` tiene guard web y área global separada, sin token Android.
- Por compatibilidad con la FK NOT NULL existente, la cuenta platform_admin mantiene una empresa administrativa real elegida explícitamente al crearla. Esa FK NO limita ni concede sus permisos globales: los determina el rol en `/platform`. No se crea una empresa ficticia ni se cambia la estructura de las claves existentes.
- Estado de acceso de usuario y estado de empresa, ambos activos por defecto para conservar usuarios y tokens actuales.
- Nuevas tablas: `drivers`, `vehicles`, `vehicle_assignments`, `emergency_contacts`, `document_types`, `driver_documents`, `access_invitations`, `administrative_audits`.
- Un Driver pertenece a una empresa y a un User de la misma empresa; nombre y estado operativo mínimos. Contacto de emergencia opcional, sin DNI, dirección, nacimiento u otros datos adicionales.
- Asignaciones con inicio/final e historial; máximo una activa por conductor y por vehículo, con restricciones únicas y transacciones. No se reasigna el vehículo de otro conductor silenciosamente.
- Documentos privados PDF/JPEG/PNG, máximo 5 MiB. Licencia/brevete es un único tipo de catálogo. Revisión PENDING/VERIFIED/REJECTED; vencimiento es una condición derivada, no una validación legal.
- Activación/restablecimiento sin correo: código aleatorio de un solo uso, guardado como hash y con vencimiento. El administrador lo entrega por un canal privado. Se introduce en `/access`, nunca en una URL ni en logs/auditoría. El destinatario elige su contraseña. No se finge envío de correo.
- `trips.driver_id` y `trips.vehicle_id` serán nullable, sin backfill y sin cambiar PUT/JSON Android. No se infiere el vehículo al recibir un viaje offline. La vinculación explícita de nuevos recorridos requiere una futura ampliación de contrato; mientras tanto se distingue autor de cuenta de contexto operativo.
- Auditoría registra actor, empresa, acción, entidad, ID, campos cambiados/estados y fecha; excluye valores personales, archivos, contraseñas, hashes y códigos.
- Android agregará consulta propia de perfil; no cacheará documentos/contactos persistentemente en esta V1 para minimizar exposición. Room y su cola no cambian.

### Preservación

Antes de migrar: `scripts/fleet-legacy-check.php before` crea copia SQLite consistente privada y hashes por registro/columnas originales de empresas, usuarios, viajes, eventos y tokens. Después: `after` compara los mismos registros y claves, además de `foreign_key_check`. No se ejecutan seeders de personas/vehículos ni resets de base. Solo se añade un tipo técnico de documento al catálogo.

## IMPLEMENTADO

Ampliación sobre Laravel **12.69.2**, PHP **8.2.12**, Sanctum **4.3.3** y SQLite. No se reconstruyó el proyecto ni se cambiaron los PUT de sincronización, revisiones, UUID, inmutabilidad de eventos ni el bloqueo de escritura SENSOR. No se incorporaron dependencias PHP ni Android.

### Migración ejecutada

`database/migrations/2026_09_30_150000_add_fleet_management.php`, ejecutada el 30/09/2026 como batch 2 con `php artisan migrate --force`.

| Tabla | Contenido / relación |
|---|---|
| drivers | company_id + user_id de la misma empresa, nombre completo, ACTIVE/INACTIVE; una cuenta por perfil |
| vehicles | empresa, placa normalizada a mayúsculas y única por empresa, marca, modelo, ACTIVE/INACTIVE |
| vehicle_assignments | empresa, conductor, vehículo, started_at/ended_at UTC, active_slot; historial preservado |
| emergency_contacts | un contacto opcional por conductor: nombre, relación y teléfono |
| document_types | catálogo técnico; único tipo inicial DRIVING_LICENSE, licencia/brevete |
| driver_documents | empresa/conductor/tipo, referencia y fechas opcionales, revisión, ruta privada/MIME/tamaño |
| access_invitations | usuario/empresa, hash SHA-256 de código aleatorio de 256 bits, vencimiento y consumo |
| administrative_audits | actor, empresa, acción, entidad/ID, nombres de campos cambiados y fecha UTC |

Columnas aditivas: `companies.status=ACTIVE`, `users.access_status=ACTIVE`, `trips.driver_id=NULL`, `trips.vehicle_id=NULL` para registros actuales. Se mantienen claves originales; nuevas relaciones operativas usan claves compuestas con company_id. Unicidad de asignación actual: `(driver_id, active_slot)` y `(vehicle_id, active_slot)`, donde 1 representa actual y NULL una asignación cerrada. Transacciones serializadas y restricciones de base impiden dobles asignaciones. Inactivar un conductor termina su asignación y deshabilita acceso; inactivar un vehículo asignado se rechaza hasta finalizar la asignación.

No se ofrecen eliminaciones de empresas, cuentas, conductores, vehículos ni historial. La edición del contacto permite retirarlo vaciando explícitamente sus tres campos. Documentos se agregan como registros privados y se revisan; no se sobrescriben archivos anteriores. `down()` rechaza rollback automático: revertir esta ampliación requiere un plan explícito de conservación, no borrar tablas con información personal.

### Roles y autorización

| Rol | Acceso |
|---|---|
| platform_admin | `/platform`: consulta global, empresas/estados, administradores de flota, gestión de acceso de cuentas no-platform, reportes y auditoría. Sin token Android ni acceso al dashboard fleet |
| fleet_admin | `/dashboard`: exclusivamente su empresa; perfiles, vehículos, documentos, contactos, asignaciones y Dashboard V1. Mantiene capacidades API anteriores |
| driver | API de viajes propia y perfil propio; sin acceso web administrativo |
| member | Conserva capacidades y comportamiento legacy; perfil propio solo si se le asocia explícitamente uno. No se convirtió automáticamente a Luis TEST |

Estados de cuenta: PENDING (requiere activación), ACTIVE y DISABLED. Empresa INACTIVE bloquea login de flota y peticiones API con tokens existentes. Cambiar estado no elimina datos ni tokens. La cuenta global sigue autorizada si su empresa de afiliación está inactiva, porque su autoridad es de plataforma; no puede usar esa excepción para obtener tokens Android.

No se aceptan company_id/role enviados por el formulario de conductor. El servidor fija ambos. Los recursos de otras empresas responden 404; roles no permitidos reciben 403. Los administradores globales no pueden promover cuentas a platform_admin ni alterar cuentas platform por la web; el alta global es un comando explícito del operador.

### Páginas y rutas

Fleet conserva todas las páginas V1 y añade:

- `GET /dashboard/drivers`, `/drivers/create`, `/drivers/{id}`, `/drivers/{id}/edit`.
- `POST /dashboard/drivers`, `PUT /dashboard/drivers/{id}`.
- `POST /dashboard/drivers/{id}/access`, `/assignment`, `/documents`.
- `GET /dashboard/documents/{id}` (descarga autorizada), `POST /dashboard/documents/{id}/review`.
- `GET /dashboard/vehicles`, `/vehicles/create`, `/vehicles/{id}`, `/vehicles/{id}/edit`.
- `POST /dashboard/vehicles`, `PUT /dashboard/vehicles/{id}`.

Platform:

- `GET/POST /platform/login`, `POST /platform/logout`.
- `GET /platform` y `/platform/companies`; `POST /platform/companies`.
- `GET /platform/companies/{id}`, `POST /platform/companies/{id}/state`.
- `GET/POST /platform/users` (el POST crea exclusivamente fleet_admin).
- `POST /platform/users/{id}/access` (invitar, deshabilitar o habilitar para nueva activación).
- `GET /platform/reports`: conteos actuales de conductores/vehículos, viajes/eventos por período local Lima, agrupaciones por tipo/origen; sin puntuaciones ni inferencias de riesgo.
- `GET /platform/audits`: consulta sin edición de auditoría administrativa.

`GET/POST /access` es el formulario público de activación/restablecimiento con CSRF y límite de intentos. Un código de 24 horas sustituye al correo mientras no exista infraestructura de correo. Se muestra una vez al administrador en sesión cifrada; no va en URL, auditoría ni logs explícitos. Su destinatario elige contraseña de al menos 12 caracteres. El restablecimiento revoca tokens del usuario y las sesiones anteriores son rechazadas por `auth.session` al cambiar el hash. Deshabilitar cuenta invalida invitaciones pendientes; habilitar exige nueva activación. No hay contraseñas consultables ni envío de correo simulado.

### Perfil API adicional

`GET /api/v1/me/driver`, Sanctum Bearer y contexto de empresa; no recibe IDs para elegir otro conductor. `/api/v1/me` no cambia su JSON ni las capacidades legacy.

Respuesta 200: envoltorio `data` con `name`, `email`, `status`, `access_status`, `emergency_contact` (name/relationship/phone o null), `documents` (id/type/type_name/review_status/expires_on/expired) y `vehicle` (plate/brand/model/status o null). No expone rutas de archivos, credenciales, otros usuarios ni referencias documentales innecesarias. Fechas documentales son calendario `YYYY-MM-DD`, no instantes. Expiración calculada según fecha de Lima. Cabecera `private, no-store`.

Errores: 401 sin token válido; 403 por estado/rol/permisos; 404 si no existe perfil, sin crear uno implícitamente. Se mantiene el formato de errores API existente. Android distingue esas respuestas de falta de conexión o error de servidor.

### Documentos, privacidad y auditoría

Disco `driver_documents`, raíz `storage/app/private/driver-documents`, sin URL pública y sin `serve`. Archivos PDF/JPEG/PNG de hasta 5120 KiB; validación de extensión, MIME real, tamaño y fechas. Nombre físico aleatorio, separación por empresa. Descarga como adjunto con nombre generado, `nosniff`, CSP sandbox y no-store. No ejecutar `storage:link` para estos documentos. PHP local admite 40M para upload/post; la aplicación restringe a 5 MiB. En otro servidor, el límite PHP/proxy debe permitir ese tamaño más el formulario.

PENDING/VERIFIED/REJECTED significa **revisión administrativa**. Un archivo VERIFIED puede estar vencido; vencimiento se muestra por separado y no implica validación legal. Licencia y brevete son un mismo tipo. No se cargaron documentos ficticios en la base manual.

Auditoría transaccional para altas/ediciones, asignaciones, cambios de acceso/empresa, cargas/revisión documental, activación y provisión global. Guarda nombres de campos, sin valores de contactos, contraseñas, hashes, códigos, tokens ni archivos. Las sesiones están cifradas por defecto. CSRF, cookies HttpOnly/SameSite, HTTPS en producción, aislamiento de recursos, CSP y controles de rol permanecen activos. No hay endpoint público de alta de plataforma.

### Compatibilidad de recorridos

`driver_id`/`vehicle_id` son preparación de esquema; no se rellenan automáticamente ni se añadieron al contrato de escritura. Todos los recorridos actuales siguen siendo válidos con NULL. Un vehículo asignado hoy no demuestra qué vehículo se usó en un viaje offline anterior. El perfil muestra recorridos **registrados por la cuenta**, con enlace al detalle/eventos; no afirma que exista contexto operativo confirmado. El detalle del vehículo solo lista viajes con `vehicle_id` explícito, por lo que hoy normalmente está vacío. La ampliación de contrato para capturar contexto al inicio del viaje es FUTURA.

## PROBADO

- Baseline: **46 pruebas / 354 aserciones**, aprobadas antes de implementar.
- Regresión completa final PHP: **68 pruebas / 520 aserciones**, aprobadas. Las 46 originales siguen incluidas; 21 pruebas nuevas de gestión/seguridad y 1 de migración.
- Se probaron: login/separación de áreas, acceso global, creación de empresas/admins y auditoría, empresa inactiva, CRUD permitido y aislamiento, contacto, asignación/reasignación/colisiones, FK compuesta, archivos privados/MIME/tamaño/revisión, activación única/vencimiento/no-flash, revocación de tokens y sesión al restablecer contraseña, estados de acceso, CSRF, provisión CLI y API de perfil propia/vacía/no existente.
- Migración aislada sobre esquema anterior con empresas/usuarios/trips/events/tokens TEST: todos los valores originales y UUID/timestamps conservados, contexto nullable y FK válidas.
- Migración **real aditiva**: comparación exacta de todas las columnas originales antes y después, repetida después de pruebas. Se conservan **Asistente Vial TEST (id 1), Luis TEST (member, id 1), Administrador TEST (fleet_admin, id 2)**, 2 viajes, 2 eventos y 2 tokens. **0 infracciones de FK**. No hubo seed de personas/vehículos en esa base.
- `pint --dirty` aplicado; plantillas Blade compiladas mediante `view:cache` y luego caché de vistas limpiada.
- Navegador real sobre SQLite TEST separada: alta de vehículo, alta de conductor con contacto/asignación, código de activación y activación completada; estados vacíos y navegación. Login platform_admin, empresas y reportes globales comprobados. Vista de perfil en escritorio y móvil de 390 px sin desbordamiento horizontal. Servidor TEST detenido y credenciales temporales eliminadas al terminar.
- Android: debug compilado, **24 unit tests aprobados**, instrumentación de **24 casos: 23 aprobados y 1 opt-in omitido**, lint **0 errores / 23 advertencias**. No se modificó Room ni BLE. Detalles en `../mobile/ETAPA_PERFIL_CONDUCTOR.md`.
- Prueba **HTTP real** independiente en emulador contra Laravel/SQLite TEST: **1 prueba aprobada**, ejecución final con el nuevo rol `driver`. Incluye login, perfil/contacto/vehículo reales del fixture, Trip ACTIVE→COMPLETED, evento SIMULATED, reapertura Room, reenvío idempotente, 401 y reautenticación. SQLite verificada con exactamente 1 viaje COMPLETED y 1 evento SIMULATED. No fue MockWebServer.

Comandos ejecutados desde backend:

```powershell
& C:\xampp\php\php.exe vendor/phpunit/phpunit/phpunit
& C:\xampp\php\php.exe vendor/bin/pint --dirty
& C:\xampp\php\php.exe artisan view:cache
& C:\xampp\php\php.exe artisan view:clear
& C:\xampp\php\php.exe scripts/fleet-legacy-check.php after
& C:\xampp\php\php.exe artisan migrate:status
```

La copia consistente y baseline de conservación están en `storage/app/private/fleet-preservation/` (ignorados por Git, contienen información privada). `before` se ejecutó una sola vez y rechaza sobrescribir el baseline. No distribuir esa copia ni los archivos de sesiones. Los fixtures de navegador/HTTP viven exclusivamente en `storage/framework/testing/`.

## Prueba manual

### Servidor y platform_admin TEST

Desde PowerShell:

```powershell
Set-Location D:\Proyectos\AsistenteVialInteligente\backend
& C:\xampp\php\php.exe artisan migrate:status
& C:\xampp\php\php.exe artisan platform:provision --company-id=1
```

El ID 1 fue comprobado como **Asistente Vial TEST** en esta instalación. El comando solicita nombre, email y contraseña oculta repetida. Para una prueba, indica un nombre claramente TEST y un email elegido por ti; no hay credenciales predeterminadas. No reutiliza ni altera a Luis/Administrador TEST. No se ejecutó esta provisión sobre tu base manual: debes crear personalmente la identidad autorizada.

Si el servidor todavía no está ejecutándose:

```powershell
& C:\xampp\php\php.exe artisan serve --host=127.0.0.1 --port=8000
```

Abre `http://127.0.0.1:8000/platform/login` e inicia sesión con lo que acabas de ingresar. Consulta Empresas, detalle, Cuentas, Reportes y Auditoría. Para crear un administrador de flota: Cuentas → formulario inferior → empresa existente → nombre/email → registrar → entregar el código privado → `/access`. No hay correo automático. No deshabilites la empresa manual mientras pruebas sincronización salvo que estés comprobando explícitamente el bloqueo.

### Conductor, vehículo, documento y Android

1. Inicia sesión como **fleet_admin existente** en `http://127.0.0.1:8000/dashboard/login`, usando tus credenciales actuales.
2. Vehículos → Registrar vehículo → placa/marca/modelo/estado → Guardar. Ingresa datos que tú decidas registrar; no hay precarga.
3. Conductores → Registrar conductor → nombre/email propio de esa cuenta, contacto opcional completo y vehículo opcional → Guardar. Si haces una prueba académica, identifica explícitamente sus datos como TEST.
4. Copia el código que aparece una sola vez y entrégalo de forma privada. Abre `http://127.0.0.1:8000/access` en otra sesión/pestaña, introduce código y nueva contraseña repetida. Vence en 24 horas. Si se pierde, el administrador genera otro e invalida el anterior.
5. Desde el perfil puedes editar nombre/estado/contacto y gestionar acceso. Para reasignar: selecciona vehículo → Guardar asignación. Para terminar: opción sin vehículo. El vehículo ocupado por otro conductor se rechaza; termina primero esa otra asignación.
6. Documentación → Adjuntar documento → Licencia de conducir (brevete) → referencia/fechas opcionales → archivo real PDF/JPEG/PNG ≤5 MiB → Guardar. El archivo se descarga únicamente desde la sesión administrativa autorizada. Registra PENDING/VERIFIED/REJECTED como revisión administrativa.
7. Android Emulator: Inicio → Ajustes → **URL base del servidor** `http://10.0.2.2:8000/`, **Email** y **Contraseña** del conductor activado → **Iniciar / renovar sesión**. La app obtiene/guarda su token; no se copia ningún token manualmente.
8. Vuelve a Inicio → **Mi perfil**. Verás perfil, contacto, vehículo y metadatos documentales existentes. Si no registraste algún elemento, aparece su estado vacío. Luis TEST seguirá sin perfil si no fue vinculado expresamente; no es un fallo de autenticación.
9. Inicia/finaliza un recorrido SIMULATED para regresión; la operación sigue siendo local/offline. Su vehículo no se atribuye automáticamente desde la asignación actual. El dashboard presenta lo recibido después de sincronizar.
10. Sin conexión, Mi perfil muestra que requiere servidor; los viajes y eventos locales continúan. Al restablecer conexión, pulsa Actualizar perfil.

Teléfono físico: requiere servidor alcanzable en tu red; `10.0.2.2` es exclusivo del emulador. Configura la URL local que corresponda, sin fijarla en código. HTTP solo en desarrollo local/debug; producción conserva HTTPS. Para exponer el servidor local a un teléfono se puede iniciar con `--host=0.0.0.0`, manteniendo acceso restringido a tu entorno de desarrollo.

## Archivos principales

- Migración `2026_09_30_150000_add_fleet_management.php`; ocho modelos nuevos y relaciones adicionales en User/Trip.
- `app/Http/Controllers/Dashboard/FleetController.php`, `PlatformController.php`, `DriverProfileController.php`, `AccessController.php`; ampliación de SessionController.
- Servicios `AccessInvitations`, `VehicleAssignments`, `AdministrativeAudit`; middleware PlatformAdmin y ajustes a FleetAdmin/TenantContext/DashboardHeaders.
- `app/Console/Commands/ProvisionPlatformAdmin.php`, rutas web/API, bootstrap, filesystem privado y sesión cifrada.
- Vistas `resources/views/fleet/`, `platform/`, `access.blade.php`, navegación/layout V1 y CSS existente ampliado.
- Tests `FleetManagementTest`, `FleetMigrationTest`; helpers aislados y `scripts/fleet-legacy-check.php`.
- Android: dominio profile, DTO, repositorio, módulo Hilt, ViewModel/pantalla, navegación/recursos/tests; detalle en documentación móvil.

## PENDIENTE / límites de esta versión

- Alta manual de platform_admin y los conductores/vehículos/documentos que decidas registrar; no se crean automáticamente en la base principal.
- Sin infraestructura de correo: entrega privada del código a cargo del administrador. Sin MFA ni antivirus de archivos; no afirmar que una revisión administrativa autentica un documento.
- Las cuentas globales se aprovisionan por operador; no se gestionan desde otra cuenta web global. No hay UI para promover roles ni cambiar email de cuentas ya registradas.
- Sin caché persistente de perfil en Android ni descarga móvil de archivos; solo metadatos propios. No se ejecutaron los cuatro tests UI antiguos que escriben en la base Room manual; se conservaron y se cubrió persistencia/sync con bases aisladas.
- SQLite se conserva por compatibilidad local. Para producción se requieren TLS, permisos de almacenamiento, backups protegidos y política de retención de documentos/auditoría; no se realizó despliegue productivo.

## FUTURO

Primero diseñar una ampliación compatible del contrato que capture conductor/vehículo al inicio del recorrido y conserve ese contexto durante trabajo offline; no inferirlo al recibir en servidor. Hardware, SENSOR, dispositivos, MPU6050, GPS, buzzer, mapas y detección permanecen fuera de esta etapa. No se modificó firmware.
