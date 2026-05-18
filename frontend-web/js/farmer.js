/* =================================================================
   farmer.js
   All logic for the farmer portal - onboarding, login, navigation,
   dashboard, and the mandi (marketplace) tab.
   ================================================================= */

const onboardingState = {
  name: '',
  phone: '',
  password: '',
  spokeId: null
};

const DASHBOARD_REFRESH_MS = 30000;
let dashboardInterval = null;
let spokesCache = null;
let produceTypesCache = null;

// Boots the app once the DOM is ready.
function initApp() {
  attachEventListeners();
  if (isLoggedIn()) {
    showMainApp();
  } else {
    showOnboardingScreen();
  }
}

// Wires every click and keypress handler in one place.
function attachEventListeners() {
  document.getElementById('btn-onboard-start').addEventListener('click', function () { showStep(2); });
  document.getElementById('btn-onboard-step2-next').addEventListener('click', advanceFromStep2);
  document.getElementById('btn-onboard-step2-back').addEventListener('click', function () { showStep(1); });
  document.getElementById('btn-onboard-step3-next').addEventListener('click', advanceFromStep3);
  document.getElementById('btn-onboard-step3-back').addEventListener('click', function () { showStep(2); });
  document.getElementById('btn-onboard-step4-next').addEventListener('click', advanceFromStep4);
  document.getElementById('btn-onboard-step4-back').addEventListener('click', function () { showStep(3); });
  document.getElementById('btn-onboard-step5-dashboard').addEventListener('click', showMainApp);
  document.getElementById('btn-step-5-retry').addEventListener('click', registerAccount);

  document.getElementById('link-show-login').addEventListener('click', function (e) {
    e.preventDefault();
    showLoginScreen();
  });
  document.getElementById('link-show-onboarding').addEventListener('click', function (e) {
    e.preventDefault();
    showOnboardingScreen();
  });

  document.getElementById('btn-login-submit').addEventListener('click', login);
  document.getElementById('btn-logout').addEventListener('click', logout);
  document.getElementById('btn-submit-listing').addEventListener('click', submitListing);

  document.getElementById('bottom-nav').addEventListener('click', function (e) {
    const item = e.target.closest('.nav-item');
    if (item && item.dataset.tab) switchTab(item.dataset.tab);
  });

  document.getElementById('input-phone').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') advanceFromStep2();
  });
  document.getElementById('input-password-confirm').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') advanceFromStep3();
  });
  document.getElementById('input-login-password').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') login();
  });
}

// Hides every top-level screen.
function hideAllScreens() {
  document.getElementById('screen-onboarding').hidden = true;
  document.getElementById('screen-login').hidden = true;
  document.getElementById('screen-main').hidden = true;
}

// Shows the onboarding wizard at step 1.
function showOnboardingScreen() {
  hideAllScreens();
  document.getElementById('screen-onboarding').hidden = false;
  showStep(1);
}

// Shows the login form.
function showLoginScreen() {
  hideAllScreens();
  document.getElementById('screen-login').hidden = false;
  document.getElementById('error-login').hidden = true;
  document.getElementById('input-login-phone').value = '';
  document.getElementById('input-login-password').value = '';
  setTimeout(function () { document.getElementById('input-login-phone').focus(); }, 50);
}

// Shows the main app shell.
function showMainApp() {
  hideAllScreens();
  document.getElementById('screen-main').hidden = false;
  const auth = getAuth();
  if (auth) {
    document.getElementById('header-name').textContent = auth.name || 'Farmer';
    findSpokeName(auth.spokeId).then(function (spokeName) {
      document.getElementById('header-spoke').textContent = spokeName ? 'From ' + spokeName : '';
    });
  }
  switchTab('dashboard');
}

// Looks up the spoke name from cache.
async function findSpokeName(spokeId) {
  if (!spokeId) return '';
  try {
    if (!spokesCache) spokesCache = await apiGet('/api/spokes');
    const match = spokesCache.find(function (s) { return s.id === spokeId; });
    return match ? match.name : '';
  } catch (e) { return ''; }
}

// Shows one of the 5 onboarding steps.
function showStep(stepNumber) {
  for (let i = 1; i <= 5; i++) {
    document.getElementById('step-' + i).hidden = (i !== stepNumber);
  }
  document.getElementById('progress-fill').style.width = (stepNumber / 5) * 100 + '%';
  document.querySelectorAll('.step-num').forEach(function (el) {
    const n = Number(el.dataset.step);
    el.classList.toggle('active', n === stepNumber);
    el.classList.toggle('done', n < stepNumber);
  });
  if (stepNumber === 4) populateSpokeDropdown();
  if (stepNumber === 5) registerAccount();
}

// Validates step 2.
function advanceFromStep2() {
  const name = document.getElementById('input-name').value.trim();
  const phone = document.getElementById('input-phone').value.trim();
  const err = document.getElementById('error-step-2');
  err.hidden = true;
  if (name.length < 2) {
    showInlineError(err, 'Please enter your full name.');
    return;
  }
  if (!/^\d{10}$/.test(phone)) {
    showInlineError(err, 'Phone number must be exactly 10 digits.');
    return;
  }
  onboardingState.name = name;
  onboardingState.phone = phone;
  showStep(3);
}

// Validates step 3.
function advanceFromStep3() {
  const p1 = document.getElementById('input-password').value;
  const p2 = document.getElementById('input-password-confirm').value;
  const err = document.getElementById('error-step-3');
  err.hidden = true;
  if (p1.length < 6) {
    showInlineError(err, 'Password must be at least 6 characters.');
    return;
  }
  if (p1 !== p2) {
    showInlineError(err, 'The two passwords do not match.');
    return;
  }
  onboardingState.password = p1;
  showStep(4);
}

// Validates step 4.
function advanceFromStep4() {
  const select = document.getElementById('select-spoke');
  const err = document.getElementById('error-step-4');
  err.hidden = true;
  if (!select.value) {
    showInlineError(err, 'Please choose your village from the list.');
    return;
  }
  onboardingState.spokeId = Number(select.value);
  showStep(5);
}

// Shows an inline error.
function showInlineError(el, message) {
  el.textContent = message;
  el.hidden = false;
}

// Populates the spoke dropdown.
async function populateSpokeDropdown() {
  const select = document.getElementById('select-spoke');
  try {
    if (!spokesCache) spokesCache = await apiGet('/api/spokes');
    select.innerHTML = '<option value="">Pick a village…</option>';
    spokesCache.forEach(function (s) {
      const opt = document.createElement('option');
      opt.value = s.id;
      opt.textContent = s.name;
      select.appendChild(opt);
    });
  } catch (e) {
    select.innerHTML = '<option value="">Could not load villages</option>';
    showToast('Could not load villages: ' + e.message, 'error');
  }
}

// Sends the registration request.
async function registerAccount() {
  document.getElementById('step-5-loading').hidden = false;
  document.getElementById('step-5-success').hidden = true;
  document.getElementById('step-5-error').hidden = true;
  try {
    const payload = {
      name: onboardingState.name,
      phone: onboardingState.phone,
      password: onboardingState.password,
      role: 'FARMER',
      spoke_id: onboardingState.spokeId
    };
    const r = await apiPost('/api/auth/register', payload);
    saveAuth(r.token, r.user_id, r.role, r.hub_id, r.spoke_id, r.name);
    document.getElementById('welcome-message').textContent =
      'Welcome to FASAL, ' + (r.name || 'farmer') + '!';
    document.getElementById('step-5-loading').hidden = true;
    document.getElementById('step-5-success').hidden = false;
  } catch (e) {
    document.getElementById('step-5-loading').hidden = true;
    document.getElementById('step-5-error').hidden = false;
    document.getElementById('step-5-error-message').textContent = e.message;
  }
}

// Submits the login form.
async function login() {
  const phone = document.getElementById('input-login-phone').value.trim();
  const password = document.getElementById('input-login-password').value;
  const err = document.getElementById('error-login');
  err.hidden = true;
  if (!phone || !password) {
    showInlineError(err, 'Please enter both phone and password.');
    return;
  }
  try {
    const r = await apiPost('/api/auth/login', { phone: phone, password: password });
    saveAuth(r.token, r.user_id, r.role, r.hub_id, r.spoke_id, r.name);
    showMainApp();
  } catch (e) {
    showInlineError(err, e.message || 'Could not sign in.');
  }
}

// Clears the session and returns to onboarding.
function logout() {
  if (dashboardInterval) {
    clearInterval(dashboardInterval);
    dashboardInterval = null;
  }
  clearAuth();
  showOnboardingScreen();
}

// Switches between tabs in the main app.
function switchTab(tabName) {
  document.querySelectorAll('.tab-panel').forEach(function (p) { p.hidden = true; });
  const panel = document.getElementById('tab-' + tabName);
  if (panel) panel.hidden = false;
  document.querySelectorAll('#bottom-nav .nav-item').forEach(function (it) {
    it.classList.toggle('active', it.dataset.tab === tabName);
  });
  if (tabName === 'dashboard') {
    loadDashboard();
  } else {
    if (dashboardInterval) {
      clearInterval(dashboardInterval);
      dashboardInterval = null;
    }
  }
  if (tabName === 'mandi') loadMandi();
}

// Loads the dashboard and sets up auto-refresh.
function loadDashboard() {
  if (dashboardInterval) clearInterval(dashboardInterval);
  refreshDashboard();
  dashboardInterval = setInterval(refreshDashboard, DASHBOARD_REFRESH_MS);
}

// Pulls the latest listings.
async function refreshDashboard() {
  const container = document.getElementById('dashboard-listings');
  if (!container.dataset.loaded) {
    container.innerHTML = '<div class="loading-state"><div class="spinner"></div></div>';
  }
  try {
    const listings = await apiGet('/api/farmer/listings');
    container.dataset.loaded = '1';
    renderSummaryCards(listings);
    renderListings(listings);
  } catch (e) {
    container.innerHTML =
      '<div class="empty-state">Could not load listings. ' + esc(e.message) + '</div>';
    if (e.status !== 401) showToast('Dashboard error: ' + e.message, 'error');
  }
}

// Renders the three summary tiles.
function renderSummaryCards(listings) {
  const totalListings = listings.length;
  const totalKg = listings.reduce(function (sum, l) {
    return sum + (Number(l.quantityKg) || 0);
  }, 0);
  const avgQ = totalListings === 0 ? 0 :
    listings.reduce(function (sum, l) {
      return sum + (Number(l.currentQuality) || 0);
    }, 0) / totalListings;
  document.getElementById('dashboard-summary').innerHTML =
    summaryTile(totalListings, 'Listings') +
    summaryTile(totalKg.toFixed(0), 'Total kg') +
    summaryTile(avgQ.toFixed(2), 'Avg Q(t)');
}

// One summary tile.
function summaryTile(value, label) {
  return '<div class="summary-card">' +
           '<p class="summary-value">' + esc(value) + '</p>' +
           '<p class="summary-label">' + esc(label) + '</p>' +
         '</div>';
}

// Sorts by quality ascending and renders listing cards.
function renderListings(listings) {
  const container = document.getElementById('dashboard-listings');
  if (!listings || listings.length === 0) {
    container.innerHTML =
      '<div class="empty-state">' +
        'No listings yet. Tap the <strong>Mandi</strong> tab below to add your first produce.' +
      '</div>';
    return;
  }
  const sorted = listings.slice().sort(function (a, b) {
    return (Number(a.currentQuality) || 0) - (Number(b.currentQuality) || 0);
  });
  container.innerHTML = sorted.map(renderListingCard).join('');
}

// HTML for one listing card.
function renderListingCard(listing) {
  const q = Number(listing.currentQuality) || 0;
  const qPct = Math.max(0, Math.min(100, q * 100)).toFixed(0);
  const qCls = qualityClass(q);
  const qty = Number(listing.quantityKg) || 0;
  return (
    '<div class="listing-card card">' +
      '<div class="listing-header">' +
        '<h4 class="listing-name">' + esc(listing.produceName) + '</h4>' +
        statusBadge(listing.status) +
      '</div>' +
      '<p class="text-small text-secondary">' +
        esc(qty.toFixed(1)) + ' kg • harvested ' + esc(formatDate(listing.harvestDate)) +
      '</p>' +
      '<div class="quality-section">' +
        '<div class="quality-bar">' +
          '<div class="quality-fill ' + qCls + '" style="width:' + qPct + '%"></div>' +
        '</div>' +
        '<p class="quality-label">Freshness: ' + qPct + '%</p>' +
      '</div>' +
    '</div>'
  );
}

// Returns a coloured status badge.
function statusBadge(status) {
  let cls = 'badge-grey';
  if (status === 'IN_TRANSIT') cls = 'badge-yellow';
  if (status === 'DELIVERED')  cls = 'badge-green';
  return '<span class="badge ' + cls + '">' + esc(status || 'PENDING') + '</span>';
}

// Loads the mandi tab.
async function loadMandi() {
  try {
    if (!produceTypesCache) {
      produceTypesCache = await apiGet('/api/farmer/produce-types');
    }
    populateProduceTypeDropdown(produceTypesCache);
  } catch (e) {
    showToast('Could not load produce types: ' + e.message, 'error');
  }
  setHarvestDateDefault();
  await refreshMandiListings();
}

// Populates the produce-type dropdown.
function populateProduceTypeDropdown(types) {
  const sel = document.getElementById('select-produce-type');
  sel.innerHTML = '<option value="">Choose produce…</option>';
  types.forEach(function (t) {
    const opt = document.createElement('option');
    opt.value = t.id;
    opt.textContent = t.name + ' (' + t.unit + ')';
    sel.appendChild(opt);
  });
}

// Sets the harvest-date field default to today.
function setHarvestDateDefault() {
  const input = document.getElementById('input-harvest-date');
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  const todayIso = y + '-' + m + '-' + d;
  input.max = todayIso;
  if (!input.value) input.value = todayIso;
}

// Loads the mandi listings.
async function refreshMandiListings() {
  const container = document.getElementById('mandi-listings');
  container.innerHTML = '<div class="loading-state"><div class="spinner"></div></div>';
  try {
    const listings = await apiGet('/api/farmer/listings');
    renderMandiListings(listings);
  } catch (e) {
    container.innerHTML =
      '<div class="empty-state">Could not load mandi listings.</div>';
    showToast('Mandi error: ' + e.message, 'error');
  }
}

// Renders the mandi listings.
function renderMandiListings(listings) {
  const container = document.getElementById('mandi-listings');
  if (!listings || listings.length === 0) {
    container.innerHTML =
      '<div class="empty-state">No active listings at your hub yet.</div>';
    return;
  }
  const sorted = listings.slice().sort(function (a, b) {
    return (Number(b.currentQuality) || 0) - (Number(a.currentQuality) || 0);
  });
  container.innerHTML = sorted.map(renderMandiCard).join('');
}

// HTML for one mandi card.
function renderMandiCard(listing) {
  const q = Number(listing.currentQuality) || 0;
  const qPct = Math.max(0, Math.min(100, q * 100)).toFixed(0);
  const qCls = qualityClass(q);
  const qty = Number(listing.quantityKg) || 0;
  const auth = getAuth() || {};
  const isMine = listing.farmerId === auth.userId;
  return (
    '<div class="mandi-card card' + (isMine ? ' is-mine' : '') + '">' +
      '<div class="listing-header">' +
        '<h4 class="listing-name">' + esc(listing.produceName) + '</h4>' +
        (isMine ? '<span class="badge badge-green">You</span>' : '') +
      '</div>' +
      '<p class="text-small text-secondary">' +
        esc(qty.toFixed(1)) + ' kg • harvested ' + esc(formatDate(listing.harvestDate)) +
      '</p>' +
      '<div class="quality-section">' +
        '<div class="quality-bar">' +
          '<div class="quality-fill ' + qCls + '" style="width:' + qPct + '%"></div>' +
        '</div>' +
        '<p class="quality-label">Freshness: ' + qPct + '%</p>' +
      '</div>' +
    '</div>'
  );
}

// Validates the form and POSTs a new listing.
async function submitListing() {
  const typeId = document.getElementById('select-produce-type').value;
  const qtyStr = document.getElementById('input-quantity').value;
  const date = document.getElementById('input-harvest-date').value;

  if (!typeId) {
    showToast('Please choose a produce type.', 'error');
    return;
  }
  const qtyNum = parseFloat(qtyStr);
  if (!qtyNum || qtyNum <= 0) {
    showToast('Quantity must be a positive number.', 'error');
    return;
  }
  if (!date) {
    showToast('Please pick a harvest date.', 'error');
    return;
  }
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const picked = new Date(date);
  if (picked.getTime() > today.getTime()) {
    showToast('Harvest date cannot be in the future.', 'error');
    return;
  }

  const button = document.getElementById('btn-submit-listing');
  button.disabled = true;
  button.textContent = 'Adding…';
  try {
    await apiPost('/api/farmer/listings', {
      produce_type_id: Number(typeId),
      quantity_kg: qtyNum,
      harvest_date: date
    });
    showToast('Listing added successfully!');
    document.getElementById('select-produce-type').value = '';
    document.getElementById('input-quantity').value = '';
    await refreshMandiListings();
  } catch (e) {
    showToast('Error: ' + e.message, 'error');
  } finally {
    button.disabled = false;
    button.textContent = 'Add Listing';
  }
}

// Shows a short notification.
function showToast(message, type) {
  type = type || 'success';
  const t = document.createElement('div');
  t.className = 'toast ' + (type === 'error' ? 'error' : 'success');
  t.textContent = message;
  document.body.appendChild(t);
  requestAnimationFrame(function () { t.classList.add('visible'); });
  setTimeout(function () {
    t.classList.remove('visible');
    setTimeout(function () {
      if (t.parentNode) t.parentNode.removeChild(t);
    }, 300);
  }, 3000);
}

// Formats a date string.
function formatDate(input) {
  if (!input) return '';
  let d;
  if (typeof input === 'string') {
    d = new Date(input);
  } else if (typeof input === 'object' && input !== null && input.year !== undefined) {
    d = new Date(input.year, (input.month || 1) - 1, input.day || 1);
  } else {
    return String(input);
  }
  if (isNaN(d.getTime())) return String(input);
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  return d.getDate() + ' ' + months[d.getMonth()] + ' ' + d.getFullYear();
}

// Maps Q(t) to a CSS class.
function qualityClass(q) {
  if (q >= 0.7) return 'green';
  if (q >= 0.4) return 'yellow';
  return 'red';
}

// HTML-escapes a value.
function esc(s) {
  if (s === null || s === undefined) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

document.addEventListener('DOMContentLoaded', initApp);
