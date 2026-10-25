'use strict';
// Login / registration page. Talks to Spring Security (login) and /api/auth/register.
(function () {
  var csrf = null;

  function $(id) { return document.getElementById(id); }

  async function loadCsrf() {
    var res = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!res.ok) { throw new Error('csrf'); }
    csrf = await res.json();
  }

  function showAlert(el, message) {
    el.textContent = message;
    el.classList.remove('d-none');
  }

  function clearErrors(form, alertEl) {
    alertEl.classList.add('d-none');
    form.querySelectorAll('.is-invalid').forEach(function (i) { i.classList.remove('is-invalid'); });
  }

  async function readError(res) {
    try { return await res.json(); } catch (e) { return null; }
  }

  async function login(email, password) {
    await loadCsrf();   // fresh token for this session
    var body = new URLSearchParams({ email: email, password: password });
    var headers = { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json' };
    headers[csrf.headerName] = csrf.token;
    return fetch('/api/auth/login', { method: 'POST', credentials: 'same-origin', headers: headers, body: body });
  }

  // Already logged in? go straight to the app.
  fetch('/api/auth/me', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
    .then(function (r) { if (r.ok) { location.replace('/'); } })
    .catch(function () { /* not logged in */ });

  $('loginForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    var alertEl = $('loginAlert');
    clearErrors(e.target, alertEl);
    var email = $('loginEmail').value.trim();
    var password = $('loginPassword').value;
    if (!email || !password) { showAlert(alertEl, 'Enter your email and password.'); return; }
    var btn = $('loginBtn');
    btn.disabled = true;
    try {
      var res = await login(email, password);
      if (res.ok) { location.replace('/'); return; }
      var err = await readError(res);
      showAlert(alertEl, res.status === 401 ? 'Invalid email or password.' : (err && err.message) || 'Login failed. Please try again.');
    } catch (ex) {
      showAlert(alertEl, 'Cannot reach the server. Check that the application is running.');
    } finally {
      btn.disabled = false;
    }
  });

  $('registerForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    var alertEl = $('registerAlert');
    clearErrors(e.target, alertEl);
    var name = $('regName').value.trim();
    var email = $('regEmail').value.trim();
    var password = $('regPassword').value;
    var btn = $('registerBtn');
    btn.disabled = true;
    try {
      await loadCsrf();
      var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
      headers[csrf.headerName] = csrf.token;
      var res = await fetch('/api/auth/register', {
        method: 'POST', credentials: 'same-origin', headers: headers,
        body: JSON.stringify({ name: name, email: email, password: password })
      });
      if (res.status === 201) {
        var loginRes = await login(email, password);
        if (loginRes.ok) { location.replace('/'); return; }
        showAlert(alertEl, 'Account created. Please log in.');
        return;
      }
      var err = await readError(res);
      if (err && err.fieldErrors) {
        Object.keys(err.fieldErrors).forEach(function (field) {
          var input = { name: 'regName', email: 'regEmail', password: 'regPassword' }[field];
          var msg = $('err-' + field);
          if (input && msg) { $(input).classList.add('is-invalid'); msg.textContent = err.fieldErrors[field]; }
        });
      }
      showAlert(alertEl, (err && err.message) || 'Registration failed. Please try again.');
    } catch (ex) {
      showAlert(alertEl, 'Cannot reach the server. Check that the application is running.');
    } finally {
      btn.disabled = false;
    }
  });

  $('themeBtn').addEventListener('click', function () {
    var next = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-bs-theme', next);
    try { localStorage.setItem('theme', next); } catch (e) { /* ignore */ }
  });
})();
