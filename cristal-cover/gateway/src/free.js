// Conversa online grátis (beta) pelo Workers AI da própria conta Cloudflare do gateway (binding AI).
//
// O modelo é o gpt-oss-20b (pesos abertos da OpenAI, Apache 2.0), que o Workers AI cobra em "neurons":
// 18.182 por milhão de tokens de entrada e 27.273 por milhão de saída (tabela da Cloudflare). A conta
// tem 10.000 neurons grátis por dia, divididos com as imagens grátis. O gateway soma o gasto do dia no
// KV e para em FREE_NEURONS_PER_DAY (padrão 9.500), antes de passar da parte grátis.
// A resposta sai no mesmo formato de pedaços (SSE) do OpenRouter, então o app e a Web não mudam.

import { GatewayError } from './http.js';

export const FREE_CHAT = {
  id: 'free/gpt-oss-20b',
  run: '@cf/openai/gpt-oss-20b',
  name: 'L.I.L.Y Online (grátis, beta)',
  vendor: 'OpenAI gpt-oss-20b · Workers AI',
  family: 'gpt-oss',
  tier: 'fast',
  speed: 'medium',
  roles: ['chat', 'quick', 'explain', 'docs'],
  context: 128000,
  neuronsPerInputToken: 18182 / 1e6,
  neuronsPerOutputToken: 27273 / 1e6,
};

/** Uma imagem FLUX.1 [schnell] 1024×1024 com 4 etapas: 4 × 4,8 + 4 × 9,6 neurons. */
export const IMAGE_NEURONS = 57.6;

function today() {
  return new Date().toISOString().slice(0, 10);
}

export function neuronCap(env) {
  const n = Number(env.FREE_NEURONS_PER_DAY ?? '9500');
  return Number.isFinite(n) && n >= 0 ? n : 9500;
}

/** Neurons gastos hoje pelo gateway (null sem o KV: não dá para contar). */
export async function neuronsToday(env) {
  if (!env.LILY_USAGE) return null;
  return Number((await env.LILY_USAGE.get(`neurons:${today()}`)) || 0) || 0;
}

export async function addNeurons(env, n) {
  if (!env.LILY_USAGE || !(n > 0)) return;
  const key = `neurons:${today()}`;
  const current = Number((await env.LILY_USAGE.get(key)) || 0) || 0;
  await env.LILY_USAGE.put(key, String(+(current + n).toFixed(2)), { expirationTtl: 3 * 86400 });
}

/** Recusa antes de chamar o Workers AI se [estimate] passaria da parte grátis do dia. */
export async function ensureFree(env, estimate, what) {
  const used = await neuronsToday(env);
  if (used == null) return;
  if (used + estimate > neuronCap(env)) {
    throw new GatewayError(429, `A cota grátis de hoje do Workers AI acabou (${what}). Ela volta à meia-noite UTC (21h em Brasília).`);
  }
}

export function freeChatAvailable(env) {
  return !!env.AI && String(env.FREE_CHAT || 'on') !== 'off';
}

/** O modelo grátis como o app vê na lista (preço zero, marcado como grátis). */
export function freeModels(env) {
  if (!freeChatAvailable(env)) return [];
  const { run, neuronsPerInputToken, neuronsPerOutputToken, ...m } = FREE_CHAT;
  return [{ ...m, priceIn: 0, priceOut: 0, created: 0, free: true }];
}

/** O texto de um pedaço do Workers AI (formato antigo {response} ou o do OpenAI {choices}). */
export function pieceText(obj) {
  if (!obj || typeof obj !== 'object') return '';
  if (typeof obj.response === 'string') return obj.response;
  const choice = Array.isArray(obj.choices) ? obj.choices[0] : null;
  if (choice) return choice.delta?.content ?? choice.message?.content ?? choice.text ?? '';
  return '';
}

/** O texto de uma resposta inteira (sem stream), incluindo o formato Responses API. */
export function wholeText(out) {
  if (typeof out === 'string') return out;
  const simple = pieceText(out);
  if (simple) return simple;
  const items = Array.isArray(out?.output) ? out.output : [];
  return items
    .filter((i) => i.type === 'message')
    .flatMap((i) => (Array.isArray(i.content) ? i.content : []))
    .filter((c) => c.type === 'output_text' && typeof c.text === 'string')
    .map((c) => c.text)
    .join('');
}

const enc = new TextEncoder();
const sse = (obj) => enc.encode(`data: ${JSON.stringify(obj)}\n\n`);

/** POST /v1/chat com o modelo grátis: valida a cota, chama o Workers AI e devolve SSE no formato do app. */
export async function handleFreeChat(body, messages, env, ctx) {
  if (!freeChatAvailable(env)) throw new GatewayError(501, 'A conversa grátis não está ligada neste gateway (falta o Workers AI: [ai] binding = "AI").');
  const maxTokens = Math.min(Math.max(Number(body.maxTokens) || 1500, 64), 2000);
  const inputChars = messages.reduce((n, m) => n + m.content.length, 0);
  const worst = (inputChars / 3.5) * FREE_CHAT.neuronsPerInputToken + maxTokens * FREE_CHAT.neuronsPerOutputToken;
  await ensureFree(env, worst, 'conversa');
  const temperature = typeof body.temperature === 'number' ? body.temperature : 0.6;
  let out;
  try {
    out = await env.AI.run(FREE_CHAT.run, { messages, stream: true, max_tokens: maxTokens, temperature });
  } catch (e) {
    const msg = String(e?.message || e);
    if (/neuron|allocation|quota|4006|429/i.test(msg)) throw new GatewayError(429, 'A cota grátis diária do Workers AI desta conta acabou. Ela volta à meia-noite (UTC).', msg);
    // Algum parâmetro recusado pelo esquema do modelo: tenta só com o essencial.
    try {
      out = await env.AI.run(FREE_CHAT.run, { messages, stream: true });
    } catch (e2) {
      throw new GatewayError(502, 'O modelo grátis não respondeu agora. Tente de novo em instantes.', String(e2?.message || e2));
    }
  }

  const finish = async (promptTokens, completionTokens) => {
    await addNeurons(env, promptTokens * FREE_CHAT.neuronsPerInputToken + completionTokens * FREE_CHAT.neuronsPerOutputToken);
  };

  // Resposta inteira (sem stream): converte uma vez só.
  if (!(out instanceof ReadableStream)) {
    const text = wholeText(out);
    const promptTokens = Number(out?.usage?.prompt_tokens) || Math.ceil(inputChars / 3.5);
    const completionTokens = Number(out?.usage?.completion_tokens) || Math.ceil(text.length / 3.5);
    const work = finish(promptTokens, completionTokens);
    if (ctx?.waitUntil) ctx.waitUntil(work);
    else await work;
    const body = new Uint8Array([
      ...(text ? sse({ choices: [{ delta: { content: text } }] }) : []),
      ...sse({ choices: [{ delta: {} }], usage: { prompt_tokens: promptTokens, completion_tokens: completionTokens, cost: 0 } }),
      ...enc.encode('data: [DONE]\n\n'),
    ]);
    return new Response(body, { headers: chatHeaders });
  }

  // Stream: repassa os pedaços sem reprocessar (o plano grátis da Cloudflare dá ~10 ms de CPU por
  // pedido; o app e a Web leem o formato do Workers AI). Só guarda o fim para ler o uso e conta os
  // bytes, para estimar o gasto se o uso não vier.
  let tail = '';
  let bytes = 0;
  const decoder = new TextDecoder();
  const { readable, writable } = new TransformStream({
    transform(chunk, controller) {
      bytes += chunk.byteLength;
      tail = (tail + decoder.decode(chunk, { stream: true })).slice(-4000);
      controller.enqueue(chunk);
    },
    async flush() {
      const usage = usageFromTail(tail);
      await finish(usage?.prompt_tokens || Math.ceil(inputChars / 3.5), usage?.completion_tokens || Math.ceil(bytes / 12));
    },
  });
  const task = out.pipeTo(writable).catch(() => {});
  ctx?.waitUntil?.(task);
  return new Response(readable, { headers: chatHeaders });
}

const chatHeaders = {
  'content-type': 'text/event-stream; charset=utf-8',
  'cache-control': 'no-store',
  'x-lily-model': FREE_CHAT.id,
  'x-lily-price-in': '0',
  'x-lily-price-out': '0',
};

/** O uso (tokens) no fim do stream do Workers AI, se vier. */
export function usageFromTail(tail) {
  let usage = null;
  for (const line of tail.split('\n')) {
    const t = line.trim();
    if (!t.startsWith('data:') || !t.includes('"usage"')) continue;
    try {
      const obj = JSON.parse(t.slice(5).trim());
      if (obj.usage) usage = obj.usage;
    } catch {
      // linha cortada no começo do rabo
    }
  }
  return usage;
}

export async function freeStatus(env) {
  const used = await neuronsToday(env);
  return { chat: freeChatAvailable(env) ? FREE_CHAT.name : null, neuronsToday: used, neuronsPerDay: neuronCap(env) };
}

