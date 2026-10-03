# Asistente Vial — API Laravel

API de viajes/eventos SIMULATED sincronizados desde Room en Android, dashboard Blade y gestión de flota/plataforma. Estado actual: [ESTADO_ACTUAL.md](../docs/ESTADO_ACTUAL.md). Las cuentas crudas BLE no llegan al servidor y las altas SENSOR permanecen deshabilitadas.

- Laravel 12 + Sanctum 4; PHP 8.2 o superior compatible con `composer.lock`.
- Base local SQLite; dashboard Blade/CSS local sin Node ni compilación frontend.
- API `/api/v1`, autenticación Bearer y aislamiento por empresa.
- Sin usuarios o empresas predeterminados. El seeder no crea datos.

## Preparación local (PowerShell, dentro de backend)

```powershell
composer install
if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
    php artisan key:generate
}
if (-not (Test-Path database/database.sqlite)) {
    New-Item -ItemType File database/database.sqlite
}
php artisan migrate
php artisan serve --host=127.0.0.1 --port=8000
```

En una copia nueva, `.env`, clave y base local deben prepararse con los pasos anteriores. En un entorno ya instalado, no regenerar la clave ni reemplazar una base existente. Estos archivos privados no se versionan.

Para registrar una identidad real, el operador ejecuta `php artisan account:provision` y responde las preguntas. Para añadir un usuario a una empresa existente, usa `--company-id` con su identificador real. La contraseña se solicita oculta. El comando no emite tokens.

El cliente inicia sesión mediante `POST /api/v1/auth/token` con `email` y `password`, conserva el token de la respuesta y usa `Authorization: Bearer <token>` en los recursos. `DELETE /api/v1/auth/token` revoca el token actual. No existe registro público. Usar HTTPS fuera del entorno local; producción rechaza peticiones HTTP a la API.

## Verificación

```powershell
php artisan config:clear
php artisan test --compact
php vendor/bin/pint --test
composer validate --strict
composer check-platform-reqs
composer audit --locked
```

Las pruebas usan SQLite en memoria y un archivo temporal exclusivo para concurrencia; no usan la base local. Se detienen si existe configuración cacheada, para evitar que una configuración de desarrollo/producción se use por error durante las pruebas.

Ver [ETAPA_BACKEND_API.md](ETAPA_BACKEND_API.md) para endpoints, decisiones, migraciones, resultados realmente ejecutados y límites; [dashboard](ETAPA_DASHBOARD_V1.md) y [gestión de flota](ETAPA_GESTION_FLOTA_V1.md) para las ampliaciones. `/dashboard` aplica acceso fleet_admin por empresa; `/platform` aplica administración global separada. No hay edición de viajes/eventos desde el dashboard. Contrato de referencia: [Android ↔ Laravel](../docs/CONTRATO_ANDROID_LARAVEL.md), sección 11, puntos 1–6 aprobados por el usuario antes de esta implementación.
