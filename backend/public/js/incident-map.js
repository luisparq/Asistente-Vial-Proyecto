(() => {
  'use strict';
  const container = document.getElementById('incident-map');
  const status = document.getElementById('map-status');
  const buttons = [document.getElementById('mode-markers'), document.getElementById('mode-heat')];
  const data = JSON.parse(document.getElementById('incident-data').textContent);
  if (!window.maplibregl) { status.textContent = 'No se pudo iniciar el mapa. Comprueba soporte WebGL en este navegador.'; return; }
  let map;
  try { map = new maplibregl.Map({container, style:container.dataset.style, center:[0,0], zoom:1, attributionControl:true}); }
  catch (_) { status.textContent = 'No se pudo iniciar el mapa. Los registros siguen disponibles en Eventos.'; return; }
  map.addControl(new maplibregl.NavigationControl());
  map.on('error', () => { status.textContent = 'No se pudo cargar parte de la cartografía. Comprueba Internet o el proveedor configurado.'; });
  const select = document.getElementById('event-select');
  function detail(feature) {
    // Render canonical stored coordinates, not tile-quantized hit-test geometry.
    feature = data.features.find(f => f.properties.uuid === feature.properties.uuid);
    if (!feature) return;
    const node = document.getElementById('event-detail'); node.replaceChildren();
    const p = feature.properties, c = feature.geometry.coordinates;
    for (const text of [p.label, p.origin_label, 'Fecha · Lima: '+p.date,
      'Latitud: '+c[1], 'Longitud: '+c[0], 'Origen GPS: '+(p.location_origin || 'No informado'),
      ...(container.dataset.global === 'true' ? ['Empresa: '+p.company] : [p.vehicle ? 'Vehículo: '+p.vehicle : 'Sin vehículo asociado', 'UUID: '+p.uuid])]) {
      const line = document.createElement('p'); line.textContent = text; node.append(line);
    }
    if (p.trip_url) { const link = document.createElement('a'); link.href = p.trip_url; link.textContent = 'Ver recorrido'; node.append(link); }
    const index = data.features.findIndex(f => f.properties.uuid === p.uuid); select.value = String(index);
  }
  select.addEventListener('change', () => { const f = data.features[Number(select.value)]; if (select.value !== '' && f) { detail(f); map.flyTo({center:f.geometry.coordinates, zoom:Math.max(map.getZoom(),15)}); } });
  map.on('load', () => {
    map.addSource('incidents', {type:'geojson', data});
    map.addLayer({id:'incident-heat', type:'heatmap', source:'incidents', layout:{visibility:'none'}, paint:{
      'heatmap-weight':1, 'heatmap-intensity':1.5, 'heatmap-radius':35, 'heatmap-opacity':0.8,
      'heatmap-color':['interpolate',['linear'],['heatmap-density'],0,'rgba(0,0,0,0)',0.15,'#D4F23A',0.4,'#E9B44C',0.65,'#C96A1B',1,'#9C2F1B']}});
    map.addLayer({id:'incident-points', type:'circle', source:'incidents', paint:{'circle-radius':7,
      'circle-color':['match',['get','origin'],'SIMULATED','#C98A12','#22507A'], 'circle-stroke-color':'#fff','circle-stroke-width':2}});
    map.addLayer({id:'incident-hit', type:'circle', source:'incidents', paint:{'circle-radius':18,'circle-opacity':0,'circle-stroke-opacity':0}});
    map.on('click','incident-hit',e=> { if (e.features.length) detail(e.features[0]); });
    map.on('mouseenter','incident-hit',()=>map.getCanvas().style.cursor='pointer');
    map.on('mouseleave','incident-hit',()=>map.getCanvas().style.cursor='');
    buttons.forEach((button,index)=> { button.disabled=false; button.addEventListener('click',()=>{
      map.setLayoutProperty('incident-points','visibility',index===0?'visible':'none');
      map.setLayoutProperty('incident-heat','visibility',index===1?'visible':'none');
      buttons.forEach((b,i)=> {b.setAttribute('aria-pressed',String(i===index)); b.classList.toggle('primary',i===index);});
      document.getElementById('heat-legend').hidden=index!==1;
    }); });
    if (data.features.length) {
      const bounds = new maplibregl.LngLatBounds(); data.features.forEach(f=>bounds.extend(f.geometry.coordinates));
      map.fitBounds(bounds,{padding:50,maxZoom:15,duration:0});
    }
    status.textContent = data.features.length ? 'Mapa listo. Selecciona un punto para consultar sus datos.' : 'Mapa listo. Sin registros georreferenciados para estos filtros.';
    container.dataset.ready='true';
  });
  // Expose only the current page map for diagnostics / focused browser tests.
  window.incidentMap = map;
})();
