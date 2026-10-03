# Asistente Vial Inteligente

Proyecto académico de asistencia vial con aplicación Android, backend Laravel, dashboard y una integración ESP32/MPU6050 en desarrollo.

## Inicio rápido para colaboradores

1. Leer [`docs/ESTADO_ACTUAL.md`](docs/ESTADO_ACTUAL.md): contiene las restricciones y avances vigentes.
2. Revisar el contrato entre móvil y backend en [`docs/CONTRATO_ANDROID_LARAVEL.md`](docs/CONTRATO_ANDROID_LARAVEL.md).
3. Consultar la guía BLE/firmware en [`docs/ETAPA_3B_BLE.md`](docs/ETAPA_3B_BLE.md) antes de cambiar esa integración.

## Estructura

| Carpeta | Contenido |
| --- | --- |
| `mobile/` | Aplicación Android en Kotlin. |
| `backend/` | API REST y dashboard en Laravel. |
| `firmware/` | Sketches ESP32/MPU6050. |
| `docs/` | Contratos, estado y guías técnicas. |
| `design/` | Recursos de diseño. |

## Estado importante

- La prueba de viajes/eventos y sincronización usa `SIMULATED`.
- `SENSOR` real no está habilitado ni validado.
- La Etapa 3B está bloqueada: I2C responde en `0x68`, pero `WHO_AM_I` devuelve `0x75`, no `0x68`.
- No aceptar `0x75` artificialmente ni eliminar la validación del MPU6050. Mientras no se valide de forma legítima, el firmware no debe publicar telemetría real.

## Seguridad del repositorio

El repositorio contiene código, documentación y plantillas. No incluye configuraciones locales ni datos sensibles: `.env`, claves, bases de datos, documentos privados, dependencias y artefactos de compilación están excluidos mediante `.gitignore`.

Para contribuir, trabajar en una rama y revisar el estado del proyecto antes de integrar cambios, especialmente en `firmware/` y BLE.
