(() => {
    const track = document.querySelector('#trip-track');
    if (!track) return;
    const message = document.querySelector('#trip-map-message');
    const fitButton = document.querySelector('#trip-fit');
    const points = JSON.parse(track.dataset.points);
    const segments = [];
    let segment = [];
    for (const point of points) {
        const previous = segment.at(-1);
        if (previous && point.time - previous.time > 30000) {
            if (segment.length > 1) segments.push(segment.map(sample => sample.coordinates));
            segment = [];
        }
        segment.push(point);
    }
    if (segment.length > 1) segments.push(segment.map(sample => sample.coordinates));
    const line = {
        type: 'Feature',
        properties: {},
        geometry: { type: 'MultiLineString', coordinates: segments },
    };
    const coordinates = points.map(point => point.coordinates);
    try {
        const style = () => document.documentElement.classList.contains('theme-dark') ? track.dataset.dark : track.dataset.light;
        const map = new maplibregl.Map({ container: 'trip-map', style: style(), center: coordinates[0], zoom: 15 });
        map.addControl(new maplibregl.NavigationControl({ showCompass: false }));
        const bounds = new maplibregl.LngLatBounds(coordinates[0], coordinates[0]);
        for (const coordinate of coordinates) bounds.extend(coordinate);
        const fit = () => map.fitBounds(bounds, { padding: 54, maxZoom: 16, duration: 0 });
        map.once('load', fit);
        fitButton.addEventListener('click', fit);
        const addMarker = (coordinate, label, type) => {
            const marker = document.createElement('span');
            marker.className = 'trip-map-pin ' + type;
            marker.textContent = label;
            new maplibregl.Marker({ element: marker, anchor: 'bottom' }).setLngLat(coordinate).addTo(map);
        };
        const last = coordinates.at(-1);
        const endLabel = track.dataset.arrival === 'true' ? 'Llegada' : 'Último punto';
        const sameLocation = last.every((value, i) => value === coordinates[0][i]);
        addMarker(coordinates[0], coordinates.length > 1 && sameLocation ? 'Salida / ' + endLabel.toLowerCase() : 'Salida', 'start');
        if (coordinates.length > 1 && !sameLocation) {
            addMarker(last, endLabel, 'end');
        }
        map.on('style.load', () => {
            if (segments.length) {
                map.addSource('trip-route', { type: 'geojson', data: line });
                map.addLayer({ id: 'trip-route-outline', type: 'line', source: 'trip-route',
                    layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#101e2b', 'line-width': 8 } });
                map.addLayer({ id: 'trip-route-line', type: 'line', source: 'trip-route',
                    layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#d4f23a', 'line-width': 5 } });
            }
            message.hidden = true;
            fitButton.disabled = false;
        });
        map.on('error', () => {
            if (fitButton.disabled) message.textContent = 'No se pudo cargar la cartografía. Las coordenadas siguen disponibles abajo.';
        });
        document.addEventListener('fleet-theme-change', () => map.setStyle(style(), { diff: false }));
    } catch {
        message.textContent = 'No se pudo abrir el mapa. Consulta las coordenadas abajo.';
    }
})();
