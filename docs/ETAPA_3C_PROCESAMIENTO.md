# Etapa 3C — software preparado, validación física pendiente

Se conserva la numeración: 3A preparación BLE; 3B transporte físico de cuentas crudas validado por el usuario; 3C procesamiento/detección experimental. La Etapa 4 (backend/API) se desarrolló anticipadamente. **3C no está cerrada.**

## Pipeline en Android

Parser existente IMU_RAW → `RawImuSample` → `ImuConverter` → `PhysicalImuSample` → `ImuProcessor` → `ProcessedImuSample` → `DrivingEventDetector` → `CandidateEvent`.

Clases puras en `domain/processing`; el ViewModel de Diagnóstico las conecta con la fuente BLE existente mientras se observa la pantalla. Todo permanece en memoria. No hay escritura de candidatos en Room/API, asociación automática a viajes ni generación de `DrivingEvent(origin=SENSOR)`.

`receivedAtMillis` es el instante UTC de recepción en Android, suministrado por `Clock`; **no es tiempo de adquisición del sensor**. El frame de 12 bytes no contiene timestamp. No cambian UUID, ejes, endianness, firmware ni frecuencia nominal de 4 Hz.

## Conversión nominal y limitaciones

El firmware real escribe `ACCEL_CONFIG (0x1C)=0x00` y `GYRO_CONFIG (0x1B)=0x00`. Con la semántica MPU6050, AFS_SEL=0 corresponde a ±2 g, 16384 cuentas/g; FS_SEL=0 a ±250 °/s, 131 cuentas/(°/s). Se divide aceleración por 16384 y giroscopio por 131. Fuente: [mapa de registros InvenSense, secciones 4.18 y 4.20](https://cdn.sparkfun.com/datasheets/Sensors/Accelerometers/RM-MPU-6000A.pdf).

**WHO_AM_I=0x75 sigue siendo anómalo y no identifica válidamente un MPU6050.** Estas unidades son nominales según la configuración escrita; su exactitud y la aplicación efectiva de las escalas en este módulo no están confirmadas físicamente. La aceleración conserva gravedad; no hay transformación de orientación ni estimación de velocidad.

La medición reportada de aproximadamente **0.943 g** sigue pendiente de explicación: no se supone corregida ni se utiliza para fijar escalas, offsets, referencia de reposo o umbrales. Falta confirmar si corresponde a un eje o a la magnitud total y leer de vuelta la configuración real del módulo.

## Configuración experimental

- Calibración `null`: sin corrección. Offsets opcionales en g y °/s, restados por eje; no se incluyen offsets físicos inventados. Configurarlos no acredita calibración validada.
- Filtro exponencial opcional con alpha explícito; deshabilitado por defecto. Se conservan valores raw, convertidos y procesados para comparar.
- Detector deshabilitado por defecto (configuración `null`). También queda deshabilitado si no se aporta referencia explícita de aceleración en reposo. Los ensayos usan únicamente configuración `TEST_ONLY`: eje longitudinal, signo hacia adelante, eje de giro y referencia fija de reposo/gravedad en coordenadas del sensor después de aplicar offsets opcionales. No se aprende esa referencia ni se compensa dinámicamente la inclinación del vehículo.
- Ventana de muestras consecutivas: duración mínima positiva, al menos dos muestras, intervalo máximo entre muestras, umbral de entrada/salida para histéresis y cooldown. Una activación requiere liberación antes de repetirse. Duplicados no cuentan; discontinuidades reinician evidencia temporal. No existen umbrales de producción.
- Confirmación: además de duración y continuidad, se exige el mínimo de muestras sobre el umbral de entrada; un único pico seguido de valores subumbral no confirma un candidato. Los giros requieren signo consistente; cambios de signo reinician evidencia conservando cooldown. El filtro opcional no se aplica dos veces a un timestamp repetido.
- Los ensayos configuran también una duración máxima de ventana: picos aislados distantes no acumulan evidencia indefinidamente. Este límite es opcional por compatibilidad, no tiene valor predeterminado validado y no libera el bloqueo de un candidato ya confirmado mientras la señal siga activa.
- Diagnóstico muestra RAW/CONVERTIDO/PROCESADO y advertencias. Desconexión, error o caducidad retiran valores actuales y reinician procesamiento. No se sustituyen muestras ausentes por valores sintéticos.

## Verificación y trabajo físico pendiente

Implementado para ensayos sintéticos: offsets transitorios, filtrado opcional, referencia explícita de reposo/orientación y candidatos de las tres maniobras, con rechazo de reposo/ruido bajo umbral, picos aislados, giros alternantes y muestras insuficientes. La calibración experimental existente calcula estadísticas/offsets gyro en memoria y no los aplica automáticamente. BLE real conserva `ImuPipeline()` sin calibración, filtro ni detector habilitados; no se crean ni persisten eventos SENSOR.

Pruebas de software con vectores **sintéticos TEST ONLY**: parser, timestamp de recepción, conversión, offsets, filtro/reset, detector deshabilitado, ventanas, histéresis, cooldown y discontinuidades. Compatibilidad con pruebas BLE/ViewModel y UI de diagnóstico. Sus resultados no validan maniobras físicas ni precisión del módulo.

Verificación ejecutada el 05/10/2026: `assembleDebug` correcto; 18 pruebas unitarias aprobadas (ImuRawTest: 4, ImuProcessingTest: 9, GattConnectionTest: 4, BleViewModelTest: 1) y una prueba instrumentada DiagnosticRawUiTest aprobada en emulador Android 14. Después del ajuste final de descarte de muestras se repitieron BleViewModelTest y DiagnosticRawUiTest, también aprobadas. No se conectó hardware ni se ejecutaron pruebas físicas de detección.

Actualización 09/10/2026: `assembleDebug` y 22 pruebas sintéticas aprobadas (ImuProcessingTest: 14, ExperimentalCalibrationTest: 3, CalibrationFlowTest: 5). Se verificaron las tres clases de candidato con offsets/filtro/orientación explícitos, reposo con gravedad, ruido, picos aislados/distantes, giro alternante y timestamps repetidos, además de la compatibilidad de la medición experimental. No se ejecutó la suite completa ni pruebas físicas. **Software experimental reforzado; Etapa 3C permanece abierta por validación física pendiente.**

Pendiente con hardware: calibración física; confirmar orientación/escala del módulo anómalo; definir/ajustar criterios; pruebas controladas de HARSH_ACCELERATION, HARSH_BRAKING y SHARP_TURN; evaluación de falsos positivos y limitaciones de 4 Hz. Solo después podrá diseñarse/habilitarse la transición candidato validado → SENSOR → viaje/Room/sincronización/Laravel. SENSOR sigue reservado; SIMULATED sigue significando dato de prueba. No se añaden GPS, buzzer, ML ni nuevas maniobras.
