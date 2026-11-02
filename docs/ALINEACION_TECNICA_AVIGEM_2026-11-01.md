# Alineación técnica — Vialigent Technologies / AVIGEM

**Fecha:** 01/11/2026  
**Alcance:** consolidación documental basada en la inspección local del repositorio. No autoriza ni incluye cambios de código, contratos, infraestructura, marcas técnicas, datos o despliegues.

## 1. Identidad, propuesta de valor y alcance

**Vialigent Technologies** es la identidad de trabajo de la desarrolladora y **AVIGEM** (Asistente Vial Inteligente para la Gestión y Evaluación de Maniobras) es el producto principal. Sus nombres, el eslogan «Inteligencia en cada maniobra» y las extensiones AVIGEM App, Fleet y One pueden usarse en documentación y propuestas; su disponibilidad legal permanece pendiente. AVIGEM Connect es solo una posible denominación futura.

La propuesta evoluciona hacia seguridad vial, gestión responsable de flotas y movilidad urbana. El cliente objetivo es el propietario o administrador de una flota; el conductor es usuario operativo y otros usuarios de la vía son beneficiarios indirectos. Esto no acredita aún una mejora de seguridad, reducción de costos, precisión de detección ni una ventaja competitiva demostrada.

La marca no cambia por sí misma `com.tecsup.asistente.conduccion`, el nombre BLE `AsistenteVial-IMU`, UUID, rutas HTTP, tablas, contratos ni repositorio. Una migración visual o técnica requiere una decisión posterior explícita.

## 2. Fuentes y estado del árbol revisado

Se revisaron los documentos de estrategia y contexto creados por Work, los contratos de BLE/GPS/Android-Laravel, la documentación de 3B y 3C, el firmware, `mobile/app/build.gradle.kts`, rutas Laravel, dependencias y archivos de implementación relevantes.

Al iniciar esta revisión había cambios no confirmados en interfaces Android y Blade/CSS de Laravel, además de los documentos estratégicos y de investigación sin seguimiento. Se preservan: no fueron editados por esta tarea. Este documento no afirma que el árbol local sea idéntico al remoto ni que esos cambios estén desplegados.

## 3. Arquitectura actual observada

```text
ESP32 + IMU + NEO-6M + buzzer manual
  -> BLE (IMU_RAW y telemetría GPS)
  -> Android Kotlin / Compose / Hilt
  -> Room / SQLite + outbox + WorkManager
  -> API REST Laravel 12 / Sanctum
  -> dashboard empresarial (/dashboard) y plataforma global (/admin)
```

El firmware principal está en `firmware/AsistenteVialImu/`: I2C de IMU por GPIO 21/22, GPS por UART2 (RX 16, TX 17), BLE y buzzer manual GPIO 4. El flujo BLE conserva un frame IMU de 12 bytes a 4 Hz y una característica GPS independiente.

Android mantiene las capas `core`, `data`, `domain`, `feature` y `navigation`. Usa Compose, Hilt, Room, OkHttp, serialización Kotlin, WorkManager y MapLibre. La base local contiene viajes, eventos, cola de sincronización y migraciones; `RoomTripRepository`, `SyncEngine` y `SyncWorker` mantienen la persistencia y sincronización. La comunicación BLE está encapsulada en `data/ble`; el diagnóstico recibe muestras sin convertirlas en eventos oficiales.

Laravel 12 usa Sanctum y expone `/api/v1` para activación/autenticación, estado de dispositivos, perfil, viajes y eventos. Las rutas web distinguen administración empresarial (`/dashboard`) de administración global (`/admin`), con middleware y contexto de empresa. Hay controladores, servicios, modelos, migraciones y pruebas de API, flota, dispositivos, soporte, mapa y auditoría en el árbol.

## 4. Inventario por componente

| Componente | Evidencia en el repositorio | Límite vigente |
| --- | --- | --- |
| Firmware | `AsistenteVialImu.ino`, `GpsTelemetry.h`, `ManualBuzzer.h` y sketches de diagnóstico separados | La identidad y escala física de IMU no están confirmadas; el buzzer es manual. |
| Android conductor | BLE/GATT, Diagnóstico, recorridos, historial, mapa, perfil, activación, configuración y sincronización | No existe SDK ni servicio BLE permanente para terceros. |
| Persistencia y sync | Room, outbox, `SyncEngine`, `SyncWorker`, sesión segura y API DTO | La existencia de código no demuestra sincronización con un VPS desplegado. |
| Laravel y Fleet | API con Sanctum, empresas/cuentas, flota, dispositivos, soporte, dashboard y plataforma global | No se verificó despliegue ni configuración de producción durante esta tarea. |
| Analítica IMU | `ImuProcessing`, calibración experimental, laboratorio y detector | Solo ensayos sintéticos/TEST_ONLY; no produce eventos `SENSOR`. |

## 5. Funcionalidades implementadas y evidencia disponible

**Implementado en código inspeccionado:** transporte BLE de cuentas IMU, telemetría GPS, diagnóstico Android, recorridos y eventos `SIMULATED`, Room, sincronización offline mediante API, autenticación/aislamiento empresarial, dashboards, mapas, monitoreo técnico y soporte. El firmware mantiene GPS y buzzer manual independientes.

**Evidencia histórica documentada, no repetida por esta tarea:** los documentos estratégicos reportan transporte IMU a Android, GPS real, un evento `SIMULATED` georreferenciado recibido en Laravel y una prueba independiente audible del buzzer. Esos reportes no demuestran la convivencia completa IMU/GPS/BLE/buzzer, un despliegue VPS, precisión del sensor ni resultados comerciales.

`SIMULATED` sigue significando dato de prueba, incluso cuando incluye GPS real. El código de salida para la sincronización restringe eventos a ese origen. `SENSOR` no se debe interpretar como detección física habilitada.

## 6. Capacidades experimentales y no validadas

El pipeline IMU de Android conserva conversión nominal, gráficas, filtrado opcional, calibración transitoria de giroscopio y candidatos `TEST_ONLY`. No persiste candidatos como eventos oficiales. La observación `WHO_AM_I = 0x75` no confirma MPU6050 ni otra familia concreta; la escala nominal y la observación aproximada de 0.943 g requieren prueba física.

También son propuestas, no capacidades actuales: indicadores de brusquedad o comodidad, análisis por tramo, alertas automáticas, retroalimentación formativa, voz, SDK/API de integración, diferenciadores urbanos, precios, sostenibilidad económica y resultados de seguridad vial.

## 7. Pendientes técnicos prioritarios

1. Ejecutar el diagnóstico IMU de solo lectura, confirmar `WHO_AM_I`, configuración, escalas, orientación, montaje e integridad I2C; después realizar pruebas físicas controladas de maniobras y falsos positivos.
2. Verificar en un circuito controlado la convivencia ESP32–IMU–GPS–BLE–Android–Laravel y documentar datos, errores y límites reales. No activar `SENSOR` hasta cumplir criterios acordados.
3. Evaluar el VPS de Elástika separadamente: acceso autorizado, sistema operativo, actualizaciones, TLS, secretos, base de datos, copias, firewall, observabilidad, restauración y presupuesto. No se asume que esté desplegado, configurado o protegido.
4. Completar investigación de usuarios, competencia, disponibilidad legal y costos con fuentes y datos reales antes de presentar conclusiones comerciales.

## 8. Evaluación preliminar de TextToSpeech local

La opción propuesta es `android.speech.tts.TextToSpeech` con voces locales compatibles, sin Gemini API como dependencia inicial. No se ha añadido código ni dependencia para ello.

Una futura evaluación debe comprobar disponibilidad offline por dispositivo e idioma, inicialización/fallo del motor, foco y volumen de audio, consentimiento/configuración del conductor, accesibilidad, frecuencia máxima, cancelación al cambiar de pantalla y consumo. Los avisos de desconexión BLE o estado del dispositivo podrían evaluarse independientemente; los avisos de maniobras solo podrían emitirse después de validar física y funcionalmente su detección. No se plantea conversación continua durante la conducción.

## 9. Evaluación preliminar de SDK Android

No hay módulo SDK ni acuerdo con una empresa de taxis. La app actual concentra BLE, diagnóstico, persistencia y sincronización dentro de un `applicationId` de aplicación; extraerlos ahora alteraría ciclo de vida, permisos, inyección Hilt, almacenamiento, soporte de segundo plano, contratos BLE/API y responsabilidad de datos.

Antes de diseñar AVIGEM Connect se debe definir el límite del SDK, API pública/versionado, propiedad de datos, consentimiento, soporte a múltiples hosts, seguridad de tokens, batería, permisos BLE de Android, restricciones de segundo plano, compatibilidad y estrategia de actualización. La API REST existente puede ser una superficie de integración futura, pero no reemplaza la conexión BLE que requeriría la aplicación anfitriona.

## 10. VPS y costos

El VPS de Elástika se considera una línea de infraestructura pendiente de verificación, no evidencia de operación comercial. No se inspeccionaron credenciales, proveedores, DNS, despliegues, bases remotas ni costos en esta tarea.

La evaluación económica futura debe separar costos de dispositivo/ensamblaje/instalación, VPS, base de datos, respaldos, mapas, conectividad, soporte, mantenimiento, reemplazos, seguridad y comercialización. Cualquier precio, margen, ahorro o punto de equilibrio requiere supuestos explícitos y evidencia; no se fija aquí.

## 11. Marca e interfaces

Puede prepararse una propuesta visual posterior para AVIGEM App, Fleet y administración Vialigent, pero el estado actual no autoriza sustituir textos, paquetes, rutas, nombres técnicos ni identificadores. La propuesta debe inventariar pantallas Android y Blade, textos de login/dashboard, nombre visible BLE, documentación, accesibilidad, migración gradual y compatibilidad con capturas/entregables académicos antes de editar UI.

## 12. Riesgos y contradicciones documentales

- Los README y documentos históricos no están todos actualizados: por ejemplo, documentación móvil antigua enumera GPS/buzzer como pendientes, mientras contratos y fuentes posteriores contienen su integración. El código y los contratos fechados posteriores prevalecen para una tarea técnica, con verificación física separada.
- La presencia de MapLibre, WorkManager, Laravel/Sanctum y dashboards en el árbol demuestra implementación, no configuración de producción ni validación integral.
- Las nuevas denominaciones estratégicas coexisten con identificadores y textos técnicos históricos. Renombrarlos sin un plan rompería BLE, API, Android, documentación de pruebas y posiblemente datos existentes.
- Cambios locales sin seguimiento en UI y documentos de Work requieren coordinación; este documento no los absorbe ni los sobrescribe.
- Un resultado sintético, una compilación o GPS real no valida el detector IMU ni permite transformar `SIMULATED` en `SENSOR`.

## 13. Hoja de ruta técnica propuesta

| Prioridad | Acción | Condición de salida |
| --- | --- | --- |
| P1 | Diagnóstico y validación IMU | Identidad/configuración leídas, escalas/orientación comprobadas y ensayos físicos documentados. |
| P2 | Integración extremo a extremo | Evidencia reproducible de persistencia, sync, GPS/BLE y manejo de fallos, sin cambiar el origen de eventos. |
| P3 | Preparación segura del VPS | Diseño y revisión autorizada de despliegue, secretos, TLS, copias y operación; sin inferir un despliegue existente. |
| P4 | Voz local | Decisión posterior a eventos confiables y evaluación de distracción, privacidad y compatibilidad offline. |
| P5 | Migración visual AVIGEM | Propuesta aprobada con inventario, impacto y plan de compatibilidad. |
| P6 | Indicadores avanzados y SDK/API | Viabilidad, valor, seguridad y acuerdos justificados antes de extraer o publicar componentes. |

## 14. Cambios que exigen aprobación expresa

- Habilitar detecciones físicas, origen `SENSOR`, alertas automáticas, calibraciones persistentes o cambios de umbrales/escala.
- Cambiar firmware, cableado, protocolo BLE, UUID, frecuencia, contratos de datos, API, modelos, migraciones o dependencias.
- Implementar TextToSpeech, SDK, nuevas integraciones, MQTT, cámaras, nuevos sensores, IA generativa o servicios facturados.
- Renombrar paquetes, `applicationId`, endpoints, base de datos, repositorio, clases o interfaces existentes para aplicar marca.
- Acceder, desplegar o modificar el VPS, credenciales, DNS, bases de datos, información de usuarios o costos reales.
- Publicar software, marca, resultados de investigación o afirmaciones de precisión, seguridad, ahorro o impacto.

## 15. Cierre de esta actualización

Esta alineación integra a Vialigent Technologies y AVIGEM como contexto estratégico sin convertir hipótesis en funcionalidades. Se preservaron todos los contratos y cambios ajenos observados. No se ejecutaron migraciones, pruebas físicas, compilaciones, despliegues, cambios de credenciales, commit, push ni merge.

Referencias: [decisiones estratégicas](DECISIONES_ESTRATEGICAS_2026-11-01.md), [contexto maestro](CONTEXTO_PROYECTO_IA.md), [Etapa 3B BLE](ETAPA_3B_BLE.md), [Etapa 3C](ETAPA_3C_PROCESAMIENTO.md), [GPS BLE](CONTRATO_GPS_BLE.md), [contrato Android-Laravel](CONTRATO_ANDROID_LARAVEL.md), `firmware/AsistenteVialImu/`, `mobile/app/` y `backend/`.
