/* =================================================================
   hub-admin.js
   All logic for the Hub Admin panel.
   ================================================================= */

const INVENTORY_REFRESH_MS = 60000;
const ALGO_STEP_DELAY_MS = 400;
let inventoryInterval = null;
let produceTypesCache = null;
let hubsCache = null;
let currentSection = 'overview';

function initApp() {
  attachListeners();
  const auth = getAuth();
  if (!isLoggedIn() || !auth || auth.role !== 'HUB_ADMIN') {
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
  document.getElementById('btn-run-algorithm').addEventListener('click', runAlgorithm);

  document.querySelectorAll('[data-section]').forEach(function (el) {
    el.addEventListener('click', function () { switchSection(el.dataset.section); });
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
    if (r.role !== 'HUB_ADMIN') {
      showInlineError(err, 'Access denied - Hub Admins only.');
      return;
    }
    saveAuth(r.token, r.user_id, r.role, r.hub_id, r.spoke_id, r.name);
    showMainApp();
  } catch (e) {
    showInlineError(err, e.message || 'Could not sign in.');
  }
}

function logout() {
  if (inventoryInterval) {
    clearInterval(inventoryInterval);
    inventoryInterval = null;
  }
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
  await loadHubInfo();
  switchSection('overview');
}

async function loadHubInfo() {
  const auth = getAuth();
  if (!hubsCache) {
    try { hubsCache = await apiGet('/api/admin/hubs'); }
    catch (e) { hubsCache = []; }
  }
  const hub = hubsCache.find(function (h) { return h.id === auth.hubId; });
  if (hub) {
    document.getElementById('hub-name').textContent = hub.name;
    document.getElementById('hub-city').textContent = hub.city;
    document.getElementById('sidebar-hub-name').textContent = hub.name;
  }
}

function switchSection(sectionName) {
  currentSection = sectionName;
  document.querySelectorAll('.content-section').forEach(function (el) { el.hidden = true; });
  const target = document.getElementById('section-' + sectionName);
  if (target) target.hidden = false;
  document.querySelectorAll('[data-section]').forEach(function (el) {
    el.classList.toggle('active', el.dataset.section === sectionName);
  });
  if (sectionName !== 'inventory' && inventoryInterval) {
    clearInterval(inventoryInterval);
    inventoryInterval = null;
  }
  if (sectionName === 'overview')  loadOverview();
  if (sectionName === 'inventory') loadInventory();
  if (sectionName === 'demand')    loadDemand();
  if (sectionName === 'surplus')   loadSurplus();
  if (sectionName === 'vehicles')  loadVehicles();
  if (sectionName === 'algorithm') resetAlgorithmSection();
}

async function loadOverview() {
  const hubId = getAuth().hubId;
  const container = document.getElementById('overview-stats');
  container.innerHTML = '<div class="loading-state"><div class="spinner"></div></div>';
  try {
    const results = await Promise.all([
      apiGet('/api/hub/' + hubId + '/inventory'),
      apiGet('/api/hub/' + hubId + '/demand'),
      apiGet('/api/hub/' + hubId + '/vehicles'),
      apiGet('/api/hub/' + hubId + '/routes')
    ]);
    const inventory = results[0] || [];
    const demand    = results[1] || [];
    const vehicles  = results[2] || [];
    const routes    = results[3] || [];
    const idleCount = vehicles.filter(function (v) { return v.status === 'IDLE'; }).length;
    const startOfToday = new Date(); startOfToday.setHours(0, 0, 0, 0);
    const routesToday = routes.filter(function (r) {
      const d = parseDateLenient(r.createdAt);
      return d && d.getTime() >= startOfToday.getTime();
    }).length;
    container.innerHTML =
      statCard('📦', inventory.length, 'Inventory Items') +
      statCard('🛒', demand.length,    'Active Demands') +
      statCard('🚛', idleCount,        'Idle Vehicles') +
      statCard('📋', routesToday,      'Routes Today');
  } catch (e) {
    container.innerHTML = '<div class="empty-state">Failed to load overview: ' + esc(e.message) + '</div>';
  }
}

function statCard(icon, value, label) {
  return '<div class="stat-card">'
       +   '<div class="stat-icon">' + icon + '</div>'
       +   '<div class="stat-value">' + esc(value) + '</div>'
       +   '<div class="stat-label">' + esc(label) + '</div>'
       + '</div>';
}

function loadInventory() {
  if (inventoryInterval) clearInterval(inventoryInterval);
  refreshInventory();
  inventoryInterval = setInterval(refreshInventory, INVENTORY_REFRESH_MS);
}

async function refreshInventory() {
  const hubId = getAuth().hubId;
  const wrap = document.getElementById('inventory-table-wrap');
  try {
    const items = await apiGet('/api/hub/' + hubId + '/inventory');
    if (!items.length) {
      wrap.innerHTML = '<div class="empty-state">No inventory recorded at this hub.</div>';
      return;
    }
    const sorted = items.slice().sort(function (a, b) {
      return (Number(a.currentQuality) || 0) - (Number(b.currentQuality) || 0);
    });
    let html = '<table><thead><tr>'
             + '<th>Produce</th><th>Quantity</th><th>Avg Harvest</th><th>Q(t)</th>'
             + '</tr></thead><tbody>';
    for (let i = 0; i < sorted.length; i++) {
      const it = sorted[i];
      const q = Number(it.currentQuality) || 0;
      const qPct = (q * 100).toFixed(0);
      const cls = q >= 0.7 ? 'badge-green' : q >= 0.4 ? 'badge-yellow' : 'badge-red';
      const fillCls = qualityClass(q);
      const qty = Number(it.quantityKg) || 0;
      html += '<tr>'
            +   '<td><strong>' + esc(it.produceName) + '</strong></td>'
            +   '<td>' + qty.toFixed(1) + ' kg</td>'
            +   '<td>' + esc(formatDate(it.avgHarvestDate)) + '</td>'
            +   '<td><span class="badge ' + cls + '">Q=' + q.toFixed(2) + '</span>'
            +     '<div class="quality-bar mt-1"><div class="quality-fill ' + fillCls
            +       '" style="width:' + qPct + '%"></div></div>'
            +   '</td>'
            + '</tr>';
    }
    html += '</tbody></table>';
    wrap.innerHTML = html;
  } catch (e) {
    wrap.innerHTML = '<div class="empty-state">Failed to load inventory: ' + esc(e.message) + '</div>';
  }
}

async function loadDemand() {
  const hubId = getAuth().hubId;
  const wrap = document.getElementById('demand-table-wrap');
  try {
    const items = await apiGet('/api/hub/' + hubId + '/demand');
    if (!items.length) {
      wrap.innerHTML = '<div class="empty-state">No demand recorded at this hub.</div>';
      return;
    }
    let html = '<table><thead><tr>'
             + '<th>Produce</th><th>Required</th><th>Min Quality</th><th>Created</th>'
             + '</tr></thead><tbody>';
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      const required = Number(it.requiredQuantityKg) || 0;
      const minQ = Number(it.minQualityThreshold) || 0;
      html += '<tr>'
            +   '<td><strong>' + esc(it.produceName) + '</strong></td>'
            +   '<td>' + required.toFixed(0) + ' kg</td>'
            +   '<td><span class="badge badge-grey">Q ≥ ' + minQ.toFixed(2) + '</span></td>'
            +   '<td>' + esc(formatDateTime(it.createdAt)) + '</td>'
            + '</tr>';
    }
    html += '</tbody></table>';
    wrap.innerHTML = html;
  } catch (e) {
    wrap.innerHTML = '<div class="empty-state">Failed to load demand: ' + esc(e.message) + '</div>';
  }
}

async function loadSurplus() {
  const hubId = getAuth().hubId;
  const wrap = document.getElementById('surplus-table-wrap');
  try {
    const items = await apiGet('/api/hub/' + hubId + '/surplus');
    if (!items.length) {
      wrap.innerHTML = '<div class="empty-state">No produce data at this hub.</div>';
      return;
    }
    let html = '<table><thead><tr>'
             + '<th>Produce</th><th>Inventory</th><th>Local Demand</th><th>Surplus</th>'
             + '</tr></thead><tbody>';
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      const inv = Number(it.inventory_qty) || 0;
      const dem = Number(it.demand_qty) || 0;
      const sur = Number(it.surplus_qty) || 0;
      const rowCls = sur > 0 ? 'row-surplus' : sur < 0 ? 'row-shortage' : '';
      let badge;
      if (sur > 0) {
        badge = '<span class="badge badge-green">+' + sur.toFixed(0) + ' kg</span>';
      } else if (sur < 0) {
        badge = '<span class="badge badge-red">' + sur.toFixed(0) + ' kg</span>';
      } else {
        badge = '<span class="badge badge-grey">0 kg</span>';
      }
      html += '<tr class="' + rowCls + '">'
            +   '<td><strong>' + esc(it.produce_type) + '</strong></td>'
            +   '<td>' + inv.toFixed(0) + ' kg</td>'
            +   '<td>' + dem.toFixed(0) + ' kg</td>'
            +   '<td>' + badge + '</td>'
            + '</tr>';
    }
    html += '</tbody></table>';
    wrap.innerHTML = html;
  } catch (e) {
    wrap.innerHTML = '<div class="empty-state">Failed to load surplus: ' + esc(e.message) + '</div>';
  }
}

async function loadVehicles() {
  const hubId = getAuth().hubId;
  const container = document.getElementById('vehicles-list');
  container.innerHTML = '<div class="loading-state"><div class="spinner"></div></div>';
  try {
    const vehicles = await apiGet('/api/hub/' + hubId + '/vehicles');
    if (!vehicles.length) {
      container.innerHTML = '<div class="empty-state">No vehicles parked at this hub.</div>';
      return;
    }
    let html = '';
    for (let i = 0; i < vehicles.length; i++) {
      const v = vehicles[i];
      const badgeCls = v.status === 'IDLE' ? 'badge-green' : 'badge-yellow';
      const cap = Number(v.capacityKg) || 0;
      html += '<div class="vehicle-card">'
            +   '<div class="vehicle-header">'
            +     '<h4>' + esc(v.name) + '</h4>'
            +     '<span class="badge ' + badgeCls + '">' + esc(v.status) + '</span>'
            +   '</div>'
            +   '<p class="text-secondary text-small">Capacity: ' + cap.toFixed(0) + ' kg</p>';
      if (v.status === 'IN_TRANSIT') {
        html += '<button class="btn btn-secondary mt-2 view-route-btn" data-vehicle-id="' + v.id + '">View Route</button>'
              + '<div class="route-details" id="route-details-' + v.id + '" hidden></div>';
      }
      html += '</div>';
    }
    container.innerHTML = html;
    container.querySelectorAll('.view-route-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        toggleVehicleRoute(Number(btn.dataset.vehicleId));
      });
    });
  } catch (e) {
    container.innerHTML = '<div class="empty-state">Failed to load vehicles: ' + esc(e.message) + '</div>';
  }
}

async function toggleVehicleRoute(vehicleId) {
  const details = document.getElementById('route-details-' + vehicleId);
  if (!details) return;
  if (!details.hidden) {
    details.hidden = true;
    return;
  }
  details.hidden = false;
  details.innerHTML = '<div class="loading-state"><div class="spinner"></div></div>';
  try {
    const routes = await apiGet('/api/hub/' + getAuth().hubId + '/routes');
    const route = (routes || []).find(function (r) {
      return r.vehicleId === vehicleId && r.status !== 'COMPLETED';
    });
    if (!route) {
      details.innerHTML = '<p class="text-small text-secondary mt-2">No active route on file.</p>';
      return;
    }
    let html = '<p class="text-small mt-2"><strong>Route #' + esc(route.id) + '</strong></p>';
    const stops = (route.stops || []).slice().sort(function (a, b) { return a.stopOrder - b.stopOrder; });
    html += '<p class="text-small">Path: ' + stops.map(function (s) { return esc(s.hubName); }).join(' → ') + '</p>';
    if (route.cargo && route.cargo.length) {
      html += '<ul class="step-list mt-2">';
      for (let i = 0; i < route.cargo.length; i++) {
        const c = route.cargo[i];
        const qty = Number(c.quantityKg) || 0;
        html += '<li class="text-small">' + qty.toFixed(0) + ' kg ' + esc(c.produceName)
              + ' → ' + esc(c.destinationHubName) + '</li>';
      }
      html += '</ul>';
    }
    if (route.requiresColdStorage) {
      html += '<p class="text-small mt-2">❄️ Cold storage required.</p>';
    }
    details.innerHTML = html;
  } catch (e) {
    details.innerHTML = '<p class="text-small text-secondary">Failed to load route: ' + esc(e.message) + '</p>';
  }
}

function resetAlgorithmSection() {
  document.getElementById('algorithm-result').innerHTML = '';
}

async function runAlgorithm() {
  const button = document.getElementById('btn-run-algorithm');
  const resultArea = document.getElementById('algorithm-result');
  const auth = getAuth();
  button.disabled = true;
  button.innerHTML = '<div class="spinner"></div> Running...';
  resultArea.innerHTML = '';
  try {
    if (!produceTypesCache) {
      produceTypesCache = await apiGet('/api/farmer/produce-types');
    }
    if (!hubsCache) {
      hubsCache = await apiGet('/api/admin/hubs');
    }
    const surplusBefore = await apiGet('/api/hub/' + auth.hubId + '/surplus');
    const result = await apiPost('/api/routing/run', { hub_id: auth.hubId });
    button.disabled = false;
    button.innerHTML = '<span class="btn-icon">⚡</span> Run Again';
    animateResult(result, surplusBefore);
  } catch (e) {
    button.disabled = false;
    button.innerHTML = '<span class="btn-icon">⚡</span> Run Algorithm Now';
    showToast('Error: ' + e.message, 'error');
  }
}

function animateResult(result, surplus) {
  const resultArea = document.getElementById('algorithm-result');
  resultArea.innerHTML = '';
  const cards = [
    buildSurplusCard(surplus),
    buildMatchedCard(result),
    buildPriorityCard(result),
    buildRoutePlannedCard(result),
    buildColdStorageCard(result),
    buildSavedCard(result)
  ];
  cards.forEach(function (html, idx) {
    setTimeout(function () {
      const wrap = document.createElement('div');
      wrap.innerHTML = html;
      const card = wrap.firstElementChild;
      resultArea.appendChild(card);
      card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }, idx * ALGO_STEP_DELAY_MS);
  });
}

function buildSurplusCard(surplus) {
  const items = (surplus || []).filter(function (s) { return (Number(s.surplus_qty) || 0) > 0; });
  let body;
  if (!items.length) {
    body = '<p class="text-secondary">No surplus found at this hub. Try another hub.</p>';
  } else {
    body = '<ul class="step-list">';
    for (let i = 0; i < items.length; i++) {
      const s = items[i];
      const sur = Number(s.surplus_qty) || 0;
      body += '<li><strong>' + esc(s.produce_type) + '</strong>: '
            + sur.toFixed(1) + ' kg available beyond local needs</li>';
    }
    body += '</ul>';
  }
  return stepCardHtml(1, '📦', 'Surplus Found',
    'We checked your stock against local demand and kept the leftovers.', body);
}

function buildMatchedCard(result) {
  const cargo = result.cargo || [];
  let body;
  if (!cargo.length) {
    body = '<p class="text-secondary">No matching demand was found at other hubs.</p>';
  } else {
    const groups = new Map();
    for (let i = 0; i < cargo.length; i++) {
      const c = cargo[i];
      if (!groups.has(c.destinationHubId)) {
        groups.set(c.destinationHubId, { name: c.destinationHubName, items: [] });
      }
      groups.get(c.destinationHubId).items.push(c);
    }
    body = '<div class="matched-list">';
    groups.forEach(function (group, hubId) {
      const stop = (result.stops || []).find(function (s) { return s.hubId === hubId; });
      const stopOrder = stop ? stop.stopOrder : null;
      const q = stopOrder !== null && result.qualityAtEachStop
        ? result.qualityAtEachStop[stopOrder]
        : null;
      const qNum = q !== undefined && q !== null ? Number(q) : null;
      const qPct = qNum !== null ? (qNum * 100).toFixed(0) + '%' : '—';
      const qCls = qNum === null ? 'badge-grey'
                 : qNum >= 0.7   ? 'badge-green'
                 : qNum >= 0.4   ? 'badge-yellow'
                 :                 'badge-red';
      body += '<div class="matched-row">'
            +   '<div class="matched-hub">'
            +     '<span>' + esc(group.name) + '</span>'
            +     '<span class="badge ' + qCls + '">Q on arrival: ' + qPct + '</span>'
            +   '</div>'
            +   '<ul class="step-list">';
      for (let j = 0; j < group.items.length; j++) {
        const c = group.items[j];
        const qty = Number(c.quantityKg) || 0;
        body += '<li>' + qty.toFixed(1) + ' kg of <strong>' + esc(c.produceName) + '</strong></li>';
      }
      body += '</ul></div>';
    });
    body += '</div>';
  }
  return stepCardHtml(2, '🤝', 'Demands Matched',
    'We found hubs that need your surplus and checked the produce will arrive fresh enough.', body);
}

function buildPriorityCard(result) {
  const cargo = result.cargo || [];
  if (!cargo.length) {
    return stepCardHtml(3, '⏱️', 'Priority Order', 'Nothing to prioritise - no cargo to ship.', '');
  }
  const ptMap = new Map();
  (produceTypesCache || []).forEach(function (pt) { ptMap.set(pt.id, pt); });
  const sorted = cargo.slice().sort(function (a, b) {
    const la = ptMap.get(a.produceTypeId) ? ptMap.get(a.produceTypeId).lambdaValue : 0;
    const lb = ptMap.get(b.produceTypeId) ? ptMap.get(b.produceTypeId).lambdaValue : 0;
    return lb - la;
  });
  let body = '<ol class="priority-list">';
  for (let i = 0; i < sorted.length; i++) {
    const c = sorted[i];
    const pt = ptMap.get(c.produceTypeId);
    const lambda = pt ? pt.lambdaValue : 0;
    const daysToHalf = lambda > 0 ? (Math.log(2) / lambda).toFixed(1) + ' days' : '∞';
    body += '<li>'
          +   '<div><strong>' + esc(c.produceName) + '</strong></div>'
          +   '<div class="text-small text-secondary">'
          +     'λ = ' + lambda.toFixed(3) + ' per day · ~' + daysToHalf + ' until 50% freshness'
          +   '</div>'
          + '</li>';
  }
  body += '</ol>';
  return stepCardHtml(3, '⏱️', 'Priority Order',
    'Most perishable first - that decides the order we deliver in.', body);
}

function buildRoutePlannedCard(result) {
  const stops = (result.stops || []).slice().sort(function (a, b) { return a.stopOrder - b.stopOrder; });
  if (!stops.length) {
    return stepCardHtml(4, '🗺️', 'Route Planned', 'No route to plan.', '');
  }
  const hubMap = new Map();
  (hubsCache || []).forEach(function (h) { hubMap.set(h.id, h); });
  let body = '<div class="route-chain">';
  for (let i = 0; i < stops.length; i++) {
    const s = stops[i];
    body += '<div class="route-stop">'
          +   '<div class="route-stop-number">' + s.stopOrder + '</div>'
          +   '<div class="route-stop-name">' + esc(s.hubName || ('Hub ' + s.hubId)) + '</div>'
          + '</div>';
    if (i < stops.length - 1) {
      const a = hubMap.get(s.hubId);
      const b = hubMap.get(stops[i + 1].hubId);
      let legHtml = '<div class="route-leg"></div>';
      if (a && b) {
        const km = haversineKm(a.latitude, a.longitude, b.latitude, b.longitude);
        const hours = km / 60.0;
        legHtml = '<div class="route-leg">'
                +   '<span>~' + km.toFixed(0) + ' km</span>'
                +   '<span>' + hours.toFixed(1) + ' h</span>'
                + '</div>';
      }
      body += legHtml;
    }
  }
  body += '</div>';
  return stepCardHtml(4, '🗺️', 'Route Planned',
    'Greedy nearest-neighbour - always head to the closest pending stop next.', body);
}

function buildColdStorageCard(result) {
  const needs = !!result.requiresColdStorage;
  const reason = result.coldStorageReason || '';
  const badge = needs
    ? '<span class="badge badge-yellow">YES - refrigeration needed</span>'
    : '<span class="badge badge-green">NO - regular truck is fine</span>';
  let body = '<div>' + badge + '</div>';
  if (needs && reason) {
    body += '<p class="mt-3">' + esc(reason) + '</p>';
  } else if (!needs) {
    body += '<p class="mt-3 text-secondary">All cargo will arrive within acceptable freshness - no refrigeration needed.</p>';
  }
  return stepCardHtml(5, '❄️', 'Cold Storage Check',
    "We projected freshness at each destination and compared to the buyer's minimum.", body);
}

function buildSavedCard(result) {
  const routeId = result.routeId || 0;
  const vehicle = result.vehicleName || '—';
  const summary = result.humanReadableSummary || '';
  const status  = routeId > 0 ? 'PLANNED' : 'NOT SAVED';
  const badgeCls = routeId > 0 ? 'badge-green' : 'badge-grey';
  let body = '<div class="saved-grid">'
           +   '<div><div class="text-small text-secondary">Route ID</div>'
           +     '<div class="text-large"><strong>#' + esc(routeId) + '</strong></div></div>'
           +   '<div><div class="text-small text-secondary">Vehicle</div>'
           +     '<div class="text-large"><strong>' + esc(vehicle) + '</strong></div></div>'
           +   '<div><div class="text-small text-secondary">Status</div>'
           +     '<div><span class="badge ' + badgeCls + '">' + status + '</span></div></div>'
           + '</div>';
  if (summary) {
    body += '<p class="summary-sentence mt-3">' + esc(summary) + '</p>';
  }
  return stepCardHtml(6, '✅', 'Route Saved',
    'The plan is stored in the database - the truck is now booked for this trip.', body);
}

function stepCardHtml(num, icon, title, subtitle, body) {
  return '<div class="algo-step-card">'
       +   '<div class="algo-step-header">'
       +     '<div class="algo-step-number">' + num + '</div>'
       +     '<div>'
       +       '<h3 class="algo-step-title">' + icon + ' ' + esc(title) + '</h3>'
       +       '<p class="text-small text-secondary">' + esc(subtitle) + '</p>'
       +     '</div>'
       +   '</div>'
       +   '<div class="algo-step-body">' + body + '</div>'
       + '</div>';
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const toRad = function (d) { return d * Math.PI / 180; };
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
          + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2))
          * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function parseDateLenient(input) {
  if (!input && input !== 0) return null;
  if (input instanceof Date) return input;
  if (typeof input === 'number') {
    const d = new Date(input);
    return isNaN(d.getTime()) ? null : d;
  }
  if (typeof input === 'string') {
    const d = new Date(input);
    return isNaN(d.getTime()) ? null : d;
  }
  if (typeof input === 'object') {
    if (input.year !== undefined) {
      return new Date(
        input.year,
        (input.month || 1) - 1,
        input.day || 1,
        input.hour || 0,
        input.minute || 0,
        input.second || 0
      );
    }
    if (input.time !== undefined) return new Date(input.time);
  }
  return null;
}

function formatDate(input) {
  const d = parseDateLenient(input);
  if (!d) return input ? String(input) : '';
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  return d.getDate() + ' ' + months[d.getMonth()] + ' ' + d.getFullYear();
}

function formatDateTime(input) {
  const d = parseDateLenient(input);
  if (!d) return input ? String(input) : '';
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  const date = d.getDate() + ' ' + months[d.getMonth()] + ' ' + d.getFullYear();
  const time = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  return date + ' ' + time;
}

function qualityClass(q) {
  if (q >= 0.7) return 'green';
  if (q >= 0.4) return 'yellow';
  return 'red';
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
