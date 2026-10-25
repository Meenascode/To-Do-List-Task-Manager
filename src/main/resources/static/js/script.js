'use strict';
/* To-Do List & Task Manager – front-end (vanilla JS).
 * All data lives in the Spring Boot backend / H2 database; nothing is stored in the browser
 * except the chosen theme. Every request uses fetch() with the session cookie + CSRF token. */
(function () {

  // ---------------------------------------------------------------- constants & state
  const STATUS = {
    TODO: { label: 'To Do', icon: 'circle' },
    IN_PROGRESS: { label: 'In Progress', icon: 'arrow-repeat' },
    COMPLETED: { label: 'Completed', icon: 'check-circle' }
  };
  const PRIORITY = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', URGENT: 'Urgent' };
  const PRIORITY_COLORS = { LOW: '#06B6D4', MEDIUM: '#2563EB', HIGH: '#F59E0B', URGENT: '#DC2626' };
  const VIEWS = [
    { id: 'dashboard', title: 'Dashboard', icon: 'speedometer2' },
    { id: 'tasks', title: 'Tasks', icon: 'list-check' },
    { id: 'kanban', title: 'Kanban board', icon: 'kanban' },
    { id: 'calendar', title: 'Calendar', icon: 'calendar3' },
    { id: 'categories', title: 'Categories & Tags', icon: 'tags' },
    { id: 'activity', title: 'Activity', icon: 'clock-history' }
  ];
  const DEFAULT_FILTERS = { q: '', status: '', priority: '', categoryId: '', tagId: '', due: '', sort: 'createdAt', dir: 'desc' };

  const state = {
    me: null, today: '', categories: [], tags: [], view: 'dashboard',
    filters: Object.assign({}, DEFAULT_FILTERS),
    cal: { year: 0, month: 0, selected: '', tasks: [] },
    charts: {}, stats: null,
    detailId: null, detail: null, returnToDetail: null, editingCategoryId: null
  };

  // ---------------------------------------------------------------- small helpers
  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
  const esc = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pad = (n) => String(n).padStart(2, '0');

  /** 'YYYY-MM-DD' -> local Date (no timezone shifting). */
  function parseDate(s) {
    const p = String(s).split('-').map(Number);
    return new Date(p[0], p[1] - 1, p[2]);
  }
  function fmtDate(s) {
    return s ? parseDate(s).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '';
  }
  function fmtDateTime(s) {
    if (!s) { return ''; }
    const d = new Date(String(s).replace(/(\.\d{3})\d+/, '$1'));
    return isNaN(d) ? '' : d.toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
  function emptyState(icon, title, text, button) {
    return '<div class="empty-state"><i class="bi bi-' + icon + '"></i><strong>' + esc(title) + '</strong>' +
      '<div class="small mb-2">' + esc(text) + '</div>' + (button || '') + '</div>';
  }

  // ---------------------------------------------------------------- API layer
  class ApiError extends Error {
    constructor(status, message, fieldErrors) {
      super(message);
      this.status = status;
      this.fieldErrors = fieldErrors || null;
    }
  }

  let csrf = null;
  let loadingCount = 0;

  function setLoading(on) {
    loadingCount = Math.max(0, loadingCount + (on ? 1 : -1));
    $('#loadingBar').hidden = loadingCount === 0;
  }

  async function loadCsrf() {
    const res = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!res.ok) { throw new ApiError(res.status, 'Could not obtain a security token. Please reload the page.'); }
    csrf = await res.json();
  }

  async function api(path, opts) {
    const o = Object.assign({ method: 'GET', body: undefined, retry: true }, opts || {});
    const init = { method: o.method, credentials: 'same-origin', headers: { 'Accept': 'application/json' } };
    setLoading(true);
    try {
      if (o.method !== 'GET') {
        if (!csrf) { await loadCsrf(); }
        init.headers[csrf.headerName] = csrf.token;
      }
      if (o.body !== undefined) {
        init.headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(o.body);
      }
      let res;
      try {
        res = await fetch(path, init);
      } catch (networkError) {
        throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
      }
      if (res.status === 401) {
        location.href = '/login.html';
        throw new ApiError(401, 'Your session has expired. Please log in again.');
      }
      if (res.status === 403 && o.method !== 'GET' && o.retry) {   // token may be stale: refresh once and retry
        await loadCsrf();
        return await api(path, { method: o.method, body: o.body, retry: false });
      }
      if (res.status === 204) { return null; }
      let data = null;
      try { data = await res.json(); } catch (parseError) { /* empty or non-JSON body */ }
      if (!res.ok) {
        throw new ApiError(res.status, (data && data.message) || 'The request failed (HTTP ' + res.status + ').', data && data.fieldErrors);
      }
      return data;
    } finally {
      setLoading(false);
    }
  }

  // ---------------------------------------------------------------- toasts & dialogs
  function toast(message, type) {
    const kind = type || 'success';
    const el = document.createElement('div');
    el.className = 'toast align-items-center border-0 text-bg-' + (kind === 'error' ? 'danger' : kind === 'warning' ? 'warning' : 'success');
    el.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    el.innerHTML = '<div class="d-flex"><div class="toast-body"></div>' +
      '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button></div>';
    $('.toast-body', el).textContent = message;
    $('#toasts').appendChild(el);
    el.addEventListener('hidden.bs.toast', () => el.remove());
    new bootstrap.Toast(el, { delay: kind === 'error' ? 6000 : 3000 }).show();
  }
  function showError(e) {
    if (e instanceof ApiError && e.status === 401) { return; }
    toast((e && e.message) || 'Something went wrong.', 'error');
  }

  function hideModal(el) {
    return new Promise((resolve) => {
      if (!el.classList.contains('show')) { resolve(); return; }
      el.addEventListener('hidden.bs.modal', () => resolve(), { once: true });
      bootstrap.Modal.getOrCreateInstance(el).hide();
    });
  }

  function confirmDialog(title, message, okLabel) {
    return new Promise((resolve) => {
      const modalEl = $('#confirmModal');
      $('#confirmTitle').textContent = title;
      $('#confirmBody').textContent = message;
      $('#confirmOk').textContent = okLabel || 'Delete';
      const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
      let result = false;
      const ok = $('#confirmOk');
      const onOk = () => { result = true; modal.hide(); };
      const onHidden = () => {
        ok.removeEventListener('click', onOk);
        modalEl.removeEventListener('hidden.bs.modal', onHidden);
        resolve(result);
      };
      ok.addEventListener('click', onOk);
      modalEl.addEventListener('hidden.bs.modal', onHidden);
      modal.show();
    });
  }

  /** Confirmation that temporarily closes the details dialog (Bootstrap shows one modal at a time). */
  async function confirmDelete(title, message) {
    const detailEl = $('#detailModal');
    const wasOpen = detailEl.classList.contains('show');
    if (wasOpen) { await hideModal(detailEl); }
    const ok = await confirmDialog(title, message, 'Delete');
    if (!ok && wasOpen && state.detailId) { openDetail(state.detailId); }
    return ok;
  }

  // ---------------------------------------------------------------- theme
  function applyTheme(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    try { localStorage.setItem('theme', theme); } catch (e) { /* storage blocked */ }
    $('#themeIcon').className = 'bi bi-' + (theme === 'dark' ? 'sun' : 'moon-stars');
    $('#themeBtn').setAttribute('aria-label', theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode');
    if (state.view === 'dashboard' && state.stats) { renderCharts(state.stats); }
  }

  // ---------------------------------------------------------------- rendering pieces
  function priorityBadge(p) { return '<span class="badge badge-priority p-' + p.toLowerCase() + '">' + PRIORITY[p] + '</span>'; }
  function statusBadge(s) { return '<span class="badge s-' + s.toLowerCase() + '"><i class="bi bi-' + STATUS[s].icon + '"></i> ' + STATUS[s].label + '</span>'; }
  function overdueBadge(t) { return t.overdue ? '<span class="badge badge-overdue"><i class="bi bi-exclamation-triangle-fill"></i> Overdue</span>' : ''; }
  function categoryLabel(t) {
    return t.categoryName ? '<span><span class="cat-dot" style="background:' + esc(t.categoryColor) + '"></span>' + esc(t.categoryName) + '</span>' : '';
  }
  function tagChips(t) {
    return t.tags && t.tags.length ? '<div class="mt-1 d-flex flex-wrap gap-1">' + t.tags.map((g) => '<span class="tag-chip">#' + esc(g.name) + '</span>').join('') + '</div>' : '';
  }
  function dueLabel(t) {
    if (!t.dueDate) { return ''; }
    return '<span class="' + (t.overdue ? 'text-danger fw-semibold' : '') + '"><i class="bi bi-calendar-event"></i> ' + fmtDate(t.dueDate) + '</span>';
  }
  function subtaskLabel(t) {
    return t.subtaskTotal ? '<span title="Subtasks completed"><i class="bi bi-list-check"></i> ' + t.subtaskDone + '/' + t.subtaskTotal + '</span>' : '';
  }

  function taskRow(t) {
    const done = t.status === 'COMPLETED';
    return '<article class="card task-row pri-' + t.priority.toLowerCase() + (done ? ' is-done' : '') + (t.overdue ? ' is-overdue' : '') + '">' +
      '<div class="d-flex gap-3 align-items-start">' +
      '<button type="button" class="check-btn' + (done ? ' checked' : '') + '" data-action="toggle" data-id="' + t.id + '" data-status="' + t.status + '" aria-label="' + (done ? 'Reopen' : 'Mark as completed') + ': ' + esc(t.title) + '"><i class="bi bi-check-lg"></i></button>' +
      '<div class="flex-grow-1 min-w-0">' +
      '<a href="#" class="task-title" data-action="open" data-id="' + t.id + '">' + esc(t.title) + '</a>' +
      '<div class="task-meta">' + priorityBadge(t.priority) + statusBadge(t.status) + overdueBadge(t) + dueLabel(t) + categoryLabel(t) + subtaskLabel(t) + '</div>' +
      tagChips(t) + '</div>' +
      '<div class="btn-group btn-group-sm flex-shrink-0">' +
      '<button type="button" class="btn btn-outline-secondary" data-action="edit" data-id="' + t.id + '" aria-label="Edit ' + esc(t.title) + '"><i class="bi bi-pencil"></i></button>' +
      '<button type="button" class="btn btn-outline-danger" data-action="delete" data-id="' + t.id + '" data-title="' + esc(t.title) + '" aria-label="Delete ' + esc(t.title) + '"><i class="bi bi-trash"></i></button>' +
      '</div></div></article>';
  }

  function renderTimeline(entries) {
    if (!entries || !entries.length) { return emptyState('clock-history', 'No activity yet', 'Changes to your tasks will appear here.'); }
    return '<ul class="timeline">' + entries.map((h) =>
      '<li class="ev-' + esc(h.eventType) + '"><div>' + (h.taskTitle ? '<strong>' + esc(h.taskTitle) + '</strong> &middot; ' : '') + esc(h.description) + '</div>' +
      '<time datetime="' + esc(h.createdAt) + '">' + fmtDateTime(h.createdAt) + '</time></li>').join('') + '</ul>';
  }

  // ---------------------------------------------------------------- navigation / routing
  function renderNav() {
    const html = VIEWS.map((v) =>
      '<a href="#' + v.id + '" data-nav' + (v.id === state.view ? ' class="active" aria-current="page"' : '') + '><i class="bi bi-' + v.icon + '"></i> ' + esc(v.title) + '</a>').join('');
    $('#navDesktop').innerHTML = html;
    $('#navMobile').innerHTML = html;
  }

  function currentViewFromHash() {
    const h = (location.hash || '').replace('#', '');
    return VIEWS.some((v) => v.id === h) ? h : 'dashboard';
  }

  async function route() {
    state.view = currentViewFromHash();
    const view = VIEWS.find((v) => v.id === state.view);
    $$('[data-view]').forEach((s) => { s.hidden = s.dataset.view !== state.view; });
    $('#pageTitle').textContent = view.title;
    document.title = view.title + ' – To-Do List & Task Manager';
    renderNav();
    await renderView();
  }

  async function renderView() {
    try {
      switch (state.view) {
        case 'dashboard': await renderDashboard(); break;
        case 'tasks': await renderTasks(); break;
        case 'kanban': await renderKanban(); break;
        case 'calendar': await renderCalendar(); break;
        case 'categories': await renderCategories(); break;
        case 'activity': await renderActivityView(); break;
        default: break;
      }
    } catch (e) { showError(e); }
  }

  async function loadMeta() {
    const results = await Promise.all([api('/api/categories'), api('/api/tags')]);
    state.categories = results[0];
    state.tags = results[1];
    fillSelects();
  }

  function fillSelects() {
    const f = state.filters;
    const catOptions = state.categories.map((c) => '<option value="' + c.id + '">' + esc(c.name) + '</option>').join('');
    const tagOptions = state.tags.map((g) => '<option value="' + g.id + '">#' + esc(g.name) + '</option>').join('');
    $('#fCategory').innerHTML = '<option value="">All categories</option>' + catOptions;
    $('#fTag').innerHTML = '<option value="">All tags</option>' + tagOptions;
    $('#fCategory').value = f.categoryId;
    $('#fTag').value = f.tagId;
    $('#tf-category').innerHTML = '<option value="">None</option>' + catOptions;
    $('#tagOptions').innerHTML = state.tags.map((g) => '<option value="' + esc(g.name) + '"></option>').join('');
  }

  async function refreshAfterChange() {
    await loadMeta();
    await renderView();
  }

  // ---------------------------------------------------------------- dashboard
  async function renderDashboard() {
    const s = await api('/api/tasks/stats');
    state.stats = s;
    state.today = s.today;
    renderStatCards(s);
    renderTrend(s);
    renderCharts(s);
    renderUpcoming(s);
    $('#activityList').innerHTML = renderTimeline(s.recentActivity);
  }

  function renderStatCards(s) {
    const cards = [
      ['Total tasks', s.total, 'collection', 'tone-primary', 'tasks', '', ''],
      ['Completed', s.completed, 'check2-circle', 'tone-success', 'tasks', 'COMPLETED', ''],
      ['In progress', s.inProgress, 'arrow-repeat', 'tone-accent', 'tasks', 'IN_PROGRESS', ''],
      ['Pending (To Do)', s.todo, 'hourglass-split', 'tone-muted', 'tasks', 'TODO', ''],
      ['Overdue', s.overdue, 'exclamation-triangle', 'tone-danger', 'tasks', '', 'overdue'],
      ['Due today', s.dueToday, 'calendar-event', 'tone-warning', 'tasks', '', 'today']
    ];
    let html = cards.map((c) =>
      '<div class="col-6 col-md-4 col-xl-3"><button type="button" class="card stat-card w-100 text-start" data-action="stat-link" data-fstatus="' + c[5] + '" data-fdue="' + c[6] + '">' +
      '<span class="stat-icon ' + c[3] + '"><i class="bi bi-' + c[2] + '"></i></span>' +
      '<span><span class="stat-value d-block">' + c[1] + '</span><span class="stat-label">' + c[0] + '</span></span></button></div>').join('');
    html += '<div class="col-12 col-xl-6"><div class="card stat-card"><span class="stat-icon tone-success"><i class="bi bi-percent"></i></span>' +
      '<span class="flex-grow-1"><span class="d-flex justify-content-between"><span class="stat-label">Completion rate</span><span class="fw-bold">' + s.completionPercent + '%</span></span>' +
      '<span class="progress mt-2 d-flex" style="height:.6rem" role="progressbar" aria-label="Completion rate" aria-valuenow="' + s.completionPercent + '" aria-valuemin="0" aria-valuemax="100"><span class="progress-bar bg-success" style="width:' + s.completionPercent + '%"></span></span></span></div></div>';
    $('#statCards').innerHTML = html;
  }

  function renderTrend(s) {
    const diff = s.completedThisWeek - s.completedLastWeek;
    const arrow = diff > 0 ? '<i class="bi bi-arrow-up-right text-success"></i> +' + diff : diff < 0 ? '<i class="bi bi-arrow-down-right text-danger"></i> ' + diff : '<i class="bi bi-dash"></i> no change';
    $('#trendText').innerHTML = 'Completed in last 7 days: <strong>' + s.completedThisWeek + '</strong><br>' + arrow + ' vs previous 7 days';
  }

  function destroyCharts() {
    Object.keys(state.charts).forEach((k) => { if (state.charts[k]) { state.charts[k].destroy(); } });
    state.charts = {};
  }

  function renderCharts(s) {
    destroyCharts();
    const pBox = $('#priorityChartBox');
    const wBox = $('#weeklyChartBox');
    if (!s.total) {
      const empty = emptyState('bar-chart-line', 'No data yet', 'Create a task to see your charts.');
      pBox.innerHTML = empty;
      wBox.innerHTML = empty;
      return;
    }
    const css = getComputedStyle(document.documentElement);
    const textColor = css.getPropertyValue('--text-secondary').trim() || '#6B7280';
    const gridColor = css.getPropertyValue('--border').trim() || '#E5E7EB';
    Chart.defaults.color = textColor;
    Chart.defaults.borderColor = gridColor;

    pBox.innerHTML = '<canvas role="img" aria-label="Doughnut chart of tasks by priority"></canvas>';
    const keys = Object.keys(PRIORITY);
    state.charts.priority = new Chart(pBox.firstElementChild, {
      type: 'doughnut',
      data: {
        labels: keys.map((k) => PRIORITY[k]),
        datasets: [{ data: keys.map((k) => s.priorityDistribution[k] || 0), backgroundColor: keys.map((k) => PRIORITY_COLORS[k]), borderWidth: 0 }]
      },
      options: { responsive: true, maintainAspectRatio: false, cutout: '62%', plugins: { legend: { position: 'bottom', labels: { color: textColor } } } }
    });

    wBox.innerHTML = '<canvas role="img" aria-label="Bar chart of tasks created and completed in the last 7 days"></canvas>';
    state.charts.weekly = new Chart(wBox.firstElementChild, {
      type: 'bar',
      data: {
        labels: s.weekly.map((d) => parseDate(d.date).toLocaleDateString(undefined, { weekday: 'short' })),
        datasets: [
          { label: 'Created', data: s.weekly.map((d) => d.created), backgroundColor: css.getPropertyValue('--primary').trim() || '#2563EB', borderRadius: 4 },
          { label: 'Completed', data: s.weekly.map((d) => d.completed), backgroundColor: css.getPropertyValue('--success').trim() || '#16A34A', borderRadius: 4 }
        ]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        scales: { x: { grid: { display: false }, ticks: { color: textColor } }, y: { beginAtZero: true, ticks: { precision: 0, color: textColor }, grid: { color: gridColor } } },
        plugins: { legend: { position: 'bottom', labels: { color: textColor } } }
      }
    });
  }

  function renderUpcoming(s) {
    const box = $('#upcomingList');
    if (!s.upcoming.length) {
      box.innerHTML = emptyState('calendar-check', 'No upcoming deadlines', 'Tasks with a due date will show up here.');
      return;
    }
    box.innerHTML = s.upcoming.map((t) =>
      '<div class="d-flex justify-content-between align-items-center gap-2 py-2 border-bottom"><div class="min-w-0">' +
      '<a href="#" class="task-title" data-action="open" data-id="' + t.id + '">' + esc(t.title) + '</a>' +
      '<div class="small text-secondary"><i class="bi bi-calendar-event"></i> ' + fmtDate(t.dueDate) + (t.dueDate === state.today ? ' (today)' : '') + '</div></div>' +
      priorityBadge(t.priority) + '</div>').join('');
  }

  async function quickAdd(e) {
    e.preventDefault();
    const title = $('#qTitle').value.trim();
    if (!title) { $('#qTitle').focus(); toast('Enter a title for the task.', 'warning'); return; }
    try {
      await api('/api/tasks', { method: 'POST', body: { title: title, priority: $('#qPriority').value, dueDate: $('#qDue').value || null } });
      $('#quickForm').reset();
      toast('Task added.');
      await refreshAfterChange();
    } catch (err) { showError(err); }
  }

  // ---------------------------------------------------------------- tasks list
  function filtersActive() {
    const f = state.filters;
    return !!(f.q || f.status || f.priority || f.categoryId || f.tagId || f.due);
  }

  function syncFilterUi() {
    const f = state.filters;
    $('#fQ').value = f.q;
    $('#fStatus').value = f.status;
    $('#fPriority').value = f.priority;
    $('#fCategory').value = f.categoryId;
    $('#fTag').value = f.tagId;
    $('#fDue').value = f.due;
    $('#fSort').value = f.sort;
    $('#fDir').innerHTML = '<i class="bi bi-sort-' + (f.dir === 'asc' ? 'up' : 'down') + '"></i>';
    $('#fDir').title = f.dir === 'asc' ? 'Ascending (click to reverse)' : 'Descending (click to reverse)';
  }

  function readFilterUi() {
    state.filters = {
      q: $('#fQ').value.trim(), status: $('#fStatus').value, priority: $('#fPriority').value,
      categoryId: $('#fCategory').value, tagId: $('#fTag').value, due: $('#fDue').value,
      sort: $('#fSort').value, dir: state.filters.dir
    };
  }

  async function renderTasks() {
    syncFilterUi();
    const params = new URLSearchParams();
    Object.keys(state.filters).forEach((k) => { if (state.filters[k]) { params.set(k, state.filters[k]); } });
    const list = await api('/api/tasks?' + params.toString());
    $('#taskCount').textContent = list.length + (list.length === 1 ? ' task' : ' tasks') + (filtersActive() ? ' match your filters' : '');
    if (list.length) {
      $('#taskList').innerHTML = list.map(taskRow).join('');
    } else if (filtersActive()) {
      $('#taskList').innerHTML = emptyState('funnel', 'No tasks match your filters', 'Try a different search or clear the filters.',
        '<button type="button" class="btn btn-outline-primary btn-sm" data-action="clear-filters">Clear filters</button>');
    } else {
      $('#taskList').innerHTML = emptyState('inbox', 'No tasks yet', 'Create your first task to get started.',
        '<button type="button" class="btn btn-primary btn-sm" data-action="new"><i class="bi bi-plus-lg"></i> New task</button>');
    }
  }

  function setFiltersAndGo(partial) {
    state.filters = Object.assign({}, DEFAULT_FILTERS, partial);
    if (location.hash === '#tasks') { renderView(); } else { location.hash = '#tasks'; }
  }

  // ---------------------------------------------------------------- kanban
  async function renderKanban() {
    const list = await api('/api/tasks?sort=dueDate&dir=asc');
    $('#kanban').innerHTML = Object.keys(STATUS).map((st) => {
      const items = list.filter((t) => t.status === st);
      return '<section class="kanban-col" data-status="' + st + '" aria-label="' + STATUS[st].label + ' column">' +
        '<div class="kanban-head"><span><i class="bi bi-' + STATUS[st].icon + '"></i> ' + STATUS[st].label + '</span><span class="badge text-bg-secondary">' + items.length + '</span></div>' +
        (items.length ? items.map(kanbanCard).join('') : '<div class="text-secondary small text-center py-3">No tasks here</div>') + '</section>';
    }).join('');
  }

  function kanbanCard(t) {
    const options = Object.keys(STATUS).map((s) => '<option value="' + s + '"' + (s === t.status ? ' selected' : '') + '>' + STATUS[s].label + '</option>').join('');
    return '<article class="card kanban-card pri-' + t.priority.toLowerCase() + (t.overdue ? ' is-overdue' : '') + '" draggable="true" data-id="' + t.id + '" style="border-left-color:' + PRIORITY_COLORS[t.priority] + '">' +
      '<a href="#" class="task-title d-block mb-1" data-action="open" data-id="' + t.id + '">' + esc(t.title) + '</a>' +
      '<div class="task-meta mb-2">' + priorityBadge(t.priority) + overdueBadge(t) + dueLabel(t) + categoryLabel(t) + subtaskLabel(t) + '</div>' +
      '<label class="visually-hidden" for="ks-' + t.id + '">Status of ' + esc(t.title) + '</label>' +
      '<select class="form-select form-select-sm" id="ks-' + t.id + '" data-change="kstatus" data-id="' + t.id + '">' + options + '</select></article>';
  }

  async function moveTask(id, status) {
    try {
      await api('/api/tasks/' + id + '/status', { method: 'PATCH', body: { status: status } });
      toast('Task moved to ' + STATUS[status].label + '.');
    } catch (e) {
      showError(e);   // the board is redrawn below from real data, so a failed move is never shown as saved
    }
    await renderKanban();
  }

  // ---------------------------------------------------------------- calendar
  function monthTitle() {
    return new Date(state.cal.year, state.cal.month - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  }

  async function renderCalendar() {
    const c = state.cal;
    if (!c.year) {
      const p = (state.today || new Date().toISOString().slice(0, 10)).split('-').map(Number);
      c.year = p[0]; c.month = p[1]; c.selected = state.today;
    }
    c.tasks = await api('/api/tasks/calendar?year=' + c.year + '&month=' + c.month);
    drawCalendar();
    drawCalendarDay();
  }

  function drawCalendar() {
    const c = state.cal;
    $('#calTitle').textContent = monthTitle();
    const byDate = {};
    c.tasks.forEach((t) => { (byDate[t.dueDate] = byDate[t.dueDate] || []).push(t); });
    const lead = (new Date(c.year, c.month - 1, 1).getDay() + 6) % 7;     // Monday first
    const days = new Date(c.year, c.month, 0).getDate();
    let html = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => '<div class="cal-dow" role="columnheader">' + d + '</div>').join('');
    for (let i = 0; i < lead; i++) { html += '<div class="cal-cell empty" aria-hidden="true"></div>'; }
    for (let d = 1; d <= days; d++) {
      const date = c.year + '-' + pad(c.month) + '-' + pad(d);
      const items = byDate[date] || [];
      const long = new Date(c.year, c.month - 1, d).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' });
      const chips = items.slice(0, 3).map((t) =>
        '<button type="button" class="cal-chip pri-' + t.priority.toLowerCase() + ' st-' + t.status.toLowerCase() + (t.overdue ? ' is-overdue' : '') + '" data-action="open" data-id="' + t.id + '" title="' + esc(t.title + ' (' + STATUS[t.status].label + ', ' + PRIORITY[t.priority] + (t.overdue ? ', overdue' : '') + ')') + '">' +
        (t.overdue ? '<i class="bi bi-exclamation-triangle-fill text-danger"></i> ' : '') + esc(t.title) + '</button>').join('');
      const more = items.length > 3 ? '<button type="button" class="cal-more" data-action="cal-select" data-date="' + date + '">+' + (items.length - 3) + ' more</button>' : '';
      const dots = items.slice(0, 8).map((t) => '<span class="cal-dot pri-' + t.priority.toLowerCase() + (t.overdue ? ' is-overdue' : '') + '"></span>').join('');
      html += '<div class="cal-cell' + (date === state.today ? ' today' : '') + (date === c.selected ? ' selected' : '') + '" role="gridcell">' +
        '<div class="cal-head"><button type="button" class="cal-day" data-action="cal-select" data-date="' + date + '" aria-label="' + esc(long) + ', ' + items.length + ' tasks">' + d + '</button>' +
        '<button type="button" class="cal-add" data-action="new" data-due="' + date + '" aria-label="Add task on ' + esc(long) + '"><i class="bi bi-plus-lg"></i></button></div>' +
        '<div class="cal-chips">' + chips + more + '</div><div class="cal-dots">' + dots + '</div></div>';
    }
    const trailing = (7 - ((lead + days) % 7)) % 7;
    for (let i = 0; i < trailing; i++) { html += '<div class="cal-cell empty" aria-hidden="true"></div>'; }
    $('#calGrid').innerHTML = html;
  }

  function drawCalendarDay() {
    const c = state.cal;
    const box = $('#calDay');
    if (!c.selected) { box.innerHTML = ''; return; }
    const items = c.tasks.filter((t) => t.dueDate === c.selected);
    box.innerHTML = '<div class="d-flex justify-content-between align-items-center mb-2 gap-2"><h2 class="h6 mb-0">' + fmtDate(c.selected) + '</h2>' +
      '<button type="button" class="btn btn-sm btn-primary" data-action="new" data-due="' + c.selected + '"><i class="bi bi-plus-lg"></i> Add task for this date</button></div>' +
      (items.length ? items.map(taskRow).join('') : '<div class="text-secondary small">No tasks due on this date.</div>') +
      '<div class="small text-secondary mt-2">Tasks without a due date are listed under Tasks.</div>';
  }

  function shiftMonth(delta) {
    const c = state.cal;
    const d = new Date(c.year, c.month - 1 + delta, 1);
    c.year = d.getFullYear(); c.month = d.getMonth() + 1;
    const first = c.year + '-' + pad(c.month) + '-01';
    c.selected = state.today && state.today.slice(0, 7) === first.slice(0, 7) ? state.today : first;
    renderView();
  }

  // ---------------------------------------------------------------- categories & tags
  async function renderCategories() {
    await loadMeta();
    $('#catList').innerHTML = state.categories.length ? state.categories.map((c) =>
      '<div class="d-flex align-items-center gap-2 py-2 border-bottom"><span class="cat-dot" style="background:' + esc(c.color) + ';width:1rem;height:1rem"></span>' +
      '<a href="#" class="flex-grow-1 task-title" data-action="cat-filter" data-id="' + c.id + '">' + esc(c.name) + '</a>' +
      '<span class="small text-secondary">' + c.taskCount + (c.taskCount === 1 ? ' task' : ' tasks') + '</span>' +
      '<button type="button" class="btn btn-sm btn-outline-secondary" data-action="cat-edit" data-id="' + c.id + '" aria-label="Edit category ' + esc(c.name) + '"><i class="bi bi-pencil"></i></button>' +
      '<button type="button" class="btn btn-sm btn-outline-danger" data-action="cat-delete" data-id="' + c.id + '" data-title="' + esc(c.name) + '" aria-label="Delete category ' + esc(c.name) + '"><i class="bi bi-trash"></i></button></div>').join('')
      : emptyState('tags', 'No categories yet', 'Create a category such as Work, Study or Home.');
    $('#tagList').innerHTML = state.tags.length ? state.tags.map((g) =>
      '<div class="d-flex align-items-center gap-2 py-2 border-bottom"><a href="#" class="flex-grow-1 task-title" data-action="tag-filter" data-id="' + g.id + '">#' + esc(g.name) + '</a>' +
      '<span class="small text-secondary">' + g.taskCount + (g.taskCount === 1 ? ' task' : ' tasks') + '</span>' +
      '<button type="button" class="btn btn-sm btn-outline-danger" data-action="tag-delete" data-id="' + g.id + '" data-title="' + esc(g.name) + '" aria-label="Delete tag ' + esc(g.name) + '"><i class="bi bi-trash"></i></button></div>').join('')
      : emptyState('hash', 'No tags yet', 'Type tags when you create or edit a task.');
  }

  function resetCategoryForm() {
    state.editingCategoryId = null;
    $('#catForm').reset();
    $('#catColor').value = '#2563eb';
    $('#catSubmitText').textContent = 'Add';
    $('#catCancel').classList.add('d-none');
  }

  async function saveCategory(e) {
    e.preventDefault();
    const name = $('#catName').value.trim();
    if (!name) { $('#catName').focus(); toast('Enter a category name.', 'warning'); return; }
    const body = { name: name, color: $('#catColor').value };
    try {
      if (state.editingCategoryId) {
        await api('/api/categories/' + state.editingCategoryId, { method: 'PUT', body: body });
        toast('Category updated.');
      } else {
        await api('/api/categories', { method: 'POST', body: body });
        toast('Category added.');
      }
      resetCategoryForm();
      await renderView();
    } catch (err) { showError(err); }
  }

  // ---------------------------------------------------------------- activity view
  async function renderActivityView() {
    const entries = await api('/api/history?limit=100');
    $('#activityFull').innerHTML = renderTimeline(entries);
  }

  // ---------------------------------------------------------------- task form (create / edit)
  function clearFormErrors() {
    $('#tfError').classList.add('d-none');
    $$('#taskForm .is-invalid').forEach((i) => i.classList.remove('is-invalid'));
  }

  function openTaskForm(task, defaults) {
    clearFormErrors();
    const d = defaults || {};
    $('#taskModalTitle').textContent = task ? 'Edit task' : 'New task';
    $('#tf-id').value = task ? task.id : '';
    $('#tf-title').value = task ? task.title : '';
    $('#tf-description').value = task ? task.description : '';
    $('#tf-status').value = task ? task.status : 'TODO';
    $('#tf-priority').value = task ? task.priority : 'MEDIUM';
    $('#tf-due').value = task ? (task.dueDate || '') : (d.due || '');
    $('#tf-category').value = task && task.categoryId ? String(task.categoryId) : '';
    $('#tf-tags').value = task ? task.tags.map((g) => g.name).join(', ') : '';
    bootstrap.Modal.getOrCreateInstance($('#taskModal')).show();
  }

  async function openEdit(id) {
    try {
      const t = await api('/api/tasks/' + id);
      const detailEl = $('#detailModal');
      if (detailEl.classList.contains('show')) { state.returnToDetail = id; await hideModal(detailEl); }
      openTaskForm(t);
    } catch (e) { showError(e); }
  }

  async function saveTask(e) {
    e.preventDefault();
    clearFormErrors();
    const title = $('#tf-title').value.trim();
    if (!title) {
      $('#tf-title').classList.add('is-invalid');
      $('#tfe-title').textContent = 'Title is required.';
      $('#tf-title').focus();
      return;
    }
    const body = {
      title: title,
      description: $('#tf-description').value,
      status: $('#tf-status').value,
      priority: $('#tf-priority').value,
      dueDate: $('#tf-due').value || null,
      categoryId: $('#tf-category').value ? Number($('#tf-category').value) : null,
      tags: $('#tf-tags').value.split(',').map((x) => x.trim()).filter(Boolean)
    };
    const id = $('#tf-id').value;
    const btn = $('#tfSave');
    btn.disabled = true;
    try {
      if (id) { await api('/api/tasks/' + id, { method: 'PUT', body: body }); }
      else { await api('/api/tasks', { method: 'POST', body: body }); }
      bootstrap.Modal.getOrCreateInstance($('#taskModal')).hide();
      toast(id ? 'Task updated.' : 'Task created.');
      await refreshAfterChange();
    } catch (err) {
      // Stay in the dialog: nothing was saved, so nothing is reported as saved.
      const box = $('#tfError');
      box.textContent = err.message;
      box.classList.remove('d-none');
      if (err.fieldErrors) {
        Object.keys(err.fieldErrors).forEach((f) => {
          const input = { title: '#tf-title', description: '#tf-description', dueDate: '#tf-due' }[f];
          const msg = $('#tfe-' + f);
          if (input && msg) { $(input).classList.add('is-invalid'); msg.textContent = err.fieldErrors[f]; }
        });
      }
    } finally {
      btn.disabled = false;
    }
  }

  // ---------------------------------------------------------------- task actions
  async function toggleTask(id, currentStatus) {
    const next = currentStatus === 'COMPLETED' ? 'TODO' : 'COMPLETED';
    try {
      await api('/api/tasks/' + id + '/status', { method: 'PATCH', body: { status: next } });
      toast(next === 'COMPLETED' ? 'Task completed.' : 'Task reopened.');
      await renderView();
      if ($('#detailModal').classList.contains('show')) { openDetail(id); }
    } catch (e) { showError(e); }
  }

  async function deleteTask(id, title) {
    if (!(await confirmDelete('Delete task?', '"' + title + '" and all of its subtasks will be permanently deleted.'))) { return; }
    try {
      await api('/api/tasks/' + id, { method: 'DELETE' });
      if (state.detailId === id) { state.detailId = null; }
      toast('Task deleted.');
      await refreshAfterChange();
    } catch (e) { showError(e); }
  }

  // ---------------------------------------------------------------- details dialog (+ subtasks + history)
  async function openDetail(id) {
    state.detailId = id;
    try {
      const res = await Promise.all([api('/api/tasks/' + id), api('/api/tasks/' + id + '/history')]);
      state.detail = res[0];
      renderDetail(res[0], res[1]);
      bootstrap.Modal.getOrCreateInstance($('#detailModal')).show();
    } catch (e) { showError(e); }
  }

  function renderDetail(t, history) {
    const done = t.status === 'COMPLETED';
    $('#detailTitle').textContent = t.title;
    let html = '<div class="d-flex flex-wrap gap-2 mb-3">' + priorityBadge(t.priority) + statusBadge(t.status) + overdueBadge(t) + '</div>';
    html += t.description ? '<p class="pre-wrap">' + esc(t.description) + '</p>' : '<p class="text-secondary fst-italic">No description.</p>';
    html += '<dl class="row small mb-2">' +
      '<dt class="col-4 col-sm-3">Due date</dt><dd class="col-8 col-sm-9">' + (t.dueDate ? fmtDate(t.dueDate) : 'Not set') + '</dd>' +
      '<dt class="col-4 col-sm-3">Category</dt><dd class="col-8 col-sm-9">' + (t.categoryName ? categoryLabel(t) : 'None') + '</dd>' +
      '<dt class="col-4 col-sm-3">Created</dt><dd class="col-8 col-sm-9">' + fmtDateTime(t.createdAt) + '</dd>' +
      '<dt class="col-4 col-sm-3">Last updated</dt><dd class="col-8 col-sm-9">' + fmtDateTime(t.updatedAt) + '</dd>' +
      (t.completedAt ? '<dt class="col-4 col-sm-3">Completed</dt><dd class="col-8 col-sm-9">' + fmtDateTime(t.completedAt) + '</dd>' : '') +
      (t.parentId ? '<dt class="col-4 col-sm-3">Subtask of</dt><dd class="col-8 col-sm-9"><a href="#" data-action="open" data-id="' + t.parentId + '">Open parent task</a></dd>' : '') +
      '</dl>' + tagChips(t);

    if (!t.parentId) {
      const total = t.subtasks.length;
      const doneCount = t.subtasks.filter((s) => s.status === 'COMPLETED').length;
      const pct = total ? Math.round(doneCount * 100 / total) : 0;
      html += '<div class="d-flex justify-content-between align-items-center mt-4"><h3 class="h6 mb-0">Subtasks</h3><span class="small text-secondary">' + doneCount + '/' + total + ' done</span></div>' +
        '<div class="progress my-2" style="height:.5rem" role="progressbar" aria-label="Subtask progress" aria-valuenow="' + pct + '" aria-valuemin="0" aria-valuemax="100"><div class="progress-bar bg-success" style="width:' + pct + '%"></div></div>' +
        '<div id="subList">' + (total ? t.subtasks.map(subtaskItem).join('') : '<div class="text-secondary small">No subtasks yet.</div>') + '</div>' +
        '<form class="input-group mt-2" id="subForm" data-parent="' + t.id + '" novalidate>' +
        '<input class="form-control" id="subTitle" maxlength="200" placeholder="Add a subtask" aria-label="New subtask title">' +
        '<button class="btn btn-outline-primary" type="submit">Add</button></form>';
    }
    html += '<h3 class="h6 mt-4 mb-3">History</h3>' + renderTimeline(history);
    $('#detailBody').innerHTML = html;

    $('#detailFooter').innerHTML =
      '<button type="button" class="btn btn-outline-danger" data-action="delete" data-id="' + t.id + '" data-title="' + esc(t.title) + '"><i class="bi bi-trash"></i> Delete</button>' +
      '<div class="d-flex gap-2"><button type="button" class="btn btn-outline-secondary" data-action="edit" data-id="' + t.id + '"><i class="bi bi-pencil"></i> Edit</button>' +
      '<button type="button" class="btn ' + (done ? 'btn-outline-primary' : 'btn-success') + '" data-action="toggle" data-id="' + t.id + '" data-status="' + t.status + '">' +
      (done ? '<i class="bi bi-arrow-counterclockwise"></i> Reopen' : '<i class="bi bi-check-lg"></i> Mark completed') + '</button></div>';
  }

  function subtaskItem(s) {
    const done = s.status === 'COMPLETED';
    return '<div class="sub-item' + (done ? ' done' : '') + '" data-sub="' + s.id + '">' +
      '<input class="form-check-input mt-0" type="checkbox"' + (done ? ' checked' : '') + ' data-change="subtoggle" data-id="' + s.id + '" aria-label="Mark subtask completed: ' + esc(s.title) + '">' +
      '<span class="sub-title">' + esc(s.title) + '</span>' +
      '<button type="button" class="btn btn-sm btn-link p-0" data-action="subedit" data-id="' + s.id + '" aria-label="Rename subtask ' + esc(s.title) + '"><i class="bi bi-pencil"></i></button>' +
      '<button type="button" class="btn btn-sm btn-link text-danger p-0" data-action="subdelete" data-id="' + s.id + '" data-title="' + esc(s.title) + '" aria-label="Delete subtask ' + esc(s.title) + '"><i class="bi bi-trash"></i></button></div>';
  }

  async function afterSubtaskChange() {
    await openDetail(state.detailId);
    renderView();
  }

  async function addSubtask(e) {
    e.preventDefault();
    const input = $('#subTitle');
    const title = input.value.trim();
    if (!title) { input.focus(); return; }
    try {
      await api('/api/tasks/' + e.target.dataset.parent + '/subtasks', { method: 'POST', body: { title: title } });
      await afterSubtaskChange();
      toast('Subtask added.');
    } catch (err) { showError(err); }
  }

  function startSubtaskEdit(id) {
    const s = state.detail.subtasks.find((x) => x.id === id);
    const row = $('.sub-item[data-sub="' + id + '"]');
    if (!s || !row) { return; }
    row.innerHTML = '<form class="d-flex gap-2 w-100" data-submit="subsave" data-id="' + id + '">' +
      '<input class="form-control form-control-sm" maxlength="200" value="' + esc(s.title) + '" aria-label="Subtask title" required>' +
      '<button class="btn btn-sm btn-primary" type="submit">Save</button>' +
      '<button class="btn btn-sm btn-outline-secondary" type="button" data-action="subcancel">Cancel</button></form>';
    $('input', row).focus();
  }

  async function saveSubtask(form) {
    const id = Number(form.dataset.id);
    const s = state.detail.subtasks.find((x) => x.id === id);
    const title = $('input', form).value.trim();
    if (!title) { return; }
    try {
      await api('/api/tasks/' + id, {
        method: 'PUT',
        body: { title: title, description: s.description, status: s.status, priority: s.priority, dueDate: s.dueDate, categoryId: s.categoryId, tags: s.tags.map((g) => g.name) }
      });
      await afterSubtaskChange();
      toast('Subtask updated.');
    } catch (err) { showError(err); }
  }

  async function toggleSubtask(id, checked) {
    try {
      await api('/api/tasks/' + id + '/status', { method: 'PATCH', body: { status: checked ? 'COMPLETED' : 'TODO' } });
      await afterSubtaskChange();
    } catch (err) { showError(err); await openDetail(state.detailId); }
  }

  async function deleteSubtask(id, title) {
    if (!(await confirmDelete('Delete subtask?', '"' + title + '" will be permanently deleted.'))) { return; }
    try {
      await api('/api/tasks/' + id, { method: 'DELETE' });
      toast('Subtask deleted.');
      await afterSubtaskChange();
    } catch (err) { showError(err); }
  }

  // ---------------------------------------------------------------- global event wiring
  document.addEventListener('click', async (e) => {
    const el = e.target.closest('[data-action]');
    if (!el) { return; }
    const action = el.dataset.action;
    const id = Number(el.dataset.id);
    if (el.tagName === 'A') { e.preventDefault(); }
    switch (action) {
      case 'new': openTaskForm(null, { due: el.dataset.due }); break;
      case 'open': openDetail(id); break;
      case 'edit': openEdit(id); break;
      case 'delete': deleteTask(id, el.dataset.title); break;
      case 'toggle': toggleTask(id, el.dataset.status); break;
      case 'stat-link': setFiltersAndGo({ status: el.dataset.fstatus, due: el.dataset.fdue }); break;
      case 'clear-filters': setFiltersAndGo({}); break;
      case 'cal-select': state.cal.selected = el.dataset.date; drawCalendar(); drawCalendarDay(); break;
      case 'cat-filter': setFiltersAndGo({ categoryId: String(id) }); break;
      case 'tag-filter': setFiltersAndGo({ tagId: String(id) }); break;
      case 'cat-edit': {
        const c = state.categories.find((x) => x.id === id);
        if (c) {
          state.editingCategoryId = id;
          $('#catName').value = c.name; $('#catColor').value = c.color.toLowerCase();
          $('#catSubmitText').textContent = 'Save'; $('#catCancel').classList.remove('d-none');
          $('#catName').focus();
        }
        break;
      }
      case 'cat-delete':
        if (await confirmDialog('Delete category?', 'Tasks in "' + el.dataset.title + '" are kept but will have no category.')) {
          try { await api('/api/categories/' + id, { method: 'DELETE' }); toast('Category deleted.'); resetCategoryForm(); await refreshAfterChange(); } catch (err) { showError(err); }
        }
        break;
      case 'tag-delete':
        if (await confirmDialog('Delete tag?', 'The tag "#' + el.dataset.title + '" will be removed from all tasks.')) {
          try { await api('/api/tags/' + id, { method: 'DELETE' }); toast('Tag deleted.'); await refreshAfterChange(); } catch (err) { showError(err); }
        }
        break;
      case 'subedit': startSubtaskEdit(id); break;
      case 'subcancel': openDetail(state.detailId); break;
      case 'subdelete': deleteSubtask(id, el.dataset.title); break;
      default: break;
    }
  });

  document.addEventListener('change', (e) => {
    const el = e.target.closest('[data-change]');
    if (!el) { return; }
    const id = Number(el.dataset.id);
    if (el.dataset.change === 'kstatus') { moveTask(id, el.value); }
    if (el.dataset.change === 'subtoggle') { toggleSubtask(id, el.checked); }
  });

  document.addEventListener('submit', (e) => {
    const form = e.target;
    if (form.id === 'subForm') { addSubtask(e); }
    else if (form.dataset && form.dataset.submit === 'subsave') { e.preventDefault(); saveSubtask(form); }
  });

  // Kanban drag & drop (the status is saved to the server on drop)
  const kanban = $('#kanban');
  let dragId = null;
  kanban.addEventListener('dragstart', (e) => {
    const card = e.target.closest('.kanban-card');
    if (!card) { return; }
    dragId = Number(card.dataset.id);
    card.classList.add('dragging');
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', String(dragId));
  });
  kanban.addEventListener('dragend', () => { $$('.dragging', kanban).forEach((c) => c.classList.remove('dragging')); $$('.drag-over', kanban).forEach((c) => c.classList.remove('drag-over')); });
  kanban.addEventListener('dragover', (e) => {
    const col = e.target.closest('.kanban-col');
    if (col && dragId !== null) { e.preventDefault(); $$('.drag-over', kanban).forEach((c) => c.classList.remove('drag-over')); col.classList.add('drag-over'); }
  });
  kanban.addEventListener('drop', (e) => {
    const col = e.target.closest('.kanban-col');
    if (!col || dragId === null) { return; }
    e.preventDefault();
    const id = dragId;
    dragId = null;
    const card = $('.kanban-card[data-id="' + id + '"]', kanban);
    const current = card ? card.closest('.kanban-col').dataset.status : null;
    if (current !== col.dataset.status) { moveTask(id, col.dataset.status); } else { renderKanban(); }
  });

  // Filters
  let searchTimer = null;
  $('#filterForm').addEventListener('submit', (e) => e.preventDefault());
  $('#filterForm').addEventListener('input', (e) => {
    if (e.target.id === 'fQ') { clearTimeout(searchTimer); searchTimer = setTimeout(() => { readFilterUi(); renderView(); }, 300); }
  });
  $('#filterForm').addEventListener('change', (e) => { if (e.target.id !== 'fQ') { readFilterUi(); renderView(); } });
  $('#fDir').addEventListener('click', () => { state.filters.dir = state.filters.dir === 'asc' ? 'desc' : 'asc'; renderView(); });
  $('#fClear').addEventListener('click', () => { state.filters = Object.assign({}, DEFAULT_FILTERS); renderView(); });

  // Other controls
  $('#quickForm').addEventListener('submit', quickAdd);
  $('#taskForm').addEventListener('submit', saveTask);
  $('#catForm').addEventListener('submit', saveCategory);
  $('#catCancel').addEventListener('click', resetCategoryForm);
  $('#calPrev').addEventListener('click', () => shiftMonth(-1));
  $('#calNext').addEventListener('click', () => shiftMonth(1));
  $('#calToday').addEventListener('click', () => { state.cal.year = 0; renderView(); });
  $('#themeBtn').addEventListener('click', () => applyTheme(document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark'));
  $('#logoutBtn').addEventListener('click', async () => {
    try { await api('/api/auth/logout', { method: 'POST' }); } catch (err) { /* leave anyway */ }
    location.href = '/login.html';
  });
  $('#navMobile').addEventListener('click', (e) => {
    if (e.target.closest('[data-nav]')) { const oc = bootstrap.Offcanvas.getInstance($('#mobileNav')); if (oc) { oc.hide(); } }
  });
  // After the edit dialog closes, go back to the details dialog it was opened from.
  $('#taskModal').addEventListener('hidden.bs.modal', () => {
    const back = state.returnToDetail;
    state.returnToDetail = null;
    if (back) { openDetail(back); }
  });
  $('#taskModal').addEventListener('shown.bs.modal', () => $('#tf-title').focus());
  window.addEventListener('hashchange', route);

  // ---------------------------------------------------------------- start-up
  async function init() {
    applyTheme(document.documentElement.getAttribute('data-bs-theme') || 'light');
    try {
      state.me = await api('/api/auth/me');
      $('#userName').textContent = state.me.name;
      $('#userEmail').textContent = state.me.email;
      $('#avatar').textContent = (state.me.name.trim().charAt(0) || '?').toUpperCase();
      const results = await Promise.all([loadMeta(), api('/api/tasks/stats')]);
      state.today = results[1].today;
    } catch (e) { showError(e); }
    await route();
  }
  init();
})();
