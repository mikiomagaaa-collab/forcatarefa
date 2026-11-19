// Geração de vídeo pela fila do fal.ai (FAL_KEY). O celular manda a descrição (e, se quiser, uma imagem
// de referência), acompanha a fila e baixa o resultado.
//
// Os identificadores dos modelos foram conferidos nas páginas do fal.ai (novembro de 2026). Preços são
// ESTIMATIVAS para o limite de gasto e para mostrar antes de gerar; o valor cobrado vem da conta fal.ai.
// O dono do gateway pode corrigir preços em VIDEO_PRICES (JSON {"ltx-2": 0.06, ...}).

import { GatewayError, base64url, fromBase64url, json, providerError, readJson, upstream } from './http.js';
import { addSpend, ensureBudget } from './budget.js';

const QUEUE = 'https://queue.fal.run';
const NEGATIVE = 'blur, distortion, low quality, watermark, text artifacts';

export const VIDEO_MODELS = [
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
    priceNote: m.perVideo ? 'por vídeo' : 'por segundo de vídeo',
  };
}

export function handleVideoModels(env) {
  if (!env.FAL_KEY) return json({ provider: null, models: [] });
  return json({ provider: 'fal', models: VIDEO_MODELS.map((m) => publicModel(m, env)) });
}

/** Valida e normaliza o pedido do app. */
export function normalize(body) {
  const model = VIDEO_MODELS.find((m) => m.id === body.model);
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
  const { model, o } = normalize(await readJson(request));
  return json({ model: model.id, estimatedUsd: estimateVideo(model, o, env), duration: o.duration, resolution: o.resolution });
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
  if (!env.FAL_KEY) throw new GatewayError(501, 'Geração de vídeo não configurada no gateway (falta FAL_KEY).');
  const { model, mode, o } = normalize(await readJson(request));
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

export async function handleVideoStatus(id, env) {
  if (!env.FAL_KEY) throw new GatewayError(501, 'Geração de vídeo não configurada no gateway (falta FAL_KEY).');
  const { job, model, status, result } = parseJob(id);
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
  const { cancel } = parseJob(id);
  const res = await upstream(env)(cancel, { method: 'PUT', headers: falHeaders(env) });
  // O fal só cancela o que ainda não começou.
  return json({ cancelled: res.ok });
}
