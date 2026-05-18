/* =================================================================
   super-admin.js
   National Operations Console - Leaflet map, Chart.js graph, and
   the live step-through algorithm demo.
   ================================================================= */

let map = null;
let hubsData = [];
let spokesData = [];
let vehiclesData = [];
let routesData = [];
let produceTypesData = [];

let hubLayer = null;
let spokeLayer = null;
let vehicleLayer = null;
let routeLayer = null;
let demoRouteLayer = null;
let demoVehicleMarker = null;
let qualityChart = null;

const toggleState = {
  hubs: true,
  spokes: false,
  vehicles: true,
  routes: true
};

const INDIA_CENTER = [20.5937, 78.9629];
const INDIA_ZOOM = 5;
const CHART_MAX_DAYS = 30;
const COLD_STORAGE_FACTOR = 0.3;
const CHART_MIN_THRESHOLD = 0.5;
const DEMO_STEP_PAUSE_MS = 1500;
const DEMO_LEG_DURATION_MS = 1500;

function initApp() {
  attachListeners();
  const auth = getAuth();
  if (!isLoggedIn() || !auth || auth.role !== 'SUPER_ADMIN') {
    showLogin();
  } else {
    showMainApp();
  }
}

function attachListeners() {
  document.getElementById('btn-login').addEventListener('click', login);
  document.getElementById('input-password').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') login();
  });
  document.getElementById('btn-logout').addEventListener('click', logout);
  document.getElementById('btn-clear-map').addEventListener('click', clearAll);
  document.getElementById('btn-run-demo').addEventListener('click', runDemoAlgorithm);
  document.getElementById('select-produce').addEventListener('change', function (e) {
    updateQualityChart(Number(e.target.value));
  });

  document.querySelectorAll('[data-toggle]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      const key = btn.dataset.toggle;
      toggleState[key] = !toggleState[key];
      btn.classList.toggle('active', toggleState[key]);
      if (key === 'hubs')     toggleHubs();
      if (key === 'spokes')   toggleSpokes();
      if (key === 'vehicles') toggleVehicles();
      if (key === 'routes')   toggleRoutes();
    });
  });

  document.getElementById('map').addEventListener('click', function (e) {
    const btn = e.target.closest('[data-pop-action="run-here"]');
    if (btn && btn.dataset.hubId) {
      const select = document.getElementById('select-hub');
      select.value = btn.dataset.hubId;
      map.closePopup();
      runDemoAlgorithm();
    }
  });
}

async function login() {
  const phone = document.getElementById('input-phone').value.trim();
  const password = document.getElementById('input-password').value;
  const err = document.getElementById('error-login');
  err.hidden = true;
  if (!phone || !password) {
    showInlineError(err, 'Please enter both phone and password.');
    return;
  }
  try {
    const r = await apiPost('/api/auth/login', { phone: phone, password: password });
    if (r.role !== 'SUPER_ADMIN') {
      showInlineError(err, 'Access denied - Super Admins only.');
      return;
    }
    saveAuth(r.token, r.user_id, r.role, r.hub_id, r.spoke_id, r.name);
    showMainApp();
  } catch (e) {
    showInlineError(err, e.message || 'Could not sign in.');
  }
}

function logout() {
  clearAuth();
  showLogin();
}

function showLogin() {
  document.getElementById('screen-login').hidden = false;
  document.getElementById('screen-main').hidden = true;
  document.getElementById('error-login').hidden = true;
  setTimeout(function () { document.getElementById('input-phone').focus(); }, 50);
}

async function showMainApp() {
  document.getElementById('screen-login').hidden = true;
  document.getElementById('screen-main').hidden = false;
  initMap();
  await loadStats();
  await loadAllData();
  populateHubDropdown();
  initQualityGraph();
}

async function loadStats() {
  try {
    const o = await apiGet('/api/admin/overview');
    document.getElementById('stat-listings').textContent = numOrDash(o.total_listings);
    document.getElementById('stat-hubs').textContent     = numOrDash(o.hubs_count);
    const inTransit = (Number(o.total_vehicles) || 0) - (Number(o.idle_vehicles) || 0);
    document.getElementById('stat-transit').textContent  = String(Math.max(0, inTransit));
    document.getElementById('stat-routes').textContent   = numOrDash(o.active_routes);
  } catch (e) {
    showToast('Failed to load stats: ' + e.message, 'error');
  }
}

function numOrDash(v) {
  return (v === null || v === undefined) ? '—' : String(v);
}

function initMap() {
  if (map) return;
  map = L.map('map', { zoomControl: true }).setView(INDIA_CENTER, INDIA_ZOOM);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap contributors',
    maxZoom: 18
  }).addTo(map);
}

async function loadAllData() {
  await Promise.all([loadHubs(), loadSpokes(), loadVehicles(), loadRoutes()]);
}

async function loadHubs() {
  try {
    hubsData = await apiGet('/api/admin/hubs');
    if (toggleState.hubs) renderHubMarkers();
  } catch (e) { showToast('Failed to load hubs: ' + e.message, 'error'); }
}

async function loadSpokes() {
  try {
    spokesData = await apiGet('/api/admin/spokes');
    if (toggleState.spokes) renderSpokeMarkers();
  } catch (e) { showToast('Failed to load spokes: ' + e.message, 'error'); }
}

async function loadVehicles() {
  try {
    vehiclesData = await apiGet('/api/admin/vehicles');
    if (toggleState.vehicles) renderVehicleMarkers();
  } catch (e) { showToast('Failed to load vehicles: ' + e.message, 'error'); }
}

async function loadRoutes() {
  try {
    routesData = await apiGet('/api/admin/routes');
    if (toggleState.routes) renderRouteLines();
  } catch (e) { showToast('Failed to load routes: ' + e.message, 'error'); }
}

function toggleHubs() {
  if (toggleState.hubs) renderHubMarkers();
  else if (hubLayer) { map.removeLayer(hubLayer); hubLayer = null; }
}

function toggleSpokes() {
  if (toggleState.spokes) renderSpokeMarkers();
  else if (spokeLayer) { map.removeLayer(spokeLayer); spokeLayer = null; }
}

function toggleVehicles() {
  if (toggleState.vehicles) renderVehicleMarkers();
  else if (vehicleLayer) { map.removeLayer(vehicleLayer); vehicleLayer = null; }
}

function toggleRoutes() {
  if (toggleState.routes) renderRouteLines();
  else if (routeLayer) { map.removeLayer(routeLayer); routeLayer = null; }
}

function clearAll() {
  if (hubLayer)          { map.removeLayer(hubLayer);          hubLayer = null; }
  if (spokeLayer)        { map.removeLayer(spokeLayer);        spokeLayer = null; }
  if (vehicleLayer)      { map.removeLayer(vehicleLayer);      vehicleLayer = null; }
  if (routeLayer)        { map.removeLayer(routeLayer);        routeLayer = null; }
  if (demoRouteLayer)    { map.removeLayer(demoRouteLayer);    demoRouteLayer = null; }
  if (demoVehicleMarker) { map.removeLayer(demoVehicleMarker); demoVehicleMarker = null; }
  document.querySelectorAll('[data-toggle]').forEach(function (btn) {
    toggleState[btn.dataset.toggle] = false;
    btn.classList.remove('active');
  });
}

function renderHubMarkers() {
  if (hubLayer) map.removeLayer(hubLayer);
  hubLayer = L.layerGroup();
  hubsData.forEach(function (hub) {
    const marker = L.circleMarker([hub.latitude, hub.longitude], {
      radius: 14,
      fillColor: '#4CAF50',
      color: '#388E3C',
      weight: 3,
      opacity: 1,
      fillOpacity: 0.75
    });
    marker.bindPopup(buildLoadingPopup(hub), { maxWidth: 300, autoPan: true });
    marker.on('popupopen', function () { populateHubPopup(marker, hub); });
    marker.bindTooltip(hub.name, { direction: 'top', offset: [0, -10] });
    hubLayer.addLayer(marker);
  });
  hubLayer.addTo(map);
}

function buildLoadingPopup(hub) {
  return '<div class="hub-marker-popup">'
       +   '<h4>' + esc(hub.name) + '</h4>'
       +   '<p class="city-name">' + esc(hub.city) + '</p>'
       +   '<p class="text-small text-secondary">Loading hub details…</p>'
       + '</div>';
}

async function populateHubPopup(marker, hub) {
  try {
    const results = await Promise.all([
      apiGet('/api/hub/' + hub.id + '/surplus'),
      apiGet('/api/hub/' + hub.id + '/demand')
    ]);
    const surplus = results[0] || [];
    const demand  = results[1] || [];
    const topSurplus = surplus
      .filter(function (s) { return (Number(s.surplus_qty) || 0) > 0; })
      .sort(function (a, b) { return (Number(b.surplus_qty) || 0) - (Number(a.surplus_qty) || 0); })
      .slice(0, 3);
    const topDemand = demand.slice()
      .sort(function (a, b) { return (Number(b.requiredQuantityKg) || 0) - (Number(a.requiredQuantityKg) || 0); })
      .slice(0, 3);
    let html = '<div class="hub-marker-popup">'
             +   '<h4>' + esc(hub.name) + '</h4>'
             +   '<p class="city-name">' + esc(hub.city) + '</p>';
    html += '<div class="pop-section-title">Top Surplus</div>';
    if (topSurplus.length === 0) {
      html += '<p class="text-small text-secondary">None.</p>';
    } else {
      html += '<ul class="pop-list">';
      topSurplus.forEach(function (s) {
        const sur = Number(s.surplus_qty) || 0;
        html += '<li>+' + sur.toFixed(0) + ' kg <strong>' + esc(s.produce_type) + '</strong></li>';
      });
      html += '</ul>';
    }
    html += '<div class="pop-section-title">Top Demand</div>';
    if (topDemand.length === 0) {
      html += '<p class="text-small text-secondary">None.</p>';
    } else {
      html += '<ul class="pop-list">';
      topDemand.forEach(function (d) {
        const req = Number(d.requiredQuantityKg) || 0;
        html += '<li>Needs ' + req.toFixed(0) + ' kg <strong>' + esc(d.produceName) + '</strong></li>';
      });
      html += '</ul>';
    }
    html += '<button class="pop-button" data-pop-action="run-here" data-hub-id="'
         +   hub.id + '">⚡ Run Algorithm Here</button>';
    html += '</div>';
    const popup = marker.getPopup();
    if (popup) popup.setContent(html);
  } catch (e) {
    const popup = marker.getPopup();
    if (popup) {
      popup.setContent('<div class="hub-marker-popup"><h4>' + esc(hub.name) + '</h4>'
                     + '<p>Failed to load: ' + esc(e.message) + '</p></div>');
    }
  }
}

function renderSpokeMarkers() {
  if (spokeLayer) map.removeLayer(spokeLayer);
  spokeLayer = L.layerGroup();
  const hubNameById = new Map();
  hubsData.forEach(function (h) { hubNameById.set(h.id, h.name); });
  spokesData.forEach(function (s) {
    const marker = L.circleMarker([s.latitude, s.longitude], {
      radius: 5,
      fillColor: '#9E9E9E',
      color: '#616161',
      weight: 1,
      opacity: 1,
      fillOpacity: 0.7
    });
    const parent = hubNameById.get(s.hubId) || 'Unknown hub';
    marker.bindTooltip(
      '<strong>' + esc(s.name) + '</strong><br>'
      + '<span class="text-small">Under ' + esc(parent) + '</span>',
      { direction: 'top' }
    );
    spokeLayer.addLayer(marker);
  });
  spokeLayer.addTo(map);
}

function renderVehicleMarkers() {
  if (vehicleLayer) map.removeLayer(vehicleLayer);
  vehicleLayer = L.layerGroup();
  const hubById = new Map();
  hubsData.forEach(function (h) { hubById.set(h.id, h); });
  const byHub = new Map();
  vehiclesData.forEach(function (v) {
    if (!byHub.has(v.currentHubId)) byHub.set(v.currentHubId, []);
    byHub.get(v.currentHubId).push(v);
  });
  byHub.forEach(function (vehicles, hubId) {
    const hub = hubById.get(hubId);
    if (!hub) return;
    vehicles.forEach(function (v, idx) {
      const angle = (2 * Math.PI / Math.max(vehicles.length, 1)) * idx;
      const radiusDeg = 0.25;
      const lat = hub.latitude + radiusDeg * Math.cos(angle);
      const lng = hub.longitude + radiusDeg * Math.sin(angle);
      const transitCls = v.status === 'IN_TRANSIT' ? 'in-transit' : '';
      const icon = L.divIcon({
        className: 'vehicle-marker-wrap',
        html: '<div class="vehicle-icon ' + transitCls + '">🚛</div>',
        iconSize: [32, 32],
        iconAnchor: [16, 16]
      });
      const marker = L.marker([lat, lng], { icon: icon });
      marker.bindTooltip(buildVehicleTooltip(v), { sticky: true, direction: 'top' });
      vehicleLayer.addLayer(marker);
    });
  });
  vehicleLayer.addTo(map);
}

function buildVehicleTooltip(v) {
  const cap = Number(v.capacityKg) || 0;
  const badgeCls = v.status === 'IDLE' ? 'badge-green' : 'badge-yellow';
  let html = '<div class="vehicle-tooltip">'
           +   '<strong>' + esc(v.name) + '</strong><br>'
           +   'Capacity: ' + cap.toFixed(0) + ' kg<br>'
           +   'Status: <span class="badge ' + badgeCls + '">' + esc(v.status) + '</span>';
  if (v.status === 'IN_TRANSIT') {
    const route = routesData.find(function (r) { return r.vehicleId === v.id && r.status !== 'COMPLETED'; });
    if (route) {
      html += '<br><br><strong>Cargo:</strong><ul style="margin:4px 0 0;padding-left:18px;">';
      (route.cargo || []).forEach(function (c) {
        const qty = Number(c.quantityKg) || 0;
        html += '<li>' + qty.toFixed(0) + ' kg ' + esc(c.produceName)
              + ' → ' + esc(c.destinationHubName) + '</li>';
      });
      html += '</ul>';
      if (route.requiresColdStorage) html += '<br>❄️ Cold storage required';
    }
  }
  html += '</div>';
  return html;
}

function renderRouteLines() {
  if (routeLayer) map.removeLayer(routeLayer);
  routeLayer = L.layerGroup();
  const hubById = new Map();
  hubsData.forEach(function (h) { hubById.set(h.id, h); });
  routesData.forEach(function (route) {
    if (route.status === 'COMPLETED') return;
    const stops = (route.stops || []).slice().sort(function (a, b) { return a.stopOrder - b.stopOrder; });
    if (stops.length < 2) return;
    const coords = stops
      .map(function (s) { return hubById.get(s.hubId); })
      .filter(function (h) { return !!h; })
      .map(function (h) { return [h.latitude, h.longitude]; });
    if (coords.length < 2) return;
    const isActive = route.status === 'ACTIVE';
    const style = isActive
      ? { color: '#2196F3', weight: 3, opacity: 0.85 }
      : { color: '#FF9800', weight: 2, opacity: 0.85, dashArray: '8,8' };
    const polyline = L.polyline(coords, style);
    polyline.bindPopup(buildRoutePopup(route));
    routeLayer.addLayer(polyline);
  });
  routeLayer.addTo(map);
}

function buildRoutePopup(route) {
  const statusCls = route.status === 'ACTIVE' ? 'badge-yellow' : 'badge-grey';
  let html = '<div class="hub-marker-popup">'
           +   '<h4>Route #' + esc(route.id) + '</h4>'
           +   '<p>Vehicle: <strong>' + esc(route.vehicleName || 'Truck') + '</strong></p>'
           +   '<p>Stops: ' + (route.stops || []).length + '</p>'
           +   '<p>Status: <span class="badge ' + statusCls + '">' + esc(route.status) + '</span></p>';
  if (route.requiresColdStorage) html += '<p>❄️ Cold storage required</p>';
  html += '</div>';
  return html;
}

function initQualityGraph() {
  apiGet('/api/farmer/produce-types').then(function (types) {
    produceTypesData = types || [];
    const select = document.getElementById('select-produce');
    select.innerHTML = '';
    produceTypesData.forEach(function (t) {
      const opt = document.createElement('option');
      opt.value = t.id;
      opt.textContent = t.name;
      select.appendChild(opt);
    });
    const def = produceTypesData.find(function (t) { return t.name === 'tomato'; }) || produceTypesData[0];
    if (def) {
      select.value = def.id;
      updateQualityChart(def.id);
    }
  }).catch(function (e) {
    showToast('Failed to load produce types: ' + e.message, 'error');
  });
}

function updateQualityChart(produceTypeId) {
  const produce = produceTypesData.find(function (t) { return t.id === produceTypeId; });
  if (!produce) return;
  const lambda = Number(produce.lambdaValue) || 0;
  const labels = [];
  const normal = [];
  const cold = [];
  const thresholdLine = [];
  for (let t = 0; t <= CHART_MAX_DAYS; t++) {
    labels.push(t);
    normal.push(Math.exp(-lambda * t));
    cold.push(Math.exp(-lambda * COLD_STORAGE_FACTOR * t));
    thresholdLine.push(CHART_MIN_THRESHOLD);
  }
  const canvas = document.getElementById('quality-chart');
  if (qualityChart) qualityChart.destroy();
  qualityChart = new Chart(canvas.getContext('2d'), {
    type: 'line',
    data: {
      labels: labels,
      datasets: [
        {
          label: 'Normal Storage',
          data: normal,
          borderColor: '#4CAF50',
          backgroundColor: 'rgba(76, 175, 80, 0.10)',
          borderWidth: 2.5,
          fill: false,
          tension: 0.15,
          pointRadius: 0,
          pointHoverRadius: 5
        },
        {
          label: 'Cold Storage (30% rate)',
          data: cold,
          borderColor: '#2196F3',
          backgroundColor: 'rgba(33, 150, 243, 0.10)',
          borderWidth: 2.5,
          fill: false,
          tension: 0.15,
          pointRadius: 0,
          pointHoverRadius: 5
        },
        {
          label: 'Typical min threshold (' + CHART_MIN_THRESHOLD.toFixed(1) + ')',
          data: thresholdLine,
          borderColor: '#F44336',
          borderWidth: 1.5,
          borderDash: [6, 6],
          fill: false,
          pointRadius: 0
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { position: 'top', labels: { boxWidth: 14, font: { size: 12 } } },
        title: {
          display: true,
          text: produce.name + '  -  λ = ' + lambda.toFixed(3) + ' per day'
        },
        tooltip: {
          callbacks: {
            label: function (ctx) {
              return ctx.dataset.label + ': Q = ' + Number(ctx.parsed.y).toFixed(3);
            }
          }
        }
      },
      scales: {
        x: {
          title: { display: true, text: 'Days since harvest' },
          ticks: { maxTicksLimit: 11 }
        },
        y: {
          min: 0,
          max: 1,
          title: { display: true, text: 'Quality Q(t)' }
        }
      }
    }
  });
}

function populateHubDropdown() {
  const select = document.getElementById('select-hub');
  select.innerHTML = '';
  hubsData.forEach(function (h) {
    const opt = document.createElement('option');
    opt.value = h.id;
    opt.textContent = h.name + ' (' + h.city + ')';
    select.appendChild(opt);
  });
}

async function runDemoAlgorithm() {
  const hubId = Number(document.getElementById('select-hub').value);
  if (!hubId) {
    showToast('Pick a source hub first.', 'error');
    return;
  }
  const button = document.getElementById('btn-run-demo');
  const resultArea = document.getElementById('demo-result');
  button.disabled = true;
  button.textContent = 'Running…';
  resultArea.innerHTML = '<div class="loading-state"><div class="spinner"></div></div>';
  try {
    const result = await apiPost('/api/routing/run', { hub_id: hubId });
    button.disabled = false;
    button.textContent = '▶ Run Algorithm for Selected Hub';
    animateDemoOnMap(result, hubId);
  } catch (e) {
    button.disabled = false;
    button.textContent = '▶ Run Algorithm for Selected Hub';
    resultArea.innerHTML = '<div class="error-message">' + esc(e.message) + '</div>';
  }
}

function animateDemoOnMap(result, sourceHubId) {
  const resultArea = document.getElementById('demo-result');
  resultArea.innerHTML = '';
  if (demoRouteLayer)    { map.removeLayer(demoRouteLayer);    demoRouteLayer = null; }
  if (demoVehicleMarker) { map.removeLayer(demoVehicleMarker); demoVehicleMarker = null; }

  const hubById = new Map();
  hubsData.forEach(function (h) { hubById.set(h.id, h); });
  const stops = (result.stops || []).slice().sort(function (a, b) { return a.stopOrder - b.stopOrder; });
  if (stops.length === 0) {
    resultArea.innerHTML = '<div class="demo-step-card">'
                         + esc(result.humanReadableSummary || 'No stops planned for this hub.')
                         + '</div>';
    return;
  }
  const sourceHub = hubById.get(sourceHubId) || hubById.get(stops[0].hubId);
  if (sourceHub) {
    map.flyTo([sourceHub.latitude, sourceHub.longitude], 6, { duration: 1.0 });
  }
  demoRouteLayer = L.layerGroup().addTo(map);
  const activeIcon = L.divIcon({
    className: 'active-vehicle-wrap',
    html: '<div class="active-vehicle-icon">🚛</div>',
    iconSize: [42, 42],
    iconAnchor: [21, 21]
  });
  if (sourceHub) {
    demoVehicleMarker = L.marker([sourceHub.latitude, sourceHub.longitude], {
      icon: activeIcon,
      zIndexOffset: 1000
    }).addTo(map);
  }
  const startCard = document.createElement('div');
  startCard.className = 'demo-step-card';
  startCard.innerHTML = '<strong>Start: ' + esc(sourceHub ? sourceHub.name : 'Source hub') + '</strong><br>'
                      + 'Vehicle <strong>' + esc(result.vehicleName || '—') + '</strong> is loading.';
  resultArea.appendChild(startCard);

  let transitionIdx = 0;

  function processNextTransition() {
    if (transitionIdx >= stops.length - 1) {
      finishAnimation();
      return;
    }
    const fromStop = stops[transitionIdx];
    const toStop   = stops[transitionIdx + 1];
    const fromHub  = hubById.get(fromStop.hubId);
    const toHub    = hubById.get(toStop.hubId);
    if (!fromHub || !toHub) {
      transitionIdx++;
      processNextTransition();
      return;
    }
    const segment = L.polyline(
      [[fromHub.latitude, fromHub.longitude], [toHub.latitude, toHub.longitude]],
      { color: '#FF6F00', weight: 4, opacity: 0.9 }
    );
    demoRouteLayer.addLayer(segment);
    animateVehicleAlong(fromHub, toHub, function () {
      const cargoHere = (result.cargo || []).filter(function (c) { return c.destinationHubId === toStop.hubId; });
      const q = result.qualityAtEachStop ? Number(result.qualityAtEachStop[toStop.stopOrder]) : null;
      const qNum = q !== null && !isNaN(q) ? q : null;
      const qPct = qNum !== null ? (qNum * 100).toFixed(0) + '%' : '—';
      const qCls = qNum === null ? 'badge-grey'
                 : qNum >= 0.7   ? 'badge-green'
                 : qNum >= 0.4   ? 'badge-yellow'
                 :                 'badge-red';
      let popup = '<div class="hub-marker-popup">'
                +   '<h4>Stop ' + toStop.stopOrder + ': ' + esc(toStop.hubName || toHub.name) + '</h4>';
      if (cargoHere.length > 0) {
        popup += '<div class="pop-section-title">Delivering</div><ul class="pop-list">';
        cargoHere.forEach(function (c) {
          const qty = Number(c.quantityKg) || 0;
          popup += '<li>' + qty.toFixed(0) + ' kg <strong>' + esc(c.produceName) + '</strong></li>';
        });
        popup += '</ul>';
      }
      popup += '<p>Quality on arrival: <span class="badge ' + qCls + '">' + qPct + '</span></p>';
      popup += '<p>Cold storage: ' + (result.requiresColdStorage ? '❄️ YES' : 'NO') + '</p>';
      popup += '</div>';
      L.popup({ closeButton: false, autoClose: true, autoPan: false })
        .setLatLng([toHub.latitude, toHub.longitude])
        .setContent(popup)
        .openOn(map);

      const stepCard = document.createElement('div');
      stepCard.className = 'demo-step-card';
      let cargoText = '';
      cargoHere.forEach(function (c) {
        const qty = Number(c.quantityKg) || 0;
        cargoText += qty.toFixed(0) + ' kg ' + c.produceName + ', ';
      });
      cargoText = cargoText.replace(/,\s*$/, '') || 'No cargo here';
      stepCard.innerHTML =
          '<strong>Stop ' + toStop.stopOrder + ': ' + esc(toHub.name) + '</strong><br>'
        + esc(cargoText) + '<br>'
        + 'Q on arrival: <span class="badge ' + qCls + '">' + qPct + '</span>';
      resultArea.appendChild(stepCard);

      transitionIdx++;
      setTimeout(processNextTransition, DEMO_STEP_PAUSE_MS);
    });
  }

  function finishAnimation() {
    const lastStop = stops[stops.length - 1];
    const lastHub = hubById.get(lastStop.hubId);
    if (lastHub) {
      const summary = '<div class="hub-marker-popup">'
                    +   '<h4>🎉 Route Complete</h4>'
                    +   '<p>Vehicle now based at <strong>' + esc(lastHub.name) + '</strong>.</p>'
                    +   '<p class="text-small">' + esc(result.humanReadableSummary || '') + '</p>'
                    + '</div>';
      L.popup({ closeButton: true, autoClose: false })
        .setLatLng([lastHub.latitude, lastHub.longitude])
        .setContent(summary)
        .openOn(map);
    }
    const finalCard = document.createElement('div');
    finalCard.className = 'demo-step-card';
    finalCard.innerHTML = '<strong>✅ Route Complete</strong><br>' + esc(result.humanReadableSummary || '');
    resultArea.appendChild(finalCard);
    setTimeout(loadRoutes, 600);
  }

  setTimeout(processNextTransition, DEMO_STEP_PAUSE_MS);
}

function animateVehicleAlong(fromHub, toHub, done) {
  if (!demoVehicleMarker) { if (done) done(); return; }
  const startLat = fromHub.latitude;
  const startLng = fromHub.longitude;
  const endLat   = toHub.latitude;
  const endLng   = toHub.longitude;
  const startedAt = performance.now();
  function frame(now) {
    const elapsed = now - startedAt;
    const t = Math.min(elapsed / DEMO_LEG_DURATION_MS, 1);
    const lat = startLat + (endLat - startLat) * t;
    const lng = startLng + (endLng - startLng) * t;
    demoVehicleMarker.setLatLng([lat, lng]);
    if (t < 1) requestAnimationFrame(frame);
    else if (done) done();
  }
  requestAnimationFrame(frame);
}

function esc(s) {
  if (s === null || s === undefined) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function showToast(message, type) {
  type = type || 'success';
  const t = document.createElement('div');
  t.className = 'toast ' + (type === 'error' ? 'error' : 'success');
  t.textContent = message;
  document.body.appendChild(t);
  requestAnimationFrame(function () { t.classList.add('visible'); });
  setTimeout(function () {
    t.classList.remove('visible');
    setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 300);
  }, 3000);
}

function showInlineError(el, message) {
  el.textContent = message;
  el.hidden = false;
}

document.addEventListener('DOMContentLoaded', initApp);
