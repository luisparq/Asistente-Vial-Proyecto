# Domain

Modelos Kotlin sin dependencias de Android o Room: Trip, DrivingEvent,
TripSummary y TripWithEvents. El contrato TripRepository expone lecturas Flow
y operaciones suspendidas de inicio/reanudación, simulación y finalización.

Los identificadores son Long locales. Las fechas de dominio usan Instant y se
almacenan como milisegundos Unix en Room; la interfaz muestra la zona horaria
del dispositivo. TripStatus distingue ACTIVE y COMPLETED. DataOrigin distingue
SIMULATED y SENSOR, pero SENSOR es solo una reserva del modelo: en esta etapa
la única operación de generación es addSimulatedEvent.

HARSH_BRAKING, HARSH_ACCELERATION y SHARP_TURN son etiquetas preliminares para
probar el flujo. No hay mediciones, detecciones ni clasificación de infracciones.

Etapa 3A añade contratos en `ble` para disponibilidad, descubrimiento, selección
y preparación de conexión; permanecen independientes de clases Android. En
`diagnostic`, SensorDataSource reserva recepción futura sin definir formato ni
generar muestras. SENSOR sigue sin utilizarse para crear eventos en esta etapa.
