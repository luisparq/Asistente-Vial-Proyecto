window.bindRoadCatalog = (map, controls) => {
  const options = [...controls.querySelectorAll('input[type="checkbox"]')];
  const status = controls.querySelector('[role="status"]');
  const sourceId = 'fleet-road-catalog';
  const trafficLayer = 'catalog-traffic';
  let catalog = null;
  let failed = false;
  let loading = false;

  const enabled = () => new Set(options.filter(option => option.checked).map(option => option.value));
  const coords = point => [Number(point.lon), Number(point.lat)];
  const feature = (kind, geometry, properties = {}) => ({
    type: 'Feature', geometry, properties: { kind, ...properties },
  });

  function updateTrafficFilter() {
    if (enabled().has('traffic') && map.getLayer(trafficLayer)) {
      map.setFilter(trafficLayer, ['all', ['==', ['get', 'kind'], 'traffic'],
        ['>', ['get', 'expires'], Date.now() / 1000]]);
    }
  }

  function render() {
    const visible = enabled();
    const features = [];
    if (catalog?.osmAvailable && visible.has('speed')) {
      for (const item of catalog.speedSections) {
        features.push(feature('speed', { type: 'LineString', coordinates: [coords(item.start), coords(item.end)] },
          { label: item.kmh + ' km/h' }));
      }
    }
    if (catalog?.osmAvailable && visible.has('schools')) {
      for (const item of catalog.zones) {
        features.push(feature('schools', { type: 'Point', coordinates: coords(item.point) }));
      }
    }
    const traffic = catalog?.trafficAvailable && visible.has('traffic')
      ? catalog.traffic.filter(item => item.expiresAt > Date.now() / 1000) : [];
    for (const item of traffic) {
      features.push(feature('traffic', { type: 'Point', coordinates: coords(item.point) }, { expires: item.expiresAt }));
    }

    map.getSource(sourceId)?.setData({ type: 'FeatureCollection', features });
    updateTrafficFilter();

    if (!visible.size) {
      status.textContent = 'Activa una capa para consultar el catálogo vial.';
      return;
    }
    if (failed || !catalog) {
      status.textContent = loading ? 'Consultando el catálogo vial…' : 'El catálogo vial está temporalmente indisponible.';
      return;
    }

    const notes = [];
    if (visible.has('speed') || visible.has('schools')) {
      notes.push(catalog.osmAvailable ? 'OpenStreetMap · cobertura según los datos publicados.' : 'OpenStreetMap temporalmente indisponible.');
    }
    if (visible.has('traffic')) {
      notes.push(catalog.trafficConfigured === false ? 'Tráfico sin configurar en el servidor.' : !catalog.trafficAvailable ? 'TomTom temporalmente indisponible.' : traffic.length
        ? 'TomTom · reportes recientes de congestión. Se consulta cada minuto.'
        : 'TomTom · sin reportes de congestión vigentes en el catálogo.');
    }
    status.textContent = notes.join(' ');
  }

  async function refresh() {
    if (loading || !enabled().size || document.hidden) return;
    loading = true;
    render();
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 25000);
    try {
      const response = await fetch(controls.dataset.url, { headers: { Accept: 'application/json' }, signal: controller.signal });
      if (!response.ok) throw new Error('Catálogo indisponible');
      const data = await response.json();
      if (!['speedSections', 'zones', 'traffic'].every(key => Array.isArray(data[key]))) throw new Error('Formato inválido');
      catalog = data;
      failed = false;
    } catch {
      catalog = null;
      failed = true;
    } finally {
      clearTimeout(timeout);
      loading = false;
      render();
    }
  }

  map.on('style.load', () => {
    map.addSource(sourceId, { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    const speedFilter = ['==', ['get', 'kind'], 'speed'];
    map.addLayer({ id: 'catalog-speed', source: sourceId, type: 'line', filter: speedFilter,
      paint: { 'line-color': '#2563eb', 'line-width': 4, 'line-opacity': 0.8 } });
    map.addLayer({ id: 'catalog-speed-labels', source: sourceId, type: 'symbol', filter: speedFilter, minzoom: 14,
      layout: { 'symbol-placement': 'line', 'text-field': ['get', 'label'], 'text-font': ['Noto Sans Regular'], 'text-size': 12 },
      paint: { 'text-color': '#1d4ed8', 'text-halo-color': '#fff', 'text-halo-width': 2 } });
    for (const [kind, color] of [['schools', '#9333ea'], ['traffic', '#dc2626']]) {
      map.addLayer({ id: 'catalog-' + kind, source: sourceId, type: 'circle', filter: ['==', ['get', 'kind'], kind],
        paint: { 'circle-radius': 7, 'circle-color': color, 'circle-stroke-color': '#fff', 'circle-stroke-width': 1.5 } });
    }
    controls.disabled = false;
    render();
  });
  controls.addEventListener('change', () => {
    render();
    if (!catalog) refresh();
  });
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) {
      render();
      refresh();
    }
  });
  setInterval(refresh, 60000);
  setInterval(updateTrafficFilter, 5000);
};
