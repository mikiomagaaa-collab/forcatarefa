// Ajudantes de interface sem framework.

/** h('div.classe#id', {atributos, onclick}, ...filhos) */
export function h(tag, attrs, ...children) {
  const [name, ...rest] = tag.split(/(?=[.#])/);
  const el = document.createElement(name || 'div');
  for (const r of rest) {
    if (r.startsWith('.')) el.classList.add(r.slice(1));
    else if (r.startsWith('#')) el.id = r.slice(1);
  }
  if (attrs && (typeof attrs !== 'object' || attrs instanceof Node || Array.isArray(attrs))) {
    children.unshift(attrs);
    attrs = null;
  }
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'html') el.innerHTML = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat(Infinity)) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

export function clear(el) {
  while (el.firstChild) el.removeChild(el.firstChild);
  return el;
}

export function icon(name) {
  const paths = {
    plus: 'M12 5v14M5 12h14',
    chat: 'M4 5h16v11H8l-4 4z',
    code: 'M8 7l-5 5 5 5M16 7l5 5-5 5',
    video: 'M3 6h12v12H3zM15 10l6-3v10l-6-3',
    tools: 'M14 6l4 4-8 8H6v-4zM16 4l4 4',
    gear: 'M12 9a3 3 0 100 6 3 3 0 000-6zM19 12l2-1-1-3-2 .5-1.5-1.5.5-2-3-1-1 2h-2l-1-2-3 1 .5 2L6 7.5 4 7 3 10l2 1v2l-2 1 1 3 2-.5 1.5 1.5-.5 2 3 1 1-2h2l1 2 3-1-.5-2 1.5-1.5 2 .5 1-3-2-1z',
    send: 'M4 12l16-8-6 16-2-6z',
    mic: 'M12 4a3 3 0 013 3v5a3 3 0 01-6 0V7a3 3 0 013-3zM6 11a6 6 0 0012 0M12 17v3',
    stop: 'M7 7h10v10H7z',
    menu: 'M4 7h16M4 12h16M4 17h16',
    trash: 'M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13',
    folder: 'M3 6h6l2 2h10v11H3z',
    github: 'M12 3a9 9 0 00-3 17.5c.5 0 .6-.2.6-.5v-2c-2.5.6-3-1-3-1-.4-1-1-1.3-1-1.3-.8-.6.1-.6.1-.6.9.1 1.4.9 1.4.9.8 1.4 2.2 1 2.7.8.1-.6.3-1 .6-1.2-2-.2-4.1-1-4.1-4.5 0-1 .4-1.8.9-2.4-.1-.2-.4-1.2.1-2.4 0 0 .8-.2 2.5.9a8.6 8.6 0 014.5 0c1.7-1.1 2.5-.9 2.5-.9.5 1.2.2 2.2.1 2.4.6.6.9 1.4.9 2.4 0 3.5-2.1 4.3-4.1 4.5.3.3.6.8.6 1.6v2.4c0 .3.2.6.6.5A9 9 0 0012 3z',
    copy: 'M8 8h11v11H8zM5 16V5h11',
    check: 'M5 12l5 5 9-10',
    cloud: 'M7 18a4 4 0 010-8 6 6 0 0111.5 1.5A3.5 3.5 0 0118 18z',
    chip: 'M7 7h10v10H7zM10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4',
  };
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('class', 'icon');
  svg.setAttribute('aria-hidden', 'true');
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', paths[name] || '');
  svg.append(p);
  return svg;
}

export function toast(text, kind = '') {
  const t = h(`div.toast${kind ? '.' + kind : ''}`, text);
  document.body.append(t);
  setTimeout(() => t.classList.add('show'), 10);
  setTimeout(() => {
    t.classList.remove('show');
    setTimeout(() => t.remove(), 300);
  }, 3800);
}

export function chips(options, current, onPick) {
  return h(
    'div.chips',
    options.map(([value, label]) => h(`button.chip${value === current ? '.on' : ''}`, { type: 'button', onclick: () => onPick(value) }, label)),
  );
}

export function field(label, input) {
  return h('label.field', h('span', label), input);
}

export function toggle(checked, onChange, label, detail) {
  const input = h('input', { type: 'checkbox', checked: checked || null, onchange: (e) => onChange(e.target.checked) });
  return h('label.toggle', h('div', h('strong', label), detail ? h('small', detail) : null), input, h('span.track'));
}
