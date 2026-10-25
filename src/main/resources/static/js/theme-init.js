// Runs before the page is painted so the saved theme is applied without a white flash.
(function () {
  var theme = 'light';
  try {
    var saved = localStorage.getItem('theme');
    if (saved === 'dark' || saved === 'light') {
      theme = saved;
    } else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      theme = 'dark';
    }
  } catch (e) { /* storage blocked: keep the light theme */ }
  document.documentElement.setAttribute('data-bs-theme', theme);
})();
