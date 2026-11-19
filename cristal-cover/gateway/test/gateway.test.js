import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FakeKV, OPENROUTER_MODELS, call, callJson, fakeFetch, makeEnv, sse } from './helpers.js';
import { pickFamilies, costFromTail } from '../src/code.js';
import { estimateVideo, normalize, VIDEO_MODELS } from '../src/video.js';
import { logTail } from '../src/repo.js';
import { fromBase64url } from '../src/http.js';

// --- Acesso -----------------------------------------------------------------------------------

test('sem chave configurada, o gateway recusa tudo (falha fechada)', async () => {
  const { status, body } = await callJson({}, 'GET', '/v1/status');
  assert.equal(status, 503);
  assert.match(body.error, /LILY_ACCESS_KEY/);
});

test('chave errada ou ausente: 401, sem chegar a nenhum provedor', async () => {
  const f = fakeFetch([]);
  const env = makeEnv({ FETCH: f, OPENROUTER_API_KEY: 'x' });
  assert.equal((await callJson(env, 'GET', '/v1/status', undefined, 'errada')).status, 401);
  assert.equal((await callJson(env, 'GET', '/v1/status', undefined, '')).status, 401);
  assert.equal(f.calls.length, 0);
});

test('status mostra o que está configurado e o limite de gasto', async () => {
  const f = fakeFetch([['GET', /openrouter\.ai\/api\/v1\/models$/, () => OPENROUTER_MODELS]]);
  const kv = new FakeKV();
  const env = makeEnv({ FETCH: f, OPENROUTER_API_KEY: 'or', FAL_KEY: 'fal', LILY_USAGE: kv, DAILY_BUDGET_USD: '2' });
  const { status, body } = await callJson(env, 'GET', '/v1/status');
  assert.equal(status, 200);
  assert.deepEqual(body.features, { code: true, video: true, search: false, repos: false });
  assert.equal(body.budget.dailyUsd, 2);
  assert.equal(body.budget.enforced, true);
  assert.equal(f.calls[0].headers.authorization, 'Bearer or');
});

// --- Modelos de programação -------------------------------------------------------------------

test('catálogo: a versão mais nova de cada família, só modelos que existem, sem :free', () => {
  const picked = pickFamilies(OPENROUTER_MODELS.data);
  const ids = picked.map((m) => m.id);
  assert.ok(ids.includes('anthropic/claude-opus-4.7'));
  assert.ok(!ids.includes('anthropic/claude-opus-4.5'));
  assert.ok(ids.includes('deepseek/deepseek-v4-pro'));
  assert.ok(!ids.some((id) => id.includes(':')));
  assert.ok(!ids.includes('openrouter/auto'));
  assert.ok(!ids.includes('meta-llama/llama-4-scout'), 'fora das famílias de programação');
  const opus = picked.find((m) => m.id === 'anthropic/claude-opus-4.7');
  assert.equal(opus.priceIn, 5);
  assert.equal(opus.priceOut, 25);
  assert.equal(opus.tier, 'frontier');
  assert.ok(opus.roles.includes('architecture'));
});

test('custo pelo uso informado no fim do stream (ou pelos tokens)', () => {
  const model = { priceIn: 3, priceOut: 15 };
  assert.equal(costFromTail('data: {"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":2,"cost":0.0123}}\n\ndata: [DONE]\n', model), 0.0123);
  const byTokens = costFromTail('data: {"usage":{"prompt_tokens":1000000,"completion_tokens":0}}\n', model);
  assert.equal(byTokens, 3);
  assert.equal(costFromTail('data: {"choices":[{"delta":{"content":"oi"}}]}\n', model), null);
});

test('chat: repassa o stream, usa a chave do gateway e soma o gasto real', async () => {
  const f = fakeFetch([
    ['GET', /\/api\/v1\/models$/, () => OPENROUTER_MODELS],
    [
      'POST',
      /\/api\/v1\/chat\/completions$/,
      () =>
        sse([
          ': OPENROUTER PROCESSING\n\n',
          'data: {"choices":[{"delta":{"content":"fun "}}]}\n\n',
          'data: {"choices":[{"delta":{"content":"main()"}}]}\n\n',
          'data: {"choices":[{"delta":{}}],"usage":{"prompt_tokens":120,"completion_tokens":8,"cost":0.0042}}\n\n',
          'data: [DONE]\n\n',
        ]),
    ],
  ]);
  const kv = new FakeKV();
  const env = makeEnv({ FETCH: f, OPENROUTER_API_KEY: 'or-key', LILY_USAGE: kv });
  const { res, pending } = await call(env, 'POST', '/v1/chat', {
    model: 'anthropic/claude-sonnet-4.6',
    messages: [{ role: 'system', content: 'Você é o Codex.' }, { role: 'user', content: 'Escreva um main.' }],
  });
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('x-lily-model'), 'anthropic/claude-sonnet-4.6');
  const text = await res.text();
  await Promise.all(pending);
  assert.match(text, /fun /);
  assert.match(text, /\[DONE\]/);
  const upstreamCall = f.calls.find((c) => c.url.endsWith('/chat/completions'));
  assert.equal(upstreamCall.headers.authorization, 'Bearer or-key');
  assert.equal(upstreamCall.body.stream, true);
  assert.deepEqual(upstreamCall.body.usage, { include: true });
  const spent = [...kv.map.entries()].find(([k]) => k.startsWith('spent:'));
  assert.equal(Number(spent[1]), 0.0042);
});

test('chat: modelo fora do catálogo é recusado antes do provedor', async () => {
  const f = fakeFetch([['GET', /\/api\/v1\/models$/, () => OPENROUTER_MODELS]]);
  const env = makeEnv({ FETCH: f, OPENROUTER_API_KEY: 'or' });
  const { status, body } = await callJson(env, 'POST', '/v1/chat', { model: 'openai/gpt-inventado', messages: [{ role: 'user', content: 'oi' }] });
  assert.equal(status, 400);
  assert.match(body.error, /indisponível/);
  assert.ok(!f.calls.some((c) => c.url.includes('/chat/completions')));
});

test('chat: o limite diário recusa antes de gastar', async () => {
  const f = fakeFetch([['GET', /\/api\/v1\/models$/, () => OPENROUTER_MODELS]]);
  const kv = new FakeKV();
  await kv.put(`spent:${new Date().toISOString().slice(0, 10)}`, '2.999');
  const env = makeEnv({ FETCH: f, OPENROUTER_API_KEY: 'or', LILY_USAGE: kv, DAILY_BUDGET_USD: '3' });
  const { status, body } = await callJson(env, 'POST', '/v1/chat', {
    model: 'anthropic/claude-opus-4.7',
    messages: [{ role: 'user', content: 'x'.repeat(20000) }],
    maxTokens: 8000,
  });
  assert.equal(status, 402);
  assert.match(body.error, /Limite diário/);
  assert.ok(!f.calls.some((c) => c.url.includes('/chat/completions')));
});

test('chat: sem créditos no provedor vira uma mensagem clara', async () => {
  const f = fakeFetch([
    ['GET', /\/api\/v1\/models$/, () => OPENROUTER_MODELS],
    ['POST', /\/chat\/completions$/, () => new Response('{"error":{"message":"Insufficient credits"}}', { status: 402 })],
  ]);
  const env = makeEnv({ FETCH: f, OPENROUTER_API_KEY: 'or' });
  const { status, body } = await callJson(env, 'POST', '/v1/chat', { model: 'deepseek/deepseek-v4-pro', messages: [{ role: 'user', content: 'oi' }] });
  assert.equal(status, 402);
  assert.match(body.error, /sem créditos/);
});

// --- Pesquisa ---------------------------------------------------------------------------------

test('pesquisa pelo Tavily, com fontes normalizadas', async () => {
  const f = fakeFetch([
    ['POST', /api\.tavily\.com\/search$/, () => ({ results: [{ title: 'Room release notes', url: 'https://developer.android.com/jetpack/androidx/releases/room', content: 'Version 2.8.5 ...' }, { title: 'x', url: 'javascript:alert(1)', content: '' }] })],
  ]);
  const env = makeEnv({ FETCH: f, TAVILY_API_KEY: 'tv' });
  const { status, body } = await callJson(env, 'POST', '/v1/search', { query: 'Room 2.8 release notes' });
  assert.equal(status, 200);
  assert.equal(body.provider, 'tavily');
  assert.equal(body.results.length, 1);
  assert.equal(f.calls[0].headers.authorization, 'Bearer tv');
});

test('pesquisa pelo Brave quando não há Tavily', async () => {
  const f = fakeFetch([['GET', /api\.search\.brave\.com/, () => ({ web: { results: [{ title: 'A', url: 'https://a.dev', description: '<strong>b</strong>' }] } })]]);
  const env = makeEnv({ FETCH: f, BRAVE_API_KEY: 'br' });
  const { body } = await callJson(env, 'POST', '/v1/search', { query: 'kotlin flow' });
  assert.equal(body.provider, 'brave');
  assert.equal(body.results[0].snippet, 'b');
  assert.equal(f.calls[0].headers['x-subscription-token'], 'br');
});

// --- Vídeo ------------------------------------------------------------------------------------

test('vídeo: catálogo com modos, durações e preço de referência', async () => {
  const env = makeEnv({ FAL_KEY: 'fal' });
  const { body } = await callJson(env, 'GET', '/v1/video/models');
  assert.equal(body.provider, 'fal');
  const ltx = body.models.find((m) => m.id === 'ltx-2');
  assert.deepEqual(ltx.modes, ['text', 'image']);
  assert.equal(ltx.fromUsd, 0.36);
  const hunyuan = body.models.find((m) => m.id === 'hunyuan');
  assert.deepEqual(hunyuan.modes, ['text']);
});

test('vídeo: estimativas por modo, resolução e som', () => {
  const ltx = VIDEO_MODELS.find((m) => m.id === 'ltx-2');
  assert.equal(estimateVideo(ltx, { duration: 10, imageUrl: 'data:', resolution: '1080p' }), 0.4);
  const veo = VIDEO_MODELS.find((m) => m.id === 'veo-3.1-fast');
  assert.equal(estimateVideo(veo, { duration: 8, audio: true, resolution: '720p' }), 1.2);
  assert.equal(estimateVideo(veo, { duration: 8, audio: false, resolution: '720p' }), 0.8);
  const wan = VIDEO_MODELS.find((m) => m.id === 'wan-2.5');
  assert.equal(estimateVideo(wan, { duration: 5, resolution: '480p' }), 0.25);
  assert.equal(estimateVideo(ltx, { duration: 6 }, { VIDEO_PRICES: '{"ltx-2":0.05}' }), 0.3);
});

test('vídeo: normaliza parâmetros fora da lista e recusa o que o modelo não faz', () => {
  const { o, mode } = normalize({ model: 'kling-2.5', prompt: 'um gato', duration: 7, aspect: '4:3' });
  assert.equal(mode, 'text');
  assert.equal(o.duration, 5);
  assert.equal(o.aspect, '16:9');
  assert.throws(() => normalize({ model: 'hunyuan', prompt: 'x', image: 'data:image/png;base64,AAAA' }), /não anima imagens/);
  assert.throws(() => normalize({ model: 'ltx-2', prompt: ' ' }), /Descreva/);
  assert.throws(() => normalize({ model: 'ltx-2', prompt: 'x', image: 'file:///etc/passwd' }), /JPEG, PNG/);
});

test('vídeo: envio à fila, acompanhamento e resultado', async () => {
  const f = fakeFetch([
    ['POST', /queue\.fal\.run\/fal-ai\/ltx-2\/image-to-video\/fast$/, () => ({
      request_id: 'req-123456',
      status_url: 'https://queue.fal.run/fal-ai/ltx-2/requests/req-123456/status',
      response_url: 'https://queue.fal.run/fal-ai/ltx-2/requests/req-123456',
      cancel_url: 'https://queue.fal.run/fal-ai/ltx-2/requests/req-123456/cancel',
    })],
    ['GET', /requests\/req-123456\/status\?logs=1$/, () => ({ status: 'COMPLETED' })],
    ['GET', /requests\/req-123456$/, () => ({ video: { url: 'https://v3.fal.media/files/abc/out.mp4', content_type: 'video/mp4', file_size: 123 }, seed: 7 })],
  ]);
  const kv = new FakeKV();
  const env = makeEnv({ FETCH: f, FAL_KEY: 'fal-key', LILY_USAGE: kv });
  const sub = await callJson(env, 'POST', '/v1/video', { model: 'ltx-2', prompt: 'a câmera gira em volta da flor', image: 'data:image/jpeg;base64,AAAA', duration: 6 });
  assert.equal(sub.status, 200);
  assert.equal(sub.body.mode, 'image');
  assert.equal(sub.body.estimatedUsd, 0.24);
  const submitted = f.calls[0];
  assert.equal(submitted.headers.authorization, 'Key fal-key');
  assert.equal(submitted.body.image_url, 'data:image/jpeg;base64,AAAA');
  assert.equal(submitted.body.duration, 6);
  const st = await callJson(env, 'GET', `/v1/video/${sub.body.jobId}`);
  assert.equal(st.body.status, 'done');
  assert.equal(st.body.video.url, 'https://v3.fal.media/files/abc/out.mp4');
  assert.equal(Number([...kv.map.values()][0]), 0.24);
});

test('vídeo: fila, processando e falha informada pelo resultado', async () => {
  let state = 'IN_QUEUE';
  const f = fakeFetch([
    ['POST', /queue\.fal\.run\/fal-ai\/kling-video/, () => ({ request_id: 'req-abcdef' })],
    ['GET', /requests\/req-abcdef\/status/, () => ({ status: state, queue_position: 2, logs: [{ message: 'step 3/20' }] })],
    ['GET', /requests\/req-abcdef$/, () => new Response(JSON.stringify({ detail: [{ msg: 'Prompt flagged by safety filter' }] }), { status: 422 })],
  ]);
  const env = makeEnv({ FETCH: f, FAL_KEY: 'fal' });
  const sub = await callJson(env, 'POST', '/v1/video', { model: 'kling-2.5', prompt: 'x' });
  // Sem as URLs na resposta, o gateway monta pela regra do fal (dono/app).
  const statusUrl = (await callJson(env, 'GET', `/v1/video/${sub.body.jobId}`)).body;
  assert.equal(statusUrl.status, 'queued');
  assert.equal(statusUrl.position, 2);
  assert.ok(f.calls.some((c) => c.url.startsWith('https://queue.fal.run/fal-ai/kling-video/requests/req-abcdef/status')));
  state = 'IN_PROGRESS';
  assert.equal((await callJson(env, 'GET', `/v1/video/${sub.body.jobId}`)).body.status, 'running');
  state = 'COMPLETED';
  const done = (await callJson(env, 'GET', `/v1/video/${sub.body.jobId}`)).body;
  assert.equal(done.status, 'failed');
  assert.match(done.error, /safety filter/);
});

test('vídeo: o id do trabalho não consegue apontar a chave para outro servidor', async () => {
  const f = fakeFetch([['GET', /evil\.example/, () => ({ status: 'COMPLETED' })]]);
  const env = makeEnv({ FETCH: f, FAL_KEY: 'fal' });
  const forged = Buffer.from(JSON.stringify({ e: 'fal-ai/ltx-2/text-to-video', r: 'req-123456', s: 'https://evil.example/steal', m: 'ltx-2', t: 0 })).toString('base64url');
  const fallback = Buffer.from(JSON.stringify({ e: 'https://evil.example', r: 'req-123456', m: 'ltx-2' })).toString('base64url');
  await callJson(env, 'GET', `/v1/video/${forged}`);
  assert.ok(!f.calls.some((c) => c.url.includes('evil.example')));
  assert.ok(f.calls.every((c) => c.url.startsWith('https://queue.fal.run/')));
  assert.equal((await callJson(env, 'GET', `/v1/video/${fallback}`)).status, 400);
});

test('vídeo: parâmetro recusado (422) tenta de novo só com o essencial', async () => {
  let n = 0;
  const f = fakeFetch([
    ['POST', /queue\.fal\.run\/fal-ai\/veo3\.1\/fast$/, () => (++n === 1 ? new Response('{"detail":"bad duration"}', { status: 422 }) : { request_id: 'req-999999' })],
  ]);
  const env = makeEnv({ FETCH: f, FAL_KEY: 'fal' });
  const sub = await callJson(env, 'POST', '/v1/video', { model: 'veo-3.1-fast', prompt: 'ondas', duration: 8 });
  assert.equal(sub.status, 200);
  assert.equal(sub.body.simplified, true);
  assert.deepEqual(f.calls[1].body, { prompt: 'ondas' });
  const job = JSON.parse(fromBase64url(sub.body.jobId));
  assert.equal(job.r, 'req-999999');
});

// --- Repositório e testes do Codex ------------------------------------------------------------

function githubFake() {
  return fakeFetch([
    ['GET', /\/repos\/dono\/app\/git\/ref\/heads\/lily-codex\/s1$/, () => new Response('{"message":"Not Found"}', { status: 404 })],
    ['GET', /\/repos\/dono\/app\/git\/ref\/heads\/main$/, () => ({ object: { sha: 'base-sha' } })],
    ['GET', /\/repos\/dono\/app\/git\/commits\/base-sha$/, () => ({ tree: { sha: 'tree-base' } })],
    ['POST', /\/repos\/dono\/app\/git\/trees$/, () => ({ sha: 'tree-new' })],
    ['POST', /\/repos\/dono\/app\/git\/commits$/, () => ({ sha: 'commit-new' })],
    ['POST', /\/repos\/dono\/app\/git\/refs$/, () => ({ ref: 'refs/heads/lily-codex/s1' })],
    ['POST', /\/actions\/workflows\/lily-codex-check\.yml\/dispatches$/, () => new Response(null, { status: 204 })],
    ['GET', /\/actions\/workflows\/lily-codex-check\.yml\/runs/, () => ({ workflow_runs: [{ id: 77, status: 'completed', conclusion: 'failure', html_url: 'https://github.com/dono/app/actions/runs/77', created_at: new Date().toISOString() }] })],
    ['GET', /\/actions\/runs\/77\/jobs$/, () => ({ jobs: [{ id: 5, name: 'Testes', conclusion: 'failure' }] })],
    ['GET', /\/actions\/jobs\/5\/logs$/, () => new Response('2026-11-19T10:00:00.0000000Z > Task :app:compileDebugKotlin\n2026-11-19T10:00:01.0000000Z e: Router.kt:42 Unresolved reference: foo\n2026-11-19T10:00:02.0000000Z BUILD FAILED in 40s\n', { status: 200 })],
    ['GET', /\/repos\/dono\/app\/contents\/src\/A\.kt/, () => ({ type: 'file', size: 10, sha: 's', content: Buffer.from('package a\n// ação\n').toString('base64') })],
  ]);
}

test('repositório: só os autorizados; o principal nunca é tocado', async () => {
  const f = githubFake();
  const env = makeEnv({ FETCH: f, GITHUB_TOKEN: 'gh', GITHUB_REPOS: 'dono/app' });
  assert.equal((await callJson(env, 'GET', '/v1/repo/file?repo=outro/app&path=a')).status, 403);
  const toMain = await callJson(env, 'POST', '/v1/repo/commit', { repo: 'dono/app', branch: 'main', base: 'main', files: [{ path: 'a.kt', content: 'x' }] });
  assert.equal(toMain.status, 400);
  const workflow = await callJson(env, 'POST', '/v1/repo/commit', { repo: 'dono/app', branch: 'lily-codex/s1', base: 'main', files: [{ path: '.github/workflows/x.yml', content: 'x' }] });
  assert.equal(workflow.status, 403);
  const escape = await callJson(env, 'POST', '/v1/repo/commit', { repo: 'dono/app', branch: 'lily-codex/s1', base: 'main', files: [{ path: '../x', content: 'x' }] });
  assert.equal(escape.status, 400);
  assert.equal(f.calls.length, 0);
});

test('repositório: lê arquivo em UTF-8 e grava num ramo novo lily-codex/', async () => {
  const f = githubFake();
  const env = makeEnv({ FETCH: f, GITHUB_TOKEN: 'gh', GITHUB_REPOS: 'dono/app' });
  const file = await callJson(env, 'GET', '/v1/repo/file?repo=dono/app&path=src/A.kt&ref=main');
  assert.equal(file.body.content, 'package a\n// ação\n');
  const commit = await callJson(env, 'POST', '/v1/repo/commit', {
    repo: 'dono/app',
    branch: 'lily-codex/s1',
    base: 'main',
    message: 'Corrige o roteador',
    files: [{ path: 'src/A.kt', content: 'package a\n' }, { path: 'src/Old.kt', content: null }],
  });
  assert.equal(commit.status, 200);
  assert.equal(commit.body.created, true);
  assert.equal(commit.body.commit, 'commit-new');
  const tree = f.calls.find((c) => c.url.endsWith('/git/trees') && c.method === 'POST');
  assert.equal(tree.body.base_tree, 'tree-base');
  assert.deepEqual(tree.body.tree[1], { path: 'src/Old.kt', mode: '100644', type: 'blob', sha: null });
  const ref = f.calls.find((c) => c.url.endsWith('/git/refs') && c.method === 'POST');
  assert.equal(ref.body.ref, 'refs/heads/lily-codex/s1');
  assert.equal(ref.body.sha, 'commit-new');
});

test('testes no Actions: valida as tarefas, dispara e traz o fim do log da falha', async () => {
  const f = githubFake();
  const env = makeEnv({ FETCH: f, GITHUB_TOKEN: 'gh', GITHUB_REPOS: 'dono/app' });
  const bad = await callJson(env, 'POST', '/v1/repo/check', { repo: 'dono/app', branch: 'lily-codex/s1', tasks: ':app:test; rm -rf /' });
  assert.equal(bad.status, 400);
  const start = await callJson(env, 'POST', '/v1/repo/check', { repo: 'dono/app', branch: 'lily-codex/s1', tasks: ':app:testDebugUnitTest', workdir: 'cristal-cover' });
  assert.equal(start.status, 200);
  const dispatch = f.calls.find((c) => c.url.endsWith('/dispatches'));
  assert.deepEqual(dispatch.body, { ref: 'lily-codex/s1', inputs: { runner: 'gradle', tasks: ':app:testDebugUnitTest', workdir: 'cristal-cover' } });
  const runner = await callJson(env, 'POST', '/v1/repo/check', { repo: 'dono/app', branch: 'lily-codex/s1', runner: 'bash', tasks: 'x' });
  assert.equal(runner.status, 400);
  const st = await callJson(env, 'GET', `/v1/repo/check?repo=dono/app&branch=lily-codex/s1&since=${encodeURIComponent(start.body.startedAt)}`);
  assert.equal(st.body.conclusion, 'failure');
  assert.match(st.body.failures[0].log, /Unresolved reference: foo/);
  assert.doesNotMatch(st.body.failures[0].log, /2026-11-19T/);
});

test('fim do log começa perto do primeiro erro', () => {
  const lines = Array.from({ length: 400 }, (_, i) => `linha ${i}`);
  lines[300] = 'e: Falhou aqui';
  const tail = logTail(lines.join('\n'), 50);
  assert.match(tail, /e: Falhou aqui/);
  assert.equal(tail.split('\n').length, 50);
});

// --- CORS e página -----------------------------------------------------------------------------

test('CORS só para origens liberadas; preflight sem liberação é recusado', async () => {
  const { default: worker } = await import('../src/index.js');
  const env = makeEnv({ ALLOWED_ORIGINS: 'https://meu-site.dev' });
  const ok = await worker.fetch(new Request('https://g.test/v1/status', { method: 'OPTIONS', headers: { origin: 'https://meu-site.dev' } }), env, {});
  assert.equal(ok.status, 204);
  assert.equal(ok.headers.get('access-control-allow-origin'), 'https://meu-site.dev');
  const other = await worker.fetch(new Request('https://g.test/v1/status', { method: 'OPTIONS', headers: { origin: 'https://outro.dev' } }), env, {});
  assert.equal(other.status, 403);
  const res = await worker.fetch(new Request('https://g.test/v1/status', { headers: { origin: 'https://meu-site.dev', authorization: 'Bearer errada' } }), env, {});
  assert.equal(res.status, 401);
  assert.equal(res.headers.get('access-control-allow-origin'), 'https://meu-site.dev');
});
