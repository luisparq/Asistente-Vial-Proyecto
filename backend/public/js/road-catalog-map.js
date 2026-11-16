// Capas viales del mapa Fleet. Cada capa consulta su propia fuente y muestra su propio estado,
// de modo que un fallo de OpenStreetMap/Overpass no oculta las capas de TomTom (ni al revés).
window.bindRoadCatalog = (map, controls) => {
  const options = [...controls.querySelectorAll('input[type="checkbox"]')];
  const note = controls.querySelector('[role="status"]');
  const tomtomConfigured = controls.dataset.trafficConfigured === '1';
  const ids = {
    speed: 'catalog-speed', flow: 'catalog-traffic-flow', incidents: 'catalog-incidents',
    schools: 'catalog-schools', assistance: 'catalog-assistance',
  };
  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const enabled = layer => options.some(option => option.value === layer && option.checked);
  const empty = () => ({ type: 'FeatureCollection', features: [] });
  const numberFormat = new Intl.NumberFormat('es-PE');

  // Estado por capa: idle | loading | ok | empty | unavailable | offline | unconfigured | render-error
  const layers = {
    speed: { state: 'idle', data: null, source: 'OpenStreetMap (Overpass)' },
    traffic: { state: 'idle', source: 'TomTom Traffic Flow' },
    incidents: { state: 'idle', data: null, source: 'TomTom Traffic Incidents' },
    schools: { state: 'idle', data: null, source: 'TomTom Search', key: null },
    assistance: { state: 'idle', data: null, source: 'TomTom Search', key: null },
  };

  function setState(layer, state, detail = '') {
    layers[layer].state = state;
    layers[layer].detail = detail;
    paintStatus();
  }

  function stateText(layer) {
    const { state, detail, source } = layers[layer];
    switch (state) {
      case 'loading': return { tone: 'muted', text: 'Consultando…' };
      case 'ok': return { tone: 'ok', text: detail };
      case 'empty': return { tone: 'muted', text: detail || 'Sin resultados en esta zona' };
      case 'stale': return { tone: 'warn', text: detail };
      case 'unconfigured': return { tone: 'warn', text: 'Fuente sin configurar en el servidor' };
      case 'offline': return { tone: 'error', text: 'Sin conexión con el servidor' };
      case 'unavailable': return { tone: 'error', text: `${source} no respondió` };
      case 'render-error': return { tone: 'error', text: 'Datos recibidos, pero no se pudieron dibujar' };
      default: return null;
    }
  }

  function paintStatus() {
    for (const option of options) {
      const badge = option.closest('label')?.querySelector('[data-layer-state]');
      if (!badge) continue;
      const info = option.checked ? stateText(option.value) : null;
      badge.textContent = info ? info.text : '';
      badge.dataset.tone = info?.tone ?? '';
      badge.hidden = !info;
    }
    const active = options.filter(option => option.checked).map(option => option.value);
    if (!active.length) {
      note.textContent = 'Activa una capa para consultarla. Cada capa indica su propio estado.';
      return;
    }
    const sources = [...new Set(active.map(layer => layers[layer].source.split(' ')[0]))];
    const parts = [`Fuentes: ${sources.join(' y ')}.`];
    if (active.includes('speed')) parts.push('Los límites solo aparecen en los tramos donde OpenStreetMap publica el dato; no equivalen a la velocidad del tráfico.');
    if (active.includes('traffic')) parts.push('Flujo: verde fluido, amarillo lento, rojo congestionado.');
    if (active.includes('schools')) parts.push('La clasificación de colegios proviene de TomTom y puede incluir errores.');
    note.textContent = parts.join(' ');
  }

  async function getJson(url) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 25000);
    try {
      const response = await fetch(url, { headers: { Accept: 'application/json' }, signal: controller.signal });
      const body = await response.json().catch(() => null);
      return { status: response.status, body };
    } catch {
      return { status: 0, body: null };
    } finally {
      clearTimeout(timeout);
    }
  }

  // 0 = sin conexión o tiempo agotado, 503 = fuente sin configurar, otro error = fuente indisponible.
  function failure(layer, status) {
    // Si ya había datos, se conservan en el mapa y se indica que la última actualización falló.
    if (layers[layer].data && status !== 503) {
      return setState(layer, 'stale', `Mostrando datos de las ${layers[layer].updatedAt}; la última actualización falló`);
    }
    setState(layer, status === 0 ? 'offline' : status === 503 ? 'unconfigured' : 'unavailable');
  }
  const stamp = layer => { layers[layer].updatedAt = new Date().toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' }); };

  function setData(sourceId, data, layer) {
    try {
      map.getSource(sourceId)?.setData(data);
    } catch {
      setState(layer, 'render-error');
    }
  }

  // ---- Límites de velocidad (OpenStreetMap vía Overpass) ----
  async function loadSpeed() {
    if (!enabled('speed') || layers.speed.busy) return;
    layers.speed.busy = true;
    if (!layers.speed.data) setState('speed', 'loading');
    const { status, body } = await getJson(controls.dataset.url);
    layers.speed.busy = false;
    if (status !== 200 || !Array.isArray(body?.speedSections)) return failure('speed', status);
    if (!body.osmAvailable) {
      layers.speed.data = null;
      setState('speed', 'unavailable');
    } else {
      layers.speed.data = body.speedSections;
      stamp('speed');
      setState('speed', body.speedSections.length ? 'ok' : 'empty',
        body.speedSections.length ? `${numberFormat.format(body.speedSections.length)} tramos con límite publicado` : 'Ningún tramo con límite publicado');
    }
    drawSpeed();
  }

  function drawSpeed() {
    const features = enabled('speed') && layers.speed.data ? layers.speed.data.map(item => ({
      type: 'Feature',
      geometry: { type: 'LineString', coordinates: [[+item.start.lon, +item.start.lat], [+item.end.lon, +item.end.lat]] },
      properties: { name: item.name || 'Tramo vial', label: `${item.kmh} km/h` },
    })) : [];
    setData(ids.speed, { type: 'FeatureCollection', features }, 'speed');
  }

  // ---- Flujo de tráfico (mosaicos TomTom a través del servidor) ----
  function syncFlow() {
    if (!map.getLayer(ids.flow)) return;
    const on = enabled('traffic') && tomtomConfigured;
    map.setLayoutProperty(ids.flow, 'visibility', on ? 'visible' : 'none');
    if (!enabled('traffic')) return;
    if (!tomtomConfigured) return setState('traffic', 'unconfigured');
    if (['idle', 'unavailable'].includes(layers.traffic.state)) {
      setState('traffic', 'loading');
      map.once('idle', () => {
        if (enabled('traffic') && layers.traffic.state === 'loading') setState('traffic', 'ok', 'Mosaicos de flujo en vivo cargados');
      });
    }
  }

  // ---- Incidentes y cierres (TomTom) ----
  async function loadIncidents() {
    if (!enabled('incidents') || layers.incidents.busy) return;
    if (!tomtomConfigured) return setState('incidents', 'unconfigured');
    layers.incidents.busy = true;
    if (!layers.incidents.data) setState('incidents', 'loading');
    const { status, body } = await getJson(controls.dataset.incidentsUrl);
    layers.incidents.busy = false;
    if (status !== 200 || !Array.isArray(body?.incidents)) return failure('incidents', status);
    layers.incidents.data = body.incidents;
    stamp('incidents');
    const closures = body.incidents.filter(item => item.kind === 'closure').length;
    setState('incidents', body.incidents.length ? 'ok' : 'empty', body.incidents.length
      ? `${body.incidents.length - closures} incidentes · ${closures} cierres vigentes` : 'Sin incidentes vigentes reportados');
    drawIncidents();
  }

  function drawIncidents() {
    const features = enabled('incidents') && layers.incidents.data ? layers.incidents.data.map(item => ({
      type: 'Feature', geometry: item.geometry,
      properties: { kind: item.kind, category: item.category, description: item.description || '', from: item.from || '', to: item.to || '' },
    })) : [];
    setData(ids.incidents, { type: 'FeatureCollection', features }, 'incidents');
  }

  // ---- Colegios cercanos y puntos de auxilio (TomTom, alrededor del centro del mapa) ----
  function centerKey() {
    const { lat, lng } = map.getCenter();
    return `${lat.toFixed(2)},${lng.toFixed(2)}`;
  }

  async function loadPlaces(layer, baseUrl, field, describe) {
    if (!enabled(layer) || layers[layer].busy) return;
    if (!tomtomConfigured) return setState(layer, 'unconfigured');
    const key = centerKey();
    if (layers[layer].key === key && layers[layer].data) return;
    layers[layer].busy = true;
    setState(layer, 'loading');
    const [lat, lon] = key.split(',');
    const url = new URL(baseUrl, location.origin);
    url.searchParams.set('lat', lat);
    url.searchParams.set('lon', lon);
    if (layer === 'assistance') url.searchParams.set('type', 'all');
    const { status, body } = await getJson(url);
    layers[layer].busy = false;
    if (status !== 200 || !Array.isArray(body?.[field])) return failure(layer, status);
    layers[layer].data = body[field];
    layers[layer].key = key;
    stamp(layer);
    setState(layer, body[field].length ? 'ok' : 'empty', body[field].length ? describe(body[field].length) : 'Sin resultados cerca del centro del mapa');
    drawPlaces(layer);
  }

  function drawPlaces(layer) {
    const features = enabled(layer) && layers[layer].data ? layers[layer].data.map(item => ({
      type: 'Feature', geometry: { type: 'Point', coordinates: [+item.lon, +item.lat] },
      properties: { name: item.name, category: item.category || '', address: item.address || '', phone: item.phone || '' },
    })) : [];
    setData(ids[layer], { type: 'FeatureCollection', features }, layer);
  }

  const loadSchools = () => loadPlaces('schools', controls.dataset.schoolsUrl, 'schools', n => `${n} lugares cerca del centro del mapa`);
  const loadAssistance = () => loadPlaces('assistance', controls.dataset.assistanceUrl, 'pois', n => `${n} puntos cerca del centro del mapa`);

  function refreshAll() {
    if (document.hidden) return;
    loadSpeed();
    syncFlow();
    loadIncidents();
    loadSchools();
    loadAssistance();
  }

  function drawAll() {
    drawSpeed();
    drawIncidents();
    drawPlaces('schools');
    drawPlaces('assistance');
    syncFlow();
    paintStatus();
  }

  map.on('style.load', () => {
    map.addSource(ids.speed, { type: 'geojson', data: empty() });
    for (const id of [ids.incidents, ids.schools, ids.assistance]) map.addSource(id, { type: 'geojson', data: empty(), attribution: '© TomTom' });
    map.addSource(ids.flow, { type: 'raster', tiles: [controls.dataset.trafficUrl], tileSize: 256, minzoom: 0, maxzoom: 22, attribution: '© TomTom' });
    map.addLayer({ id: ids.flow, source: ids.flow, type: 'raster', layout: { visibility: 'none' }, paint: { 'raster-opacity': 0.85 } });
    map.addLayer({ id: ids.speed, source: ids.speed, type: 'line', paint: { 'line-color': '#2563eb', 'line-width': 4, 'line-opacity': 0.8 } });
    map.addLayer({ id: ids.speed + '-labels', source: ids.speed, type: 'symbol', minzoom: 14,
      layout: { 'symbol-placement': 'line', 'text-field': ['get', 'label'], 'text-font': ['Noto Sans Regular'], 'text-size': 12 },
      paint: { 'text-color': '#1d4ed8', 'text-halo-color': '#fff', 'text-halo-width': 2 } });
    map.addLayer({ id: ids.incidents, source: ids.incidents, type: 'line', filter: ['==', ['geometry-type'], 'LineString'],
      layout: { 'line-cap': 'round' },
      paint: { 'line-color': ['match', ['get', 'kind'], 'closure', '#b91c1c', '#f59e0b'], 'line-width': 5, 'line-opacity': 0.9 } });
    map.addLayer({ id: ids.incidents + '-points', source: ids.incidents, type: 'circle', filter: ['==', ['geometry-type'], 'Point'],
      paint: { 'circle-radius': 6, 'circle-color': ['match', ['get', 'kind'], 'closure', '#b91c1c', '#f59e0b'], 'circle-stroke-color': '#fff', 'circle-stroke-width': 1.5 } });
    map.addLayer({ id: ids.schools, source: ids.schools, type: 'circle',
      paint: { 'circle-radius': 6, 'circle-color': '#9333ea', 'circle-stroke-color': '#fff', 'circle-stroke-width': 1.5 } });
    map.addLayer({ id: ids.assistance, source: ids.assistance, type: 'circle',
      paint: { 'circle-radius': 6, 'circle-color': '#0f766e', 'circle-stroke-color': '#fff', 'circle-stroke-width': 1.5 } });
    controls.disabled = false;
    drawAll();
  });

  // Error en mosaicos de flujo: TomTom o el proxy del servidor no los entregaron.
  map.on('error', event => {
    if (event.sourceId === ids.flow && enabled('traffic')) setState('traffic', 'unavailable');
  });

  const popup = (event, html) => new maplibregl.Popup({ closeButton: true, closeOnClick: true }).setLngLat(event.lngLat).setHTML(html).addTo(map);
  map.on('click', ids.speed, event => {
    const p = event.features?.[0]?.properties;
    if (p) popup(event, `<strong>${escapeHtml(p.name)}</strong><br>Límite publicado: ${escapeHtml(p.label)}<br><small>Fuente: OpenStreetMap</small>`);
  });
  for (const id of [ids.incidents, ids.incidents + '-points']) {
    map.on('click', id, event => {
      const p = event.features?.[0]?.properties;
      if (!p) return;
      const route = [p.from, p.to].filter(Boolean).map(escapeHtml).join(' → ');
      popup(event, `<strong>${escapeHtml(p.category)}</strong>${p.description ? '<br>' + escapeHtml(p.description) : ''}${route ? '<br>' + route : ''}<br><small>Fuente: TomTom</small>`);
    });
  }
  for (const id of [ids.schools, ids.assistance]) {
    map.on('click', id, event => {
      const p = event.features?.[0]?.properties;
      if (!p) return;
      popup(event, `<strong>${escapeHtml(p.name)}</strong><br>${escapeHtml(p.category)}${p.address ? '<br>' + escapeHtml(p.address) : ''}${p.phone ? '<br>' + escapeHtml(p.phone) : ''}<br><small>Fuente: TomTom</small>`);
    });
  }
  for (const id of [ids.speed, ids.incidents, ids.incidents + '-points', ids.schools, ids.assistance]) {
    map.on('mouseenter', id, () => { map.getCanvas().style.cursor = 'pointer'; });
    map.on('mouseleave', id, () => { map.getCanvas().style.cursor = ''; });
  }

  controls.addEventListener('change', event => {
    const layer = event.target?.value;
    if (layer === 'traffic' && !event.target.checked) layers.traffic.state = 'idle';
    drawAll();
    refreshAll();
  });
  let moveTimer;
  map.on('moveend', () => {
    clearTimeout(moveTimer);
    moveTimer = setTimeout(() => { loadSchools(); loadAssistance(); }, 600);
  });
  document.addEventListener('visibilitychange', refreshAll);
  setInterval(() => { loadSpeed(); loadIncidents(); }, 60000);
  paintStatus();
};
