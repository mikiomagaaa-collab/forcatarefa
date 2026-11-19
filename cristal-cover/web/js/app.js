// L.I.L.Y Web: a casca do app (barra lateral, rotas, presença, avatar) e a conversa.

import { Gateway, GatewayError, usd } from './core/gateway.js';
import { pickRemote, label } from './core/routers.js';
import { render } from './core/markdown.js';
import { Presence } from './core/presence.js';
import * as store from './core/store.js';
import * as local from './core/local.js';
import { AvatarView } from './ui/avatar.js';
import { h, clear, icon, toast } from './ui/dom.js';
import { renderCodex } from './ui/codex.js';
import { renderVideo } from './ui/video.js';
import { renderSettings, renderLegal } from './ui/settings.js';

export const app = {
  settings: store.settings(),
  presence: new Presence(),
  status: null,
  models: [],
  avatars: [],
};

app.gateway = new Gateway(() => ({ url: app.settings.gatewayUrl, key: store.accessKey(), online: navigator.onLine }));

app.saveSettings = (change) => {
  app.settings = { ...app.settings, ...change };
  store.saveSettings(app.settings);
  applyTheme();
  app.avatars.forEach((a) => a.setLook({ ...lookOf(), ...(a.fixed ? { presentation: a.fixed } : {}) }));
  renderSidebar();
};

/** Recursos online podem ser usados agora (gateway configurado, consentimento e internet). */
app.onlineReady = () => app.settings.consent && !!store.accessKey() && navigator.onLine;

app.refreshGateway = async () => {
  if (!store.accessKey()) return (app.status = null);
  try {
    app.status = await app.gateway.status();
    app.models = app.status.features.code || app.status.features.chatFree ? await app.gateway.codeModels(true) : [];
  } catch (e) {
    app.status = { error: e.message };
  }
  renderSidebar();
  renderTop();
  return app.status;
};

function lookOf() {
  const a = app.settings.avatar;
  return { hue: a.hue >= 0 ? a.hue : 258, style: a.style, presentation: a.presentation, motion: a.motion };
}

app.avatar = (canvas, presentation) => {
  const v = new AvatarView(canvas, { ...lookOf(), ...(presentation ? { presentation } : {}) });
  v.fixed = presentation || null;
  const unsub = app.presence.subscribe((s) => v.setState(s));
  v.run();
  app.avatars.push(v);
  v.dispose = () => {
    v.stop();
    unsub();
    app.avatars = app.avatars.filter((x) => x !== v);
  };
  return v;
};

function applyTheme() {
  const t = app.settings.theme;
  if (t === 'system') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', t);
}

// --- Erros da página (o Diagnóstico da versão web) -----------------------------------------------

globalThis.addEventListener?.('error', (e) => store.diagnostics.add({ kind: 'erro', message: String(e.message), where: `${e.filename}:${e.lineno}` }));
globalThis.addEventListener?.('unhandledrejection', (e) => store.diagnostics.add({ kind: 'promessa', message: String(e.reason?.message || e.reason) }));

// --- Rotas ---------------------------------------------------------------------------------------

const NAV = [
  ['chat', 'Conversa', 'chat'],
  ['codex', 'Codex', 'code'],
  ['video', 'Vídeo', 'video'],
  ['tools', 'Ferramentas', 'tools'],
  ['settings', 'Configurações', 'gear'],
];

let cleanup = [];

export function go(path) {
  location.hash = '#/' + path;
}

function route() {
  const [name, arg] = (location.hash.replace(/^#\/?/, '') || 'chat').split('/');
  cleanup.forEach((f) => f());
  cleanup = [];
  const view = clear(document.getElementById('view'));
  closePanel();
  document.getElementById('sidebar').classList.remove('open');
  const ctx = { app, go, onCleanup: (f) => cleanup.push(f), openPanel, closePanel, setTitle };
  if (name === 'codex') renderCodex(view, ctx, decodeURIComponent(arg || ''));
  else if (name === 'video') renderVideo(view, ctx, decodeURIComponent(arg || ''));
  else if (name === 'tools') renderTools(view, ctx);
  else if (name === 'settings') renderSettings(view, ctx);
  else if (name === 'legal') renderLegal(view, ctx, arg);
  else renderChat(view, ctx, arg);
  renderSidebar(name, arg);
  renderTop();
}

function setTitle(t) {
  document.getElementById('title').textContent = t;
  document.title = t === 'Conversa' ? 'L.I.L.Y' : `${t} · L.I.L.Y`;
}

function openPanel(content) {
  const p = clear(document.getElementById('panel'));
  p.hidden = false;
  p.append(content);
  document.getElementById('app').classList.add('with-panel');
}

function closePanel() {
  const p = document.getElementById('panel');
  p.hidden = true;
  clear(p);
  document.getElementById('app').classList.remove('with-panel');
}

function renderSidebar(current = (location.hash.replace(/^#\/?/, '') || 'chat').split('/')[0], arg) {
  const nav = clear(document.getElementById('nav'));
  NAV.forEach(([key, text, ic]) => nav.append(h(`button${current === key ? '.on' : ''}`, { type: 'button', onclick: () => go(key) }, icon(ic), text)));
  const recent = clear(document.getElementById('recent'));
  store.conversations.list().slice(0, 40).forEach((c) =>
    recent.append(
      h(
        `li${current === 'chat' && arg === c.id ? '.on' : ''}`,
        h('button.open', { type: 'button', title: c.title, onclick: () => go('chat/' + c.id) }, c.title),
        h('button.del', { type: 'button', 'aria-label': 'Apagar conversa', onclick: () => { store.conversations.remove(c.id); if (arg === c.id) go('chat'); else renderSidebar(current, arg); } }, '×'),
      ),
    ),
  );
  const foot = clear(document.getElementById('sidebar-foot'));
  const online = app.onlineReady() && app.status && !app.status.error;
  foot.append(
    h('div.status', online ? `Online · ${app.models.length} modelos` : store.accessKey() ? 'Gateway configurado' : 'Sem serviço online'),
    h('div.status', local.current() ? `No navegador: ${local.current()}` : 'Nenhum modelo no navegador'),
    h('div.status', h('a', { href: '#/legal/termos' }, 'Termos'), ' · ', h('a', { href: '#/legal/privacidade' }, 'Privacidade'), ' · ', h('a', { href: '#/legal/creditos' }, 'Créditos')),
  );
}

function renderTop() {
  const right = clear(document.getElementById('top-right'));
  const online = app.onlineReady() && app.status && !app.status.error;
  right.append(h(`span.badge${online ? '.ok' : ''}`, h('span.dot'), online ? 'Online' : local.current() ? 'No navegador' : 'Offline'));
}

// --- Conversa ------------------------------------------------------------------------------------

function systemPrompt(where) {
  const mem = app.settings.memoryEnabled ? store.memories.list().map((m) => m.text) : [];
  let s =
    where === 'online'
      ? 'Você é a L.I.L.Y, a assistente do Liquid Flip. Nesta resposta você roda num modelo online, pelo serviço que a pessoa configurou. '
      : 'Você é a L.I.L.Y, a assistente do Liquid Flip, rodando dentro do navegador da pessoa, no computador dela. ';
  s += 'Responda em português do Brasil, a menos que peçam outro idioma. Seja clara, gentil e direta. Se faltar contexto ou você não souber, diga isso com honestidade em vez de inventar. ';
  s += 'Você não navega na internet nem acessa outros apps, a não ser pelo que a pessoa enviar aqui.';
  if (mem.length) s += '\n\nO que a pessoa pediu para você lembrar (use só quando for útil):\n' + mem.map((m) => `- ${m}`).join('\n');
  return s;
}

/** Onde responder agora: online (pelo roteador) ou no navegador, conforme o modo escolhido. */
export function plan(manual, light = false) {
  const s = app.settings;
  const canOnline = s.online !== 'OFF' && app.onlineReady() && app.models.length > 0;
  const canLocal = !!local.current();
  if (manual === 'local' && canLocal) return { where: 'local', label: local.current(), reason: 'Escolhido por você (no navegador).' };
  if (manual && manual !== 'auto' && manual !== 'local') {
    const m = app.models.find((x) => x.id === manual);
    if (m && canOnline) return { where: 'online', model: m, label: label(m), reason: `Escolhido por você: ${label(m)} (online).` };
  }
  const remote = canOnline ? pickRemote(app.models, s.profile, 'chat', light) : null;
  if (s.online === 'PREFER' && remote) return { where: 'online', model: remote, label: label(remote), reason: `Online primeiro: ${label(remote)} (${remote.vendor}).` };
  if (canLocal) return { where: 'local', label: local.current(), reason: 'No navegador (nada sai do computador).' };
  if (remote) return { where: 'online', model: remote, label: label(remote), reason: `Sem modelo no navegador: ${label(remote)} online.` };
  return null;
}

/** Uma resposta (online ou no navegador), em pedaços. */
export async function answer(messages, { manual, onText, signal, light, temperature = 0.7 } = {}) {
  const p = plan(manual, light);
  if (!p) throw new GatewayError(0, 'Conecte o serviço online ou carregue um modelo no navegador (Configurações).');
  const full = [{ role: 'system', content: systemPrompt(p.where) }, ...messages];
  const r = p.where === 'online' ? await app.gateway.chat(p.model.id, full, { temperature, signal, onText, maxTokens: 4096 }) : await local.chat(full, { onText, signal, temperature });
  return { ...r, plan: p };
}

function speak(text) {
  if (!('speechSynthesis' in globalThis)) return;
  const u = new SpeechSynthesisUtterance(text.replace(/[#*`>|]/g, '').slice(0, 1200));
  u.lang = 'pt-BR';
  u.onstart = () => app.presence.speaking('voz', 0.6);
  u.onboundary = (e) => app.presence.speaking('voz', Math.min(1, 0.45 + (e.charLength || 4) * 0.06));
  u.onend = u.onerror = () => app.presence.clear('voz');
  speechSynthesis.cancel();
  speechSynthesis.speak(u);
}

function listen(onFinal, onState) {
  const R = globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition;
  if (!R) {
    toast('Este navegador não reconhece fala (use o Chrome ou o Edge).', 'error');
    return null;
  }
  const r = new R();
  r.lang = 'pt-BR';
  r.interimResults = true;
  r.onstart = () => {
    app.presence.listening('microfone', 0.4);
    onState(true);
  };
  r.onresult = (e) => {
    const res = [...e.results];
    app.presence.listening('microfone', 0.5 + Math.random() * 0.4);
    const last = res[res.length - 1];
    if (last.isFinal) onFinal(res.map((x) => x[0].transcript).join(' '));
  };
  r.onend = r.onerror = () => {
    app.presence.clear('microfone');
    onState(false);
  };
  r.start();
  return r;
}

const SUGGESTIONS = [
  'Explique o que é computação em nuvem como se eu tivesse 12 anos',
  'Monte um plano de estudos de 1 semana para a prova de biologia',
  '/codex Revise o README do meu projeto',
  '/video Uma gota de vidro líquido caindo em câmera lenta',
];

function renderChat(view, ctx, id) {
  setTitle('Conversa');
  let conv = id ? store.conversations.get(id) : null;
  if (id && !conv) return go('chat');
  conv ||= { id: store.uid(), title: 'Nova conversa', messages: [] };
  let controller = null;
  let running = null;
  let manual = 'auto';
  const col = h('div.column');
  const list = h('div.messages');
  const textarea = h('textarea', { rows: 1, placeholder: 'Pergunte qualquer coisa · /codex · /video', 'aria-label': 'Mensagem' });
  const select = h('select', { 'aria-label': 'Modelo', onchange: (e) => (manual = e.target.value) });
  const sendBtn = h('button.icon-btn.on', { type: 'button', 'aria-label': 'Enviar' }, icon('send'));
  const micBtn = h('button.icon-btn', { type: 'button', 'aria-label': 'Falar' }, icon('mic'));
  let recognition = null;

  function fillModels() {
    clear(select);
    select.append(h('option', { value: 'auto' }, 'Automático'));
    if (local.current()) select.append(h('option', { value: 'local' }, `No navegador · ${local.current()}`));
    if (app.onlineReady()) app.models.forEach((m) => select.append(h('option', { value: m.id }, `${label(m)} · ${m.vendor}`)));
    select.value = manual;
  }
  fillModels();

  function draw() {
    clear(col);
    if (!conv.messages.length) {
      const big = h('canvas', { width: 220, height: 220, 'aria-label': 'A avatar da L.I.L.Y' });
      col.append(
        h(
          'div.empty',
          big,
          h('h2', greeting()),
          h('p.muted', 'Converse, programe com o Codex, crie vídeos ou use as ferramentas de texto.'),
          h('div.suggestions', SUGGESTIONS.map((s) => h('button', { type: 'button', onclick: () => send(s) }, s))),
        ),
      );
      const av = app.avatar(big);
      ctx.onCleanup(() => av.dispose());
    }
    clear(list);
    conv.messages.forEach((m, i) => list.append(messageEl(m, i)));
    col.append(list);
    col.append(composer());
  }

  function messageEl(m) {
    if (m.role === 'user') return h('div.msg.user', h('div.bubble', m.text));
    const body = h('div.body.rich', { html: render(m.text || '') });
    if (m.status === 'streaming') body.classList.add('typing');
    body.querySelectorAll('[data-copy]').forEach((b) => (b.onclick = () => navigator.clipboard.writeText(b.closest('.code').querySelector('code').textContent).then(() => toast('Código copiado'))));
    return h(
      `div.msg.assistant${m.status === 'error' ? '.error' : ''}`,
      h('div.body', body, m.status !== 'streaming' ? h('div.meta', m.model ? h('span', m.model) : null, m.cost != null ? h('span', usd(m.cost)) : null, h('button', { type: 'button', onclick: () => navigator.clipboard.writeText(m.text).then(() => toast('Copiado')) }, 'Copiar'), h('button', { type: 'button', onclick: () => speak(m.text) }, 'Ouvir'), h('button', { type: 'button', onclick: () => (store.memories.add(m.text.slice(0, 300)) ? toast('Guardado na memória') : toast('Isso não pode ir para a memória', 'error')) }, 'Lembrar')) : null),
    );
  }

  function composer() {
    textarea.oninput = () => {
      textarea.style.height = 'auto';
      textarea.style.height = Math.min(240, textarea.scrollHeight) + 'px';
    };
    textarea.onkeydown = (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        send(textarea.value);
      }
    };
    // Com texto, envia (e interrompe a resposta em andamento); sem texto, o botão para a resposta.
    sendBtn.onclick = () => (controller && !textarea.value.trim() ? controller.abort() : send(textarea.value));
    micBtn.onclick = () => {
      if (recognition) return recognition.stop();
      recognition = listen((text) => send(text, true), (on) => {
        micBtn.classList.toggle('on', on);
        if (!on) recognition = null;
      });
    };
    const p = plan(manual);
    return h(
      'div.composer-wrap',
      h('div.composer', textarea, h('div.bar', select, h('span.grow'), micBtn, sendBtn)),
      h('div.note', p ? (p.where === 'online' ? `${p.reason} O texto vai para o seu gateway e o provedor.` : p.reason) : 'Conecte o serviço online ou carregue um modelo no navegador em Configurações.'),
    );
  }

  async function send(text, voice = false) {
    const t = String(text || '').trim();
    if (!t) return;
    // Uma pergunta nova no meio de uma resposta: para a anterior e responde a nova (nada é descartado).
    if (controller) {
      controller.abort();
      globalThis.speechSynthesis?.cancel?.();
      await running?.catch(() => {});
    }
    if (/^\/codex\b/i.test(t)) return go('codex/' + encodeURIComponent(t.replace(/^\/codex\s*/i, '')));
    if (/^\/v[ií]deo\b/i.test(t)) return go('video/' + encodeURIComponent(t.replace(/^\/v[ií]deo\s*/i, '')));
    let finishRun;
    running = new Promise((r) => (finishRun = r));
    textarea.value = '';
    textarea.style.height = 'auto';
    if (!conv.messages.length) conv.title = t.split(/\s+/).slice(0, 6).join(' ').slice(0, 42) || 'Nova conversa';
    conv.messages.push({ role: 'user', text: t });
    const reply = { role: 'assistant', text: '', status: 'streaming' };
    conv.messages.push(reply);
    draw();
    const lastEl = () => list.lastElementChild;
    controller = new AbortController();
    sendBtn.replaceChildren(icon('stop'));
    app.presence.thinking('conversa', 'Pensando');
    const past = conv.messages.slice(0, -1).filter((m) => m.status !== 'error' && m.status !== 'stopped').slice(-16).map((m) => ({ role: m.role === 'user' ? 'user' : 'assistant', content: m.text.slice(0, 4000) }));
    try {
      const r = await answer(past, {
        manual,
        signal: controller.signal,
        onText: (txt) => {
          reply.text = txt;
          const el = lastEl()?.querySelector('.rich');
          if (el) el.innerHTML = render(txt);
          el?.closest('.view')?.scrollTo?.(0, 1e9);
        },
      });
      reply.text = r.text.trim() || 'O modelo não devolveu nenhum texto. Tente reformular.';
      reply.model = r.plan.where === 'online' ? `${r.plan.label} · online${r.plan.model?.free ? ' · grátis' : ''}` : `${r.plan.label} · no navegador`;
      reply.cost = r.plan.model?.free ? null : r.usage?.costUsd ?? null;
      reply.status = 'done';
      app.presence.finished('conversa', true, 'Resposta pronta');
      if (voice || app.settings.speakAnswers) speak(reply.text);
    } catch (e) {
      if (e.name === 'AbortError') {
        reply.status = 'stopped';
        reply.text ||= '(parado)';
        app.presence.clear('conversa');
      } else {
        reply.status = 'error';
        reply.text = e.message || 'Não consegui responder.';
        app.presence.finished('conversa', false, 'Não consegui responder');
      }
    } finally {
      controller = null;
      finishRun();
      sendBtn.replaceChildren(icon('send'));
      store.conversations.put(conv);
      if (location.hash !== '#/chat/' + conv.id) history.replaceState?.(null, '', '#/chat/' + conv.id);
      draw();
      renderSidebar('chat', conv.id);
      view.scrollTo?.(0, 1e9);
    }
  }

  view.append(col);
  draw();
  textarea.focus();
}

function greeting() {
  const hr = new Date().getHours();
  return hr < 12 ? 'Bom dia' : hr < 18 ? 'Boa tarde' : 'Boa noite';
}

// --- Ferramentas de texto (as mesmas instruções do app) --------------------------------------------

const TOOLS = {
  CORRECT: ['Corrigir', 'Corrija só os erros de ortografia, acentuação, digitação e pontuação do texto abaixo, sem mudar o sentido nem o estilo. Mantenha o jeito de escrever da pessoa. Responda só com o resultado, sem aspas, sem explicações e sem comentários.'],
  REWRITE: ['Reescrever', 'Reescreva o texto abaixo num tom neutro e claro, mantendo o sentido. Responda só com o resultado, sem aspas, sem explicações e sem comentários.'],
  SUMMARIZE: ['Resumir', 'Resuma o texto abaixo em português, com os pontos principais, em poucas frases ou tópicos curtos. Não acrescente nada que não esteja no texto. Responda só com o resultado.'],
  TRANSLATE: ['Traduzir para o inglês', 'Traduza o texto abaixo para inglês, mantendo o tom. Responda só com o resultado, sem aspas, sem explicações e sem comentários.'],
  EXPLAIN: ['Explicar', 'Explique o texto abaixo em português simples, como para alguém que não conhece o assunto.'],
  ORGANIZE: ['Organizar', 'Organize as informações do texto abaixo em tópicos claros (listas, datas e responsáveis quando houver). Não invente nada. Responda só com o resultado.'],
};

function renderTools(view, ctx) {
  setTitle('Ferramentas');
  let task = 'CORRECT';
  const input = h('textarea', { rows: 8, placeholder: 'Cole ou escreva o texto aqui' });
  const out = h('div.rich');
  const run = h('button.primary', { type: 'button' }, 'Aplicar');
  const chipsEl = h('div.chips');
  const drawChips = () => {
    clear(chipsEl);
    Object.entries(TOOLS).forEach(([k, [lbl]]) => chipsEl.append(h(`button.chip${k === task ? '.on' : ''}`, { type: 'button', onclick: () => { task = k; drawChips(); } }, lbl)));
  };
  drawChips();
  run.onclick = async () => {
    const text = input.value.trim();
    if (!text) return;
    run.disabled = true;
    out.innerHTML = '';
    out.classList.add('typing');
    app.presence.thinking('ferramenta', TOOLS[task][0]);
    try {
      const r = await answer([{ role: 'user', content: `${TOOLS[task][1]}\n\nTexto:\n${text}` }], { light: task === 'CORRECT' || task === 'SUMMARIZE', temperature: task === 'CORRECT' || task === 'TRANSLATE' ? 0.2 : 0.7, onText: (t) => (out.innerHTML = render(t)) });
      out.innerHTML = render(r.text);
      out.append(h('div.meta.faint', `${r.plan.label} · ${r.plan.where === 'online' ? 'online' : 'no navegador'}`, r.usage?.costUsd != null ? ` · ${usd(r.usage.costUsd)}` : ''));
      app.presence.finished('ferramenta', true);
    } catch (e) {
      out.innerHTML = '';
      out.append(h('p.danger', e.message));
      app.presence.finished('ferramenta', false);
    } finally {
      out.classList.remove('typing');
      run.disabled = false;
    }
  };
  view.append(h('div.column', h('div.card', h('h2', 'Ferramentas de texto'), h('p.faint', 'As mesmas do app e do teclado. Usam o modelo do navegador ou o online, conforme as Configurações.'), chipsEl, h('label.field', h('span', 'Texto'), input), h('div.row', run)), h('div.card', h('h2', 'Resultado'), out)));
}

// --- Início --------------------------------------------------------------------------------------

function start() {
  applyTheme();
  document.getElementById('menu').append(icon('menu'));
  document.getElementById('menu').onclick = () => document.getElementById('sidebar').classList.toggle('open');
  document.getElementById('new-chat').onclick = () => (location.hash === '#/chat' ? route() : go('chat'));
  app.avatar(document.getElementById('brand-avatar'), 'ORB');
  const floating = h('div.floating-avatar', h('canvas', { width: 92, height: 92, 'aria-hidden': 'true' }));
  document.body.append(floating);
  app.avatar(floating.querySelector('canvas'), 'FACE');
  // A avatar flutuante aparece só durante tarefas, fora da tela de conversa vazia.
  app.presence.subscribe((s) => floating.classList.toggle('show', app.settings.avatar.enabled && s.mode !== 'IDLE' && !location.hash.startsWith('#/settings')));
  addEventListener('hashchange', route);
  addEventListener('online', () => app.refreshGateway());
  route();
  app.refreshGateway();
}

if (typeof document !== 'undefined' && document.getElementById('app')) start();
