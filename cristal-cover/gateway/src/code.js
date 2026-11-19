// Modelos de programação e de conversa, pelo OpenRouter (uma chave para vários fornecedores).
//
// O catálogo nunca é uma lista de nomes escrita à mão: o gateway lê a lista AO VIVO do OpenRouter
// (/api/v1/models) e, para cada família abaixo, pega a versão mais nova que existe de verdade, com o
// preço e o contexto informados pelo próprio OpenRouter. Quando sai uma versão nova, ela entra sozinha;
// um nome que não existe nunca aparece.

import { GatewayError, json, providerError, readJson, upstream } from './http.js';
import { addSpend, ensureBudget } from './budget.js';
import { freeModels, handleFreeChat } from './free.js';

const OPENROUTER = 'https://openrouter.ai/api/v1';

/**
 * Famílias e papéis. tier: frontier (raciocínio e arquitetura), strong (o dia a dia de código),
 * fast (correções simples, documentação, explicações). roles: para que o roteador do app pode usar.
 */
export const FAMILIES = [
  { key: 'claude-opus', match: /^anthropic\/claude-opus-[\d.]+$/, vendor: 'Anthropic', tier: 'frontier', speed: 'slow', roles: ['architecture', 'debug', 'review', 'security', 'refactor'] },
  { key: 'claude-sonnet', match: /^anthropic\/claude-sonnet-[\d.]+$/, vendor: 'Anthropic', tier: 'strong', speed: 'medium', roles: ['generate', 'refactor', 'debug', 'review', 'security', 'chat'] },
  { key: 'claude-haiku', match: /^anthropic\/claude-haiku-[\d.]+$/, vendor: 'Anthropic', tier: 'fast', speed: 'fast', roles: ['quick', 'docs', 'explain', 'chat'] },
  { key: 'gpt', match: /^openai\/gpt-[5-9](\.\d+)?$/, vendor: 'OpenAI', tier: 'frontier', speed: 'slow', roles: ['architecture', 'review', 'security', 'debug', 'chat'] },
  { key: 'gpt-codex', match: /^openai\/gpt-[5-9](\.\d+)?-codex$/, vendor: 'OpenAI', tier: 'strong', speed: 'medium', roles: ['generate', 'refactor', 'debug'] },
  { key: 'gpt-mini', match: /^openai\/gpt-[5-9](\.\d+)?-mini$/, vendor: 'OpenAI', tier: 'fast', speed: 'fast', roles: ['quick', 'docs', 'explain', 'chat'] },
  { key: 'gemini-pro', match: /^google\/gemini-[\d.]+-pro(-preview)?$/, vendor: 'Google', tier: 'frontier', speed: 'medium', roles: ['architecture', 'review', 'refactor', 'chat'] },
  { key: 'gemini-flash', match: /^google\/gemini-[\d.]+-flash(-preview)?$/, vendor: 'Google', tier: 'fast', speed: 'fast', roles: ['quick', 'docs', 'explain', 'chat'] },
  { key: 'deepseek', match: /^deepseek\/deepseek-(v[\d.]+(-pro)?|chat)$/, vendor: 'DeepSeek', tier: 'strong', speed: 'medium', roles: ['generate', 'quick', 'refactor', 'debug', 'chat'] },
  { key: 'qwen-coder', match: /^qwen\/qwen[\d.]*-coder(-plus|-[\d.]+b)?$/, vendor: 'Qwen', tier: 'strong', speed: 'fast', roles: ['generate', 'quick', 'refactor'] },
  { key: 'kimi', match: /^moonshotai\/kimi-k[\d.]+$/, vendor: 'Moonshot', tier: 'strong', speed: 'medium', roles: ['generate', 'refactor', 'debug'] },
  { key: 'glm', match: /^z-ai\/glm-[\d.]+$/, vendor: 'Zhipu', tier: 'strong', speed: 'medium', roles: ['generate', 'refactor'] },
];

let memoryCache = null;
const CACHE_MS = 6 * 3600 * 1000;

/** Escolhe, por família, o modelo mais novo da lista ao vivo. */
export function pickFamilies(live, families = FAMILIES) {
  const out = [];
  for (const fam of families) {
    const candidates = live.filter((m) => typeof m.id === 'string' && !m.id.includes(':') && fam.match.test(m.id));
    if (!candidates.length) continue;
    candidates.sort((a, b) => (b.created || 0) - (a.created || 0) || b.id.localeCompare(a.id, 'en', { numeric: true }));
    const m = candidates[0];
    const priceIn = Number(m.pricing?.prompt);
    const priceOut = Number(m.pricing?.completion);
    if (!(priceIn >= 0) || !(priceOut >= 0)) continue;
    out.push({
      id: m.id,
      name: m.name || m.id,
      vendor: fam.vendor,
      family: fam.key,
      tier: fam.tier,
      speed: fam.speed,
      roles: fam.roles,
      context: m.context_length || 0,
      // Por milhão de tokens (o OpenRouter informa por token).
      priceIn: +(priceIn * 1e6).toFixed(4),
      priceOut: +(priceOut * 1e6).toFixed(4),
      created: m.created || 0,
    });
  }
  return out;
}

export async function codeCatalog(env, { refresh = false } = {}) {
  if (!env.OPENROUTER_API_KEY) return [];
  const now = Date.now();
  if (!refresh && memoryCache && now - memoryCache.at < CACHE_MS) return memoryCache.models;
  const res = await upstream(env)(`${OPENROUTER}/models`, {
    headers: { authorization: `Bearer ${env.OPENROUTER_API_KEY}` },
  });
  if (!res.ok) throw await providerError('OpenRouter', res);
  const body = await res.json();
  const live = Array.isArray(body.data) ? body.data : [];
  let models = pickFamilies(live);
  // Modelos extras escolhidos pelo dono do gateway (CODE_MODELS = JSON [{"id","roles","tier"}]).
  if (env.CODE_MODELS) {
    try {
      for (const extra of JSON.parse(env.CODE_MODELS)) {
        const m = live.find((x) => x.id === extra.id);
        if (!m || models.some((x) => x.id === m.id)) continue;
        models.push({
          id: m.id,
          name: m.name || m.id,
          vendor: (m.id.split('/')[0] || '').replace(/^\w/, (c) => c.toUpperCase()),
          family: 'extra',
          tier: extra.tier || 'strong',
          speed: extra.speed || 'medium',
          roles: extra.roles || ['generate', 'chat'],
          context: m.context_length || 0,
          priceIn: +(Number(m.pricing?.prompt) * 1e6).toFixed(4),
          priceOut: +(Number(m.pricing?.completion) * 1e6).toFixed(4),
          created: m.created || 0,
        });
      }
    } catch {
      // CODE_MODELS inválido: ignora (o /v1/status mostra os modelos que entraram).
    }
  }
  memoryCache = { at: now, models };
  return models;
}

export function resetCodeCache() {
  memoryCache = null;
}

/** O grátis (Workers AI) primeiro e, com a chave, o catálogo do OpenRouter. */
export async function handleModels(env) {
  const free = freeModels(env);
  if (!env.OPENROUTER_API_KEY) {
    if (!free.length) throw new GatewayError(501, 'Modelos online não configurados no gateway (falta OPENROUTER_API_KEY ou o Workers AI).');
    return json({ provider: 'workers-ai', models: free });
  }
  try {
    return json({ provider: free.length ? 'workers-ai+openrouter' : 'openrouter', models: [...free, ...(await codeCatalog(env))] });
  } catch (e) {
    if (!free.length) throw e;
    return json({ provider: 'workers-ai', models: free, openrouterError: e.publicMessage || String(e.message || e) });
  }
}

/** Estimativa conservadora (US$) antes de chamar o modelo. */
export function estimateChat(model, messages, maxTokens) {
  const chars = messages.reduce((n, m) => n + String(m.content ?? '').length, 0);
  return ((chars / 3.5) * model.priceIn + maxTokens * model.priceOut) / 1e6;
}

/** Custo real a partir do fim do stream (o OpenRouter manda o uso no último pedaço). */
export function costFromTail(tail, model) {
  let usage = null;
  for (const line of tail.split('\n')) {
    const t = line.trim();
    if (!t.startsWith('data:') || !t.includes('"usage"')) continue;
    try {
      const obj = JSON.parse(t.slice(5).trim());
      if (obj.usage) usage = obj.usage;
    } catch {
      // linha cortada no começo do rabo: ignora
    }
  }
  if (!usage) return null;
  if (typeof usage.cost === 'number') return usage.cost;
  const pin = usage.prompt_tokens || 0;
  const pout = usage.completion_tokens || 0;
  return (pin * model.priceIn + pout * model.priceOut) / 1e6;
}

export async function handleChat(request, env, ctx) {
  const body = await readJson(request);
  const messages = Array.isArray(body.messages) ? body.messages : [];
  if (!messages.length) throw new GatewayError(400, 'Mande pelo menos uma mensagem.');
  for (const m of messages) {
    if (!['system', 'user', 'assistant'].includes(m.role) || typeof m.content !== 'string') {
      throw new GatewayError(400, 'Mensagens precisam de role (system, user, assistant) e content (texto).');
    }
  }
  if (String(body.model || '').startsWith('free/')) {
    const clean = messages.map((m) => ({ role: m.role, content: m.content }));
    return handleFreeChat(body, clean, env, ctx);
  }
  if (!env.OPENROUTER_API_KEY) throw new GatewayError(501, 'Modelos online pagos não configurados no gateway (falta OPENROUTER_API_KEY). Use o modelo grátis.');
  const catalog = await codeCatalog(env);
  const model = catalog.find((m) => m.id === body.model);
  if (!model) throw new GatewayError(400, `Modelo indisponível neste gateway: ${body.model}. Atualize a lista de modelos.`);
  const maxTokens = Math.min(Math.max(Number(body.maxTokens) || 4096, 64), 32_000);
  await ensureBudget(env, estimateChat(model, messages, maxTokens));

  const res = await upstream(env)(`${OPENROUTER}/chat/completions`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
      'content-type': 'application/json',
      'x-title': 'L.I.L.Y Gateway',
    },
    body: JSON.stringify({
      model: model.id,
      messages,
      stream: true,
      temperature: typeof body.temperature === 'number' ? body.temperature : 0.2,
      max_tokens: maxTokens,
      usage: { include: true },
    }),
  });
  if (!res.ok || !res.body) throw await providerError('OpenRouter', res);

  // Repassa o stream sem reprocessar cada pedaço (pouca CPU no Worker) e guarda só o fim para ler o uso.
  let tail = '';
  const decoder = new TextDecoder();
  const { readable, writable } = new TransformStream({
    transform(chunk, controller) {
      tail = (tail + decoder.decode(chunk, { stream: true })).slice(-8000);
      controller.enqueue(chunk);
    },
    async flush() {
      const cost = costFromTail(tail, model);
      const work = addSpend(env, cost ?? 0);
      if (ctx?.waitUntil) ctx.waitUntil(work);
      else await work;
    },
  });
  const piping = res.body.pipeTo(writable).catch(() => {});
  if (ctx?.waitUntil) ctx.waitUntil(piping);
  return new Response(readable, {
    headers: {
      'content-type': 'text/event-stream; charset=utf-8',
      'cache-control': 'no-store',
      'x-lily-model': model.id,
      'x-lily-price-in': String(model.priceIn),
      'x-lily-price-out': String(model.priceOut),
    },
  });
}
