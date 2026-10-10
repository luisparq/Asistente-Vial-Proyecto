# Mapa empresarial de incidencias

URL: `/dashboard/map`, acceso fleet_admin existente. Sin cambios de datos, permisos, API, Android o firmware.

MapLibre GL JS 5.17.0, JS/CSS/licencia locales en `backend/public/vendor/maplibre` (incluidos selectivamente por .gitignore). Cartografía vectorial OpenStreetMap mediante OpenFreeMap Liberty: sin registro ni API key. Atribución automática de MapLibre/proveedor conservada. Solo se solicitan mapas; no se envía la colección de eventos al proveedor. Las peticiones de teselas pueden revelar la zona visible; el usuario autorizó expresamente la prueba con eventos existentes.

Configuración opcional en entorno local (sin versionar .env):
- `MAP_STYLE_URL`: URL de estilo, por defecto `https://tiles.openfreemap.org/styles/liberty`.
- `MAP_ALLOWED_ORIGINS`: orígenes HTTPS separados por comas que usa ese estilo (teselas, sprites, glyphs); por defecto `https://tiles.openfreemap.org`.
- Si existe caché de configuración, regenerarla tras cambiar el proveedor. Internet/WebGL necesarios; el error de carga se muestra sin inventar cartografía. No hay precarga masiva ni función offline de mapas.

Referencias de uso: https://openfreemap.org/quick_start/ ; https://openfreemap.org/ ; https://maplibre.org/maplibre-gl-js/docs/examples/heatmap-layer/

La consulta parte de la empresa autenticada antes de filtrar/serializar. Excluye coordenadas null/fuera de rango y viajes ajenos. Fechas se filtran como días inclusivos de America/Lima y se consultan en UTC. Filtros de tipo y origen reutilizan enums. Vehículos se muestran solo si tienen asociación directa válida con viajes/eventos de la empresa; no se deducen de asignaciones actuales ni de conductores. No se habilitan escrituras SENSOR.

Marcadores SIMULATED en ámbar, futuros SENSOR diferenciados como reservados/no validados. Heatmap con peso uniforme por evento: concentración visual relativa al zoom, no riesgo vial ni frecuencia normalizada por viajes. Funciona con un solo evento. Selección por punto o desplegable muestra el dato original almacenado (evita aproximación geométrica de teselas) y enlace al recorrido. Sin datos: aviso explícito. Diseño responsive, contador y leyenda.

Archivos: nueva config/maps.php, IncidentMapController.php, IncidentMapRequest.php, vista dashboard/map.blade.php, public/js/incident-map.js, public/css/incident-map.css y assets MapLibre; rutas web, navegación/stacks de layout, CSP solo para esta página en DashboardHeaders, dos pruebas en DashboardTest, .gitignore y este documento.

Verificación: 2 pruebas Laravel aprobadas, 19 aserciones (filtros fecha/tipo/vehículo, exclusión de coordenadas inválidas, aislamiento, acceso y CSP). Chrome headless con vista temporal solo lectura de registros existentes: teselas OpenFreeMap cargadas, 1 evento real almacenado de origen SIMULATED, alternancia marcadores/calor, selección del punto, coordenadas exactas y revisión visual móvil. Sin errores JS. La vista temporal usa render local; autenticación HTTP y aislamiento fueron comprobados con pruebas Laravel, no con credenciales reales. No se alteraron registros ni se crearon identidades en la base local. Temporales de verificación permanecen ignorados en storage/framework/testing.
