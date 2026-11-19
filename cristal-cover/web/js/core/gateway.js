// Cliente do L.I.L.Y Gateway para o navegador (o mesmo protocolo do app Android).
// Nada aqui tem chave de provedor: só o endereço do gateway e a chave de acesso.

export class GatewayError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/** Uma linha do stream (formato OpenAI/OpenRouter, repassado pelo gateway). */
export function parseSse(line) {
  const t = line.trim();
  if (!t.startsWith('data:')) return null;
  const payload = t.slice(5).trim();
  if (payload === '[DONE]') return { done: true };
  let obj;
  try {
    obj = JSON.parse(payload);
  } catch {
    return null;
  }
  const error = obj.error ? obj.error.message || (typeof obj.error === 'string' ? obj.error : JSON.stringify(obj.error)) : null;
  const delta = obj.choices?.[0]?.delta?.content ?? null;
  const usage = obj.usage
    ? { promptTokens: obj.usage.prompt_tokens || 0, completionTokens: obj.usage.completion_tokens || 0, costUsd: typeof obj.usage.cost === 'number' ? obj.usage.cost : null }
    : null;
  return { delta, usage, error };
}

export class Gateway {
  /** getConfig() → { url, key, online } */
  constructor(getConfig, fetchImpl) {
    this.getConfig = getConfig;
    this.fetch = fetchImpl || ((...a) => globalThis.fetch(...a));
    this.modelsCache = null;
    this.videoCache = null;
  }

  base() {
    const c = this.getConfig();
    if (!c.key) throw new GatewayError(0, 'Conecte o L.I.L.Y Gateway em Configurações.');
    if (c.online === false) throw new GatewayError(0, 'Sem internet agora.');
    const url = (c.url || (globalThis.location ? globalThis.location.origin : '')).replace(/\/+$/, '');
    return { url, key: c.key };
  }

  async request(method, path, body, signal) {
    const { url, key } = this.base();
    let res;
    try {
      res = await this.fetch(url + path, {
        method,
        headers: { authorization: `Bearer ${key}`, accept: 'application/json', ...(body !== undefined ? { 'content-type': 'application/json' } : {}) },
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal,
      });
    } catch (e) {
      throw new GatewayError(0, `Não consegui falar com o gateway: ${e.message}`);
    }
    if (!res.ok) throw await this.errorOf(res);
    return res.json();
  }

  async errorOf(res) {
    let message = null;
    try {
      message = (await res.json()).error;
    } catch {
      // sem corpo
    }
    return new GatewayError(res.status, message || (res.status === 401 ? 'O gateway recusou a chave de acesso.' : `O gateway respondeu com erro ${res.status}.`));
  }

  status() {
    return this.request('GET', '/v1/status');
  }

  async codeModels(refresh = false) {
    if (this.modelsCache && !refresh) return this.modelsCache;
    const r = await this.request('GET', '/v1/models');
    this.modelsCache = r.models || [];
    return this.modelsCache;
  }

  /**
   * Resposta em pedaços. onText(textoAcumulado) a cada pedaço; devolve { text, usage, model }.
   * signal (AbortController) cancela.
   */
  async chat(model, messages, { temperature = 0.2, maxTokens = 4096, signal, onText } = {}) {
    const { url, key } = this.base();
    let res;
    try {
      res = await this.fetch(url + '/v1/chat', {
        method: 'POST',
        headers: { authorization: `Bearer ${key}`, 'content-type': 'application/json', accept: 'text/event-stream' },
        body: JSON.stringify({ model, messages, temperature, maxTokens }),
        signal,
      });
    } catch (e) {
      if (e.name === 'AbortError') throw e;
      throw new GatewayError(0, `Não consegui falar com o gateway: ${e.message}`);
    }
    if (!res.ok) throw await this.errorOf(res);
    const used = res.headers.get('x-lily-model') || model;
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let text = '';
    let usage = null;
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let nl;
      while ((nl = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, nl);
        buffer = buffer.slice(nl + 1);
        const ev = parseSse(line);
        if (!ev) continue;
        if (ev.done) continue;
        if (ev.error) throw new GatewayError(502, `O modelo online parou com erro: ${ev.error}`);
        if (ev.usage) usage = ev.usage;
        if (ev.delta) {
          text += ev.delta;
          onText?.(text);
        }
      }
    }
    return { text, usage, model: used };
  }

  search(query, max = 6) {
    return this.request('POST', '/v1/search', { query, max });
  }

  async videoModels(refresh = false) {
    if (this.videoCache && !refresh) return this.videoCache;
    const r = await this.request('GET', '/v1/video/models');
    this.videoCache = r.models || [];
    return this.videoCache;
  }

  videoQuote(req) {
    return this.request('POST', '/v1/video/quote', req);
  }

  videoSubmit(req) {
    return this.request('POST', '/v1/video', req);
  }

  /**
   * A situação de um vídeo. No vídeo grátis o gateway responde em NDJSON e mantém a conexão aberta até
   * o fim (o Space cancela se ela cair): cada linha vai para onProgress e volta a última.
   */
  async videoStatus(jobId, onProgress = () => {}, signal) {
    const { url, key } = this.base();
    let res;
    try {
      res = await this.fetch(`${url}/v1/video/${jobId}`, { headers: { authorization: `Bearer ${key}`, accept: 'application/json, application/x-ndjson' }, signal });
    } catch (e) {
      throw new GatewayError(0, `Não consegui falar com o gateway: ${e.message}`);
    }
    if (!res.ok) throw await this.errorOf(res);
    if (!/ndjson/.test(res.headers.get('content-type') || '')) return res.json();
    const reader = res.body.getReader();
    const dec = new TextDecoder();
    let buf = '';
    let last = { status: 'running' };
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += dec.decode(value, { stream: true });
      let cut;
      while ((cut = buf.indexOf('\n')) >= 0) {
        const line = buf.slice(0, cut).trim();
        buf = buf.slice(cut + 1);
        if (!line) continue;
        try {
          last = JSON.parse(line);
        } catch {
          continue;
        }
        if (last.status === 'done' || last.status === 'failed') {
          reader.cancel().catch(() => {});
          return last;
        }
        onProgress(last);
      }
    }
    return last;
  }

  /** Uma imagem grátis (Workers AI do gateway), 1024×1024, em data URL. */
  image(prompt, seed) {
    return this.request('POST', '/v1/image', { prompt, ...(Number.isInteger(seed) ? { seed } : {}) });
  }

  videoCancel(jobId) {
    return this.request('DELETE', `/v1/video/${jobId}`);
  }

  repos() {
    return this.request('GET', '/v1/repos');
  }

  tree(repo, ref) {
    return this.request('GET', `/v1/repo/tree?repo=${encodeURIComponent(repo)}&ref=${encodeURIComponent(ref)}`);
  }

  file(repo, ref, path) {
    return this.request('GET', `/v1/repo/file?repo=${encodeURIComponent(repo)}&ref=${encodeURIComponent(ref)}&path=${encodeURIComponent(path)}`);
  }

  commit(repo, base, branch, message, files) {
    return this.request('POST', '/v1/repo/commit', { repo, base, branch, message, files });
  }

  checkStart(repo, branch, runner, tasks, workdir) {
    return this.request('POST', '/v1/repo/check', { repo, branch, runner, tasks, workdir });
  }

  checkStatus(repo, branch, since) {
    return this.request('GET', `/v1/repo/check?repo=${encodeURIComponent(repo)}&branch=${encodeURIComponent(branch)}&since=${encodeURIComponent(since)}`);
  }
}

/** Valor em dólar no formato brasileiro. */
export function usd(v) {
  return 'US$ ' + Number(v || 0).toLocaleString('pt-BR', { minimumFractionDigits: v < 0.1 ? 4 : 2, maximumFractionDigits: v < 0.1 ? 4 : 2 });
}
