(function () {
  'use strict';
  const key = 'ft-theme-preference';
  const choices = ['light', 'dark', 'system'];
  const root = document.documentElement;
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let preference = 'light';
  let timer;
  try { const saved = localStorage.getItem(key); if (choices.includes(saved)) preference = saved; } catch {}
  function apply(animate = false) {
    const theme = preference === 'system' ? (system.matches ? 'dark' : 'light') : preference;
    clearTimeout(timer);
    root.classList.toggle('theme-changing', animate && !motion.matches && root.dataset.theme !== theme);
    root.dataset.theme = theme;
    root.dataset.themePreference = preference;
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = theme === 'dark' ? '#0E192A' : '#071F50';
    timer = setTimeout(() => root.classList.remove('theme-changing'), 250);
    document.dispatchEvent(new CustomEvent('ft-theme-change'));
  }
  window.ftTheme = Object.freeze({
    getPreference: () => preference,
    getResolvedTheme: () => root.dataset.theme,
    setPreference(value) {
      if (!choices.includes(value)) return;
      preference = value;
      try { localStorage.setItem(key, value); } catch {}
      apply(true);
    }
  });
  system.addEventListener('change', () => { if (preference === 'system') apply(true); });
  window.addEventListener('storage', event => {
    if (event.key !== key && event.key !== null) return;
    preference = choices.includes(event.newValue) ? event.newValue : 'light';
    apply(true);
  });
  document.addEventListener('DOMContentLoaded', () => apply());
  apply();
})();
