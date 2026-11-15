# Mapa en vivo de AVIGEM Fleet

La pantalla está en **Operaciones → Mapa en vivo**. Usa MapLibre GL JS incluido en Laravel, con los estilos Liberty y Dark de [OpenFreeMap](https://github.com/hyperknot/openfreemap-styles). MapLibre muestra la atribución cartográfica.

## Datos y configuración

`GET /dashboard/live-map/vehicles` requiere una sesión web de Fleet con acceso activo. La empresa se toma de la sesión. La respuesta privada contiene `updated_at` en UTC y la lista `vehicles` con `id`, `name`, `plate`, `driver` y `position`. El límite es de 30 peticiones por minuto. La pantalla hace una consulta al abrirse, luego espera entre consultas y suspende la espera cuando la pestaña se oculta. Al regresar, vuelve a consultar.

- `LIVE_MAP_POLL_SECONDS` vale 30 por defecto y admite un mínimo de 15.
- `LIVE_MAP_RECENT_SECONDS` vale 120 por defecto. Una posición válida con antigüedad hasta ese umbral se muestra verde. Una posición más antigua se muestra ámbar. La edad se calcula a partir de la medición para que la sincronización offline de una posición vieja conserve su antigüedad.
- `MAP_STYLE_URL` controla el estilo claro. `LIVE_MAP_DARK_STYLE_URL` controla el oscuro. Los dominios de estilos y teselas deben estar en `MAP_ALLOWED_ORIGINS`.

Las tarjetas muestran «Actividad no confirmada». La hora de actualización de la lista corresponde a la consulta. Cuando la fuente real esté conectada, cada posición incluirá `latitude` y `longitude` numéricas, `measured_at` (hora de medición) y `received_at` (hora registrada por Laravel), ambas en UTC e ISO 8601. Una posición ausente, inválida o con tiempos inconsistentes se muestra como «Sin ubicación disponible».

## Fuentes revisadas

- **Recorridos.** El contrato de sincronización guarda estado, tiempos y revisión. Las coordenadas se adjuntan a eventos de un recorrido.
- **Eventos.** Pueden incluir datos de GPS NEO-6M. La escritura actual admite eventos con origen `SIMULATED`. El mapa de incidencias sigue siendo la vista para esos registros.
- **Posiciones periódicas.** El firmware notifica el GPS por BLE y Android lo consume en el mapa del conductor. Laravel carece de una ruta de ingestión periódica por vehículo, por lo que el endpoint de Fleet devuelve `position: null`.
- **Comunicación del dispositivo.** La conexión BLE se observa en Android. Las sesiones y muestras MQTT de Diagnostics son de IMU y no acreditan una ubicación ni la actividad actual de un vehículo.
- **Imágenes.** El registro de flota carece de campos de fotos autorizadas para conductor o vehículo. Las tarjetas usan iconos neutros.

## Extensión GPS real propuesta, pendiente de autorización

Reutilizar las notificaciones BLE actuales. Android validaría el fix y mantendría en su cola offline la última posición pendiente por recorrido, con un intervalo mínimo inicial de 30 segundos por vehículo. Enviar solo después de sincronizar el recorrido y mientras exista una asociación autorizada entre conductor, recorrido y vehículo. Ante reintentos, conservar el mismo identificador de observación y la hora original de medición. Esto requiere acordar cómo reconstruir la fecha UTC completa a partir del frame BLE y el reloj de Android.

Propuesta de escritura REST bajo el autenticador de conductor existente: `PUT /api/v1/trips/{uuid}/latest-position` (ruta ilustrativa, a ajustar al prefijo real). Laravel obtendría la empresa y el vehículo del recorrido autorizado, validaría la asignación y coordenadas, limitaría la frecuencia y registraría su propia hora de recepción. Una migración añadiría una fila por vehículo con clave y vínculo a su empresa, identificador de observación y ambos tiempos. Una escritura atómica solo reemplazaría la fila si la medición es más reciente, incluso con entregas tardías o simultáneas. Los duplicados conservarían la recepción original.

Fleet leería ese dato con el mismo ámbito empresarial y mantendría las consultas HTTP. La implementación requerirá pruebas de duplicados, entregas fuera de orden, sincronización offline, autorización al escribir y leer, además de validar la medición con el dispositivo cuando esté disponible.
