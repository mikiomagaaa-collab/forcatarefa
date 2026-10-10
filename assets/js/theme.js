const theme = window.ftTheme;
const host = document.querySelector('#menu') || document.querySelector('.admin-header .admin-toolbar');
const icons = {
  light: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
  dark: '<path d="M20.8 13.2A9 9 0 0 1 10.8 3.1 9 9 0 1 0 20.8 13.2Z"/>',
  system: '<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4"/>'
};
function icon(kind) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  for (const [key, value] of Object.entries({viewBox:'0 0 24 24',fill:'none',stroke:'currentColor','stroke-width':'1.7','stroke-linecap':'round','stroke-linejoin':'round','aria-hidden':'true'})) svg.setAttribute(key, value);
  svg.innerHTML = icons[kind];
  return svg;
}
if (host && theme) {
  const menu = document.createElement('details'); menu.className = 'nav-appearance';
  const summary = document.createElement('summary'); summary.append(icon(theme.getResolvedTheme()), document.createTextNode('Aparência'));
  const panel = document.createElement('div'); panel.className = 'appearance-panel';
  const title = document.createElement('p'); title.className = 'appearance-title'; title.textContent = 'Aparência do site';
  const group = document.createElement('div'); group.setAttribute('role','radiogroup'); group.setAttribute('aria-label','Tema do site');
  const buttons = [];
  for (const [value, label] of [['light','Claro'],['dark','Escuro'],['system','Automático']]) {
    const button = document.createElement('button'); button.type = 'button'; button.setAttribute('role','radio'); button.dataset.themeChoice = value;
    const check = document.createElement('span'); check.className = 'appearance-check'; check.setAttribute('aria-hidden','true'); check.textContent = '✓';
    button.append(icon(value),document.createTextNode(label),check);
    button.addEventListener('click', () => theme.setPreference(value));
    button.addEventListener('keydown', event => {
      let next = buttons.indexOf(button);
      if (['ArrowRight','ArrowDown'].includes(event.key)) next = (next + 1) % buttons.length;
      else if (['ArrowLeft','ArrowUp'].includes(event.key)) next = (next + buttons.length - 1) % buttons.length;
      else if (event.key === 'Home') next = 0;
      else if (event.key === 'End') next = buttons.length - 1;
      else return;
      event.preventDefault(); buttons[next].focus(); theme.setPreference(buttons[next].dataset.themeChoice);
    });
    buttons.push(button); group.append(button);
  }
  const note = document.createElement('p'); note.className = 'appearance-note'; note.textContent = 'Automático acompanha a aparência do seu aparelho.';
  const live = document.createElement('span'); live.className = 'sr-only'; live.setAttribute('role','status'); live.setAttribute('aria-live','polite');
  panel.append(title,group,note,live); menu.append(summary,panel); host.append(menu);
  function sync(announce = false) {
    const selected = theme.getPreference();
    for (const button of buttons) { const active = button.dataset.themeChoice === selected; button.setAttribute('aria-checked', String(active)); button.tabIndex = active ? 0 : -1; }
    summary.querySelector('svg').replaceWith(icon(theme.getResolvedTheme()));
    if (announce) live.textContent = `Aparência ${selected === 'system' ? 'automática' : selected === 'dark' ? 'escura' : 'clara'} selecionada.`;
  }
  document.addEventListener('ft-theme-change', () => sync(true)); sync();
  menu.addEventListener('toggle', () => { if (menu.open) host.querySelectorAll('.nav-more[open]').forEach(item => item.open = false); });
  document.addEventListener('click', event => { if (!menu.contains(event.target)) menu.open = false; });
  menu.addEventListener('keydown', event => { if (event.key === 'Escape') { event.stopPropagation(); event.preventDefault(); menu.open = false; summary.focus(); } });
}
