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


## Laboratorio Android — 12/10/2026

Diagnóstico incorpora gráficas Compose Canvas de aceleración nominal (g) y giro (°/s), X/Y/Z, ventana de 30 s y máximo 128 muestras. Tiempo monotónico de recepción para gráficos/ensayos; no cambia fechas persistidas. No se unen huecos mayores a 500 ms. Se limpia al perder recepción, cambiar dispositivo o salir; no es un servicio de fondo.

Se reutiliza la medición de 30 s (aproximadamente 120 muestras a 4 Hz), estadísticas Welford y offsets gyro. Cribado **TEST_ONLY**, no tolerancia validada: mínimo 100 muestras y DE por eje ≤0.02 g / ≤1 °/s. No demuestra inmovilidad. Comparación original/corregida solo gyro, explícita, en memoria; no modifica aceleración ni guarda offsets. Desconexión descarta calibración.

Laboratorio sintético reproducible: reposo, aceleración, frenada y giro, eje X+, yaw Z y gravedad (0,0,1). Umbrales **TEST_ONLY** 0.30 g / 30 °/s, salida 0.10 g / 10 °/s; 3 muestras sobre umbral, 500 ms, intervalo máximo 400 ms, ventana 2 s y cooldown 1.5 s. Expone valores, condiciones temporales y candidatos; no crea DrivingEvent ni SENSOR. BLE mantiene detector normal deshabilitado: observación separada solo por botón, con eje/signo/giro explícitos y referencia de reposo capturada bajo responsabilidad del operador. Inclinación, orientación errónea, sesgo y muestreo de 4 Hz pueden producir falsos positivos o perder movimientos rápidos.

### Diagnóstico de identidad y escalas

Fuente primaria: [TDK/InvenSense, mapa de registros MPU-6000/6050](https://cdn.sparkfun.com/datasheets/Sensors/Accelerometers/RM-MPU-6000A.pdf). Registro WHO_AM_I **0x75** (dirección) contiene normalmente **0x68** (identidad), independientemente de AD0. Dirección I2C 7 bits 0x68 correcta; RX nativo separado del byte de selección, transacciones combined/repeated START y STOP, copia solo tras éxito. No se encontró confusión dirección/contenido en el firmware actual. Bloque 0x3B de 14 bytes big-endian omite temperatura al formar 12 bytes little-endian; Android interpreta int16 firmado correctamente.

Firmware escribe 0x6B=0x01, 0x6C=0, 0x1B=0, 0x1C=0: escalas nominales ±2 g (16384 LSB/g), ±250 °/s (131 LSB/(°/s)), compatibles con ImuConverter. **Escritura ACK no prueba read-back**: falta confirmar físicamente 0x1B/0x1C y 0x6B/0x6C después de inicializar. No se cambió firmware por falta de error verificable.

WHO_AM_I=0x75 no confirma MPU6050 ni identifica otro modelo. Hipótesis pendientes: comportamiento de módulo no conforme/identidad diferente, selección de registro o integridad eléctrica de la transacción; no se concluye sensor defectuoso. La magnitud reportada 0.943 g no se corrige: distinguir eje vs norma vectorial, comprobar montaje, read-back de escalas y referencia física. Próximo diagnóstico propuesto, no implementado: registrar read-back de los cuatro registros y observar transacción I2C con analizador si la anomalía persiste. No se acepta 0x75 como identidad válida.

Zigzag/alternancia requerirán historial firmado y ventanas entre candidatos; baches/comfort requieren orientación vertical, gravedad y frecuencia adecuada. Vector3, buffer y ventanas permiten extensión sin cambiar transporte, pero 4 Hz limita vibraciones/maniobras rápidas. No se implementaron nuevos detectores. Etapa 3C permanece experimental, pendiente de identidad, escalas, orientación y validación física controlada.

Verificación de laboratorio 12/10/2026: assembleDebug aprobado; 45 pruebas unitarias relevantes aprobadas (procesamiento, parser, calibración/flujo, búfer, BLE/GATT, GPS y compatibilidad de ubicación/mapa); 12 pruebas instrumentadas distintas aprobadas en emulador Android 14 (Diagnóstico 4, BLE/navegación 8). Tras los últimos ajustes se repitieron 10 unitarias de laboratorio/calibración y las 4 de Diagnóstico, todas aprobadas. Sin pruebas físicas nuevas; sin commit/push.
