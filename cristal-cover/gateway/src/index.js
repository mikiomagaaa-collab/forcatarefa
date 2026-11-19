// L.I.L.Y Gateway: o serviço online da L.I.L.Y AI (Cloudflare Worker).
//
// O app no celular só conhece o endereço deste gateway e uma chave de acesso (LILY_ACCESS_KEY). As
// chaves dos provedores ficam aqui, como segredos do Worker, e nunca vão para o APK:
//   OPENROUTER_API_KEY  modelos de programação e conversa (vários fornecedores numa chave)
//   FAL_KEY             geração de vídeo paga (fila do fal.ai); sem ela, só o vídeo grátis
//   HF_TOKEN            (opcional, grátis) usa a cota diária da sua conta do Hugging Face no vídeo grátis
//   binding AI          imagens grátis pelo Workers AI da própria conta Cloudflare (sem chave)
//   TAVILY_API_KEY ou BRAVE_API_KEY   pesquisa na internet do Codex
//   GITHUB_TOKEN + GITHUB_REPOS       repositórios autorizados do Codex e os testes no Actions
// Limite de gasto: DAILY_BUDGET_USD (padrão 3) com o KV LILY_USAGE ligado.
// Instalação, custos e segurança: cristal-cover/docs/GATEWAY.md.

import { GatewayError, json, sameSecret } from './http.js';
import { dailyLimit, spentToday } from './budget.js';
import { codeCatalog, handleChat, handleModels } from './code.js';
import { handleSearch, searchProvider } from './search.js';
import { handleImage, imageInfo, imagesToday } from './image.js';
import { handleVideoCancel, handleVideoModels, handleVideoQuote, handleVideoStatus, handleVideoSubmit } from './video.js';
import { allowedRepos, handleCheckStart, handleCheckStatus, handleCommit, handleFile, handleRepos, handleTree } from './repo.js';

export const VERSION = '1.1.0';

function authorized(request, env) {
  const header = request.headers.get('authorization') || '';
  const given = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  return sameSecret(given, env.LILY_ACCESS_KEY || '');
}

async function status(env) {
  let codeModels = 0;
  let codeError = null;
  if (env.OPENROUTER_API_KEY) {
    try {
      codeModels = (await codeCatalog(env)).length;
    } catch (e) {
      codeError = e.publicMessage || String(e.message || e);
    }
  }
  const spent = await spentToday(env);
  const images = env.AI ? { ...imageInfo(env), usedToday: await imagesToday(env) } : null;
  return json({
    ok: true,
    version: VERSION,
    features: {
      code: !!env.OPENROUTER_API_KEY && codeModels > 0,
      video: true,
      videoPaid: !!env.FAL_KEY,
      image: !!env.AI,
      search: !!searchProvider(env),
      repos: !!env.GITHUB_TOKEN && allowedRepos(env).length > 0,
    },
    codeModels,
    codeError,
    searchProvider: searchProvider(env),
    repos: allowedRepos(env),
    free: {
      image: images,
      video: { model: 'LTX-Video (Hugging Face)', account: env.HF_TOKEN ? 'sua conta do Hugging Face' : 'cota anônima (menor)' },
    },
    budget: { dailyUsd: dailyLimit(env), spentTodayUsd: spent, enforced: spent != null },
  });
}

export async function handle(request, env, ctx) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, '') || '/';
  // A L.I.L.Y Web é servida pelos "assets" do Worker (pasta public/); sem ela, a raiz responde o estado.
  if (path === '/' || path === '/health') return json({ service: 'L.I.L.Y Gateway', version: VERSION, web: 'public/' });
  if (!env.LILY_ACCESS_KEY) throw new GatewayError(503, 'Gateway sem chave de acesso: configure o segredo LILY_ACCESS_KEY.');
  if (!authorized(request, env)) throw new GatewayError(401, 'Chave de acesso inválida.');
  const m = request.method;
  if (m === 'GET' && path === '/v1/status') return status(env);
  if (m === 'GET' && path === '/v1/models') return handleModels(env);
  if (m === 'POST' && path === '/v1/chat') return handleChat(request, env, ctx);
  if (m === 'POST' && path === '/v1/search') return handleSearch(request, env);
  if (m === 'POST' && path === '/v1/image') return handleImage(request, env);
  if (m === 'GET' && path === '/v1/video/models') return handleVideoModels(env);
  if (m === 'POST' && path === '/v1/video/quote') return handleVideoQuote(request, env);
  if (m === 'POST' && path === '/v1/video') return handleVideoSubmit(request, env);
  const job = path.match(/^\/v1\/video\/([A-Za-z0-9_-]{10,2000})$/);
  if (job && m === 'GET') return handleVideoStatus(job[1], env, ctx);
  if (job && m === 'DELETE') return handleVideoCancel(job[1], env);
  if (m === 'GET' && path === '/v1/repos') return handleRepos(env);
  if (m === 'GET' && path === '/v1/repo/tree') return handleTree(url, env);
  if (m === 'GET' && path === '/v1/repo/file') return handleFile(url, env);
  if (m === 'POST' && path === '/v1/repo/commit') return handleCommit(request, env);
  if (m === 'POST' && path === '/v1/repo/check') return handleCheckStart(request, env);
  if (m === 'GET' && path === '/v1/repo/check') return handleCheckStatus(url, env);
  throw new GatewayError(404, 'Rota desconhecida.');
}

/** CORS só para as origens liberadas em ALLOWED_ORIGINS ("*" ou lista separada por vírgulas). */
export function corsHeaders(request, env) {
  const origin = request.headers.get('origin');
  const allowed = String(env.ALLOWED_ORIGINS || '').split(',').map((s) => s.trim()).filter(Boolean);
  if (!origin || !allowed.length) return {};
  if (!allowed.includes('*') && !allowed.includes(origin)) return {};
  return {
    'access-control-allow-origin': allowed.includes('*') ? '*' : origin,
    'access-control-allow-methods': 'GET, POST, DELETE, OPTIONS',
    'access-control-allow-headers': 'authorization, content-type',
    'access-control-expose-headers': 'x-lily-model, x-lily-price-in, x-lily-price-out',
    'access-control-max-age': '600',
    vary: 'origin',
  };
}

function withHeaders(res, extra) {
  if (!Object.keys(extra).length) return res;
  const out = new Response(res.body, res);
  for (const [k, v] of Object.entries(extra)) out.headers.set(k, v);
  return out;
}

export default {
  async fetch(request, env, ctx) {
    const cors = corsHeaders(request, env);
    if (request.method === 'OPTIONS') return new Response(null, { status: Object.keys(cors).length ? 204 : 403, headers: cors });
    try {
      return withHeaders(await handle(request, env, ctx), cors);
    } catch (e) {
      const statusCode = e instanceof GatewayError ? e.status : 500;
      const message = e instanceof GatewayError ? e.publicMessage : 'Erro interno do gateway.';
      return withHeaders(json({ error: message, ...(e.detail ? { detail: String(e.detail).slice(0, 400) } : {}) }, statusCode), cors);
    }
  },
};
