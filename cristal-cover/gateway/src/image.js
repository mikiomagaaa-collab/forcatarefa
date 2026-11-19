// Imagens grátis pelo Workers AI da própria conta Cloudflare do gateway (binding AI, sem chave).
//
// A Cloudflare dá 10.000 "neurons" por dia de graça (zeram à meia-noite UTC). Pela tabela de preços do
// FLUX.1 [schnell] (4,80 neurons por bloco de 512×512 e 9,60 por etapa), uma imagem 1024×1024 com 4
// etapas gasta cerca de 57,6 neurons: uns 170 por dia. O gateway conta as imagens do dia no KV e para
// em FREE_IMAGES_PER_DAY (padrão 150), antes de passar da parte grátis: assim nunca vira cobrança, nem
// no plano pago dos Workers. Sem o KV, no plano grátis a própria Cloudflare recusa depois do limite.

import { GatewayError, json, readJson } from './http.js';
import { IMAGE_NEURONS, addNeurons, ensureFree } from './free.js';

export const IMAGE_MODEL = {
  id: 'flux-1-schnell',
  run: '@cf/black-forest-labs/flux-1-schnell',
  name: 'FLUX.1 [schnell]',
  family: 'Black Forest Labs',
  license: 'Apache 2.0 (Black Forest Labs), pelo Workers AI da sua conta Cloudflare',
  width: 1024,
  height: 1024,
  steps: 4,
};

function today() {
  return new Date().toISOString().slice(0, 10);
}

export function imageLimit(env) {
  const n = Number(env.FREE_IMAGES_PER_DAY ?? '150');
  return Number.isFinite(n) && n >= 0 ? Math.floor(n) : 150;
}

/** Quantas imagens grátis já saíram hoje (null sem o KV: não dá para contar). */
export async function imagesToday(env) {
  if (!env.LILY_USAGE) return null;
  return Number((await env.LILY_USAGE.get(`img:${today()}`)) || 0) || 0;
}

async function countImage(env) {
  if (!env.LILY_USAGE) return;
  const key = `img:${today()}`;
  const current = Number((await env.LILY_USAGE.get(key)) || 0) || 0;
  await env.LILY_USAGE.put(key, String(current + 1), { expirationTtl: 3 * 86400 });
}

function fromBase64(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function toBase64(bytes) {
  let bin = '';
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(bin);
}

/** O Workers AI devolve a imagem em base64 ({image}) ou como bytes/stream, conforme a versão: aceita os dois. */
export async function imageBytes(out) {
  if (!out) return null;
  if (typeof out.image === 'string') return fromBase64(out.image.replace(/^data:image\/\w+;base64,/, ''));
  const raw = out.image ?? out;
  if (raw instanceof Uint8Array) return raw;
  if (raw instanceof ArrayBuffer) return new Uint8Array(raw);
  if (typeof ReadableStream !== 'undefined' && raw instanceof ReadableStream) return new Uint8Array(await new Response(raw).arrayBuffer());
  return null;
}

function mimeOf(bytes) {
  if (bytes[0] === 0x89 && bytes[1] === 0x50) return 'image/png';
  if (bytes[0] === 0x52 && bytes[1] === 0x49) return 'image/webp';
  return 'image/jpeg';
}

export function imageInfo(env) {
  return { model: IMAGE_MODEL.name, free: true, perDay: imageLimit(env), width: IMAGE_MODEL.width, height: IMAGE_MODEL.height };
}

/** POST /v1/image {prompt, seed?}: uma imagem 1024×1024 grátis (dentro da cota diária). */
export async function handleImage(request, env) {
  if (!env.AI) throw new GatewayError(501, 'Imagens grátis não estão ligadas neste gateway (falta o Workers AI: [ai] binding = "AI" no wrangler.toml).');
  const body = await readJson(request, 20_000);
  const prompt = String(body.prompt || '').trim().slice(0, 2000);
  if (!prompt) throw new GatewayError(400, 'Descreva a imagem.');
  const limit = imageLimit(env);
  const used = await imagesToday(env);
  if (used != null && used >= limit) {
    throw new GatewayError(429, `As ${limit} imagens grátis de hoje já foram usadas. Elas voltam à meia-noite (horário UTC, 21h em Brasília).`);
  }
  await ensureFree(env, IMAGE_NEURONS, 'imagens');
  const input = { prompt, steps: IMAGE_MODEL.steps };
  const seed = Number(body.seed);
  if (Number.isInteger(seed) && seed >= 0) input.seed = seed % 2147483647;
  let out;
  try {
    out = await env.AI.run(IMAGE_MODEL.run, input);
  } catch (e) {
    const msg = String(e?.message || e);
    if (/neuron|allocation|quota|limit|4006|429/i.test(msg)) {
      throw new GatewayError(429, 'A cota grátis diária do Workers AI desta conta acabou. Ela volta à meia-noite (UTC).', msg);
    }
    throw new GatewayError(502, 'O Workers AI não conseguiu gerar a imagem. Tente de novo ou mude a descrição.', msg);
  }
  const bytes = await imageBytes(out);
  if (!bytes || bytes.length < 100) throw new GatewayError(502, 'O Workers AI terminou sem devolver uma imagem.');
  await countImage(env);
  await addNeurons(env, IMAGE_NEURONS);
  return json({
    image: `data:${mimeOf(bytes)};base64,${toBase64(bytes)}`,
    model: IMAGE_MODEL.name,
    width: IMAGE_MODEL.width,
    height: IMAGE_MODEL.height,
    costUsd: 0,
    free: true,
    usedToday: used == null ? null : used + 1,
    perDay: limit,
  });
}
