# Monitoreo básico de dispositivos

- Android envía el último estado técnico del dispositivo BLE recordado por la cuenta autenticada. No envía coordenadas ni mediciones IMU. Sin sesión o en demo no hay reportes.
- La identidad `client_device_id` es un UUID local persistente por cuenta y dispositivo seleccionado, no una identificación física ni una asignación de hardware validada por el administrador. Cambiar de dispositivo genera otro UUID; volver a un dispositivo después de cambiarlo también crea otro registro en esta versión mínima. No se transmite la dirección Bluetooth.
- Se conserva cifrado el último reporte por cuenta en preferencias separadas de Room. Las muestras intermedias se reemplazan: es estado técnico, no un historial de telemetría. Room y su cola de viajes/eventos no cambian.
- Mientras Android está en primer plano se toma como máximo un reporte cada 60 segundos y se intenta enviar. El Worker existente reintenta los pendientes con conectividad y reconciliación periódica (15 minutos). No hay servicio permanente ni garantía de ejecución con el proceso cerrado. Al reabrir se recupera el reporte pendiente.
- Los reintentos reutilizan RetryPolicy (espera progresiva y Retry-After). 401/403 quedan bloqueados hasta una sesión nueva; 409/422 no se desbloquean automáticamente. UUID y fecha de observación permiten repetir el envío sin duplicar el registro. No se genera tráfico por cada frame BLE.

## API

`PUT /api/v1/devices/{client_device_id}/status`, Sanctum y empresa activa. Solo conductor activo con perfil propio y permiso de escritura existente. La empresa y el usuario se obtienen de la sesión, nunca del cuerpo. Un identificador ya vinculado a otro usuario de la misma empresa se rechaza. El namespace del UUID es por empresa.

Campos: `name`, `observed_at`, `last_contact_at` nullable, `ble_state`, `imu_state`, `gps_state`, `technical_error` nullable. Fechas UTC ISO 8601 con milisegundos; último contacto = última muestra IMU/GPS recibida, no hora de recepción HTTP. Enums limitados a los estados existentes. No admite campos de ubicación ni IDs de empresa/usuario. Errores técnicos son mensajes disponibles de conexión GATT; ausencia de error no prueba que todo el hardware esté validado.

El servidor guarda `received_at`. Repetir el mismo reporte no refresca esa fecha. Los reportes antiguos no sobrescriben uno posterior; una misma fecha con contenido diferente devuelve 409.

## Presentación

- **Comunicación reciente**: observación Android y recepción servidor dentro de los últimos 3 minutos. Indica comunicación Android → servidor, no garantiza conexión vigente del ESP32. Consultar además el estado BLE reportado.
- **Sin comunicación reciente**: reporte caducado; también aplica a un reporte antiguo entregado después de recuperar Internet.
- **Sin información**: no existe reporte. Los dispositivos aparecen después del primer envío, no hay inventario ficticio.

`/dashboard/devices` muestra solo la empresa autenticada. `/admin/devices` muestra la vista global ordenada por empresa. El vehículo mostrado es la asignación vigente del conductor, no una asignación permanente del ESP32. Fechas presentadas en America/Lima. Recargar la página para actualizar los estados; no hay seguimiento en tiempo real.

## Prueba manual

1. Iniciar Laravel con la configuración de red local habitual y abrir Android con una cuenta DRIVER activada de la empresa, URL y sesión válidas.
2. Seleccionar/recordar AsistenteVial-IMU y mantener Android abierto. Conectar BLE y observar IMU/GPS en Diagnóstico.
3. Abrir `/dashboard/devices` con fleet_admin de esa empresa o `/admin/devices` con platform_admin. Recargar después de un minuto; revisar BLE/IMU/GPS y las fechas.
4. Desconectar BLE manualmente y esperar hasta 60 segundos para ver el cambio reportado. Cerrar Android y esperar más de 3 minutos: el estado de comunicación caduca.
5. Apagar Internet/servidor temporalmente: el último reporte queda cifrado pendiente. Restablecer y reabrir Android; el reintento respeta la espera pendiente. Los viajes siguen guardándose offline como antes.

La validación física Android → Laravel de este monitoreo requiere esta prueba; las pruebas automatizadas no equivalen a probar el ESP32. WHO_AM_I = 0x75 continúa anómalo y SENSOR sigue deshabilitado.

## Archivos de esta fase

- Backend: `app/Models/Device.php`, `app/Http/Requests/DeviceReportRequest.php`, `app/Http/Controllers/DeviceController.php`, `database/migrations/2026_10_11_120000_create_devices_table.php`, `resources/views/devices/index.blade.php` y `tests/Feature/DeviceMonitoringTest.php`.
- Rutas y navegación web: `backend/routes/api.php`, `backend/routes/web.php`, layouts `dashboard/layout.blade.php` y `platform/layout.blade.php`.
- Android: `data/devices/DeviceReport.kt`, `DeviceReportSync.kt`, `DeviceMonitor.kt`, `SecureDeviceReportStore.kt`; conexión con `MainActivity.kt`, `core/di/BleModule.kt` y `data/sync/SyncWorker.kt`. Pruebas: `DeviceReportTest.kt`.
- Se conservan los cambios previos de reconexión BLE sin sustituirlos. No se modifican firmware ni esquemas Room.

## Verificación automatizada

Laravel: 6 pruebas y 41 aserciones aprobadas, con fixtures TEST y SQLite aislada. Incluyen permisos, empresa/cuenta, reenvío, conflicto, reportes antiguos, caducidad y vistas administrativas.
Android: compilación debug y pruebas seleccionadas de reportes (3), reconexión (8) y GATT (4). Los reportes HTTP se prueban contra MockWebServer, no contra el ESP32 ni contra Laravel real. Se comprueba Retry-After, coalescencia, identidad persistente, 401 y cambio de cuenta.
Migración aditiva aplicada a SQLite local tras crear copia de seguridad excluida de Git. No se crean cuentas ni registros de dispositivos ficticios en la base local.
