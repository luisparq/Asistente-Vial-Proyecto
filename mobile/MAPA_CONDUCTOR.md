# Mi mapa (Android)

Pantalla opcional desde Inicio. Reutiliza los flujos BLE/GPS y Room existentes; no inicia conexiones ni modifica recorridos o sincronización.

- Turquesa: última posición GPS válida mientras BLE permanece conectado. Se elimina al caducar (ventana existente de 5 segundos), perder fix o desconectarse.
- Ámbar: eventos `SIMULATED` georreferenciados del recorrido activo. Tocarlos muestra tipo, fecha local y coordenadas guardadas. Son datos de prueba, no riesgos comprobados.
- «Centrar mi posición» utiliza exclusivamente GPS del ESP32, no ubicación del teléfono.

MapLibre Native Android 13.6.1; cartografía OpenStreetMap mediante OpenFreeMap Liberty, con atribución del proveedor visible en el mapa. Requiere Internet para cargar cartografía; los recorridos y el monitoreo siguen funcionando sin abrirlo.

Proveedor configurable al compilar mediante propiedad Gradle `mapStyleUrl`, con URL HTTPS de un estilo MapLibre compatible. Predeterminado: `https://tiles.openfreemap.org/styles/liberty`. No requiere claves API. Cualquier proveedor alternativo debe respetar sus condiciones y atribución; no se configura descarga masiva ni cartografía offline.

Prueba física: instalar con Run, conectar `AsistenteVial-IMU`, esperar GPS válido y abrir Inicio → Mi mapa → Centrar mi posición. Iniciar un recorrido y generar un evento simulado con GPS reciente para ver su marcador. Desconectar BLE debe retirar el marcador actual, manteniendo los eventos guardados del recorrido activo. La prueba física de esta nueva pantalla queda pendiente.
