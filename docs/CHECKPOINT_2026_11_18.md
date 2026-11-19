# Checkpoint AVIGEM del 18/11/2026

## Cambios integrados

El parche administrativo v2 conserva el registro de empresa en cuatro pasos y elimina el perfil del administrador global. Agrega accesos de nueva empresa, códigos de activación cortos, enlaces para compartirlos manualmente por WhatsApp y SMS y restablecimiento de contraseña para administradores empresariales activos. La contraseña vigente continúa funcionando hasta consumir el código. Los códigos se almacenan mediante SHA-256, vencen a las 24 horas y son de un solo uso. Se mantiene la compatibilidad con códigos hexadecimales antiguos y se normalizan espacios, guiones y mayúsculas.

La interfaz Android v2 incorpora navegación inferior, resumen del recorrido, cronómetro, confirmación de finalización, mapa completo y acceso al modo de presentación. La generación manual de maniobras se retiró de la pantalla del recorrido. El repositorio conserva los métodos usados por pruebas. Room utiliza el esquema 5 y las migraciones 1→2→3→4→5 están registradas también en los tres tests señalados en la auditoría.

Las rutas API que consumen TomTom requieren Sanctum y el contexto de empresa. El mapa Fleet utiliza rutas equivalentes protegidas por sesión web y rol empresarial. El catálogo OSM continúa con su ruta y límite de frecuencia existentes, y Android conserva su acceso a ese catálogo. Los tiles utilizan caché privada del navegador. La caché de rutas agrupa coordenadas a cuatro decimales y los registros de fallos incluyen estados HTTP o clases de excepción, evitando claves, cuerpos de respuesta y URLs del proveedor.

Se retiró `backend/composer.phar`, se agregó su exclusión y se trasladó el informe de TomTom a `docs/`. Los cuatro archivos de parche se eliminaron después de integrar v2. El README del laboratorio Spring declara su archivo y sus limitaciones. El firmware principal cuenta con una guía de preparación y validación.

## Compatibilidad con MariaDB

La ejecución con MariaDB 10.4.32 detectó que la regla de empresa y rol de usuarios solo tenía restricciones en SQLite. La migración `2026_11_18_000001_enforce_mysql_user_company_role.php` incorpora la misma regla en MySQL y MariaDB mediante triggers. Se aplica a nuevas escrituras y actualizaciones y conserva los registros existentes.

La prueba concurrente utiliza una base SQLite temporal con procesos secundarios. Se corrigió su conexión principal para que utilice esa misma base incluso al ejecutar el resto de la suite con MariaDB.

Al publicar el backend, ejecutar sus migraciones pendientes para incorporar la nueva restricción. Esta revisión la aplicó en las bases temporales de prueba. El despliegue y la aplicación de migraciones en los entornos reales quedan pendientes.

## Verificaciones

- Laravel completo en SQLite temporal en memoria, 147 pruebas y 1420 aserciones aprobadas.
- Laravel completo en MariaDB temporal, 147 pruebas y 1420 aserciones aprobadas. Instancia aislada en el puerto 33317 y base `avigem_audit_test`.
- Android `assembleDebug` aprobado y APK generado.
- Android `testDebugUnitTest` aprobado, 111 pruebas, con comprobación de compatibilidad de códigos de activación.
- Android `compileDebugAndroidTestKotlin` y empaquetado del APK instrumentado aprobados.
- Emulador Android, tres pruebas de navegación y las pruebas de recorrido y activación aprobadas. Se actualizaron las rutas de navegación de los tests y el paso de confirmación de finalización.
- Emulador Android, dos pruebas de migración aprobadas desde bases antiguas, conservando registros, UUID y payloads pendientes. En total se verificaron siete casos instrumentados seleccionados.

La ejecución instrumentada de Gradle en modo offline encontró complementos UTP ausentes de la caché. Los APK instrumentados compilados se instalaron en el emulador y se ejecutaron directamente con AndroidJUnitRunner.

PHPUnit se ejecutó directamente con el autoload local y las variables de entorno de pruebas, debido al fallo del lanzador estándar al abrir su bootstrap en este entorno. Los servicios TomTom se simularon en las pruebas. Los servidores HTTP de las pruebas Android utilizan la dirección local explícita `127.0.0.1` para evitar depender del nombre del equipo. HTTP IPv6 de loopback se admite en debug.

## Detección SENSOR pendiente

La generación de eventos SENSOR durante recorridos requiere criterios y umbrales definidos por el equipo y evidencia física. El flujo de presentación usa TEST_ONLY y los eventos de prueba se identifican como SIMULATED.

En Android se requiere conectar las lecturas IMU con el detector aprobado y el recorrido activo, guardar el evento y su UUID mediante una transacción Room junto a su entrada de outbox, y habilitar ese origen en `OutboxWriter`. Deben preservarse procedencia, instante, calidad GPS, separación temporal de eventos y consistencia de reintentos. `SyncEngine` requiere pruebas de compatibilidad y reenvío idempotente con ese payload. También debe revisarse el filtro de eventos visibles y del mapa.

En Laravel, `EventWriteRequest` reconoce el enum de origen, mientras `TripSync` rechaza las escrituras SENSOR mediante `origin_not_enabled`. Se requiere acordar y validar el contrato de sensor, dispositivo, versión de criterios y evidencias de detección, autorizar su asociación al viaje y empresa y verificar idempotencia y cálculo de alertas por origen. Esa habilitación queda pendiente.

## Comprobaciones externas pendientes

La revisión visual completa del panel, la voz en teléfono, los datos TomTom en vivo, la identidad y calibración del IMU y las mediciones de flash y heap requieren sus entornos correspondientes. El sketch principal actual no incluye ni inicia `MqttTelemetry.h`. La demostración MQTT independiente se mantiene en `firmware/DiagnosticoMqttImu/`.

Las encuestas y entrevistas del repositorio son instrumentos y guías. La existencia de respuestas fuera del repositorio requiere evidencia del responsable. La validación académica debe asociar capturas y resultados a cada función efectivamente probada.
