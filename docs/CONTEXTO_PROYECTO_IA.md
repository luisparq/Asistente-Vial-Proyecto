# Contexto maestro — Vialigent Technologies / AVIGEM

Referencia para equipo, Work y Codex. Actualización: 01/11/2026. Leer primero las [decisiones estratégicas](DECISIONES_ESTRATEGICAS_2026-11-01.md). No sustituye contratos técnicos ni autoriza cambios de código.

## Identidad y propósito

Desarrolladora: VIALIGENT TECHNOLOGIES. Producto: AVIGEM, «Asistente Vial Inteligente para la Gestión y Evaluación de Maniobras». Eslogan: «Inteligencia en cada maniobra». Nombres internos elegidos; registro/disponibilidad legal pendientes. App para conductor, Fleet para empresa y One provisional para módulo físico. Connect no es producto aprobado.

Se conserva el problema original de supervisión de vehículos conducidos por terceros. Enfoque: seguridad vial, gestión responsable y movilidad urbana; cliente B2B de pequeñas/medianas flotas, segmento prioritario por validar en taxis/transporte urbano de Arequipa. Conductor es usuario; pasajeros y otros usuarios de la vía son beneficiarios indirectos.

## Arquitectura y contratos que se conservan

Repositorio: https://github.com/luisparq/Asistente-Vial-Proyecto

Directorio original: `D:\Proyectos\AsistenteVialInteligente`.

ESP32 + IMU + NEO-6M → BLE → Android Kotlin/Compose/Hilt → Room/SQLite → WorkManager/API REST → Laravel 12/Sanctum → administración y dashboard empresarial.

- `/admin`: platform_admin, company_id NULL. `/dashboard`: fleet_admin con empresa. Android: conductor con empresa. Mantener aislamiento y permisos.
- Firmware `firmware/AsistenteVialImu/AsistenteVialImu.ino`: IMU SDA21/SCL22; GPS TX→RX16 y RX→TX17; buzzer GPIO4 manual. No cambia cableado por identidad comercial.
- Nombre BLE técnico `AsistenteVial-IMU`; servicio `73e8ca02-41d7-45bd-9d39-7187649746ca`; IMU_RAW `e77a0a03-3641-40f2-87b7-f178622259cd`, 12 bytes little-endian a 4 Hz. GPS independiente a 1 Hz: [contrato GPS](CONTRATO_GPS_BLE.md).
- [Contrato Android/Laravel](CONTRATO_ANDROID_LARAVEL.md): conservar idempotencia, UTC, UUID de registros, revisiones, campos opcionales y compatibilidad offline. Nunca conectar Android directamente a la base del servidor.
- MapLibre Native/GL JS y proveedor cartográfico configurable, con atribución. Mapa opcional; concentración de eventos no demuestra riesgo vial.

## Cómo expresar el estado

**IMPLEMENTADO:** código comprobado en archivos; no implica prueba física, despliegue ni resultado comercial.

**VALIDADO:** indicar método, fecha y fuente. Una compilación valida construcción; un ensayo sintético valida comportamiento bajo ese ensayo; una prueba física reportada no equivale a certificación independiente.

**EXPERIMENTAL:** procesamiento nominal, calibración transitoria y candidatos TEST_ONLY; no detecciones oficiales.

**PROPUESTO:** voz TextToSpeech local, SDK/API de integración, diferenciadores urbanos y modelo comercial; no autorizados para implementación por este documento.

## Estado de referencia y evidencia

- Implementados en repositorio local: transporte IMU/GPS, mapas, recorridos/eventos, Room, sincronización offline, roles, empresas/cuentas, monitoreo técnico y soporte. Referencias: `mobile/.../domain/processing/`, `mobile/.../feature/map/`, `mobile/.../data/devices/`, `backend/routes/web.php` y contratos enlazados.
- Pruebas físicas reportadas por el responsable: IMU variable en Android, GPS real, evento SIMULATED con posición real recibido en Laravel y buzzer independiente audible. No se han repetido en esta actualización.
- El principal incluye buzzer manual; la prueba independiente no confirma toda la integración. Reconexión implementada no garantiza recuperación con proceso cerrado/restricciones Android.
- IMU: `WHO_AM_I = 0x75` permanece anómalo; no identifica válidamente MPU6050. Escalas nominales no certificadas. Magnitud aproximada de 0.943 g y calibración física pendientes.
- SENSOR no habilitado como detección física validada. SIMULATED siempre es prueba; GPS real no cambia ese origen. No hay evidencia de reducción de accidentes ni de ahorro.
- Elástika adquirido según el usuario; despliegue final no verificado. No asumir que cambios locales ya están publicados.

## Precedencia documental y antecedentes

Las decisiones del 01/11 sustituyen propuestas estratégicas anteriores en discusión, no resultados técnicos históricos. Conservar título académico y entregables originales.

README y ESTADO_ACTUAL contienen apartados históricos del 03/10; algunos pendientes (GPS/buzzer) y versiones de esquema están superados por documentos posteriores. **No usar esos apartados aislados como inventario vigente.** Para avances técnicos leer [checkpoint 10/10](CHECKPOINT_2026_10_10.md), [3C/laboratorio 12/10](ETAPA_3C_PROCESAMIENTO.md), [3B](ETAPA_3B_BLE.md), [mapas](MAPA_INCIDENCIAS.md) y [monitoreo](MONITOREO_DISPOSITIVOS.md), contrastando con código. No se reescriben esos antecedentes en esta tarea.

## Límites para próximas tareas

No renombrar paquetes, endpoints, nombre/UUID BLE ni repositorio para aplicar marca sin tarea específica. No alterar datos históricos, producir SENSOR, alertas automáticas u offsets permanentes sin validación/autorización. No incorporar Gemini, MQTT, cámaras, SDK o nuevas dependencias por inferencia.

No versionar credenciales, .env, tokens, bases privadas, backups ni documentos personales. No inventar cuentas, coordenadas, costos, encuestas, resultados o acuerdos comerciales. Solicitar evidencia para afirmaciones de precisión o eficacia.

## Investigación y decisiones siguientes

Cuatro encuestas y guía de entrevistas enlazadas en el documento estratégico: borradores sin respuestas. ODS 3/meta 3.6 y ODS 11/meta 11.2 son alineación conceptual, no impacto probado. Onway es referencia comercial; AVIRU necesita fuente verificable. Prioridades: investigación real, disponibilidad legal, IMU/validación física y viabilidad económica. Voz local y SDK siguen propuestos.

## Registro de esta actualización

Solo documentación nueva. Había cambios visuales pendientes en Android y Laravel al iniciar; se preservaron sin modificarlos. No se ejecutaron migraciones, compilaciones, pruebas, despliegues, cambios de cuentas, commit, push ni merge. La comprobación del repositorio fue local; no acredita el estado remoto.
