// Teste de ponta a ponta da L.I.L.Y Web num Chromium de verdade: o gateway real (mesmo código do
// Worker) com provedores simulados, servindo a página. Uso: node test/e2e.mjs <pasta-das-capturas>
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { existsSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const here = dirname(fileURLToPath(import.meta.url));
const gatewayDir = join(here, '..', '..', 'gateway');
const out = process.argv[2] || join(here, 'telas');
mkdirSync(out, { recursive: true });
execFileSync('node', [join(gatewayDir, 'scripts', 'build-web.mjs')], { stdio: 'inherit' });
const { default: worker } = await import(join(gatewayDir, 'src', 'index.js'));

const KEY = 'chave-de-teste-1234567890';
const replies = [];
const now = Math.floor(Date.now() / 1000);
const live = {
  data: [
    { id: 'anthropic/claude-opus-4.7', name: 'Anthropic: Claude Opus 4.7', created: now, context_length: 200000, pricing: { prompt: '0.000005', completion: '0.000025' } },
    { id: 'anthropic/claude-sonnet-4.6', name: 'Anthropic: Claude Sonnet 4.6', created: now, context_length: 1000000, pricing: { prompt: '0.000003', completion: '0.000015' } },
    { id: 'deepseek/deepseek-v4-pro', name: 'DeepSeek: V4 Pro', created: now, context_length: 160000, pricing: { prompt: '0.00000046', completion: '0.00000092' } },
    { id: 'google/gemini-3.1-pro-preview', name: 'Google: Gemini 3.1 Pro', created: now, context_length: 1000000, pricing: { prompt: '0.000002', completion: '0.000012' } },
    { id: 'google/gemini-3-flash-preview', name: 'Google: Gemini 3 Flash', created: now, context_length: 1000000, pricing: { prompt: '0.0000003', completion: '0.0000025' } },
  ],
};
let videoPolls = 0;
const repo = { 'app/src/Router.kt': 'package app\n\nfun rota() = foo()\n' };
const sse = (text) => {
  const enc = new TextEncoder();
  const half = Math.floor(text.length / 2);
  const ch = (s) => `data: ${JSON.stringify({ choices: [{ delta: { content: s } }] })}\n\n`;
  return new Response(new ReadableStream({ start(c) { c.enqueue(enc.encode(ch(text.slice(0, half)))); c.enqueue(enc.encode(ch(text.slice(half)) + 'data: {"choices":[{"delta":{}}],"usage":{"prompt_tokens":120,"completion_tokens":40,"cost":0.0021}}\n\ndata: [DONE]\n\n')); c.close(); } }), { headers: { 'content-type': 'text/event-stream' } });
};
const providers = async (url, init = {}) => {
  const u = typeof url === 'string' ? url : url.url;
  const m = (init.method || 'GET').toUpperCase();
  const j = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { 'content-type': 'application/json' } });
  if (u.endsWith('/api/v1/models')) return j(live);
  if (u.endsWith('/chat/completions')) return sse(replies.shift() || 'Olá! Sou a L.I.L.Y. Como posso ajudar?');
  if (u.includes('api.tavily.com')) return j({ results: [{ title: 'Kotlin docs', url: 'https://kotlinlang.org/docs/functions.html', content: 'Functions' }] });
  if (u.startsWith('https://queue.fal.run/') && m === 'POST') return j({ request_id: 'req-e2e-123456' });
  if (u.includes('/requests/req-e2e-123456/status')) return j({ status: ++videoPolls < 2 ? 'IN_PROGRESS' : 'COMPLETED' });
  if (u.includes('/requests/req-e2e-123456')) return j({ video: { url: 'https://v3.fal.media/files/exemplo.mp4' } });
  if (u.includes('api.github.com/repos/dono/app/git/trees')) return j({ sha: 's', tree: Object.keys(repo).map((p) => ({ path: p, type: 'blob', size: 40 })) });
  if (u.includes('api.github.com/repos/dono/app/contents/')) {
    const p = decodeURIComponent(u.split('/contents/')[1].split('?')[0]);
    return repo[p] != null ? j({ type: 'file', size: 40, sha: 's', content: Buffer.from(repo[p]).toString('base64') }) : j({ message: 'Not Found' }, 404);
  }
  if (u.endsWith('api.github.com/repos/dono/app')) return j({ default_branch: 'main', private: true });
  return j({ message: `sem simulação: ${m} ${u}` }, 599);
};
const env = { LILY_ACCESS_KEY: KEY, OPENROUTER_API_KEY: 'or', FAL_KEY: 'fal', TAVILY_API_KEY: 'tv', GITHUB_TOKEN: 'gh', GITHUB_REPOS: 'dono/app', FETCH: providers };

const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.md': 'text/markdown; charset=utf-8', '.webmanifest': 'application/manifest+json' };
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname.startsWith('/v1/')) {
    const body = req.method === 'GET' || req.method === 'HEAD' ? undefined : await new Promise((r) => { const b = []; req.on('data', (d) => b.push(d)); req.on('end', () => r(Buffer.concat(b))); });
    const r = await worker.fetch(new Request(`http://127.0.0.1${req.url}`, { method: req.method, headers: req.headers, body }), env, { waitUntil: () => {} });
    res.writeHead(r.status, Object.fromEntries(r.headers));
    if (r.body) for await (const chunk of r.body) res.write(chunk);
    return res.end();
  }
  const file = join(gatewayDir, 'public', url.pathname === '/' ? 'index.html' : decodeURIComponent(url.pathname));
  if (!file.startsWith(join(gatewayDir, 'public')) || !existsSync(file)) return res.writeHead(404).end('não encontrado');
  res.writeHead(200, { 'content-type': types[extname(file)] || 'application/octet-stream' });
  res.end(await readFile(file));
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const base = `http://127.0.0.1:${server.address().port}`;

const { chromium } = createRequire(import.meta.url)('playwright');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1360, height: 860 }, deviceScaleFactor: 1, colorScheme: 'dark' });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => m.type() === 'error' && !/Failed to load resource|WebGPU|webgpu/i.test(m.text()) && errors.push(m.text()));
const shot = async (name) => page.screenshot({ path: join(out, `${name}.png`) });
const step = (t) => console.log('•', t);

step('início');
await page.goto(base + '/');
await page.waitForSelector('.empty h2');
await page.waitForTimeout(400);
await shot('web-01-inicio');

step('configurar o gateway');
await page.goto(base + '/#/settings');
await page.fill('input[type=password]', KEY);
await page.click('text=Salvar e testar');
await page.waitForSelector('text=O que o seu gateway oferece');
await page.click('text=Permitir processamento externo');
await page.waitForTimeout(300);
await shot('web-02-configuracoes');

step('conversa online');
await page.goto(base + '/#/chat');
await page.waitForSelector('textarea');
replies.push('Claro! Aqui vai um plano curto:\n\n1. **Segunda**: células\n2. **Terça**: genética\n\n```js\nconsole.log("estude!")\n```');
await page.fill('textarea', 'Monte um plano de estudos de biologia');
await page.keyboard.press('Enter');
await page.waitForSelector('.msg.assistant .meta');
const meta = await page.textContent('.msg.assistant .meta');
if (!/online/.test(meta) || !/US\$ 0,0021/.test(meta)) throw new Error(`resposta sem modelo/custo: ${meta}`);
await shot('web-03-conversa');

step('Codex até o diff');
replies.push(
  JSON.stringify({ action: 'read', path: 'app/src/Router.kt', thought: 'Ler o arquivo do erro' }),
  JSON.stringify({ action: 'web_search', query: 'Kotlin unresolved reference', thought: 'Conferir na documentação' }),
  JSON.stringify({ action: 'edit', path: 'app/src/Router.kt', find: 'foo()', replace: 'rotaPadrao()', thought: 'A função foo não existe' }),
  JSON.stringify({ action: 'check' }),
  JSON.stringify({ action: 'finish', summary: 'Troquei a chamada inexistente foo() por rotaPadrao().', changes: 'Router.kt', verified: ['O erro aponta Router.kt:3'], hypotheses: ['rotaPadrao existe em outro módulo'], risks: ['Só o CI confirma'], sources: ['https://kotlinlang.org/docs/functions.html'] }),
  JSON.stringify({ ok: true, issues: [] }),
);
await page.goto(base + '/#/codex/' + encodeURIComponent("e: file:///w/app/src/Router.kt:3:14 Unresolved reference 'foo'."));
await page.waitForSelector('text=Projeto autorizado');
await page.click('button.chip:has-text("Repositório (GitHub)")');
await page.click('button.chip:has-text("dono/app")');
await page.click('text=Começar');
await page.waitForSelector('text=Aprovar e aplicar', { timeout: 20000 });
await page.waitForTimeout(300);
await shot('web-04-codex-diff');

step('vídeo');
await page.goto(base + '/#/video/' + encodeURIComponent('Uma gota de vidro líquido caindo em câmera lenta'));
await page.waitForSelector('text=Criar um vídeo');
await page.waitForSelector('text=Estimativa');
await page.click('text=Gerar vídeo');
await page.waitForSelector('text=Seus vídeos');
await page.waitForTimeout(11000);
await shot('web-05-video');

step('termos');
await page.goto(base + '/#/legal/termos');
await page.waitForSelector('text=Uso aceitável');
await shot('web-06-termos');

step('celular (tela estreita)');
await page.setViewportSize({ width: 412, height: 860 });
await page.goto(base + '/#/chat');
await page.waitForTimeout(400);
await shot('web-07-estreita');

await browser.close();
server.close();
if (errors.length) {
  console.error('Erros na página:\n' + errors.join('\n'));
  process.exit(1);
}
console.log('L.I.L.Y Web: tudo certo');
