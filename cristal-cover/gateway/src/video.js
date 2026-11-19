// Geração de vídeo. Dois caminhos:
//  - GRÁTIS: o LTX-Video no Space oficial da Lightricks no Hugging Face (ZeroGPU), pela API do Gradio.
//    Não precisa de chave; com HF_TOKEN (conta grátis do Hugging Face) a cota diária é a da sua conta,
//    senão é a cota anônima, menor. Poucos vídeos por dia (cada um reserva ~1 min de GPU "xlarge").
//    O Gradio cancela o vídeo se a conexão de acompanhamento cair, então o status desse modelo é uma
//    resposta longa (NDJSON) que fica aberta até o vídeo sair.
//  - PAGO: a fila do fal.ai (FAL_KEY). Só aparece com a chave configurada.
// O celular manda a descrição (e, se quiser, uma imagem de referência), acompanha e baixa o resultado.
//
// Os identificadores dos modelos foram conferidos nas páginas do fal.ai (novembro de 2026). Preços são
// ESTIMATIVAS para o limite de gasto e para mostrar antes de gerar; o valor cobrado vem da conta fal.ai.
// O dono do gateway pode corrigir preços em VIDEO_PRICES (JSON {"ltx-2": 0.06, ...}).

import { GatewayError, base64url, fromBase64url, json, providerError, readJson, upstream } from './http.js';
import { addSpend, ensureBudget } from './budget.js';

const QUEUE = 'https://queue.fal.run';
const NEGATIVE = 'blur, distortion, low quality, watermark, text artifacts';
const LTX_NEGATIVE = 'worst quality, inconsistent motion, blurry, jittery, distorted';

/** Tamanho em pixels por formato (múltiplos de 32, como o Space exige). */
const LTX_SIZES = { '16:9': [736, 416], '9:16': [416, 736], '1:1': [512, 512] };

export const VIDEO_MODELS = [
  {
    id: 'ltx-free',
    name: 'LTX-Video (grátis)',
    family: 'LTX (Lightricks)',
    provider: 'hf',
    free: true,
    summary: 'Grátis pela cota diária do Hugging Face: poucos vídeos por dia, até 8 s, ~736×416.',
    license: 'LTX-Video 0.9.8 (pesos abertos da Lightricks), no Space oficial Lightricks/ltx-video-distilled.',
    space: 'https://lightricks-ltx-video-distilled.hf.space',
    endpoints: { text: 'text_to_video', image: 'image_to_video' },
    durations: [2, 4, 6, 8],
    resolutions: ['480p'],
    aspects: ['16:9', '9:16', '1:1'],
    audio: false,
    quality: 2,
    speed: 'medium',
    typicalSeconds: 90,
  },
  {
    id: 'ltx-2',
    name: 'LTX-2',
    family: 'LTX (Lightricks)',
    summary: 'Rápido e barato, 1080p, bom para clipes e animar fotos.',
    license: 'Pesos abertos da Lightricks (confira a licença para uso comercial).',
    endpoints: { text: 'fal-ai/ltx-2/text-to-video', image: 'fal-ai/ltx-2/image-to-video/fast' },
    durations: [6, 8, 10],
    resolutions: ['1080p'],
    aspects: ['16:9', '9:16'],
    audio: true,
    quality: 3,
    speed: 'fast',
    typicalSeconds: 45,
    pricePerSecond: { text: 0.06, image: 0.04 },
    input(o) {
      const i = { prompt: o.prompt, duration: o.duration, resolution: o.resolution, aspect_ratio: o.aspect, fps: 25, generate_audio: !!o.audio };
      if (o.imageUrl) i.image_url = o.imageUrl;
      return i;
    },
  },
  {
    id: 'wan-2.5',
    name: 'Wan 2.5',
    family: 'Wan (Alibaba)',
    summary: 'Boa aderência à descrição, 480p barato ou 1080p.',
    license: 'Modelo da Alibaba (Wan); uso pela API do fal.ai.',
    endpoints: { text: 'fal-ai/wan-25-preview/text-to-video', image: 'fal-ai/wan-25-preview/image-to-video' },
    durations: [5, 10],
    resolutions: ['480p', '1080p'],
    aspects: ['16:9', '9:16', '1:1'],
    audio: false,
    quality: 3,
    speed: 'medium',
    typicalSeconds: 120,
    // 480p: US$ 0,05/s (listado); 1080p: estimativa conservadora.
    pricePerSecond: { '480p': 0.05, '1080p': 0.15 },
    input(o) {
      const i = { prompt: o.prompt, duration: String(o.duration), resolution: o.resolution, negative_prompt: NEGATIVE };
      if (o.imageUrl) i.image_url = o.imageUrl;
      else i.aspect_ratio = o.aspect;
      return i;
    },
  },
  {
    id: 'kling-2.5',
    name: 'Kling 2.5 Turbo Pro',
    family: 'Kling (Kuaishou)',
    summary: 'Movimento muito coerente e pessoas naturais. Ótimo para animar uma foto.',
    license: 'Modelo comercial da Kuaishou; uso pela API do fal.ai.',
    endpoints: { text: 'fal-ai/kling-video/v2.5-turbo/pro/text-to-video', image: 'fal-ai/kling-video/v2.5-turbo/pro/image-to-video' },
    durations: [5, 10],
    resolutions: ['1080p'],
    aspects: ['16:9', '9:16', '1:1'],
    audio: false,
    quality: 4,
    speed: 'medium',
    typicalSeconds: 100,
    pricePerSecond: { any: 0.07 },
    input(o) {
      const i = { prompt: o.prompt, duration: String(o.duration), negative_prompt: NEGATIVE, cfg_scale: 0.5 };
      if (o.imageUrl) i.image_url = o.imageUrl;
      else i.aspect_ratio = o.aspect;
      return i;
    },
  },
  {
    id: 'veo-3.1-fast',
    name: 'Veo 3.1 Fast',
    family: 'Veo (Google)',
    summary: 'A melhor aderência à descrição e som gerado junto. O mais caro.',
    license: 'Modelo comercial do Google; uso pela API do fal.ai.',
    endpoints: { text: 'fal-ai/veo3.1/fast', image: 'fal-ai/veo3.1/fast/image-to-video' },
    durations: [8],
    resolutions: ['720p', '1080p'],
    aspects: ['16:9', '9:16'],
    audio: true,
    quality: 5,
    speed: 'medium',
    typicalSeconds: 70,
    // US$ 0,10/s sem som, 0,15/s com som (listado).
    pricePerSecond: { silent: 0.1, audio: 0.15 },
    input(o) {
      const i = { prompt: o.prompt, duration: `${o.duration}s`, resolution: o.resolution, generate_audio: !!o.audio };
      if (o.imageUrl) i.image_url = o.imageUrl;
      else i.aspect_ratio = o.aspect;
      return i;
    },
  },
  {
    id: 'hunyuan',
    name: 'HunyuanVideo',
    family: 'HunyuanVideo (Tencent)',
    summary: 'Pesos abertos, só a partir de texto, cerca de 5 s.',
    license: 'Pesos abertos da Tencent (Tencent Hunyuan Community License).',
    endpoints: { text: 'fal-ai/hunyuan-video' },
    durations: [5],
    resolutions: ['720p'],
    aspects: ['16:9', '9:16'],
    audio: false,
    quality: 3,
    speed: 'slow',
    typicalSeconds: 150,
    // Cobrado por vídeo (US$ 0,40 listado).
    perVideo: 0.4,
    input(o) {
      return { prompt: o.prompt, resolution: o.resolution, aspect_ratio: o.aspect };
    },
  },
];

function priceOverrides(env) {
  try {
    return env.VIDEO_PRICES ? JSON.parse(env.VIDEO_PRICES) : {};
  } catch {
    return {};
  }
}

/** Estimativa em US$ para os parâmetros pedidos. */
export function estimateVideo(model, o, env = {}) {
  if (model.free) return 0;
  const override = priceOverrides(env)[model.id];
  if (typeof override === 'number') return +(override * o.duration).toFixed(4);
  if (model.perVideo) return model.perVideo;
  const p = model.pricePerSecond;
  const perSecond =
    p.any ??
    (o.imageUrl ? p.image : p.text) ??
    p[o.resolution] ??
    (o.audio ? p.audio : p.silent) ??
    Math.max(...Object.values(p));
  return +(perSecond * o.duration).toFixed(4);
}

export function publicModel(m, env = {}) {
  const modes = ['text', 'image'].filter((k) => m.endpoints[k]);
  const sample = { duration: m.durations[0], resolution: m.resolutions[0], aspect: m.aspects[0], audio: false };
  return {
    id: m.id,
    name: m.name,
    family: m.family,
    summary: m.summary,
    license: m.license,
    modes,
    durations: m.durations,
    resolutions: m.resolutions,
    aspects: m.aspects,
    audio: m.audio,
    quality: m.quality,
    speed: m.speed,
    typicalSeconds: m.typicalSeconds,
    // Preço de referência: o menor clipe, só texto e sem som.
    fromUsd: estimateVideo(m, sample, env),
    priceNote: m.free ? 'grátis (cota diária do Hugging Face)' : m.perVideo ? 'por vídeo' : 'por segundo de vídeo',
    free: !!m.free,
    provider: m.provider || 'fal',
  };
}

/** Os modelos que este gateway pode usar: o grátis sempre; os do fal.ai só com a FAL_KEY. */
export function availableVideoModels(env) {
  return VIDEO_MODELS.filter((m) => m.free || !!env.FAL_KEY);
}

export function handleVideoModels(env) {
  const models = availableVideoModels(env);
  const provider = env.FAL_KEY ? 'hf+fal' : 'hf';
  return json({ provider, freeToken: !!env.HF_TOKEN, models: models.map((m) => publicModel(m, env)) });
}

/** Valida e normaliza o pedido do app. */
export function normalize(body, env = {}) {
  const model = availableVideoModels(env).find((m) => m.id === body.model);
  if (!model) throw new GatewayError(400, `Modelo de vídeo desconhecido: ${body.model}`);
  const prompt = String(body.prompt || '').trim().slice(0, 2000);
  if (!prompt) throw new GatewayError(400, 'Descreva o vídeo.');
  let imageUrl = null;
  if (body.image) {
    const img = String(body.image);
    if (!/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(img) && !/^https:\/\/[^\s]+$/.test(img)) {
      throw new GatewayError(400, 'A imagem de referência precisa ser JPEG, PNG ou WebP.');
    }
    if (img.length > 8_000_000) throw new GatewayError(413, 'Imagem de referência grande demais (até ~6 MB).');
    imageUrl = img;
  }
  const mode = imageUrl ? 'image' : 'text';
  if (!model.endpoints[mode]) {
    throw new GatewayError(400, mode === 'image' ? `${model.name} não anima imagens: escolha outro modelo.` : `${model.name} precisa de uma imagem de referência.`);
  }
  const pick = (list, v) => (list.includes(v) ? v : list[0]);
  return {
    model,
    mode,
    o: {
      prompt,
      imageUrl,
      duration: pick(model.durations, Number(body.duration)),
      resolution: pick(model.resolutions, body.resolution),
      aspect: pick(model.aspects, body.aspect),
      audio: model.audio && !!body.audio,
    },
  };
}

export async function handleVideoQuote(request, env) {
  const { model, o } = normalize(await readJson(request), env);
  return json({ model: model.id, free: !!model.free, estimatedUsd: estimateVideo(model, o, env), duration: o.duration, resolution: o.resolution });
}

function falHeaders(env) {
  return { authorization: `Key ${env.FAL_KEY}`, 'content-type': 'application/json' };
}

/** Só URLs da fila do fal.ai (o id do trabalho vem do app: não pode apontar a chave para outro lugar). */
function safeQueueUrl(u) {
  return typeof u === 'string' && /^https:\/\/queue\.(fal\.run|falrun\.com)\/[\w./-]+$/.test(u) ? u : null;
}

function baseOf(endpoint) {
  return endpoint.split('/').slice(0, 2).join('/');
}

export async function handleVideoSubmit(request, env) {
  const { model, mode, o } = normalize(await readJson(request), env);
  if (model.provider === 'hf') return hfSubmit(model, mode, o, env);
  if (!env.FAL_KEY) throw new GatewayError(501, 'Geração de vídeo paga não configurada no gateway (falta FAL_KEY).');
  const estimate = estimateVideo(model, o, env);
  await ensureBudget(env, estimate);
  const endpoint = model.endpoints[mode];
  const f = upstream(env);
  let res = await f(`${QUEUE}/${endpoint}`, { method: 'POST', headers: falHeaders(env), body: JSON.stringify(model.input(o)) });
  let fallback = false;
  if (res.status === 422) {
    // O esquema do modelo mudou ou recusou um parâmetro: tenta de novo só com o essencial.
    fallback = true;
    const minimal = { prompt: o.prompt, ...(o.imageUrl ? { image_url: o.imageUrl } : {}) };
    res = await f(`${QUEUE}/${endpoint}`, { method: 'POST', headers: falHeaders(env), body: JSON.stringify(minimal) });
  }
  if (!res.ok) throw await providerError('fal.ai', res);
  const sub = await res.json();
  if (!sub.request_id) throw new GatewayError(502, 'fal.ai não devolveu o número do pedido.');
  await addSpend(env, estimate);
  const job = {
    e: endpoint,
    r: sub.request_id,
    s: safeQueueUrl(sub.status_url),
    o: safeQueueUrl(sub.response_url),
    c: safeQueueUrl(sub.cancel_url),
    m: model.id,
    t: Date.now(),
  };
  return json({
    jobId: base64url(JSON.stringify(job)),
    model: model.id,
    mode,
    estimatedUsd: estimate,
    typicalSeconds: model.typicalSeconds,
    simplified: fallback,
    duration: o.duration,
    resolution: o.resolution,
    aspect: o.aspect,
  });
}

function parseJob(id) {
  let job;
  try {
    job = JSON.parse(fromBase64url(id));
  } catch {
    throw new GatewayError(400, 'Identificador de trabalho inválido.');
  }
  const model = VIDEO_MODELS.find((m) => m.id === job.m);
  if (model?.provider === 'hf') {
    if (!Object.values(model.endpoints).includes(job.a) || typeof job.ev !== 'string' || !/^[\w-]{6,80}$/.test(job.ev)) {
      throw new GatewayError(400, 'Identificador de trabalho inválido.');
    }
    return { job, model, hf: true };
  }
  const known = model && Object.values(model.endpoints).includes(job.e);
  if (!known || typeof job.r !== 'string' || !/^[\w-]{6,80}$/.test(job.r)) throw new GatewayError(400, 'Identificador de trabalho inválido.');
  const base = `${QUEUE}/${baseOf(job.e)}/requests/${job.r}`;
  return {
    job,
    model,
    status: safeQueueUrl(job.s) || `${base}/status`,
    result: safeQueueUrl(job.o) || base,
    cancel: safeQueueUrl(job.c) || `${base}/cancel`,
  };
}

/** O vídeo no resultado (os modelos usam "video" ou "videos"). */
export function videoFrom(result) {
  const v = result?.video || (Array.isArray(result?.videos) ? result.videos[0] : null);
  if (v && typeof v.url === 'string') return { url: v.url, contentType: v.content_type || 'video/mp4', fileSize: v.file_size || null };
  return null;
}

export async function handleVideoStatus(id, env, ctx) {
  const parsed = parseJob(id);
  if (parsed.hf) return hfStatus(parsed.job, parsed.model, env, ctx);
  if (!env.FAL_KEY) throw new GatewayError(501, 'Geração de vídeo paga não configurada no gateway (falta FAL_KEY).');
  const { job, model, status, result } = parsed;
  const f = upstream(env);
  const res = await f(`${status}${status.includes('?') ? '&' : '?'}logs=1`, { headers: falHeaders(env) });
  if (!res.ok) throw await providerError('fal.ai', res);
  const s = await res.json();
  const elapsed = Math.round((Date.now() - (job.t || Date.now())) / 1000);
  const logs = (s.logs || []).map((l) => String(l.message || '')).filter(Boolean).slice(-3);
  if (s.status === 'IN_QUEUE') return json({ status: 'queued', position: s.queue_position ?? null, elapsed, typicalSeconds: model.typicalSeconds });
  if (s.status === 'IN_PROGRESS') return json({ status: 'running', elapsed, typicalSeconds: model.typicalSeconds, logs });
  if (s.status !== 'COMPLETED') return json({ status: 'running', elapsed, typicalSeconds: model.typicalSeconds, logs });
  // COMPLETED também vale para falhas: o resultado diz.
  const out = await f(result, { headers: falHeaders(env) });
  if (!out.ok) {
    let message = `O fal.ai não conseguiu gerar o vídeo (erro ${out.status}).`;
    try {
      const body = await out.json();
      const detail = Array.isArray(body.detail) ? body.detail.map((d) => d.msg || JSON.stringify(d)).join('; ') : body.detail || body.error;
      if (detail) message = `O fal.ai recusou: ${String(detail).slice(0, 300)}`;
    } catch {
      // sem corpo
    }
    return json({ status: 'failed', error: message, elapsed });
  }
  const body = await out.json();
  const video = videoFrom(body);
  if (!video) return json({ status: 'failed', error: 'O fal.ai terminou sem devolver um vídeo.', elapsed });
  return json({ status: 'done', elapsed, video, seed: body.seed ?? null });
}

export async function handleVideoCancel(id, env) {
  const parsed = parseJob(id);
  // No Space do Hugging Face o vídeo para quando o app fecha a conexão de acompanhamento.
  if (parsed.hf) return json({ cancelled: false, howToCancel: 'close-status-connection' });
  const { cancel } = parsed;
  const res = await upstream(env)(cancel, { method: 'PUT', headers: falHeaders(env) });
  // O fal só cancela o que ainda não começou.
  return json({ cancelled: res.ok });
}

// --- Grátis: Space do Hugging Face (API do Gradio) ------------------------------------------------

function hfHeaders(env) {
  return env.HF_TOKEN ? { authorization: `Bearer ${env.HF_TOKEN}` } : {};
}

/** Os bytes da imagem de referência (data URL do app ou um endereço https). */
async function referenceBytes(src, env) {
  const m = /^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/=]+)$/.exec(src);
  if (m) {
    const bin = atob(m[2]);
    return { bytes: Uint8Array.from(bin, (c) => c.charCodeAt(0)), type: m[1] };
  }
  const res = await upstream(env)(src);
  if (!res.ok) throw new GatewayError(400, 'Não consegui baixar a imagem de referência.');
  const buf = new Uint8Array(await res.arrayBuffer());
  if (buf.length > 8_000_000) throw new GatewayError(413, 'Imagem de referência grande demais.');
  return { bytes: buf, type: (res.headers.get('content-type') || 'image/jpeg').split(';')[0] };
}

/** Envia a imagem ao Space (upload do Gradio) e devolve o FileData que a API espera. */
async function hfUpload(model, src, env) {
  const { bytes, type } = await referenceBytes(src, env);
  const form = new FormData();
  form.append('files', new Blob([bytes], { type }), `referencia.${type.split('/')[1] || 'jpg'}`);
  const res = await upstream(env)(`${model.space}/gradio_api/upload`, { method: 'POST', headers: hfHeaders(env), body: form });
  if (!res.ok) throw await providerError('Hugging Face', res);
  const paths = await res.json();
  const path = Array.isArray(paths) ? paths[0] : null;
  if (typeof path !== 'string') throw new GatewayError(502, 'O Space do vídeo grátis não aceitou a imagem.');
  return { path, meta: { _type: 'gradio.FileData' } };
}

/** Os parâmetros na ordem da API do Space (text_to_video / image_to_video, iguais aos da tela dele). */
export function ltxData(o, image) {
  const [width, height] = LTX_SIZES[o.aspect] || LTX_SIZES['16:9'];
  return [o.prompt, LTX_NEGATIVE, image, null, height, width, image ? 'image-to-video' : 'text-to-video', o.duration, 9, 42, true, 1, true];
}

async function hfSubmit(model, mode, o, env) {
  const image = o.imageUrl ? await hfUpload(model, o.imageUrl, env) : null;
  const api = model.endpoints[mode];
  const res = await upstream(env)(`${model.space}/gradio_api/call/${api}`, {
    method: 'POST',
    headers: { ...hfHeaders(env), 'content-type': 'application/json' },
    body: JSON.stringify({ data: ltxData(o, image) }),
  });
  if (!res.ok) throw await providerError('Hugging Face', res);
  const out = await res.json();
  if (typeof out.event_id !== 'string' || !/^[\w-]{6,80}$/.test(out.event_id)) throw new GatewayError(502, 'O Space do vídeo grátis não devolveu o número do pedido.');
  const job = { p: 'hf', m: model.id, a: api, ev: out.event_id, t: Date.now() };
  return json({
    jobId: base64url(JSON.stringify(job)),
    model: model.id,
    mode,
    estimatedUsd: 0,
    free: true,
    stream: true,
    typicalSeconds: model.typicalSeconds,
    simplified: false,
    duration: o.duration,
    resolution: o.resolution,
    aspect: o.aspect,
  });
}

/** Lê os eventos SSE do Gradio ("event: ...\ndata: ...\n\n"). */
export async function* sseEvents(body) {
  const reader = body.getReader();
  const dec = new TextDecoder();
  let buf = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += dec.decode(value, { stream: true }).replace(/\r\n/g, '\n');
    let cut;
    while ((cut = buf.indexOf('\n\n')) >= 0) {
      const block = buf.slice(0, cut);
      buf = buf.slice(cut + 2);
      let event = 'message';
      const data = [];
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
      }
      let parsed = null;
      try {
        parsed = data.length ? JSON.parse(data.join('\n')) : null;
      } catch {
        parsed = data.join('\n');
      }
      yield { event, data: parsed };
    }
  }
}

/** O vídeo no resultado do Gradio ({video: FileData} ou FileData), só em endereços do próprio Space. */
export function gradioVideo(data, space) {
  const first = Array.isArray(data) ? data[0] : data;
  const file = first?.video ?? first;
  if (!file) return null;
  let url = typeof file === 'string' ? null : file.url;
  const path = typeof file === 'string' ? file : file.path;
  if (!url && path) url = `${space}/gradio_api/file=${path}`;
  if (typeof url !== 'string') return null;
  let host;
  try {
    host = new URL(url).hostname;
  } catch {
    return null;
  }
  if (!host.endsWith('.hf.space') || !url.startsWith('https://')) return null;
  return { url, contentType: file.mime_type || 'video/mp4', fileSize: file.size ?? null };
}

export function hfError(data) {
  const msg = typeof data === 'string' ? data : data?.message || data?.error || (data ? JSON.stringify(data) : '');
  if (/quota|exceeded|GPU limit|ZeroGPU/i.test(msg)) {
    return `A cota grátis de GPU do Hugging Face acabou por agora (${msg.slice(0, 200)}). Ela volta com o tempo; com um token grátis do Hugging Face no gateway (HF_TOKEN) a cota é maior.`;
  }
  return msg ? `O vídeo grátis falhou no Space: ${msg.slice(0, 300)}` : 'O vídeo grátis falhou no Space (sem detalhes). Tente de novo mais tarde.';
}

/**
 * Acompanha o vídeo grátis numa resposta longa em NDJSON: uma linha {"status":"running"} a cada sinal do
 * Space e a linha final "done" (com o vídeo) ou "failed". O app mantém a conexão aberta até o fim.
 */
async function hfStatus(job, model, env, ctx) {
  const started = job.t || Date.now();
  const elapsed = () => Math.round((Date.now() - started) / 1000);
  const res = await upstream(env)(`${model.space}/gradio_api/call/${job.a}/${job.ev}`, { headers: hfHeaders(env) });
  if (res.status === 404) {
    return json({ status: 'failed', free: true, elapsed: elapsed(), error: 'Este vídeo grátis se perdeu: a conexão com o Hugging Face caiu antes do fim (o Space cancela nesse caso). Gere de novo.' });
  }
  if (!res.ok || !res.body) throw await providerError('Hugging Face', res);
  const enc = new TextEncoder();
  const { readable, writable } = new TransformStream();
  const writer = writable.getWriter();
  const line = (o) => writer.write(enc.encode(`${JSON.stringify({ free: true, elapsed: elapsed(), ...o })}\n`));
  const task = (async () => {
    let finished = false;
    try {
      await line({ status: 'running', typicalSeconds: model.typicalSeconds });
      for await (const ev of sseEvents(res.body)) {
        if (ev.event === 'complete') {
          const video = gradioVideo(ev.data, model.space);
          await line(video ? { status: 'done', video } : { status: 'failed', error: 'O Space terminou sem devolver um vídeo.' });
          finished = true;
          break;
        }
        if (ev.event === 'error') {
          await line({ status: 'failed', error: hfError(ev.data) });
          finished = true;
          break;
        }
        await line({ status: 'running', typicalSeconds: model.typicalSeconds });
      }
      if (!finished) await line({ status: 'failed', error: 'A conexão com o Hugging Face terminou sem resultado. Gere de novo.' });
    } catch (e) {
      try {
        await line({ status: 'failed', error: `A conexão com o Hugging Face caiu: ${String(e?.message || e).slice(0, 200)}` });
      } catch {
        // o app já fechou a conexão
      }
    } finally {
      try {
        await writer.close();
      } catch {
        // já fechado
      }
    }
  })();
  ctx?.waitUntil?.(task);
  return new Response(readable, { headers: { 'content-type': 'application/x-ndjson; charset=utf-8', 'cache-control': 'no-store' } });
}

