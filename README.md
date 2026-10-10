# Asistente Vial Inteligente

Sistema académico con Android como interfaz del conductor, Laravel como API y administración de flota, y ESP32 como periférico BLE. Resumen histórico: **2026-10-03**. Para los avances posteriores y el estado vigente, consultar [checkpoint del 10/10/2026](docs/CHECKPOINT_2026_10_10.md).

**Etapa 3B: transmisión cruda IMU → I2C → ESP32 → BLE GATT/NOTIFY → Android validada físicamente**, según la prueba realizada y reportada por el responsable del proyecto con un teléfono Android real. El módulo mantiene la anomalía `WHO_AM_I = 0x75`: su identidad NO está confirmada y funciona mediante el modo experimental explícito. No hay detección de maniobras validada.

## Leer antes de trabajar

1. [Estado actual y límites](docs/ESTADO_ACTUAL.md).
2. [Contrato Android ↔ Laravel](docs/CONTRATO_ANDROID_LARAVEL.md), aprobado en su sección 11, puntos 1–6; conservar UUID de registros, revisiones, fechas, idempotencia y aislamiento.
3. [Protocolo BLE y evidencia física 3B](docs/ETAPA_3B_BLE.md); conservar nombre, UUID, frame y frecuencia.

## Arquitectura y estado

Sistema objetivo: `IMU/MPU6050 → ESP32 → BLE → Android → API REST/HTTPS → Laravel → base de datos → dashboard`.

Actualmente hay dos flujos distintos: las cuentas crudas BLE se muestran en Diagnóstico, en memoria; los viajes y eventos **SIMULATED** se guardan en Room y se sincronizan con Laravel/SQLite. Las lecturas BLE todavía no generan eventos, no se guardan en Room y no se envían al servidor.

| Carpeta | Implementación actual |
| --- | --- |
| `mobile/` | Kotlin, Compose/Material 3, Hilt, ViewModel/Flow; viajes, historial Room, sesión Sanctum, cola offline, sincronización manual/WorkManager, perfil propio y diagnóstico GATT real. |
| `backend/` | Laravel 12/Sanctum 4, SQLite local, API `/api/v1`, idempotencia y seguridad por empresa; dashboard Blade y gestión de flota/plataforma. |
| `firmware/` | ESP32 Dev Module, lectura cruda de seis ejes por I2C y publicación BLE; compatibilidad experimental con identidad no confirmada. |
| `docs/` | Contratos, estado del sistema y guías técnicas. |
| `design/` | Espacio para recursos de diseño. |

## Android ↔ Laravel y offline

Room (esquema 2) es la fuente de viajes e historial. Iniciar/finalizar y registrar eventos de prueba funciona sin Internet. Cada escritura crea una operación persistente con UUID estable. Laravel recibe PUT idempotentes: revisión 1 `ACTIVE`, revisión 2 `COMPLETED`; los eventos son inmutables.

En Ajustes, **Sincronizar** intenta enviar directamente mediante `SyncEngine`, muestra el resultado y conserva WorkManager como respaldo. Respeta `Retry-After`, bloqueos y leases; solo adelanta reintentos de `network_unavailable`. La sesión se cifra con Android Keystore. Fechas persistidas/intercambiadas en UTC; presentación Android en zona local del dispositivo.

El dashboard muestra lo recibido por el servidor, no las operaciones todavía offline. Conductores y vehículos existen en gestión de flota, pero no se infieren ni se asignan automáticamente a los viajes existentes. `Device` y el enlace contractual de nuevos viajes con contexto operativo siguen pendientes.

## Preparar una copia del repositorio

- Android: abrir `mobile/` en Android Studio, JDK 17 y SDK 35. Configurar el SDK local sin versionar `local.properties`. Ver [guía Android](mobile/README.md).
- Laravel: seguir [preparación local](backend/README.md): Composer, `.env` propio, clave local, SQLite y migraciones. No hay cuentas ni credenciales predeterminadas. No reemplazar una base existente.
- Integración: en Android → Ajustes introducir URL y credenciales proporcionadas por el operador. `http://10.0.2.2:8000/` corresponde al emulador; el teléfono físico requiere una dirección accesible del servidor. HTTP solo para desarrollo debug/local; HTTPS en producción. Ver [integración](mobile/ETAPA_INTEGRACION_LARAVEL.md) y [sincronización manual](mobile/CORRECCION_SINCRONIZACION_MANUAL.md).
- Hardware: mantener SDA GPIO21, SCL GPIO22 y dirección `0x68`; cargar `firmware/AsistenteVialImu/AsistenteVialImu.ino`. Procedimiento GATT en [Etapa 3B](docs/ETAPA_3B_BLE.md). BLE no necesita Laravel ni Internet.

## Límites permanentes

- `SIMULATED` = eventos de prueba. `SENSOR` está reservado; sus altas siguen deshabilitadas en la API y no hay productor de detecciones físicas validadas.
- `WHO_AM_I = 0x75` no identifica válidamente un MPU6050. La excepción experimental permite probar transporte crudo y muestra la anomalía; no certifica identidad, calibración o precisión.
- Pendientes: conversión/calibración, criterios y validación de detección, eventos SENSOR e integración local/remota, GPS, buzzer y pruebas controladas posteriores.
- No inventar datos, precisión, resultados, credenciales o pruebas. No presentar eventos experimentales como infracciones legales.
- Android usa la API, nunca conexión directa a MySQL/MariaDB. Las futuras alertas inmediatas deberán funcionar localmente.
- No modificar arbitrariamente contratos API/BLE, migraciones, UUID o datos existentes; revisar primero código y documentación.

## Seguridad y verificación

Se versionan código, contratos, esquemas y fixtures explícitos de prueba. `.gitignore` excluye `.env`, secretos, bases locales, documentos privados de Laravel, logs, dependencias, builds y configuración local. Cada commit requiere revisar también los archivos ya rastreados: `.gitignore` no los desversiona automáticamente.

Los resultados automatizados históricos están en las guías de cada fase. La evidencia física reportada el 2026-10-03 valida recepción de seis cuentas crudas variables en Android y Serial, sin métricas de precisión ni validación de maniobras. No equivale a haber ejecutado nuevamente todas las pruebas al actualizar esta documentación.
