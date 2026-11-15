(() => {
    const root = document.getElementById('live-map');
    const search = document.getElementById('live-search');
    const filter = document.getElementById('live-filter');
    const list = document.getElementById('live-vehicles');
    const empty = document.getElementById('live-empty');
    const updated = document.getElementById('live-updated');
    const message = document.getElementById('live-map-message');
    const template = document.getElementById('live-vehicle-card');
    const fit = document.getElementById('live-fit');
    const cartography = window.fleetMapStyle(document.getElementById('live-style'), root.dataset);
    const formatDate = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'medium' });
    const formatAge = new Intl.RelativeTimeFormat('es-PE');
    const labels = {
        recent: 'Ubicación reciente', stale: 'Ubicación desactualizada', missing: 'Sin ubicación disponible',
    };
    const cards = new Map();
    const markers = new Map();
    let map;
    let mapReady = false;
    let mapError = false;
    let vehicles = [];
    let visible = [];
    let selected;
    let firstFit = true;
    let timer;
    let loading = false;
    let loaded = false;
    let updatedAt;
    let clockOffset = 0;

    function positionState(position) {
        if (!position || !Number.isFinite(position.latitude) || Math.abs(position.latitude) > 90
            || !Number.isFinite(position.longitude) || Math.abs(position.longitude) > 180) return null;
        const received = Date.parse(position.received_at);
        const measured = Date.parse(position.measured_at);
        const now = Date.now() + clockOffset;
        if (!Number.isFinite(received) || !Number.isFinite(measured) || measured > received || received > now) return null;
        const age = Math.floor((now - measured) / 1000);
        return {
            coordinates: [position.longitude, position.latitude], received, measured, age,
            status: age <= Number(root.dataset.recent) ? 'recent' : 'stale',
        };
    }

    function ageLabel(age) {
        if (age < 60) return formatAge.format(-age, 'second');
        if (age < 3600) return formatAge.format(-Math.floor(age / 60), 'minute');
        if (age < 86400) return formatAge.format(-Math.floor(age / 3600), 'hour');
        return formatAge.format(-Math.floor(age / 86400), 'day');
    }

    function updateMapMessage() {
        const hasPoints = visible.some(vehicle => vehicle.gps);
        message.hidden = mapReady && !mapError && hasPoints;
        message.textContent = mapError ? 'No se pudo cargar toda la cartografía.'
            : !mapReady ? 'Cargando cartografía…'
            : !loaded ? 'Esperando datos de la flota…' : 'Sin ubicaciones GPS para mostrar.';
        fit.disabled = !mapReady || !hasPoints;
    }

    function centerVehicles() {
        const points = visible.filter(vehicle => vehicle.gps).map(vehicle => vehicle.gps.coordinates);
        if (!mapReady || !points.length) return;
        const bounds = new maplibregl.LngLatBounds();
        points.forEach(point => bounds.extend(point));
        map.fitBounds(bounds, { padding: 56, maxZoom: 15 });
    }

    function selectVehicle(id, fromMarker = false) {
        const vehicle = visible.find(row => row.id === id);
        if (!vehicle) return;
        selected = id;
        syncSelection();
        if (mapReady && vehicle.gps) {
            map.easeTo({ center: vehicle.gps.coordinates, zoom: Math.max(map.getZoom(), 15) });
        }
        if (fromMarker) cards.get(id).querySelector('button').scrollIntoView({ block: 'nearest' });
    }

    function syncSelection() {
        cards.forEach((card, id) => card.querySelector('button').setAttribute('aria-pressed', id === selected));
        markers.forEach((marker, id) => marker.getElement().setAttribute('aria-pressed', id === selected));
    }

    function renderCard(vehicle) {
        let card = cards.get(vehicle.id);
        if (!card) {
            card = template.content.firstElementChild.cloneNode(true);
            card.querySelector('button').addEventListener('click', () => selectVehicle(vehicle.id));
            cards.set(vehicle.id, card);
            list.append(card);
        }
        const gps = vehicle.gps;
        const status = gps?.status ?? 'missing';
        const fields = {
            name: vehicle.name || vehicle.plate || `Vehículo ${vehicle.id}`,
            plate: vehicle.plate || '',
            driver: vehicle.driver || 'Sin conductor asignado',
            gps: labels[status],
            received: gps ? `Recibida en AVIGEM el ${formatDate.format(gps.received)}` : '',
            measured: gps ? `${gps.measured === gps.received ? 'Antigüedad' : 'Medida el ' + formatDate.format(gps.measured)} · ${ageLabel(gps.age)}` : '',
            action: gps && mapReady ? 'Localizar en el mapa' : '',
        };
        Object.entries(fields).forEach(([field, value]) => {
            const element = card.querySelector(`[data-field="${field}"]`);
            if (element.textContent !== value) element.textContent = value;
            element.hidden = !value;
        });
        card.hidden = !vehicle.visible;
    }

    function renderMarker(vehicle) {
        const { id, gps } = vehicle;
        let marker = markers.get(id);
        if (!mapReady || !gps || !vehicle.visible) {
            marker?.remove();
            markers.delete(id);
            return;
        }
        if (!marker) {
            const button = document.createElement('button');
            button.type = 'button';
            button.append(template.content.querySelector('.live-vehicle-title svg').cloneNode(true));
            button.addEventListener('click', () => selectVehicle(id, true));
            marker = new maplibregl.Marker({ element: button }).setLngLat(gps.coordinates).addTo(map);
            markers.set(id, marker);
        }
        const button = marker.getElement();
        button.classList.add('live-map-marker');
        button.classList.toggle('live-gps-recent', gps.status === 'recent');
        button.classList.toggle('live-gps-stale', gps.status === 'stale');
        button.setAttribute('aria-label', `${vehicle.plate || vehicle.name}, ${labels[gps.status]}`);
        marker.setLngLat(gps.coordinates);
    }

    function normalize(value) {
        return value.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
    }

    function render() {
        const term = normalize(search.value.trim());
        const activeIds = new Set(vehicles.map(vehicle => vehicle.id));
        cards.forEach((card, id) => {
            if (activeIds.has(id)) return;
            card.remove();
            cards.delete(id);
            markers.get(id)?.remove();
            markers.delete(id);
        });
        if (!activeIds.has(selected)) selected = undefined;
        const rows = vehicles.map(vehicle => {
            const gps = positionState(vehicle.position);
            const matches = normalize([vehicle.name, vehicle.plate, vehicle.driver].join(' ')).includes(term);
            return { ...vehicle, gps, visible: matches && (!filter.value || filter.value === (gps?.status ?? 'missing')) };
        });
        visible = rows.filter(vehicle => vehicle.visible);
        rows.forEach(vehicle => {
            renderCard(vehicle);
            renderMarker(vehicle);
        });
        syncSelection();
        empty.hidden = !loaded || visible.length > 0;
        empty.textContent = vehicles.length ? 'Ningún vehículo coincide con los filtros.' : 'La empresa aún no tiene vehículos registrados.';
        updateMapMessage();
        if (firstFit && !fit.disabled) {
            firstFit = false;
            centerVehicles();
        }
    }

    async function refresh() {
        clearTimeout(timer);
        if (loading || document.hidden) return;
        loading = true;
        try {
            const response = await fetch(root.dataset.url, { headers: { Accept: 'application/json' } });
            if (response.status === 401 || response.status === 403 || response.redirected) {
                vehicles = [];
                loaded = false;
                render();
                updated.textContent = 'Se requiere acceso de Fleet. Vuelve a iniciar sesión.';
                return;
            }
            if (!response.ok) throw new Error('Flota no disponible');
            const snapshot = await response.json();
            const snapshotTime = Date.parse(snapshot.updated_at);
            if (!Array.isArray(snapshot.vehicles) || !Number.isFinite(snapshotTime)) throw new Error('Respuesta inválida');
            vehicles = snapshot.vehicles;
            updatedAt = snapshotTime;
            clockOffset = updatedAt - Date.now();
            loaded = true;
            render();
            updated.textContent = `Actualizado el ${formatDate.format(updatedAt)}`;
        } catch {
            render();
            updated.textContent = updatedAt
                ? `Error al actualizar. Última consulta: ${formatDate.format(updatedAt)}`
                : 'No se pudo consultar la flota. Se reintentará automáticamente.';
        } finally {
            loading = false;
            if (!document.hidden) timer = setTimeout(refresh, Number(root.dataset.poll) * 1000);
        }
    }

    search.addEventListener('input', render);
    filter.addEventListener('change', render);
    fit.addEventListener('click', centerVehicles);
    document.addEventListener('visibilitychange', refresh);
    try {
        map = new maplibregl.Map({ container: 'live-canvas', style: cartography.initial, center: [-71.54, -16.40], zoom: 11 });
        new ResizeObserver(() => map.resize()).observe(map.getContainer());
        map.addControl(new maplibregl.NavigationControl({ showCompass: false }));
        map.on('error', () => {
            mapError = true;
            updateMapMessage();
        });
        map.on('style.load', () => {
            mapReady = true;
            mapError = false;
            render();
        });
        window.bindRoadCatalog?.(map, document.getElementById('live-road-catalog'));
        cartography.bind(map, () => {
            mapError = false;
        });
    } catch {
        mapError = true;
        updateMapMessage();
    }
    refresh();
})();
