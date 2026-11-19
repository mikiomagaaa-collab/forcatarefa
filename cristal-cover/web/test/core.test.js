import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fileDiff, unified } from '../js/core/diff.js';
import { checkFile, parseError } from '../js/core/languages.js';
import { pickRemote, pickCode, classify, pickVideo } from '../js/core/routers.js';
import { Gateway, parseSse, usd } from '../js/core/gateway.js';
import { render } from '../js/core/markdown.js';
import { Presence, blink, mouthOpen, target } from '../js/core/presence.js';
import { Session, Agent, MemoryWorkspace, parseAction, RepoWorkspace } from '../js/core/agent.js';

const MODELS = [
  { id: 'anthropic/claude-opus-4.7', name: 'Anthropic: Claude Opus 4.7', vendor: 'Anthropic', tier: 'frontier', speed: 'slow', roles: ['architecture', 'debug', 'review', 'security', 'refactor'], context: 200000, priceIn: 5, priceOut: 25 },
  { id: 'anthropic/claude-sonnet-4.6', name: 'Anthropic: Claude Sonnet 4.6', vendor: 'Anthropic', tier: 'strong', speed: 'medium', roles: ['generate', 'refactor', 'debug', 'review', 'security', 'chat'], context: 1000000, priceIn: 3, priceOut: 15 },
  { id: 'deepseek/deepseek-v4-pro', name: 'DeepSeek: V4 Pro', vendor: 'DeepSeek', tier: 'strong', speed: 'medium', roles: ['generate', 'quick', 'refactor', 'debug', 'chat'], context: 160000, priceIn: 0.46, priceOut: 0.92 },
  { id: 'google/gemini-3.1-pro-preview', name: 'Google: Gemini 3.1 Pro', vendor: 'Google', tier: 'frontier', speed: 'medium', roles: ['architecture', 'review', 'refactor', 'chat'], context: 1000000, priceIn: 2, priceOut: 12 },
  { id: 'google/gemini-3-flash-preview', name: 'Google: Gemini 3 Flash', vendor: 'Google', tier: 'fast', speed: 'fast', roles: ['quick', 'docs', 'explain', 'chat'], context: 1000000, priceIn: 0.3, priceOut: 2.5 },
];

test('roteadores iguais aos do app', () => {
  assert.equal(pickRemote(MODELS, 'BALANCED').tier, 'strong');
  assert.equal(pickRemote(MODELS, 'ECO').tier, 'fast');
  assert.equal(pickRemote(MODELS, 'QUALITY').tier, 'frontier');
  assert.equal(classify('o app dá crash'), 'DEBUG');
  assert.equal(pickCode('GENERATE', MODELS, 'BALANCED').model.id, 'deepseek/deepseek-v4-pro');
  assert.equal(pickCode('REVIEW', MODELS, 'BALANCED', { avoidVendor: 'Google' }).model.vendor, 'Anthropic');
  assert.equal(pickCode('DEBUG', MODELS, 'BALANCED', { escalate: true }).model.tier, 'frontier');
  const vids = [
    { id: 'ltx-2', name: 'LTX-2', modes: ['text', 'image'], durations: [6], audio: true, quality: 3, speed: 'fast', fromUsd: 0.36 },
    { id: 'veo', name: 'Veo', modes: ['text', 'image'], durations: [8], audio: true, quality: 5, speed: 'medium', fromUsd: 0.8 },
    { id: 'hy', name: 'Hunyuan', modes: ['text'], durations: [5], audio: false, quality: 3, speed: 'slow', fromUsd: 0.4 },
  ];
  assert.equal(pickVideo(vids, true, false, 'FAST').model.id, 'ltx-2');
  assert.equal(pickVideo(vids, false, false, 'BEST').model.id, 'veo');
  assert.equal(usd(0.0105), 'US$ 0,0105');
});

test('diff, verificadores e leitura de erros iguais aos do app', () => {
  const before = Array.from({ length: 20 }, (_, i) => `linha ${i + 1}`).join('\n');
  const after = before.replace('linha 10', 'linha DEZ') + '\nlinha 21';
  const d = fileDiff('a.txt', before, after);
  assert.equal(d.added, 2);
  assert.equal(d.removed, 1);
  assert.equal(d.hunks[0].header, '@@ -7,7 +7,7 @@');
  assert.match(unified(d), /-linha 10\n\+linha DEZ/);
  assert.equal(checkFile('A.kt', 'fun a() { val s = "${m["k"]} }"; if (x) { y() } }').problems.length, 0);
  assert.ok(checkFile('A.kt', 'fun a() {\n  if (x) {\n').problems.length > 0);
  assert.equal(checkFile('a.js', 'const r = /\\{"/; const t = `a ${b} {`; f(r)').problems.length, 0);
  assert.ok(checkFile('a.json', '{"a": [1,]}').problems.length > 0);
  assert.ok(checkFile('i.html', '<div><span></div>').problems.length > 0);
  assert.equal(checkFile('i.html', '<html><body><p>oi<br><img src=x></body></html>').problems.length, 0);
  assert.ok(checkFile('a.py', 'def f():\nreturn 1\n').problems.length > 0);
  const files = ['app/src/main/java/app/demo/Router.kt'];
  const e = parseError("e: file:///w/app/src/main/java/app/demo/Router.kt:42:5 Unresolved reference 'foo'.", files);
  assert.deepEqual(e.locations[0], ['app/src/main/java/app/demo/Router.kt', 42]);
});

test('stream do gateway: texto em pedaços e o custo no fim', async () => {
  assert.deepEqual(parseSse('data: [DONE]'), { done: true });
  assert.equal(parseSse(': OPENROUTER PROCESSING'), null);
  const sse = ': OPENROUTER PROCESSING\n\ndata: {"choices":[{"delta":{"content":"Olá"}}]}\n\ndata: {"choices":[{"delta":{"content":", tudo bem?"}}]}\n\ndata: {"choices":[{"delta":{}}],"usage":{"prompt_tokens":10,"completion_tokens":3,"cost":0.002}}\n\ndata: [DONE]\n\n';
  const calls = [];
  const fetchFake = async (url, init) => {
    calls.push({ url, init });
    const enc = new TextEncoder();
    return new Response(new ReadableStream({ start(c) { c.enqueue(enc.encode(sse.slice(0, 40))); c.enqueue(enc.encode(sse.slice(40))); c.close(); } }), { headers: { 'x-lily-model': 'deepseek/deepseek-v4-pro' } });
  };
  const g = new Gateway(() => ({ url: 'https://g.test', key: 'k-123' }), fetchFake);
  const seen = [];
  const r = await g.chat('deepseek/deepseek-v4-pro', [{ role: 'user', content: 'oi' }], { onText: (t) => seen.push(t) });
  assert.equal(r.text, 'Olá, tudo bem?');
  assert.equal(r.usage.costUsd, 0.002);
  assert.equal(calls[0].init.headers.authorization, 'Bearer k-123');
  assert.ok(seen.length >= 2);
  await assert.rejects(() => new Gateway(() => ({ url: 'https://g.test', key: '' })).status(), /Conecte o L.I.L.Y Gateway/);
});

test('markdown escapa HTML e só aceita links http(s)', () => {
  const html = render('# Título\n\n<script>alert(1)</script> **forte** [x](javascript:alert(1)) [ok](https://a.dev)\n\n| a | b |\n| --- | --- |\n| 1 | 2 |\n\n```js\nconst a = "<b>";\n```');
  assert.ok(!html.includes('<script>'));
  assert.ok(html.includes('&lt;script&gt;'));
  assert.ok(!html.includes('href="javascript'));
  assert.ok(html.includes('href="https://a.dev"'));
  assert.ok(html.includes('<table>'));
  assert.ok(html.includes('&lt;b&gt;'));
});

test('presença com prioridades e transitórios (igual ao app)', () => {
  let now = 1000;
  const p = new Presence(() => now);
  p.working('video', 'Gerando vídeo', 0.4);
  p.thinking('codex');
  assert.equal(p.state.mode, 'THINKING');
  assert.equal(p.state.active, 2);
  p.listening('mic', 0.7);
  assert.equal(p.state.mode, 'LISTENING');
  p.clear('mic');
  p.finished('codex', true);
  assert.equal(p.state.mode, 'SUCCESS');
  now += 3000;
  p.recompute();
  assert.equal(p.state.mode, 'WORKING');
  assert.ok(target('SUCCESS').happyEyes > 0.5);
  assert.equal(mouthOpen('IDLE', 1, 0), 0);
  const samples = Array.from({ length: 2000 }, (_, i) => blink(i * 10));
  assert.ok(samples.some((x) => x > 0.9));
});

function scripted(replies) {
  const sent = [];
  return { sent, factory: () => ({ complete: async (messages) => { sent.push(messages); return { text: replies.shift() || '{"action":"finish","summary":"fim"}', costUsd: 0.001 }; } }) };
}

const A = (o) => JSON.stringify(o);

test('Codex no navegador: lê, edita, revisa, aprova, aplica e desfaz', async () => {
  const ws = new MemoryWorkspace({ 'src/Calc.kt': 'package app\n\nobject Calc {\n    fun soma(a: Int, b: Int): Int = a - b\n}\n', 'build/x.txt': 'lixo' });
  const { factory } = scripted([
    A({ action: 'search', query: 'fun soma', thought: 'Procurar' }),
    '```json\n' + A({ action: 'read', path: 'src/Calc.kt' }) + '\n```',
    A({ action: 'edit', path: 'src/Calc.kt', find: 'a - b', replace: 'a + b' }),
    A({ action: 'check' }),
    A({ action: 'finish', summary: 'Corrigi a soma.', changes: '- → +', verified: ['linha 4 subtraía'], hypotheses: ['sem testes'], risks: [], sources: [] }),
    A({ ok: true, issues: [] }),
  ]);
  const s = new Session({ workspace: ws, mode: 'TASK', task: 'A soma está errada', errorText: '', models: async () => MODELS, llmFor: factory, gateway: null, profile: 'BALANCED', manual: null, web: false, reviewEnabled: true, autoLines: null });
  const waiting = new Promise((resolve) => (s.onChange = () => s.phase === 'AWAITING_APPROVAL' && resolve()));
  const run = s.start();
  await waiting;
  assert.equal(s.diffs.length, 1);
  assert.equal(s.review.ok, true);
  assert.match(await ws.read('src/Calc.kt'), /a - b/);
  s.onChange = null;
  s.decide({ type: 'approve' });
  await run;
  assert.equal(s.phase, 'DONE');
  assert.match(await ws.read('src/Calc.kt'), /a \+ b/);
  assert.equal(s.verification, 'SYNTAX_ONLY');
  await s.undo();
  assert.match(await ws.read('src/Calc.kt'), /a - b/);
  assert.ok(s.events.some((e) => e.type === 'staged'));
  assert.ok(!(await ws.files()).some((f) => f.path.startsWith('build/')));
});

test('Codex: auto-reparo num repositório (CI falha, nova tentativa, CI passa)', async () => {
  const repo = { 'app/Router.kt': 'package app\n\nfun rota() = foo()\n' };
  const branch = {};
  let branchName = null;
  const ci = [{ ok: false, log: "e: file:///w/app/Router.kt:3:14 Unresolved reference 'bar'.\nBUILD FAILED" }, { ok: true, log: 'BUILD SUCCESSFUL' }];
  let lastCi = null;
  const commits = [];
  const gateway = {
    tree: async (r, ref) => {
      if (ref === branchName) return { files: Object.keys(branch).map((path) => ({ path, size: 1 })) };
      if (ref !== 'main') throw new Error('404');
      return { files: Object.keys(repo).map((path) => ({ path, size: 1 })) };
    },
    file: async (r, ref, path) => {
      const src = ref === branchName ? branch : repo;
      return src[path] != null ? { exists: true, content: src[path] } : { exists: false };
    },
    commit: async (r, base, br, message, files) => {
      if (!branchName) Object.assign(branch, repo);
      branchName = br;
      files.forEach((f) => (f.content == null ? delete branch[f.path] : (branch[f.path] = f.content)));
      commits.push(files);
      return { branch: br, commit: 'abc1234def' };
    },
    checkStart: async () => {
      lastCi = ci.shift();
      return { startedAt: new Date().toISOString() };
    },
    checkStatus: async () => ({ status: 'completed', conclusion: lastCi.ok ? 'success' : 'failure', url: 'https://github.com/x', failures: lastCi.ok ? [] : [{ job: 'Testes', log: lastCi.log }] }),
    search: async () => ({ results: [] }),
  };
  const replies = [
    A({ action: 'read', path: 'app/Router.kt' }),
    A({ action: 'edit', path: 'app/Router.kt', find: 'foo()', replace: 'bar()' }),
    A({ action: 'finish', summary: 'foo → bar', changes: 'foo → bar' }),
    A({ action: 'read', path: 'app/Router.kt' }),
    A({ action: 'edit', path: 'app/Router.kt', find: 'bar()', replace: 'rotaPadrao()' }),
    A({ action: 'finish', summary: 'bar → rotaPadrao', changes: 'bar → rotaPadrao' }),
  ];
  const ws = new RepoWorkspace(gateway, 'dono/app', 'main', 'lily-codex/teste', { runner: 'gradle', tasks: 'test', workdir: '.' });
  const s = new Session({ workspace: ws, mode: 'REPAIR', task: '', errorText: "e: file:///w/app/Router.kt:3:14 Unresolved reference 'foo'.", models: async () => MODELS, llmFor: () => ({ complete: async () => ({ text: replies.shift(), costUsd: 0 }) }), gateway, profile: 'BALANCED', manual: null, web: false, reviewEnabled: false, autoLines: null, pollMs: 1, sleep: async () => {} });
  s.onChange = () => s.phase === 'AWAITING_APPROVAL' && setTimeout(() => s.decide({ type: 'approve' }), 0);
  await s.start();
  assert.equal(s.verification, 'ERROR_GONE');
  assert.equal(s.attempt, 2);
  assert.equal(commits.length, 2);
  assert.match(branch['app/Router.kt'], /rotaPadrao/);
  assert.match(repo['app/Router.kt'], /foo\(\)/);
});

test('ação em JSON com texto em volta', () => {
  assert.equal(parseAction('Vou ler.\n```json\n{"action":"read","path":"a.kt"}\n```').path, 'a.kt');
  assert.equal(parseAction('nada aqui'), null);
});

test('agente recusa caminhos fora do projeto', async () => {
  const ws = new MemoryWorkspace({ 'a.txt': 'x' });
  const agent = new Agent({ workspace: ws, llm: null, emit: () => {} });
  assert.match(await agent.execute({ action: 'read', path: '../etc/passwd' }).catch((e) => e.message), /não permitido/);
});
