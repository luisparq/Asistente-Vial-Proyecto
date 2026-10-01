# Presentación de fechas en la zona del dispositivo

## Diagnóstico

En el caso manual informado, `2026-09-30T02:26:55.999Z` corresponde al 29/09/2026 a las 21:26:55.999 en `America/Lima`. El final UTC `02:27:11.905Z` corresponde a 21:27:11.905 del día anterior y el evento `02:27:05.016Z` a 21:27:05.016. No hay evidencia de un desfase en los datos persistidos o transmitidos.

Historial ya utilizaba `ZoneId.systemDefault()` mediante `TripInfo`. Al revisar el emulador conectado, `adb shell getprop persist.sys.timezone` devolvió `GMT`. La zona del equipo Windows o la ubicación física en Arequipa no determinan la zona del emulador.

Se encontró además que Ajustes mostraba la fecha del historial sin propietario interpolando un `Instant` directamente, lo que produce una representación UTC.

## Cambios

- `core/ui/LocalTimestamp.kt`: formateador compartido de presentación, con zona del dispositivo consultada en cada llamada y formato localizado. Añade el identificador de zona visible para explicar la hora mostrada.
- `core/ui/TripComponents.kt`: utiliza ese formateador para inicio, final y eventos en Historial, detalle y recorrido activo.
- `feature/settings/SettingsScreen.kt`: utiliza el mismo formato para los recorridos pendientes de asignación.
- `LocalTimestampTest.kt`: conversión de los tres instantes del caso manual a Lima con cambio de día; dispositivo UTC; cambio de zona predeterminada entre llamadas; conservación exacta de UTC y milisegundos al serializar.
- `NavigationTest.kt`: desplazamiento explícito hasta el botón de retorno en Ajustes. Con una sesión iniciada hay más contenido; la prueba anterior pulsaba un botón fuera de pantalla. No cambia el comportamiento de la aplicación.

No se modifican repositorios, timestamps guardados, esquema Room, DTO, contrato API ni backend por esta corrección. La interfaz muestra segundos; persistencia y sincronización conservan milisegundos. No se aplica una resta fija de cinco horas ni se impone Perú a usuarios en otras zonas.

## Verificación manual

En los ajustes del **Android del emulador**, buscar “Fecha y hora”. Si la zona automática deja GMT, desactivar la selección automática de zona y elegir Perú/Lima (`America/Lima`). No modificar manualmente el reloj ni las fechas de los viajes. Volver a abrir la pantalla Historial; si estaba abierta, salir y entrar para volver a renderizarla.

Con Lima, el recorrido informado debe aparecer el 29/09/2026 a las 21:26:55–21:27:11 y el evento a las 21:27:05, con `(America/Lima)`. Con GMT, debe seguir apareciendo el 30/09/2026 a las 02:26:55–02:27:11, ahora con `(GMT)`. Ambos representan los mismos instantes.

No se cambia automáticamente la configuración del dispositivo del usuario.

## Verificaciones ejecutadas

- `assembleDebug`: correcto.
- `testDebugUnitTest`: **17 pruebas aprobadas**, incluidas las tres nuevas pruebas de fechas y las existentes de contrato/sincronización y BLE.
- `connectedDebugAndroidTest` con las exclusiones indicadas: **22 casos, 21 aprobados, 1 omitido, 0 fallos** en el XML final. Pasaron navegación, Room, migración, UUID, cola, errores de API, persistencia aislada y BLE. El omitido es Laravel HTTP real opt-in.
- `lintDebug`: **0 errores, 23 advertencias**, sin incremento respecto al informe anterior.
- `git diff --check`: correcto.

En la primera regresión instrumentada falló el retorno desde Ajustes por un botón fuera de pantalla; pasó tras añadir desplazamiento al test. Las tres pruebas nuevas fallaron inicialmente solo por esperar `Sep` donde JDK 17 usa `Sept` para `Locale.UK`; se corrigió esa expectativa y la batería unitaria completa pasó. No se alteró la conversión de zona para resolver estas diferencias de texto.

Informes: `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/debug/` y `app/build/reports/lint-results-debug.html`.

Comando de verificación (desde `mobile`, con JDK 17):

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug '-Pandroid.testInstrumentationRunnerArguments.notClass=com.tecsup.asistente.conduccion.TripFlowTest,com.tecsup.asistente.conduccion.RestartPersistenceTest' --max-workers=2 --console=plain
```

Se detectó una sesión manual persistida en el emulador. Se excluyen `TripFlowTest` y `RestartPersistenceTest` (cuatro casos), porque escriben viajes en la base principal y podrían enviarlos bajo esa identidad. La primera ejecución se interrumpió antes de instrumentación al detectar esta condición. Las pruebas de Room/sincronización usan bases separadas. La prueba Laravel real es opt-in y no se repite para este cambio exclusivamente de presentación. No se afirma una nueva ejecución completa de los casos excluidos.
