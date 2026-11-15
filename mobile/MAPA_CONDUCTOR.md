# Mi mapa (Android)

Pantalla opcional desde Inicio. Reutiliza los flujos BLE/GPS y Room existentes; no inicia conexiones ni modifica recorridos o sincronización.

- Turquesa: última posición GPS válida mientras BLE permanece conectado. Se elimina al caducar (ventana existente de 5 segundos), perder fix o desconectarse.
- Ámbar: eventos `SIMULATED` georreferenciados del recorrido activo. Tocarlos muestra tipo, fecha local y coordenadas guardadas. Son datos de prueba, no riesgos comprobados.
- «Centrar mi posición» utiliza exclusivamente GPS del ESP32, no ubicación del teléfono.

MapLibre Native Android 13.6.1; cartografía OpenStreetMap mediante OpenFreeMap Liberty, con atribución del proveedor visible en el mapa. Requiere Internet para cargar cartografía; los recorridos y el monitoreo siguen funcionando sin abrirlo.

Proveedor configurable al compilar mediante propiedad Gradle `mapStyleUrl`, con URL HTTPS de un estilo MapLibre compatible. Predeterminado: `https://tiles.openfreemap.org/styles/liberty`. No requiere claves API. Cualquier proveedor alternativo debe respetar sus condiciones y atribución; no se configura descarga masiva ni cartografía offline.

Prueba física: instalar con Run, conectar `AsistenteVial-IMU`, esperar GPS válido y abrir Inicio → Mi mapa → Centrar mi posición. Iniciar un recorrido y generar un evento simulado con GPS reciente para ver su marcador. Desconectar BLE debe retirar el marcador actual, manteniendo los eventos guardados del recorrido activo. La prueba física de esta nueva pantalla queda pendiente.

## Avisos viales de Arequipa

Driver usa el servidor AVIGEM configurado en la app. Al abrir **Mi mapa**, consulta `GET /api/v1/road-catalog` y actualiza los datos cada minuto. El GPS BLE se evalúa dentro de Driver. Los avisos por voz requieren una voz en español y que la pantalla esté visible.

- **Límites de velocidad y colegios:** Laravel consulta OpenStreetMap mediante Overpass y guarda los resultados durante 12 horas. Se muestran los límites numéricos registrados para ambos sentidos y las ubicaciones de colegios. «Colegio cerca» indica proximidad al colegio. No certifica el perímetro de una zona escolar.
- **Tráfico adelante:** Laravel consulta incidentes de congestión vigentes de TomTom cuando se configura `TOMTOM_TRAFFIC_KEY` en el servidor. Driver usa el sentido del tramo del reporte para dar el aviso. Los datos caducan en un máximo de tres minutos.
- **Mapa:** azul para límites, violeta para colegios y rojo para tráfico. Fleet usa el mismo catálogo en **Operaciones → Mapa en vivo → Capas viales de Arequipa**.

Para publicar, despliega los cambios de Laravel e instala la versión actualizada de Driver. La fuente de OpenStreetMap no requiere clave. Se puede configurar otra instancia HTTPS compatible mediante `OVERPASS_URL`. La clave de TomTom pertenece al entorno de Laravel y no debe ponerse en la app ni en el repositorio.

La API devuelve `osmAvailable` y `trafficAvailable` para distinguir una fuente indisponible de una lista vacía. Cuando una vía no tiene un límite publicado o hay límites en conflicto, Driver indica que el límite no está disponible.
