# AVIGEM One Diagnostics — backend-spring-diagnostics

MVP del módulo de diagnóstico IMU con **Java 17 · Spring Boot 3.3 · Maven · Arquitectura Hexagonal · JPA · MySQL · Flyway**.

> **NOTA DE ETAPA**: En esta versión únicamente se aceptan `TEST_DATA`.  
> El backend asigna ese origen; el cliente no puede elegirlo.  
> Los valores RAW registrados **no representan mediciones físicas reales**.

---

## Requisitos

| Herramienta | Versión mínima |
|-------------|---------------|
| Java JDK    | 17 o superior |
| Maven       | 3.9+          |
| MySQL/MariaDB | MySQL 8.0+ o MariaDB compatible |

---

## Configuración MySQL

Crea la base de datos y un usuario local. Reemplaza el placeholder por una contraseña
propia; no guardes credenciales reales en el repositorio.

```sql
CREATE DATABASE avigem_diagnostics CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER '<TU_USUARIO>'@'localhost' IDENTIFIED BY '<TU_PASSWORD_SEGURA>';
GRANT ALL PRIVILEGES ON avigem_diagnostics.* TO '<TU_USUARIO>'@'localhost';
FLUSH PRIVILEGES;
```

Las tablas se crean automáticamente con **Flyway** al iniciar la aplicación.  
No se modifica ningún esquema existente de forma destructiva y no hay borrado en cascada
del historial.

---

## Configuración de la aplicación

Archivo: `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/avigem_diagnostics?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
    username: ${DB_USERNAME:}
    password: ${DB_PASSWORD:}
server:
  address: ${SERVER_ADDRESS:127.0.0.1}
  port: ${SERVER_PORT:8085}
```

Define las credenciales por entorno. Ejemplo en PowerShell:

```powershell
$env:DB_USERNAME = "<TU_USUARIO>"
$env:DB_PASSWORD = "<TU_PASSWORD_SEGURA>"
```

En Bash:

```bash
export DB_USERNAME='<TU_USUARIO>'
export DB_PASSWORD='<TU_PASSWORD_SEGURA>'
```

No hay usuario ni contraseña reales en el código ni en la documentación.

---

## Ejecución

```bash
cd backend-spring-diagnostics

# Compilar y ejecutar pruebas
mvn test

# Iniciar servidor
mvn spring-boot:run
```

La API queda disponible en `http://localhost:8085`.

Por seguridad, la aplicación escucha en `127.0.0.1` de forma predeterminada. No se
debe abrir el puerto 8085 a Internet: esta etapa no implementa autenticación.

---

## Despliegue interno en VPS (systemd)

Las plantillas están en `deploy/`. El procedimiento está pensado para
`/var/www/asistente-vial` y mantiene Diagnostics accesible únicamente desde el VPS.

### 1. Actualizar, probar y compilar

```bash
cd /var/www/asistente-vial
git status --short
git pull --ff-only origin main

cd backend-spring-diagnostics
mvn clean verify
```

Si `git status --short` muestra cambios, detente antes del `pull` para no mezclar
archivos modificados manualmente en el servidor.

Debe finalizar con `BUILD SUCCESS`. Java 17 es la versión objetivo; un JDK posterior
también puede ejecutar el artefacto compilado para Java 17.

### 2. Crear la base y las credenciales

Ejecuta como `root`. No copies el contenido final de `/etc/avigem-diagnostics.env`
en chats ni capturas.

```bash
DBPASS2=$(openssl rand -hex 16)
mariadb <<SQL
CREATE DATABASE IF NOT EXISTS avigem_diagnostics CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'avigem'@'localhost' IDENTIFIED BY '${DBPASS2}';
ALTER USER 'avigem'@'localhost' IDENTIFIED BY '${DBPASS2}';
GRANT ALL PRIVILEGES ON avigem_diagnostics.* TO 'avigem'@'localhost';
FLUSH PRIVILEGES;
SQL

install -m 600 deploy/avigem-diagnostics.env.example /etc/avigem-diagnostics.env
sed -i "s/REEMPLAZAR_PASSWORD/${DBPASS2}/" /etc/avigem-diagnostics.env
unset DBPASS2
```

La base es independiente de Laravel. Flyway aplica automáticamente V1, V2 y V3 al
primer arranque y se detiene si encuentra un esquema inesperado.

### 3. Instalar y arrancar el servicio

```bash
install -m 644 deploy/avigem-diagnostics.service /etc/systemd/system/avigem-diagnostics.service
systemctl daemon-reload
systemctl enable --now avigem-diagnostics
```

### 4. Verificar el resultado

```bash
systemctl status avigem-diagnostics --no-pager
journalctl -u avigem-diagnostics -n 60 --no-pager
curl -fsS http://127.0.0.1:8085/ | grep -F "AVIGEM Diagnostics"
curl -fsS "http://127.0.0.1:8085/api/v1/diagnostics/devices?page=0&size=1"
```

Las dos llamadas deben responder sin error. Si Flyway o MariaDB fallan, conserva el
servicio sin exposición pública y revisa el `journalctl` antes de continuar.

Para abrir la interfaz desde otra PC sin publicar el puerto:

```bash
ssh -L 8085:127.0.0.1:8085 root@IP_DEL_VPS
```

Luego abre `http://localhost:8085` en el navegador local.

### Actualizaciones posteriores

```bash
cd /var/www/asistente-vial
git pull --ff-only origin main
cd backend-spring-diagnostics
mvn clean verify
systemctl restart avigem-diagnostics
systemctl status avigem-diagnostics --no-pager
```

---

## Endpoints implementados

**Base:** `/api/v1/diagnostics`

### Dispositivos

| Método | URL | Descripción |
|--------|-----|-------------|
| `POST` | `/devices` | Registrar dispositivo |
| `GET` | `/devices?page=0&size=20` | Listar dispositivos paginados |
| `GET` | `/devices/{deviceId}` | Obtener dispositivo por id |

### Sesiones

| Método | URL | Descripción |
|--------|-----|-------------|
| `POST` | `/devices/{deviceId}/sessions` | Iniciar sesión |
| `POST` | `/sessions/{sessionId}/samples` | Agregar lote de muestras IMU |
| `POST` | `/sessions/{sessionId}/complete` | Completar sesión y calcular estadísticas |
| `POST` | `/sessions/{sessionId}/cancel?reason=...` | Cancelar sesión |
| `GET` | `/sessions/{sessionId}` | Detalle + resumen estadístico |
| `GET` | `/devices/{deviceId}/sessions?page=0&size=20` | Historial paginado |

---

## Reglas de estado y resultado

- `SessionStatus`: `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.
- `SessionResult`: `PENDING`, `PASSED`, `OBSERVED`, `FAILED`, `NOT_EVALUATED`.
- Una sesión cancelada queda con `status = CANCELLED` y `result = NOT_EVALUATED`.
- Una sesión `TEST_DATA` completada **con una o más muestras** queda con `result = OBSERVED`; **nunca** `PASSED`.
  El campo `resultNote` indica que solo se verificó el flujo de software.
- Una sesión completada **sin muestras** queda con `status = COMPLETED` y `result = NOT_EVALUATED`.
  No se crea `DiagnosticSummary` y `resultNote` indica “Sin datos suficientes para evaluar la sesión”.
- El motivo de cancelación se guarda en `cancellationReason` y **no sobrescribe** `notes`.
- `dataOrigin` siempre es `TEST_DATA` y lo asigna el backend.

---

## Ejemplos de peticiones

### 1. Registrar dispositivo

```bash
curl -X POST http://localhost:8085/api/v1/diagnostics/devices \
  -H "Content-Type: application/json" \
  -d '{"deviceCode": "AVIGEM-TEST-001", "description": "Dispositivo de prueba TEST_DATA"}'
```

Respuesta `201`:

```json
{
  "id": 1,
  "deviceCode": "AVIGEM-TEST-001",
  "description": "Dispositivo de prueba TEST_DATA",
  "registeredAt": "2026-11-04T13:00:00Z"
}
```

Un código duplicado, incluso con espacios alrededor, responde `409 CONFLICT`.

### 2. Listar dispositivos

```bash
curl "http://localhost:8085/api/v1/diagnostics/devices?page=0&size=10"
```

Respuesta `200` (estructura paginada):

```json
{
  "content": [
    {
      "id": 1,
      "deviceCode": "AVIGEM-TEST-001",
      "description": "Dispositivo de prueba TEST_DATA",
      "registeredAt": "2026-11-04T13:00:00Z"
    }
  ],
  "page": {
    "size": 10,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 3. Iniciar sesión

```bash
curl -X POST http://localhost:8085/api/v1/diagnostics/devices/1/sessions \
  -H "Content-Type: application/json" \
  -d '{"notes": "Sesión de diagnóstico TEST_DATA #1"}'
```

Respuesta `201`:

```json
{
  "id": 1,
  "deviceId": 1,
  "status": "IN_PROGRESS",
  "result": "PENDING",
  "dataOrigin": "TEST_DATA",
  "notes": "Sesión de diagnóstico TEST_DATA #1",
  "cancellationReason": null,
  "resultNote": null,
  "startedAt": "2026-11-04T13:00:01Z",
  "finishedAt": null
}
```

### 4. Agregar muestras TEST_DATA

```bash
curl -X POST http://localhost:8085/api/v1/diagnostics/sessions/1/samples \
  -H "Content-Type: application/json" \
  -d '{
    "samples": [
      {"axRaw": 1024, "ayRaw": 512, "azRaw": -256, "gxRaw": 300, "gyRaw": -100, "gzRaw": 50},
      {"axRaw": 1100, "ayRaw": 480, "azRaw": -300, "gxRaw": 280, "gyRaw": -120, "gzRaw": 60},
      {"axRaw":  980, "ayRaw": 530, "azRaw": -220, "gxRaw": 310, "gyRaw":  -90, "gzRaw": 45}
    ]
  }'
```

> Los valores están en RAW int16 (`-32768` a `32767`). No se aplica ninguna escala ni conversión.
> Un elemento `null` dentro de `samples` responde `400 VALIDATION_ERROR`.

### 5. Completar sesión

```bash
curl -X POST http://localhost:8085/api/v1/diagnostics/sessions/1/complete
```

Respuesta `200`:

```json
{
  "id": 1,
  "deviceId": 1,
  "status": "COMPLETED",
  "result": "OBSERVED",
  "dataOrigin": "TEST_DATA",
  "resultNote": "Solo se verificó el flujo de software con TEST_DATA; no representa una medición física real.",
  "finishedAt": "2026-11-04T13:00:30Z"
}
```

Si la sesión no tiene muestras, la respuesta es `COMPLETED` + `NOT_EVALUATED`, sin `summary`:

```json
{
  "id": 2,
  "deviceId": 1,
  "status": "COMPLETED",
  "result": "NOT_EVALUATED",
  "dataOrigin": "TEST_DATA",
  "resultNote": "Sin datos suficientes para evaluar la sesión",
  "finishedAt": "2026-11-04T13:01:00Z"
}
```

### 6. Cancelar sesión

```bash
curl -X POST "http://localhost:8085/api/v1/diagnostics/sessions/1/cancel?reason=Prueba+interrumpida"
```

Respuesta `200`:

```json
{
  "id": 1,
  "status": "CANCELLED",
  "result": "NOT_EVALUATED",
  "dataOrigin": "TEST_DATA",
  "notes": "Sesión de diagnóstico TEST_DATA #1",
  "cancellationReason": "Prueba interrumpida"
}
```

El motivo no puede superar 500 caracteres.

### 7. Consultar detalle con estadísticas

```bash
curl http://localhost:8085/api/v1/diagnostics/sessions/1
```

Respuesta `200`:

```json
{
  "id": 1,
  "deviceId": 1,
  "status": "COMPLETED",
  "result": "OBSERVED",
  "dataOrigin": "TEST_DATA",
  "resultNote": "Solo se verificó el flujo de software con TEST_DATA; no representa una medición física real.",
  "summary": {
    "sampleCount": 3,
    "ax": {"min": 980, "max": 1100, "avg": 1034.67, "std": 49.57},
    "ay": {"min": 480, "max": 530, "avg": 507.33, "std": 20.68},
    "az": {"min": -300, "max": -220, "avg": -258.67, "std": 32.71},
    "gx": {"min": 280, "max": 310, "avg": 296.67, "std": 12.47},
    "gy": {"min": -120, "max": -90, "avg": -103.33, "std": 12.47},
    "gz": {"min": 45, "max": 60, "avg": 51.67, "std": 6.24},
    "computedAt": "2026-11-04T13:00:30Z"
  }
}
```

### 8. Historial paginado

```bash
curl "http://localhost:8085/api/v1/diagnostics/devices/1/sessions?page=0&size=10"
```

---

## Estructura del proyecto

```
backend-spring-diagnostics/
├── .gitignore                    # ignora target/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/avigem/diagnostics/
    │   │   ├── DiagnosticsApplication.java
    │   │   ├── config/
    │   │   ├── domain/
    │   │   │   ├── model/          # Entidades de dominio, PageQuery, PageResult,
    │   │   │   │                   # DataOrigin, excepciones e ImuStatsCalculator
    │   │   │   └── port/
    │   │   │       └── out/        # Puertos de salida (repositorios)
    │   │   ├── application/
    │   │   │   ├── dto/            # DTOs de request/response
    │   │   │   ├── port/
    │   │   │   │   └── in/         # Puerto de entrada (DiagnosticsUseCase)
    │   │   │   └── usecase/        # DiagnosticsService
    │   │   └── infrastructure/
    │   │       ├── persistence/    # Entidades JPA, repos Spring Data, mappers, adaptadores
    │   │       └── web/            # Controlador REST + GlobalExceptionHandler
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           ├── V1__initial_schema.sql
    │           ├── V2__add_cancellation_reason.sql
    │           └── V3__add_data_origin.sql
    └── test/
        ├── java/com/avigem/diagnostics/
        │   ├── domain/             # ImuStatsCalculatorTest (unitario puro)
        │   ├── application/usecase/ # DiagnosticsServiceTest + integración H2
        │   └── infrastructure/web/ # DiagnosticsControllerTest (MockMvc)
        └── resources/
            └── application.yml     # H2 + Flyway para tests
```

---

## Decisiones técnicas

| Decisión | Justificación |
|----------|--------------|
| Arquitectura hexagonal | Dominio aislado; `domain` no depende de Spring Data ni de DTOs de aplicación |
| `PageQuery` / `PageResult` propios | Paginación sin filtrar `Page`/`Pageable` de Spring hacia el dominio |
| `short` / `SMALLINT` para RAW | Fiel al rango int16 sin cast silencioso |
| Validación `requireRaw()` | Rechazo explícito de nulos y valores fuera de rango |
| Flyway en lugar de `ddl-auto=create` | Migraciones versionadas y no destructivas |
| `ddl-auto=validate` en producción | Garantiza coherencia sin modificar esquema |
| H2 en modo MySQL para tests | Tests rápidos sin MySQL real |
| Std poblacional (÷N) | Estándar para sensores; sin sesgo de muestra |
| Sin DELETE en cascada | Preservación histórica obligatoria |
| `TEST_DATA` asignado por backend | El cliente no puede declarar un origen distinto |
| `OBSERVED` para sesiones TEST_DATA completadas con muestras | Solo se verificó el flujo de software, no una medición física |
| `NOT_EVALUATED` sin muestras | No hay datos suficientes y no se crea `DiagnosticSummary` |
| `NOT_EVALUATED` al cancelar | Separa el estado de sesión del resultado técnico |
| `cancellation_reason` separado | No se pierden las notas originales |
| `open-in-view=false` | Evita consultas implícitas durante la renderización |

---

## Pendientes / Próxima etapa

- [ ] Autenticación (JWT o API Key) cuando se requiera
- [ ] Integración BLE real (firmware AVIGEM One)
- [ ] Umbrales configurables para clasificar resultados técnicos
- [ ] Paginación de muestras individuales
- [ ] Exportación de sesiones (CSV / JSON)
- [ ] Métricas (Micrometer/Actuator)
- [ ] Pruebas de migración contra MySQL real
- [ ] Idempotencia/bloqueo optimista para operaciones concurrentes
