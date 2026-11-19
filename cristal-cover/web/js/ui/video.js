// Video AI na L.I.L.Y Web: o mesmo serviço do app (o modelo roda no gateway; aqui só o pedido e o resultado).

import { pickVideo, VIDEO_QUALITY } from '../core/routers.js';
import { usd } from '../core/gateway.js';
import * as store from '../core/store.js';
import { h, clear, toast, chips, field, toggle } from './dom.js';

const MOTION = { GENTLE: ['Suave', 'slow, gentle camera movement, subtle motion'], NATURAL: ['Natural', ''], DYNAMIC: ['Dinâmico', 'dynamic camera movement, energetic motion'] };
let polling = null;
/** Vídeos com a conexão de acompanhamento aberta (não abrir outra; cancelar fecha a conexão). */
const streaming = new Map();

function progressOf(j) {
  if (j.status === 'queued') return 0.04;
  if (j.status === 'running') return Math.min(0.93, 0.08 + (0.85 * (j.elapsed || 0)) / Math.max(10, j.typicalSeconds || 90));
  return j.status === 'done' ? 1 : 0;
}

export function renderVideo(view, ctx, preset) {
  const { app } = ctx;
  ctx.setTitle('Vídeo e imagem');
  const col = h('div.column');
  view.append(col);
  if (!app.onlineReady()) {
    col.append(h('div.card', h('h2', 'Conecte o serviço online'), h('p.muted', 'Os vídeos e as imagens rodam no seu L.I.L.Y Gateway (grátis pelo Hugging Face e pelo Workers AI). Aqui você descreve, acompanha e baixa o resultado.'), h('button.primary', { type: 'button', onclick: () => ctx.go('settings') }, 'Configurar')));
    return;
  }
  let models = [];
  let image = null; // data URL
  let quality = 'BALANCED';
  let manual = null;
  let motion = 'NATURAL';
  let audio = false;
  let aspect = '16:9';
  let duration = 0;
  let resolution = '';
  const prompt = h('textarea', { rows: 3, placeholder: 'Ex.: uma onda de vidro líquido se formando ao pôr do sol, câmera lenta' });
  prompt.value = preset || '';
  const imageCard = h('div');
  const form = h('div');
  const results = h('div');
  col.append(imageCard, form, results);

  async function drawImageCard() {
    let free = null;
    try {
      const st = await app.gateway.status();
      free = st.features?.image ? st.free?.image : null;
    } catch {
      // sem status: não mostra o cartão
    }
    clear(imageCard);
    if (!free) return;
    const ip = h('textarea', { rows: 2, placeholder: 'Ex.: um farol de vidro líquido ao pôr do sol' });
    const out = h('div');
    const left = h('p.faint', free.usedToday != null ? `Hoje: ${free.usedToday} de ${free.perDay} grátis.` : `Até ${free.perDay} por dia, grátis.`);
    const go = h('button.primary', { type: 'button' }, 'Gerar imagem grátis');
    go.onclick = async () => {
      const text = ip.value.trim();
      if (!text) return toast('Descreva a imagem.', 'error');
      go.disabled = true;
      app.presence.working('imagem', 'Gerando imagem online');
      try {
        const r = await app.gateway.image(text, Math.floor(Math.random() * 1e6));
        clear(out);
        out.append(h('img.result', { src: r.image, alt: text }), h('div.row', h('a.ghost', { href: r.image, download: 'lily-imagem.png' }, 'Baixar')));
        if (r.usedToday != null) left.textContent = `Hoje: ${r.usedToday} de ${r.perDay} grátis.`;
        app.presence.finished('imagem', true, 'Imagem pronta');
      } catch (e) {
        toast(e.message, 'error');
        app.presence.finished('imagem', false, 'A imagem falhou');
      } finally {
        go.disabled = false;
      }
    };
    imageCard.append(h('div.card', h('h2', 'Criar uma imagem'), h('p.muted', `${free.model} · ${free.width} × ${free.height} · grátis, em segundos`), field('Descrição', ip), go, left, out));
  }

  async function load() {
    try {
      models = await app.gateway.videoModels();
      if (!models.length) form.append(h('p.warn', 'O seu gateway ainda não tem a geração de vídeo (publique a versão 1.1 ou mais nova).'));
    } catch (e) {
      toast(e.message, 'error');
    }
    draw();
  }

  function chosen() {
    const auto = pickVideo(models, !!image, audio, quality);
    const m = (manual && models.find((x) => x.id === manual && (image ? x.modes.includes('image') : x.modes.includes('text')))) || auto?.model;
    return { m, auto };
  }

  let quoteTimer = null;
  function draw() {
    clear(form);
    const { m, auto } = chosen();
    if (m) {
      if (!m.aspects.includes(aspect)) aspect = m.aspects[0];
      if (!m.durations.includes(duration)) duration = m.durations[0];
      if (!m.resolutions.includes(resolution)) resolution = m.resolutions[0];
      if (!m.audio) audio = false;
    }
    const drop = h('div.drop', { onclick: () => fileInput.click() }, image ? h('img', { src: image, alt: 'Imagem de referência' }) : 'Opcional: clique para escolher uma imagem para animar');
    const fileInput = h('input', { type: 'file', accept: 'image/jpeg,image/png,image/webp', hidden: true, onchange: (e) => readImage(e.target.files[0]) });
    const card = h('div.card.strong', h('h2', 'Criar um vídeo'), field('O que deve acontecer', prompt), drop, fileInput);
    if (image) card.append(h('button.ghost', { type: 'button', onclick: () => { image = null; draw(); } }, 'Tirar a imagem'));
    card.append(h('h3', 'Qualidade'), chips(Object.entries(VIDEO_QUALITY).map(([k, v]) => [k, v.label]), manual ? '' : quality, (v) => { quality = v; manual = null; draw(); }), h('p.faint', auto?.reason || (models.length ? 'Nenhum modelo faz este pedido.' : 'Carregando os modelos…')));
    if (m) {
      if (m.aspects.length > 1) card.append(h('h3', 'Formato'), chips(m.aspects.map((a) => [a, { '16:9': 'Paisagem 16:9', '9:16': 'Retrato 9:16', '1:1': 'Quadrado 1:1' }[a] || a]), aspect, (v) => { aspect = v; draw(); }));
      if (m.durations.length > 1) card.append(h('h3', 'Duração'), chips(m.durations.map((d) => [d, `${d} s`]), duration, (v) => { duration = v; draw(); }));
      if (m.resolutions.length > 1) card.append(h('h3', 'Resolução'), chips(m.resolutions.map((r) => [r, r]), resolution, (v) => { resolution = v; draw(); }));
      card.append(h('h3', 'Movimento'), chips(Object.entries(MOTION).map(([k, v]) => [k, v[0]]), motion, (v) => { motion = v; draw(); }));
      if (models.some((x) => x.audio)) card.append(toggle(audio, (v) => { audio = v; draw(); }, 'Com som gerado'));
      card.append(h('h3', 'Escolher o modelo'), chips(models.filter((x) => (image ? x.modes.includes('image') : x.modes.includes('text'))).map((x) => [x.id, x.free ? x.name : `${x.name} · pago`]), manual || '', (v) => { manual = manual === v ? null : v; draw(); }), h('p.faint', `${m.family} · ${m.summary}`));
    }
    const cost = h('p.faint', m?.free ? 'Sem custo: usa a cota diária grátis de GPU do Hugging Face (poucos vídeos por dia). Deixe esta aba aberta até o fim.' : 'Calculando a estimativa…');
    card.append(h('div.card', h('strong', m?.free ? 'Grátis · Hugging Face' : 'Processamento online (fal.ai)'), cost));
    const go = h('button.primary', { type: 'button', disabled: !m || null }, 'Gerar vídeo');
    go.onclick = () => submit(m, go);
    card.append(go);
    form.append(card);
    clearTimeout(quoteTimer);
    if (m && !m.free) {
      quoteTimer = setTimeout(async () => {
        try {
          const q = await app.gateway.videoQuote(request(m, 'x', image ? 'data:image/jpeg;base64,AAAA' : null));
          cost.textContent = `Estimativa: ${usd(q.estimatedUsd)} · cobrado na conta do serviço`;
        } catch (e) {
          cost.textContent = e.message;
        }
      }, 300);
    }
  }

  function request(m, text, img) {
    const hint = MOTION[motion][1];
    return { model: m.id, prompt: text + (hint ? `. ${hint}` : ''), duration, resolution, aspect, audio, ...(img ? { image: img } : {}) };
  }

  function readImage(file) {
    if (!file) return;
    const img = new Image();
    img.onload = () => {
      const s = Math.min(1, 1280 / Math.max(img.width, img.height));
      const c = document.createElement('canvas');
      c.width = Math.round(img.width * s);
      c.height = Math.round(img.height * s);
      c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
      image = c.toDataURL('image/jpeg', 0.88);
      URL.revokeObjectURL(img.src);
      draw();
    };
    img.src = URL.createObjectURL(file);
  }

  async function submit(m, button) {
    const text = prompt.value.trim();
    if (!text) return toast('Descreva o vídeo.', 'error');
    button.disabled = true;
    try {
      const r = await app.gateway.videoSubmit(request(m, text, image));
      store.videos.put({ id: store.uid(), jobId: r.jobId, model: m.name, prompt: text, duration: r.duration, resolution: r.resolution, aspect: r.aspect, estimatedUsd: r.estimatedUsd, free: !!r.free, typicalSeconds: r.typicalSeconds, status: 'queued', startedAt: Date.now(), note: r.simplified ? 'O modelo recusou alguns ajustes; foi gerado só com a descrição e a imagem.' : null });
      toast('Enviado. A avatar mostra o andamento.');
      startPolling(ctx);
      drawResults();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      button.disabled = false;
    }
  }

  function drawResults() {
    clear(results);
    const jobs = store.videos.list();
    if (!jobs.length) return;
    const grid = h('div.video-grid');
    jobs.forEach((j) => {
      const p = progressOf(j);
      const status = { queued: 'Na fila', running: `Gerando · cerca de ${Math.round(p * 100)}% (estimado)`, done: 'Pronto', failed: j.error || 'Não foi gerado', cancelled: 'Cancelado' }[j.status];
      grid.append(
        h(
          'div.card.video-card',
          j.status === 'done' && j.url ? h('video', { src: j.url, controls: true, loop: true, playsinline: true, preload: 'metadata' }) : h('div.thumb', status),
          h('p', j.prompt),
          h('p.faint', `${j.model} · ${j.duration} s · ${j.resolution} · ${j.aspect} · ${j.free ? 'grátis' : `~${usd(j.estimatedUsd)}`}`),
          h(`p.${j.status === 'done' ? 'ok' : j.status === 'failed' ? 'danger' : 'faint'}`, status),
          j.note ? h('p.warn', j.note) : null,
          ['queued', 'running'].includes(j.status) ? h(`div.progress${j.status === 'queued' ? '.indeterminate' : ''}`, h('div', { style: { width: `${Math.round(p * 100)}%` } })) : null,
          h(
            'div.row',
            j.status === 'done' && j.url ? h('a.ghost', { href: j.url, download: 'lily-video.mp4', target: '_blank', rel: 'noopener' }, 'Baixar') : null,
            (j.status === 'queued' || (j.free && j.status === 'running')) ? h('button.ghost', { type: 'button', onclick: async () => { streaming.get(j.jobId)?.abort(); try { await app.gateway.videoCancel(j.jobId); } catch { /* fila já andou */ } store.videos.put({ ...j, status: 'cancelled' }); drawResults(); } }, 'Cancelar') : null,
            !['queued', 'running'].includes(j.status) ? h('button.ghost', { type: 'button', onclick: () => { store.videos.remove(j.id); drawResults(); } }, 'Apagar') : null,
          ),
        ),
      );
    });
    results.append(h('h2', 'Seus vídeos'), h('p.faint', 'Os links do serviço expiram depois de um tempo: baixe o que quiser guardar.'), grid);
  }

  ctx.onCleanup(() => (onUpdate = null));
  onUpdate = drawResults;
  load();
  drawImageCard();
  drawResults();
  if (store.videos.list().some((j) => ['queued', 'running'].includes(j.status))) startPolling(ctx);
}

let onUpdate = null;

/** Acompanha a fila a cada 5 s enquanto houver vídeo em andamento (também fora desta tela). */
export function startPolling(ctx) {
  if (polling) return;
  const { app } = ctx;
  polling = setInterval(async () => {
    const active = store.videos.list().filter((j) => ['queued', 'running'].includes(j.status));
    if (!active.length) {
      clearInterval(polling);
      polling = null;
      return;
    }
    for (const j of active) {
      if (streaming.has(j.jobId)) continue;
      const ctrl = new AbortController();
      streaming.set(j.jobId, ctrl);
      try {
        const s = await app.gateway.videoStatus(j.jobId, (p) => {
          store.videos.put({ ...j, status: p.status === 'queued' ? 'queued' : 'running', elapsed: p.elapsed, position: p.position ?? null });
          app.presence.working('video', 'Gerando vídeo (grátis)', progressOf({ ...j, status: 'running', elapsed: p.elapsed }));
          onUpdate?.();
        }, ctrl.signal);
        if (ctrl.signal.aborted || store.videos.list().find((x) => x.id === j.id)?.status === 'cancelled') continue;
        const next = { ...j, status: s.status, elapsed: s.elapsed, position: s.position ?? null };
        if (s.status === 'done') next.url = s.video?.url;
        if (s.status === 'failed') next.error = s.error;
        store.videos.put(next);
        if (s.status === 'done') app.presence.finished('video', true, 'Vídeo pronto');
        else if (s.status === 'failed') app.presence.finished('video', false, 'O vídeo não saiu');
        else app.presence.working('video', 'Gerando vídeo', progressOf(next));
      } catch {
        // sem rede: tenta na próxima rodada
      } finally {
        streaming.delete(j.jobId);
      }
    }
    onUpdate?.();
  }, 5000);
}
