export function initAdminNavigation() {
  const links = [...document.querySelectorAll('.admin-tabs a')];
  const panels = [...document.querySelectorAll('[data-admin-area]')];
  function showArea() {
    const requested = location.hash.slice(1);
    const area = ['admin-classifications','admin-editorial'].includes(requested) ? 'admin-proposals' : requested;
    const selected = links.some(link => link.hash === '#' + area) ? area : 'admin-intentions';
    for (const panel of panels) panel.hidden = panel.dataset.adminArea !== selected;
    for (const link of links) {
      if (link.hash === '#' + selected) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    }
  }
  window.addEventListener('hashchange', showArea);
  window.addEventListener('popstate', showArea);
  document.querySelector('#admin-content').addEventListener('click', event => {
    const link = event.target.closest('a[href^="#admin-"]');
    if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    history.pushState(null, '', link.hash);
    showArea();
  });
  showArea();
}
