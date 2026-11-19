// Provedores simulados para os testes do gateway: respondem como as APIs reais (formatos dos
// documentos públicos) e registram cada chamada, para conferir o que o gateway mandou.

import worker from '../src/index.js';
import { resetCodeCache } from '../src/code.js';

export class FakeKV {
  constructor() {
    this.map = new Map();
  }
  async get(k) {
    return this.map.has(k) ? this.map.get(k) : null;
  }
  async put(k, v) {
    this.map.set(k, v);
  }
}

/** routes: [[método, regex da URL, (url, init, corpo) => Response | objeto JSON]] */
export function fakeFetch(routes) {
  const calls = [];
  const fn = async (input, init = {}) => {
    const url = typeof input === 'string' ? input : input.url;
    const method = (init.method || 'GET').toUpperCase();
    let body = init.body;
    if (typeof body === 'string') {
      try {
        body = JSON.parse(body);
      } catch {
        // texto
      }
    }
    calls.push({ method, url, headers: init.headers || {}, body });
    for (const [m, re, handler] of routes) {
      if (m === method && re.test(url)) {
        const out = await handler(url, init, body);
        if (out instanceof Response) return out;
        return new Response(JSON.stringify(out), { status: 200, headers: { 'content-type': 'application/json' } });
      }
    }
    return new Response(JSON.stringify({ message: `rota simulada ausente: ${method} ${url}` }), { status: 599 });
  };
  fn.calls = calls;
  return fn;
}

export function makeEnv(extra = {}) {
  resetCodeCache();
  return { LILY_ACCESS_KEY: 'chave-de-teste-123', ...extra };
}

export async function call(env, method, path, body, key = 'chave-de-teste-123') {
  const headers = { authorization: `Bearer ${key}` };
  if (body !== undefined) headers['content-type'] = 'application/json';
  const req = new Request(`https://gateway.test${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const pending = [];
  const ctx = { waitUntil: (p) => pending.push(p) };
  const res = await worker.fetch(req, env, ctx);
  return { res, pending };
}

export async function callJson(env, method, path, body, key) {
  const { res } = await call(env, method, path, body, key);
  return { status: res.status, body: await res.json() };
}

/** Lista do OpenRouter no formato do /api/v1/models (preços em US$ por token, como texto). */
export const OPENROUTER_MODELS = {
  data: [
    { id: 'anthropic/claude-opus-4.5', name: 'Claude Opus 4.5', created: 1760000000, context_length: 200000, pricing: { prompt: '0.000005', completion: '0.000025' } },
    { id: 'anthropic/claude-opus-4.7', name: 'Claude Opus 4.7', created: 1780000000, context_length: 200000, pricing: { prompt: '0.000005', completion: '0.000025' } },
    { id: 'anthropic/claude-sonnet-4.6', name: 'Claude Sonnet 4.6', created: 1775000000, context_length: 1000000, pricing: { prompt: '0.000003', completion: '0.000015' } },
    { id: 'deepseek/deepseek-v4-pro', name: 'DeepSeek V4 Pro', created: 1779000000, context_length: 160000, pricing: { prompt: '0.00000046', completion: '0.00000092' } },
    { id: 'deepseek/deepseek-v4-pro:free', name: 'DeepSeek V4 Pro (free)', created: 1779000001, context_length: 160000, pricing: { prompt: '0', completion: '0' } },
    { id: 'google/gemini-3.1-pro-preview', name: 'Gemini 3.1 Pro', created: 1778000000, context_length: 1000000, pricing: { prompt: '0.000002', completion: '0.000012' } },
    { id: 'openrouter/auto', name: 'Auto', created: 1700000000, context_length: 2000000, pricing: { prompt: '-1', completion: '-1' } },
    { id: 'meta-llama/llama-4-scout', name: 'Llama 4 Scout', created: 1750000000, context_length: 128000, pricing: { prompt: '0.0000001', completion: '0.0000003' } },
  ],
};

export function sse(chunks) {
  const enc = new TextEncoder();
  return new Response(
    new ReadableStream({
      start(c) {
        for (const ch of chunks) c.enqueue(enc.encode(ch));
        c.close();
      },
    }),
    { status: 200, headers: { 'content-type': 'text/event-stream' } },
  );
}
