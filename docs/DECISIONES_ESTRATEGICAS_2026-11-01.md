# Vialigent Technologies / AVIGEM — decisiones estratégicas

Decisiones del 31/10 y 01/11/2026. Consolidación documental: 01/11/2026.
Fuente de decisiones: actualización expresa del responsable del proyecto. No autoriza implementar nuevas capacidades ni acredita disponibilidad legal de marcas.

## 1. Antecedentes y problema original

El proyecto nació de la dificultad de propietarios y administradores para conocer cómo se conducen sus vehículos cuando los operan terceros. La falta de información sobre hábitos de conducción dificulta la supervisión del uso del vehículo y de los riesgos operativos. Esta motivación empresarial se conserva.

Título académico histórico: «Desarrollo de un asistente inteligente de conducción para reducir maniobras peligrosas en vehículos de transporte en Arequipa». Se conserva en los entregables originales, sin modificar retroactivamente lo presentado. El proyecto vincula Innovación y Emprendimiento, Tecnologías Emergentes y desarrollo tecnológico Android/Laravel/IoT.

## 2. Evolución estratégica

Tres pilares: seguridad vial, gestión vehicular responsable y movilidad urbana. Proteger la inversión empresarial y considerar a pasajeros, peatones y ciclistas son propósitos compatibles. No se limita el producto a autobuses o pasajeros: incluye taxis, alquiler operado por terceros y transporte de personal.

Propuesta consolidada: «AVIGEM es una solución inteligente de asistencia y análisis de conducción urbana, desarrollada por Vialigent Technologies, que busca identificar maniobras potencialmente peligrosas y convertir las mediciones del vehículo en información útil para los conductores y administradores de flotas, contribuyendo a la gestión responsable de los vehículos y a una movilidad más segura».

Esta formulación expresa objetivos; no certifica detección física ni reducción de accidentes.

## 3. Identidad elegida

- **VIALIGENT TECHNOLOGIES:** identidad de trabajo de la desarrolladora; futura empresa tecnológica, sin afirmar constitución ni registro.
- **AVIGEM:** producto principal.
- **AVIGEM App:** Android para conductores.
- **AVIGEM Fleet:** plataforma empresarial.
- **AVIGEM One:** nombre provisional del primer módulo IoT.
- **AVIGEM Connect:** posibilidad futura para integración; no aprobado como producto.

No se requieren marcas independientes para cada componente. Indecopi y Sunarp siguen pendientes; no se garantiza disponibilidad comercial o legal. Esta decisión no renombra paquetes, rutas, repositorio ni el nombre BLE técnico vigente.

## 4. Significado y eslogan

**AVIGEM: Asistente Vial Inteligente para la Gestión y Evaluación de Maniobras.**

Eslogan: **«Inteligencia en cada maniobra».**

Descripción comercial de trabajo: «AVIGEM es un copiloto digital inteligente que integra sensorización, asistencia al conductor y análisis de maniobras para apoyar una conducción más responsable y una mejor supervisión de flotas urbanas». La asistencia y el análisis descritos deben presentarse con sus límites experimentales actuales.

## 5. Público objetivo y beneficiarios

Cliente B2B: propietarios, empresas y administradores de pequeñas y medianas flotas. Segmento prioritario por validar: vehículos conducidos por terceros, especialmente taxis y transporte urbano de Arequipa. Usuario operativo: conductor. Beneficiarios indirectos: pasajeros, peatones, ciclistas y otros usuarios de la vía.

La experiencia del pasajero amplía la investigación, sin sustituir al cliente empresarial. Tamaño del mercado, disposición de pago y rentabilidad no están validados.

## 6. Competencia y referencias

[Onway de Entel Digital](https://www.enteldigital.cl/productos/onway) presenta gestión de flotas, monitoreo y módulos operativos. Es una referencia de oferta comercial existente, no una comparación técnica independiente. AVIRU se conserva como referencia regional mencionada por el equipo: falta identificar su fuente oficial y verificar su alcance antes de compararlo.

AVIGEM no se presentará como primera solución, tecnología exclusiva ni alternativa más barata, precisa o eficaz sin evidencia. El análisis comparativo futuro debe registrar fuente, fecha, segmento, funciones verificadas, precio comparable cuando exista y limitaciones de la comparación.

## 7. Diferenciadores propuestos por validar

Especialización urbana; adaptación a flotas pequeñas y medianas; información comprensible y accionable; retroalimentación responsable; análisis contextual de maniobras/vías; indicadores experimentales de brusquedad o comodidad; modularidad IoT/Android/web; integración con otras aplicaciones y costo total sostenible. Son hipótesis, no ventajas demostradas.

## 8. Design Thinking y nuevos instrumentos

Se amplía la investigación sin borrar antecedentes. Se preparan cuatro borradores de diez preguntas y una guía de entrevistas en archivos separados:

- [Conductores](ENCUESTA_CONDUCTORES_2026-11-01.md).
- [Propietarios y administradores](ENCUESTA_PROPIETARIOS_2026-11-01.md).
- [Pasajeros](ENCUESTA_PASAJEROS_2026-11-01.md).
- [Peatones, ciclistas y otros usuarios](ENCUESTA_USUARIOS_VIA_2026-11-01.md).
- [Guía y protocolo de entrevistas](GUIA_ENTREVISTAS_2026-11-01.md).

Los borradores no han sido aplicados. Preguntas neutrales, situaciones vividas, participación voluntaria y opción de omitir respuestas. Separar hipótesis, fuentes documentales, respuestas reales y conclusiones sustentadas; no inventar encuestados ni extrapolar muestras por conveniencia a toda Arequipa.

## 9. ODS

Principal: **ODS 3, Salud y Bienestar, meta 3.6**, relacionada con muertes y lesiones de tránsito. Complementario: **ODS 11, Ciudades y Comunidades Sostenibles, meta 11.2**, transporte seguro, accesible y sostenible. Vinculación conceptual, sin impacto cuantificado propio.

Fuentes primarias: [ONU, ODS 3](https://sdgs.un.org/goals/goal3) y [ONU, ODS 11](https://sdgs.un.org/goals/goal11). La formulación original de 3.6 incluye una meta global con plazo 2020; no debe reescribirse como logro de AVIGEM ni trasladar sus porcentajes al prototipo.

## 10. Arquitectura conservada

ESP32 + IMU + GPS → BLE → Android Kotlin/Compose → Room/SQLite → WorkManager/API REST → Laravel 12/Sanctum → dashboards.

NEO-6M, buzzer, mapas e historial forman parte del desarrollo existente. `/admin`: platform_admin sin empresa; `/dashboard`: administración empresarial; Android: conductor asociado a empresa. Se conservan autenticación, aislamiento, idempotencia y contratos.

No se autorizan MQTT, cámaras, otros sensores, cambio de tecnologías o reconstrucción. VPS Elástika adquirido según el responsable; despliegue concluido no comprobado.

## 11. Voz local propuesta

Android TextToSpeech con voces locales compatibles es la opción propuesta para avisos breves configurables, desconexión, maniobras verificadas y resumen del recorrido. No está autorizada su implementación. Evaluar disponibilidad offline real en cada dispositivo, distracción, frecuencia, consentimiento y configuración. La detección debe proceder de procesamiento local validado, no de generación de riesgos mediante texto.

## 12. Sin dependencia inicial de Gemini

La primera versión comercial no dependerá de Gemini API. Se busca controlar costos variables, conectividad y sostenibilidad. IA generativa queda como posibilidad futura, no requisito del MVP. En web se priorizan estadísticas y ayuda contextual; no se autoriza chatbot de pago. Una herramienta local también tiene costos de integración, mantenimiento y evaluación.

## 13. Integración SDK/API futura

Se consideran AVIGEM independiente (IoT + App + Fleet) e integrado (IoT + SDK/API + plataforma). Un futuro SDK Android podría incorporar BLE, procesamiento y sincronización a una aplicación anfitriona. Una API REST por sí sola no conecta el teléfono por BLE.

No existe SDK ni acuerdos confirmados con Taxitel o Aló 45; no hay autorización de desarrollo. Evaluar primero compatibilidad, privacidad, permisos, batería y restricciones de segundo plano. AVIGEM Connect sigue siendo solo una denominación posible.

## 14. Modelo de negocio sostenible

Hipótesis B2B: dispositivo IoT más suscripción por vehículo o flota. Estimar con evidencia componentes, ensamblaje, instalación, VPS, almacenamiento, comunicaciones, soporte, mantenimiento, reemplazos y comercialización. Calcular después margen y equilibrio bajo escenarios explícitos de demanda y costos. No se fijan precios, ganancias ni ahorros ficticios.

## 15. Implementación, evidencia y límites

Revisión local de documentación y archivos el 01/11/2026, sin nuevas compilaciones ni pruebas físicas:

- Código existente: BLE IMU/GPS, persistencia y sincronización, mapas, gestión empresarial/global, monitoreo técnico de dispositivos y soporte. Existencia de código no equivale a validación de cada flujo en producción.
- Validación física reportada por el responsable: transporte IMU real a Android, coordenadas GPS reales y evento SIMULATED georreferenciado recibido en Laravel; buzzer independiente sonó manualmente.
- Principal incluye buzzer manual mediante `ManualBuzzer.h`; su convivencia física con IMU/GPS/BLE no se da por comprobada por la prueba independiente.
- 3C experimental: conversión nominal, gráficas, calibración transitoria y detector sintético TEST_ONLY. Identidad `WHO_AM_I = 0x75` no confirmada; observación aproximada de 0.943 g pendiente de explicación.
- SIMULATED significa prueba incluso con coordenadas reales. SENSOR no representa detecciones físicas validadas y no se habilita por esta actualización.
- No hay evidencia de reducción de accidentes, lesiones, costos o precisión certificada. Alertas automáticas, calibración física y piloto permanecen pendientes.

Fuentes locales: [contexto maestro](CONTEXTO_PROYECTO_IA.md), [checkpoint](CHECKPOINT_2026_10_10.md), [3B](ETAPA_3B_BLE.md), [3C](ETAPA_3C_PROCESAMIENTO.md), [GPS BLE](CONTRATO_GPS_BLE.md), [API](CONTRATO_ANDROID_LARAVEL.md), [mapas](MAPA_INCIDENCIAS.md) y [dispositivos](MONITOREO_DISPOSITIVOS.md).

## 16. Decisiones, hipótesis y pendientes

Firmes: identidad interna elegida, tres pilares, cliente B2B, arquitectura conservada, límites de evidencia y ausencia de dependencia inicial de Gemini. No equivalen a registro legal ni aprobación de nuevas funciones.

Hipótesis: segmento prioritario, diferenciación urbana, disposición de pago, accesibilidad económica, utilidad de voz y conveniencia de SDK/API.

Pendientes: disponibilidad legal; fuente de AVIRU; investigación real; costos; identidad/calibración IMU; validación de maniobras y falsos positivos; pruebas de integración; situación del VPS. No se asume cierre de estas tareas.

## 17. Próximos pasos priorizados

1. Confirmar identidad legal disponible y revisar instrumentos de investigación antes de aplicarlos.
2. Recoger evidencia de problemas y necesidades del segmento prioritario; conservar respuestas y límites metodológicos.
3. Resolver identificación/escalas del IMU y planificar validación física controlada antes de habilitar SENSOR o alertas.
4. Verificar convivencia de buzzer manual, IMU, GPS y BLE; documentar resultados reales.
5. Estimar costos y contrastar disposición de pago; evaluar despliegue y privacidad por separado.
6. Solo con autorización posterior, priorizar voz local o estudio de integración SDK/API.
