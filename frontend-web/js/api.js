/* =================================================================
   api.js
   Shared API helpers and auth-token storage for every FASAL page.
   Loaded BEFORE the per-page script (farmer.js, hub-admin.js, super-admin.js).
   ================================================================= */

// Single place to change the backend URL - edit this one line for deployment.
const API_BASE = 'http://localhost:4567';

// Key under which we store the logged-in user's auth bundle in localStorage.
const AUTH_STORAGE_KEY = 'fasal_auth';

// Wrapper around fetch that automatically adds the Authorization header
// and parses JSON responses. Throws a clear Error if the response is not OK.
async function apiFetch(path, options) {
  options = options || {};

  const headers = Object.assign({}, options.headers || {});
  headers['Content-Type'] = 'application/json';
  const token = getToken();
  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }

  const config = Object.assign({}, options, { headers: headers });

  let response;
  try {
    response = await fetch(API_BASE + path, config);
  } catch (e) {
    const err = new Error('Network error - is the backend running? (' + e.message + ')');
    err.status = 0;
    throw err;
  }

  const raw = await response.text();
  let data = null;
  if (raw && raw.length > 0) {
    try { data = JSON.parse(raw); } catch (e) { data = raw; }
  }

  if (!response.ok) {
    const message = (data && typeof data === 'object' && data.error)
      ? data.error
      : ('Request failed: HTTP ' + response.status);
    const err = new Error(message);
    err.status = response.status;
    err.body = data;
    throw err;
  }

  return data;
}

// Shorthand helper for GET requests.
async function apiGet(path) {
  return apiFetch(path, { method: 'GET' });
}

// Shorthand helper for POST requests.
async function apiPost(path, body) {
  return apiFetch(path, {
    method: 'POST',
    body: JSON.stringify(body || {})
  });
}

// Saves the auth bundle into localStorage after login or registration.
function saveAuth(token, userId, role, hubId, spokeId, name) {
  const bundle = { token, userId, role, hubId, spokeId, name };
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(bundle));
}

// Returns the current Bearer token, or null when no one is logged in.
function getToken() {
  const auth = getAuth();
  return auth ? auth.token : null;
}

// Returns the full saved auth object, or null if not logged in.
function getAuth() {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch (e) { return null; }
}

// Removes the saved auth bundle - equivalent to logging out.
function clearAuth() {
  localStorage.removeItem(AUTH_STORAGE_KEY);
}

// Returns true when a token is saved.
function isLoggedIn() {
  return !!getToken();
}
