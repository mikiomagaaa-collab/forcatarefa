// Confere o gateway publicado com pedidos de verdade (roda no GitHub depois da publicação):
// endereço no ar, chave aceita, uma pergunta ao modelo grátis e uma imagem grátis.
// Uso: GATEWAY_URL=https://... LILY_ACCESS_KEY=... node scripts/smoke.mjs
// Não mostra a chave. Escreve o resultado no resumo da execução (GITHUB_STEP_SUMMARY) e sai com erro
// se algo essencial falhar. O vídeo não é testado aqui (leva minutos e usa a fila do Hugging Face).
import { appendFileSync } from 'node:fs';

const base = String(process.env.GATEWAY_URL || '').replace(/\/+$/, '');
const key = process.env.LILY_ACCESS_KEY || '';
const lines = [];
let failed = false;

const report = (ok, text) => {
  if (!ok) failed = true;
  lines.push(`- ${ok ? '✅' : '❌'} ${text}`);
  console.log(`${ok ? 'OK ' : 'ERRO'} ${text}`);
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const auth = { authorization: `Bearer ${key}` };

async function call(path, init = {}, timeout = 60_000) {
  const res = await fetch(base + path, { ...init, signal: AbortSignal.timeout(timeout) });
  const text = await res.text();
  let body = null;
  try { body = JSON.parse(text); } catch { /* SSE ou texto */ }
  return { status: res.status, text, body };
}
const why = (r) => r.body?.error?.message || r.body?.error || r.text.slice(0, 200);

// Junta os pedaços da resposta (formato do OpenRouter e do Workers AI).
export function sseText(raw) {
  let out = '';
  let error = null;
  for (const line of raw.split('\n')) {
    if (!line.startsWith('data:')) continue;
    const data = line.slice(5).trim();
    if (!data || data === '[DONE]') continue;
    try {
      const j = JSON.parse(data);
      if (j.error) error = j.error.message || String(j.error);
      const piece = j.choices?.[0]?.delta?.content ?? j.response;
      if (typeof piece === 'string') out += piece;
    } catch { /* linha incompleta */ }
  }
  return { text: out.trim(), error };
}

async function main() {
  if (!base) { report(false, 'Sem endereço do gateway para conferir.'); return; }

  // 1) No ar (um subdomínio workers.dev novo pode levar alguns minutos para responder).
  let health = null;
  for (let i = 1; i <= 18 && !health; i++) {
    try {
      const r = await call('/health', {}, 15_000);
      if (r.status === 200 && r.body?.service) health = r.body;
      else console.log(`Tentativa ${i}: HTTP ${r.status}`);
    } catch (e) {
      console.log(`Tentativa ${i}: ${e.message}`);
    }
    if (!health) await sleep(10_000);
  }
  if (!health) { report(false, `O endereço ${base} não respondeu em 3 minutos.`); return; }
  report(true, `No ar: ${base} (versão ${health.version})`);

  if (!key) { report(false, 'Sem LILY_ACCESS_KEY para conferir o resto.'); return; }

  // 2) A chave é aceita e o que está ligado.
  const st = await call('/v1/status', { headers: auth });
  if (st.status !== 200) { report(false, `/v1/status com a sua chave: HTTP ${st.status} (${why(st)})`); return; }
  const f = st.body?.features || {};
  report(true, `Chave aceita. Ligado: conversa grátis ${f.chatFree ? 'sim' : 'não'}, imagens ${f.image ? 'sim' : 'não'}, vídeo grátis ${f.video ? 'sim' : 'não'}, modelos pagos ${f.code ? 'sim' : 'não'}`);

  // 3) Uma pergunta ao modelo grátis.
  try {
    const r = await call('/v1/chat', {
      method: 'POST',
      headers: { ...auth, 'content-type': 'application/json' },
      body: JSON.stringify({
        model: 'free/gpt-oss-20b',
        maxTokens: 300,
        messages: [{ role: 'user', content: 'Quanto é 2 + 3? Responda só com o número.' }],
      }),
    }, 90_000);
    const { text, error } = r.status === 200 ? sseText(r.text) : { text: '', error: why(r) };
    if (text) report(true, `Pergunta ao modelo grátis ("Quanto é 2 + 3?"): respondeu "${text.slice(0, 80).replace(/\s+/g, ' ')}"`);
    else report(false, `Pergunta ao modelo grátis: sem resposta (HTTP ${r.status}${error ? `, ${error}` : ''})`);
  } catch (e) {
    report(false, `Pergunta ao modelo grátis: ${e.message}`);
  }

  // 4) Uma imagem grátis (~1% da cota diária do Workers AI).
  try {
    const r = await call('/v1/image', {
      method: 'POST',
      headers: { ...auth, 'content-type': 'application/json' },
      body: JSON.stringify({ prompt: 'a small purple crystal on a dark background, simple icon', seed: 7 }),
    }, 90_000);
    const img = r.body?.image || '';
    if (r.status === 200 && img.startsWith('data:image/')) {
      const kb = Math.round((img.length * 3) / 4 / 1024);
      report(true, `Imagem grátis gerada (${r.body.model}, ${kb} KB; hoje: ${r.body.usedToday ?? '?'} de ${r.body.perDay})`);
    } else {
      report(false, `Imagem grátis: HTTP ${r.status} (${why(r)})`);
    }
  } catch (e) {
    report(false, `Imagem grátis: ${e.message}`);
  }
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].split('/').pop())) {
  await main();
  if (process.env.GITHUB_STEP_SUMMARY) {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, ['### Conferência do gateway publicado', '', ...lines, ''].join('\n') + '\n');
  }
  process.exit(failed ? 1 : 0);
}
