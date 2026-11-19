# Informe de Integración: API de TomTom, Voz TTS y Frases Concientizadoras en AVIGEM

> Informe de implementación del 16/11. Consulte [el checkpoint vigente](CHECKPOINT_2026_11_18.md) para las verificaciones y los pendientes.

**Fecha:** 15 de noviembre de 2026  
**Estado:** Implementado y Verificado  

---

## 1. Resumen Ejecutivo
Se completó la integración de los servicios de movilidad inteligente mediante la API de **TomTom** en el backend de Laravel, la personalización de la síntesis de voz (TTS) con un perfil masculino natural, el saludo de bienvenida al iniciar trayecto y un sistema de base de datos local con rotación de frases concientizadoras para la aplicación Android.

---

## 2. Cambios Implementados en el Backend (Laravel)

### 2.1. Configuración de Credenciales
- La clave de API se configura localmente en el archivo `.env`:
  ```env
  TOMTOM_TRAFFIC_KEY=TU_CLAVE_TOMTOM
  ```
- La revisión del 18/11 utiliza respuestas simuladas mediante `Http::fake`. La comprobación en vivo para **Arequipa** queda pendiente de evidencia.

### 2.2. Nuevo Servicio: `TomTomService.php`
Ubicación: `backend/app/Services/TomTomService.php`
- **Geocodificación Inversa (`reverseGeocode`):** Traduce coordenadas GPS (`lat, lon`) a dirección legible (calle, distrito, ciudad) con caché automático de 7 días.
- **Enrutamiento Dinámico con Tráfico (`calculateRoute`):** Calcula distancia, duración, tiempo de demora por congestión (`trafficDelaySeconds`), hora estimada de llegada (`eta`) y vector de coordenadas.
- **Flujo de Tráfico por Segmento (`flowSegment`):** Consulta la velocidad real de circulación vs. velocidad libre y cierres viales en un punto específico.
- **Puntos de Auxilio y Emergencia (`emergencyPois`):** Búsqueda en radio de 5 km de hospitales (`7321`), comisarías (`7322`), estaciones de servicio/grifos (`7311`) y talleres mecánicos (`7310`).

### 2.3. Endpoints REST API Expuestos
Controlador: `backend/app/Http/Controllers/RoadAssistanceController.php`  
Rutas en `backend/routes/api.php`:
| Método | Endpoint | Parámetros | Descripción |
|---|---|---|---|
| `GET` | `/api/v1/road/reverse-geocode` | `lat`, `lon` | Obtiene dirección formal de una coordenada GPS |
| `GET` | `/api/v1/road/route` | `origin`, `destination` | Ruta óptima con demoras por tráfico y ETA |
| `GET` | `/api/v1/road/traffic-flow-segment` | `lat`, `lon` | Velocidad actual y demora en un tramo vial |
| `GET` | `/api/v1/road/emergency-pois` | `lat`, `lon`, `type` | Puntos de auxilio (hospital, police, fuel, repair, all) |

### 2.4. Pruebas Automatizadas
Ubicación: `backend/tests/Feature/RoadAssistanceTest.php`
- Cobertura completa de los 4 nuevos endpoints con `Http::fake`.
- **Resultado:** 4 tests, 17 aserciones pasando al 100% (`OK (4 tests, 17 assertions)`).

---

## 3. Cambios Implementados en la Aplicación Móvil (Android)

### 3.1. Síntesis de Voz Masculina Agradable
Archivos: `RoadAlertPanel.kt` y `DiagnosticScreen.kt`
- Filtro inteligente en el catálogo del motor TTS del dispositivo para buscar voces masculinas en español (`male`, o voces nativas de Google `-eed-` y `-sfb-`).
- Calibración acústica:
  - **Tono (`pitch`):** Ajustado a `0.88f` (timbre masculino cálido, sobrio y natural).
  - **Velocidad (`speechRate`):** Ajustada a `0.98f` (cadencia clara para conducción).
- Soporte multirregional: Fallback automático a `es-419` y `es-ES` si el paquete específico de Perú (`es-PE`) no está instalado en el teléfono.

### 3.2. Saludo de Inicio de Recorrido
Archivo: `HomeScreen.kt`
- Al pulsar el botón **«Iniciar recorrido»**, el asistente reproduce automáticamente por TTS el mensaje de bienvenida y acompañamiento:
  > *«Iniciando recorrido. Conduce con precaución, AVIGEM cuidará tu viaje.»*

### 3.3. Base de Datos Local y Frases Concientizadoras Rotativas
Ubicación: `mobile/app/src/main/java/com/tecsup/asistente/conduccion/data/local/AwarenessPhrasesRepository.kt`
- **Estructura SQLite Local (`awareness_phrases.db`):**
  - Tabla: `awareness_phrases` con columnas `id`, `event_type`, `phrase`, `last_used_at`, `use_count`.
- **Catálogo de Frases Versionado en Git:**
  - **Frenado Brusco (`HARSH_BRAKING`):**
    1. *«Recuerda mantener tu distancia. Anticipar la frenada protege tu vida y la de los demás.»*
    2. *«Frenar con suavidad cuida a tus pasajeros y evita choques por alcance. Conduce con calma.»*
    3. *«Mira más allá del auto de adelante para reducir la marcha progresivamente sin sobresaltos.»*
  - **Aceleración Brusca (`HARSH_ACCELERATION`):**
    1. *«Acelerar de forma gradual optimiza el combustible y te da el control total de tu vehículo.»*
    2. *«Apurar la marcha en ciudad no ahorra tiempo, solo aumenta el riesgo. Mantén un ritmo seguro.»*
    3. *«Presiona el pedal con suavidad. Llegar a salvo siempre es la prioridad.»*
  - **Giro Brusco (`SHARP_TURN`):**
    1. *«Reduce la velocidad antes de entrar a la curva, no dentro de ella. Mantendrás la estabilidad.»*
    2. *«Toma los virajes con espacio y señaliza a tiempo. La suavidad en el volante evita derrapes.»*
    3. *«Evita maniobras repentinas al doblar. Tu estabilidad garantiza la seguridad de la vía.»*
  - **Inicio de Recorrido (`START_TRIP`):**
    1. *«Iniciando recorrido. Conduce con precaución, AVIGEM cuidará tu viaje.»*
- **Lógica de Rotación Dinámica:**  
  El método `getNextPhrase(eventType)` selecciona la frase con menor uso o con `last_used_at` más antiguo y actualiza su registro en SQLite, garantizando que el conductor no escuche el mismo mensaje repetido consecutivamente.
- **Integración:** Inyectado en `DiagnosticViewModel.kt` para complementar las detecciones de maniobras.

---

## 4. Instrucciones para Despliegue y Pruebas
1. **Backend:**
   ```bash
   cd backend
   php artisan config:clear
   php artisan route:list --path=api/v1/road
   ```
2. **Móvil:**
   - Compilar e instalar la app Android en el dispositivo.
   - Presionar **«Iniciar recorrido»** para verificar el saludo inicial por voz.
   - En la pantalla de diagnóstico/pruebas de maniobras, comprobar la rotación de las 3 frases concientizadoras y el nuevo timbre de voz.
