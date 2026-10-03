# Estado actual del proyecto

Última actualización: 2026-10-03. Este documento es el punto de entrada para personas y agentes que clonen el repositorio.

## Arquitectura

`MPU6050 -> ESP32 -> BLE -> Android -> API REST Laravel -> base de datos -> dashboard`

## Avances disponibles

- **Android (`mobile/`)**: aplicación Kotlin con Room, viajes y eventos de prueba, persistencia offline, sincronización con Laravel, gestión de perfil y UI de diagnóstico BLE.
- **Laravel (`backend/`)**: API REST, autenticación, sincronización, dashboard y gestión de conductores/vehículos.
- **Firmware (`firmware/`)**: sketch ESP32 para el perfil BLE `AsistenteVial-IMU` y sketch aislado para diagnosticar `WHO_AM_I`.
- **Contrato**: `CONTRATO_ANDROID_LARAVEL.md` describe la comunicación Android/Laravel.

## Estado de datos: SIMULATED y SENSOR

`SIMULATED` es el único origen habilitado para probar viajes, eventos y sincronización. Sus datos son de prueba, no mediciones físicas.

`SENSOR` real **no está habilitado ni validado**. La telemetría cruda BLE se muestra únicamente como diagnóstico en memoria: no crea eventos de conducción, no se guarda en Room y no se envía a Laravel.

## Bloqueo: Etapa 3B, validación física del MPU6050

El módulo responde por I2C en la dirección `0x68`, pero la lectura del registro `WHO_AM_I` (`0x75`) devuelve `0x75` en las pruebas actuales. Un MPU6050 validado debe confirmar `0x68` en esa comprobación.

Reglas obligatorias para esta etapa:

- No aceptar `0x75` como identidad válida.
- No borrar, omitir ni debilitar la validación `WHO_AM_I` para continuar.
- No marcar SENSOR como listo ni enviar telemetría a Laravel mientras no exista una validación física legítima.
- Mantener el firmware en modo seguro: ante identidad no confirmada, no publicar muestras BLE.

Próximas acciones seguras: repetir la lectura con el sketch `firmware/DiagnosticoWhoAmI/DiagnosticoWhoAmI.ino`, comprobar alimentación/cableado/protoboard y, si persiste, probar otro módulo MPU6050/GY-521. Documentar el resultado antes de reabrir la integración real.

## Trabajo paralelo recomendado

Se puede avanzar en Laravel, dashboard, contrato API, experiencia Android, pruebas y documentación sin modificar el contrato BLE ni declarar que la Etapa 3B está resuelta. Los cambios que afecten a telemetría SENSOR, UUID BLE o validación del MPU6050 requieren revisión específica de esta documentación.

## Guía de colaboración

1. Leer este documento, `README.md` y `ETAPA_3B_BLE.md` antes de cambiar firmware/BLE.
2. Crear una rama por cambio y abrir un pull request para integrar mejoras.
3. No versionar `.env`, bases SQLite, archivos de `storage`, documentos privados, claves, builds ni configuraciones de IDE; `.gitignore` los excluye.
4. Mantener las pruebas de datos `SIMULATED` separadas de cualquier evidencia física.
