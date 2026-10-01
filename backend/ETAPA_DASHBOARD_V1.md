# Dashboard administrativo V1 — Asistente Vial Inteligente

## Objetivo y auditoría previa

Consulta administrativa de los datos recibidos por el Laravel existente, con acceso por empresa para `fleet_admin`. No es una aplicación Laravel nueva y no cambia el productor Android.

Se revisaron `Company`, `User`, `Trip`, `DrivingEvent`, sus relaciones y migraciones; enums; `TripAccess`, `TripSync`, middleware API, Sanctum, rutas, validadores, tests y documentación, incluido `docs/CONTRATO_ANDROID_LARAVEL.md`. También se contrastaron los campos con Android y su integración/documentación de fechas.

Hallazgos:

- Laravel 12.69.2, PHP 8.2.12, Sanctum 4.3.3 y SQLite; mismos modelos para API y dashboard.
- Empresa obligatoria, roles `member` y `fleet_admin`, UUID públicos, revisión 1/2, eventos inmutables. `User.role` no admite asignación masiva desde formularios.
- La API es Bearer-only: `config/sanctum.php` conserva `guard => []` y `stateful => []`. La autorización API exige un token con contexto de empresa.
- El guard de sesión `web` y el grupo web/CSRF ya formaban parte de Laravel. No había frontend Vite, Tailwind, package.json ni vistas de dashboard.
- No hay campos de conductor, vehículo, dispositivo, coordenadas, velocidad o distancia; no se incorporaron para esta V1.

## IMPLEMENTADO — arquitectura

Rutas web → autenticación de sesión → middleware `FleetAdmin` → controlador de dashboard → `DashboardQuery` → los modelos/tablas existentes → vistas Blade.

- **Blade + CSS local**, sin dependencias externas, CDN, npm ni compilación frontend.
- Layout reutilizable y parciales para tablas, paginación, fechas y gráficos.
- Gráficos de barras con `<meter>` HTML y valores numéricos visibles/accesibles. No requieren JavaScript ni Chart.js para estos conteos.
- Lectura solamente de los datos de negocio. No hay edición de viajes/eventos ni nuevas migraciones.
- Las consultas web se mantienen separadas de `TripAccess`, que evalúa capacidades del token API. No se simulan tokens para sesiones web ni se modifica `User::permits()`.

### Autenticación y autorización

- Formulario con email/contraseña existentes. Solo autentica `fleet_admin`; errores genéricos para credenciales/rol inválidos.
- Contraseñas hasheadas por la implementación existente; no se guardan contraseñas en vistas o parámetros URL.
- Regeneración de ID de sesión al entrar, invalidación de sesión y regeneración CSRF al salir. No hay “recordarme”.
- CSRF en POST de login y logout mediante middleware Laravel y `@csrf`.
- Límites de intento de login: 5 por cuenta/minuto y 20 por IP/minuto. Reutilizan la caché configurada de Laravel.
- `FleetAdmin` comprueba rol y existencia de empresa en cada petición protegida. `member` no accede ni mediante URL directa.
- Headers de no almacenamiento de páginas privadas, bloqueo de frames, `nosniff`, CSP sin scripts y política de referencia same-origin.
- En producción se rechaza HTTP en las rutas web del dashboard. La cookie de sesión es Secure por defecto cuando `APP_ENV=production`; mantener `SESSION_SECURE_COOKIE=true`, `SESSION_HTTP_ONLY=true`, `SESSION_SAME_SITE=lax` y `SESSION_ENCRYPT=true` al desplegar. Configurar HTTPS y proxies confiables conforme al alojamiento real. No se hizo un despliegue de producción.
- Sanctum/API permanece separado. Cerrar sesión web no revoca el token Android.

### Aislamiento por empresa

`DashboardQuery` agrega `company_id` desde el usuario autenticado antes de cualquier filtro, agregado, paginación o búsqueda. No toma una empresa de la URL. El detalle busca UUID dentro de ese conjunto y devuelve 404 para un recorrido ajeno. Sus eventos también se filtran por empresa y recorrido. La selección de usuarios solo ofrece/acepta usuarios de la propia empresa. Las relaciones existentes tienen claves foráneas compuestas que preservan pertenencia.

Blade escapa nombres, email y otros valores. La configuración no permite editar datos sensibles. No hay endpoints web de escritura de viajes/eventos.

## IMPLEMENTADO — rutas

| Método | Ruta | Función |
|---|---|---|
| GET | `/dashboard/login` | Formulario |
| POST | `/dashboard/login` | Autenticación web |
| POST | `/dashboard/logout` | Cerrar sesión web |
| GET | `/dashboard` | Resumen y actividad recibida |
| GET | `/dashboard/trips` | Recorridos, filtros y paginación |
| GET | `/dashboard/trips/{uuid}` | Detalle por `client_trip_id` y eventos |
| GET | `/dashboard/events` | Eventos, filtros y paginación |
| GET | `/dashboard/analytics` | Conteos y gráficos |
| GET | `/dashboard/settings` | Empresa/cuenta/zona, solo lectura |

La raíz `/` conserva su respuesta de servicio API. Las rutas y respuestas JSON `/api/v1` no se modifican.

## IMPLEMENTADO — páginas, métricas y filtros

**Resumen:** total de recorridos recibidos, COMPLETED, ACTIVE, total de eventos y eventos SIMULATED; hasta cinco recorridos/eventos ordenados por primera recepción. `ACTIVE` es estado registrado, no presencia actual del conductor. El servidor desconoce lo que queda en la cola offline.

**Recorridos:** UUID, usuario que registró, inicio, final, estado y revisión. Filtros por estado, inicio desde/hasta y usuario de la empresa. Paginación de 20, orden determinista por inicio e ID. El detalle añade primera recepción/último cambio y eventos paginados; señala un final anterior al inicio sin cambiarlo. El creador no se etiqueta como conductor asignado.

**Eventos:** UUID, tipo original y etiqueta comprensible, origen, fecha y enlace al recorrido. Filtros por tipo, origen y fechas; 20 registros por página. `SENSOR` solo es un filtro del vocabulario reservado: no se crean datos ni se habilita su escritura. La API continúa rechazándolo.

**Analítica:** conteos por tipo, por origen, por día local y recorridos por estado. Período predeterminado de 30 días hasta el día actual en Lima; rango máximo 366 días. Eventos se filtran por ocurrencia y recorridos por inicio. Solo se dibujan días/categorías con registros; no se inventan puntos para llenar el gráfico. La agrupación diaria recorre timestamps en lotes de 500, manteniendo conteos por día y sin SQL específico de SQLite. Para volúmenes grandes será necesario evaluar agregación/índices según mediciones reales.

**Configuración:** empresa, nombre, email, rol, zona de presentación y alcance. No hay edición ni asignación de roles.

Todas las páginas ofrecen estados vacíos. SIMULATED se presenta como “Simulado · dato de prueba”, con aviso visible de que no son detecciones físicas ni infracciones comprobadas.

### Fechas

Persistencia e intercambio siguen en UTC con milisegundos. El dashboard usa `America/Lima`, indicado en pantalla; no infiere zona del navegador. Las fechas visibles se formatean a segundos. Los filtros interpretan días locales: desde medianoche inclusive hasta medianoche del día siguiente exclusiva, convertidas a UTC para consultar. Por ejemplo, `2026-09-30T02:26:55.999Z` se presenta como `29/09/2026 21:26:55`. No se reescribe ningún timestamp.

## Archivos creados/modificados

Nuevos:

- `app/Http/Controllers/Dashboard/SessionController.php`, `DashboardController.php`.
- `app/Http/Middleware/FleetAdmin.php`, `DashboardHeaders.php`.
- `app/Http/Requests/DashboardFilterRequest.php`.
- `app/Services/DashboardQuery.php`, `app/Support/DashboardDisplay.php`.
- `resources/views/dashboard/`: layout, login, overview, trips, trip, events, analytics, settings; parciales de tablas, fechas, gráficos y paginación.
- `public/css/dashboard.css`.
- `tests/Feature/DashboardTest.php` y helper aislado `tests/Support/dashboard_preview.php`.
- Este documento.

Modificados:

- `routes/web.php`: rutas del dashboard; raíz API conservada.
- `bootstrap/app.php`: redirección de invitados web a login; errores JSON API conservados.
- `config/session.php`: cookie Secure predeterminada en producción.
- `config/sanctum.php`: solo comentario aclaratorio; configuración Bearer-only intacta.

No se modifican modelos, migraciones, servicios/controladores/rutas API, Android ni firmware por esta etapa. El workspace ya contenía cambios Android de etapas anteriores y se conservaron.

## PROBADO

Verificado el 30/09/2026:

- `php artisan test --compact --log-junit storage/framework/testing/dashboard-tests.xml`: **46 pruebas aprobadas, 354 aserciones, 0 fallos y 0 errores**. Son **26 pruebas previas del backend + 20 nuevas del dashboard**.
- Regresión existente: autenticación API, `/me`, creación y actualización de viajes, revisiones antiguas, conflictos, idempotencia y concurrencia, eventos SIMULATED, rechazo de SENSOR y aislamiento entre empresas. Todas aprobaron.
- Dashboard: invitados, member, fleet_admin, credenciales inválidas, rate limiting, CSRF forzado (Laravel lo omite normalmente en testing), logout sin revocar tokens Android, empresa vacía, métricas, listado/detalle, acceso cruzado por URL, filtros, límites del día local, analítica, paginación, escape HTML, revocación del rol, sesión web frente a token, headers y HTTPS de producción.
- Primera ejecución: 43 aprobadas y un fallo en el fixture de revocación, que intentaba asignar `role` con `update()` pese a la protección del modelo. Se corrigió solo el fixture con `forceFill()->save()`. La ejecución siguiente pasó 44/44. Después de añadir dos pruebas de headers/HTTPS, pasó la batería final 46/46.
- Pint en los archivos PHP de esta fase: aprobado. `view:cache`: todas las vistas compilaron. `route:list`: 9 rutas web de dashboard y las 8 rutas API conservadas. `git diff --check`: correcto.
- Navegador real, con Browser y base TEST aislada: login, resumen con un viaje/evento TEST, navegación a recorridos, detalle y evento asociado, configuración de solo lectura, analítica, filtro SENSOR con resultado vacío, logout y bloqueo posterior al volver a `/dashboard`.
- Revisión visual a 1366×1000 y 390×844. En móvil la navegación se adapta y las tablas tienen desplazamiento horizontal propio; sin desbordamiento horizontal del documento en las vistas inspeccionadas. La revisión visual de escritorio quedó en `storage/framework/testing/dashboard-desktop.png` (solo fixture TEST). El servidor temporal en 18889 se detuvo al finalizar; no es el servidor de uso manual.

El reporte JUnit y la captura son artefactos locales ignorados por Git. No se atribuyen pruebas físicas ni una nueva ejecución Android end-to-end a esta fase.

Las pruebas PHPUnit usan SQLite `:memory:` forzada por `phpunit.xml`. La prueba de concurrencia existente usa otra SQLite temporal y subprocesos. Se rechaza configuración cacheada al ejecutar tests. Nunca se ejecutó `migrate:fresh` contra la base manual.

La revisión de navegador usa una SQLite `storage/framework/testing/dashboard-preview-*.sqlite` y una empresa/usuario/viaje/evento explícitamente TEST ONLY. No es la cuenta manual ni un dato experimental. El helper genera credenciales aleatorias en un archivo ignorado, sin credenciales por defecto de la aplicación.

## Ejecutar y probar manualmente

### 1. Abrir el servidor

En PowerShell:

```powershell
Set-Location 'D:\Proyectos\AsistenteVialInteligente\backend'
& 'C:\xampp\php\php.exe' artisan migrate:status
& 'C:\xampp\php\php.exe' artisan serve --host=127.0.0.1 --port=8000
```

Dejar la terminal abierta. Si el servidor del puerto 8000 ya está ejecutándose, usarlo; no abrir otro en el mismo puerto. Esta etapa no necesita migraciones nuevas ni instalar paquetes.

Abrir **http://127.0.0.1:8000/dashboard**. Sin sesión redirige a `/dashboard/login`. Android sigue usando `http://10.0.2.2:8000/` desde el emulador; no cambiar esa URL por una ruta del dashboard.

### 2. Usar o crear un administrador TEST en la misma empresa

Si ya tienes un `fleet_admin` en la empresa deseada, entrar con ese email/contraseña. Una cuenta `member` usada por Android no tiene acceso web administrativo.

Para encontrar el ID real de empresa de la cuenta TEST existente, en otra terminal:

```powershell
Set-Location 'D:\Proyectos\AsistenteVialInteligente\backend'
& 'C:\xampp\php\php.exe' artisan tinker
```

Dentro de Tinker (consultas de lectura):

```php
$email = readline('Email de tu cuenta TEST existente: ');
$user = App\Models\User::where('email', strtolower(trim($email)))->firstOrFail();
$user->only(['id', 'company_id', 'name', 'email', 'role']);
$user->company->only(['id', 'name']);
exit
```

Anotar el `company_id` real. Volver a PowerShell y ejecutar:

```powershell
$testCompanyId = Read-Host 'company_id real de la empresa TEST que acabas de consultar'
& 'C:\xampp\php\php.exe' artisan account:provision "--company-id=$testCompanyId"
```

Responder al comando existente con un nombre claramente TEST, un email de prueba nuevo elegido por ti, seleccionar **fleet_admin** y elegir/repetir una contraseña de al menos 12 caracteres. La empresa ya existe, por eso no se crea otra. No se cambia ni elimina la cuenta Android actual.

Si necesitas una empresa TEST completamente nueva, omitir la opción:

```powershell
& 'C:\xampp\php\php.exe' artisan account:provision
```

En ese caso elegir un nombre de empresa TEST y rol fleet_admin. Esa nueva empresa tendrá dashboard vacío y no verá los viajes de la empresa anterior, como exige el aislamiento.

### 3. Recorrido de prueba manual

1. Iniciar sesión en `/dashboard/login` con el administrador de la empresa correcta. No pegar un token Android.
2. Comprobar empresa y usuario en el encabezado y en Configuración.
3. Abrir Recorridos, filtrar el 29/09/2026 si se busca el viaje manual previo, y abrir su UUID real.
4. Verificar COMPLETED, revisión 2, fechas locales y el evento SIMULATED asociado.
5. En Eventos aplicar tipo/origen/fechas. El filtro SENSOR debería quedar vacío mientras no existan registros de ese origen; no habilita su escritura.
6. En Analítica seleccionar un rango que incluya la fecha del evento. Los conteos son solo de lo recibido.
7. Cerrar sesión y comprobar que `/dashboard` solicita acceso de nuevo.
8. Opcionalmente, sincronizar desde Android otro recorrido SIMULATED autorizado y actualizar el navegador. El dashboard no crea recorridos por sí mismo.

No hay actualización automática en vivo: recargar o navegar vuelve a consultar la base.

### 4. Repetir las comprobaciones automatizadas

```powershell
& 'C:\xampp\php\php.exe' artisan test --compact
& 'C:\xampp\php\php.exe' artisan view:cache
& 'C:\xampp\php\php.exe' artisan route:list --path=dashboard
```

Los fixtures son exclusivamente de testing. No utilizar el helper de preview para crear cuentas operativas ni configurar Android contra su base temporal.

## PENDIENTE / limitaciones

- Despliegue HTTPS real, hardening del alojamiento, pruebas de carga y navegadores adicionales no se realizaron en esta fase.
- La revisión visual responsive no sustituye una auditoría exhaustiva de accesibilidad.
- No se repite aquí una prueba Android física/emulador → Laravel; se ejecuta regresión del contrato API, y Android no cambia. La prueba manual anterior sigue siendo evidencia de su fase, no una nueva ejecución.
- No hay edición de información, recuperación web de contraseña, exportaciones, actualización en vivo ni configuración individual de zona.
- No se conocen eventos aún pendientes en Room; conteos no prueban recepción completa ni calidad de conducción.

## FUTURO

Incorporar solo tras definición y autorización: GPS real, eventos SENSOR validados, vehículos, conductores, dispositivos, cámaras, mapas y análisis espacial. Se pueden añadir controladores, vistas y enlaces al layout sin reconstruir las páginas actuales. No se crean tablas o campos anticipados.

Siguiente paso recomendado: realizar la prueba manual del dashboard con el fleet_admin de la empresa TEST existente. Después, acordar el alcance de integración física 3B cuando el hardware esté disponible. No se avanzó a esa etapa.
