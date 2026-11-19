// Pesquisa na internet para o Codex (documentação, erros conhecidos, mudanças de versão). Usa o
// Tavily (TAVILY_API_KEY) ou o Brave Search (BRAVE_API_KEY). O conteúdo das páginas o app baixa
// direto (não precisa de chave).

import { GatewayError, json, providerError, readJson, upstream } from './http.js';
import { addSpend, ensureBudget } from './budget.js';

export function searchProvider(env) {
  if (env.TAVILY_API_KEY) return 'tavily';
  if (env.BRAVE_API_KEY) return 'brave';
  return null;
}

export async function handleSearch(request, env) {
  const provider = searchProvider(env);
  if (!provider) throw new GatewayError(501, 'Pesquisa não configurada no gateway (falta TAVILY_API_KEY ou BRAVE_API_KEY).');
  const body = await readJson(request);
  const query = String(body.query || '').trim().slice(0, 400);
  if (!query) throw new GatewayError(400, 'Pesquisa vazia.');
  const max = Math.min(Math.max(Number(body.max) || 6, 1), 10);
  // Tavily: 1 crédito (~US$ 0,008); Brave: ~US$ 0,005 por chamada.
  const cost = provider === 'tavily' ? 0.008 : 0.005;
  await ensureBudget(env, cost);
  const f = upstream(env);
  let results;
  if (provider === 'tavily') {
    const res = await f('https://api.tavily.com/search', {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bearer ${env.TAVILY_API_KEY}` },
      body: JSON.stringify({ query, max_results: max, search_depth: 'basic', include_answer: false }),
    });
    if (!res.ok) throw await providerError('Tavily', res);
    const data = await res.json();
    results = (data.results || []).map((r) => ({ title: r.title || r.url, url: r.url, snippet: String(r.content || '').slice(0, 600) }));
  } else {
    const url = `https://api.search.brave.com/res/v1/web/search?q=${encodeURIComponent(query)}&count=${max}`;
    const res = await f(url, { headers: { accept: 'application/json', 'x-subscription-token': env.BRAVE_API_KEY } });
    if (!res.ok) throw await providerError('Brave Search', res);
    const data = await res.json();
    results = (data.web?.results || []).map((r) => ({ title: r.title || r.url, url: r.url, snippet: String(r.description || '').replace(/<[^>]+>/g, '').slice(0, 600) }));
  }
  await addSpend(env, cost);
  return json({ provider, query, results: results.filter((r) => /^https?:\/\//.test(r.url || '')) });
}
