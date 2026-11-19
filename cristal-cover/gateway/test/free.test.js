// Fotos e vídeos grátis: imagens pelo Workers AI (binding AI) e vídeo pelo Space oficial do
// LTX-Video no Hugging Face (API do Gradio), com o formato que o Gradio 5 usa de verdade.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FakeKV, OPENROUTER_MODELS, call, callJson, fakeFetch, makeEnv } from './helpers.js';
import { gradioVideo, ltxData } from '../src/video.js';
import { fromBase64url } from '../src/http.js';

const JPEG = Uint8Array.from([0xff, 0xd8, 0xff, 0xe0, ...new Array(200).fill(7)]);
const PNG = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, ...new Array(200).fill(9)]);
const b64 = (bytes) => Buffer.from(bytes).toString('base64');

function fakeAI(reply) {
  const calls = [];
  return {
    calls,
    async run(model, input) {
      calls.push({ model, input });
      return typeof reply === 'function' ? reply(input) : reply;
    },
  };
}

// --- Imagens -----------------------------------------------------------------------------------

test('imagem grátis: FLUX pelo Workers AI, devolve a foto e conta o dia', async () => {
  const ai = fakeAI({ image: b64(JPEG) });
  const kv = new FakeKV();
  const env = makeEnv({ AI: ai, LILY_USAGE: kv, FREE_IMAGES_PER_DAY: '2' });
  const r = await callJson(env, 'POST', '/v1/image', { prompt: 'um gato de vidro líquido', seed: 7 });
  assert.equal(r.status, 200);
  assert.match(r.body.image, /^data:image\/jpeg;base64,/);
  assert.equal(r.body.costUsd, 0);
  assert.equal(r.body.free, true);
  assert.equal(r.body.usedToday, 1);
  assert.equal(r.body.perDay, 2);
  assert.equal(ai.calls[0].model, '@cf/black-forest-labs/flux-1-schnell');
  assert.deepEqual(ai.calls[0].input, { prompt: 'um gato de vidro líquido', steps: 4, seed: 7 });

  const st = await callJson(env, 'GET', '/v1/status');
  assert.equal(st.body.features.image, true);
  assert.equal(st.body.free.image.usedToday, 1);
  assert.equal(st.body.free.image.perDay, 2);

  // O limite do dia para antes de virar cobrança (sem chamar o Workers AI de novo).
  await callJson(env, 'POST', '/v1/image', { prompt: 'segunda' });
  const third = await callJson(env, 'POST', '/v1/image', { prompt: 'terceira' });
  assert.equal(third.status, 429);
  assert.match(third.body.error, /grátis de hoje/);
  assert.equal(ai.calls.length, 2);
});

test('imagem grátis: aceita a resposta em bytes (stream) e reconhece PNG', async () => {
  const ai = fakeAI(() => new Response(PNG).body);
  const r = await callJson(makeEnv({ AI: ai }), 'POST', '/v1/image', { prompt: 'x' });
  assert.equal(r.status, 200);
  assert.match(r.body.image, /^data:image\/png;base64,/);
  assert.equal(r.body.usedToday, null);
});

test('imagem grátis: sem Workers AI, descrição vazia e cota esgotada viram mensagens claras', async () => {
  assert.equal((await callJson(makeEnv(), 'POST', '/v1/image', { prompt: 'x' })).status, 501);
  assert.equal((await callJson(makeEnv({ AI: fakeAI({}) }), 'POST', '/v1/image', { prompt: '  ' })).status, 400);
  const ai = { run: async () => { throw new Error('4006: you have used up your daily free allocation of 10,000 neurons'); } };
  const r = await callJson(makeEnv({ AI: ai }), 'POST', '/v1/image', { prompt: 'x' });
  assert.equal(r.status, 429);
  assert.match(r.body.error, /cota grátis diária/);
});

// --- Vídeo grátis ------------------------------------------------------------------------------

const SPACE = 'https://lightricks-ltx-video-distilled.hf.space';

function spaceFake({ events, upload } = {}) {
  return fakeFetch([
    ['POST', /hf\.space\/gradio_api\/upload$/, () => upload || ['/tmp/gradio/abc/referencia.jpeg']],
    ['POST', /hf\.space\/gradio_api\/call\/(text|image)_to_video$/, () => ({ event_id: 'ev-1234567' })],
    [
      'GET',
      /hf\.space\/gradio_api\/call\/(text|image)_to_video\/ev-1234567$/,
      () => new Response(events ?? 'event: heartbeat\ndata: null\n\nevent: generating\ndata: null\n\nevent: complete\ndata: [{"video": {"path": "/tmp/gradio/x/out.mp4", "url": "' + SPACE + '/gradio_api/file=/tmp/gradio/x/out.mp4", "size": 12345, "mime_type": "video/mp4"}, "subtitles": null}, 1234]\n\n', { headers: { 'content-type': 'text/event-stream' } }),
    ],
  ]);
}

async function ndjson(res) {
  return (await res.text()).trim().split('\n').map((l) => JSON.parse(l));
}

test('vídeo grátis: texto para vídeo no Space oficial, sem FAL_KEY e sem gastar', async () => {
  const f = spaceFake();
  const kv = new FakeKV();
  const env = makeEnv({ FETCH: f, HF_TOKEN: 'hf_teste', LILY_USAGE: kv });
  const models = await callJson(env, 'GET', '/v1/video/models');
  assert.deepEqual(models.body.models.map((m) => m.id), ['ltx-free']);
  assert.equal(models.body.freeToken, true);

  const sub = await callJson(env, 'POST', '/v1/video', { model: 'ltx-free', prompt: 'uma gota de vidro caindo', duration: 4, aspect: '9:16' });
  assert.equal(sub.status, 200);
  assert.equal(sub.body.free, true);
  assert.equal(sub.body.stream, true);
  assert.equal(sub.body.estimatedUsd, 0);
  const post = f.calls.find((c) => c.url.endsWith('/gradio_api/call/text_to_video'));
  assert.equal(post.url, `${SPACE}/gradio_api/call/text_to_video`);
  assert.equal(post.headers.authorization, 'Bearer hf_teste');
  assert.deepEqual(post.body.data, ['uma gota de vidro caindo', 'worst quality, inconsistent motion, blurry, jittery, distorted', null, null, 736, 416, 'text-to-video', 4, 9, 42, true, 1, true]);
  assert.equal(JSON.parse(fromBase64url(sub.body.jobId)).ev, 'ev-1234567');

  const { res } = await call(env, 'GET', `/v1/video/${sub.body.jobId}`);
  assert.match(res.headers.get('content-type'), /application\/x-ndjson/);
  const lines = await ndjson(res);
  assert.equal(lines[0].status, 'running');
  const last = lines.at(-1);
  assert.equal(last.status, 'done');
  assert.equal(last.video.url, `${SPACE}/gradio_api/file=/tmp/gradio/x/out.mp4`);
  assert.equal(last.video.fileSize, 12345);
  assert.equal(f.calls.some((c) => /fal\.run/.test(c.url)), false);
  assert.equal(await kv.get(`spent:${new Date().toISOString().slice(0, 10)}`), null);
});

test('vídeo grátis: anima uma foto (upload do Gradio e FileData na ordem da API)', async () => {
  const f = spaceFake();
  const env = makeEnv({ FETCH: f });
  const sub = await callJson(env, 'POST', '/v1/video', { model: 'ltx-free', prompt: 'a flor balança', image: `data:image/jpeg;base64,${b64(JPEG)}`, duration: 6 });
  assert.equal(sub.status, 200);
  const upload = f.calls.find((c) => c.url.endsWith('/gradio_api/upload'));
  assert.ok(upload.body instanceof FormData);
  assert.equal(upload.headers.authorization, undefined);
  const post = f.calls.find((c) => c.url.endsWith('/gradio_api/call/image_to_video'));
  assert.deepEqual(post.body.data[2], { path: '/tmp/gradio/abc/referencia.jpeg', meta: { _type: 'gradio.FileData' } });
  assert.equal(post.body.data[6], 'image-to-video');
  assert.equal(post.body.data[7], 6);
});

test('vídeo grátis: cota do Hugging Face esgotada e conexão perdida viram mensagens claras', async () => {
  const quota = spaceFake({ events: 'event: error\ndata: "You have exceeded your GPU quota (60s requested vs. 30s left). Try again in 2:00:00"\n\n' });
  const env = makeEnv({ FETCH: quota });
  const sub = await callJson(env, 'POST', '/v1/video', { model: 'ltx-free', prompt: 'x' });
  const { res } = await call(env, 'GET', `/v1/video/${sub.body.jobId}`);
  const last = (await ndjson(res)).at(-1);
  assert.equal(last.status, 'failed');
  assert.match(last.error, /cota grátis de GPU do Hugging Face/);
  assert.match(last.error, /HF_TOKEN/);

  const lost = fakeFetch([
    ['POST', /gradio_api\/call\/text_to_video$/, () => ({ event_id: 'ev-1234567' })],
    ['GET', /gradio_api\/call\/text_to_video\/ev-1234567$/, () => new Response('Not Found', { status: 404 })],
  ]);
  const env2 = makeEnv({ FETCH: lost });
  const sub2 = await callJson(env2, 'POST', '/v1/video', { model: 'ltx-free', prompt: 'x' });
  const st = await callJson(env2, 'GET', `/v1/video/${sub2.body.jobId}`);
  assert.equal(st.body.status, 'failed');
  assert.match(st.body.error, /Gere de novo/);
});

test('vídeo grátis: só aceita vídeos do próprio Space e o formato na ordem certa', () => {
  assert.equal(gradioVideo([{ video: { url: 'https://evil.example/x.mp4', path: '/x' } }], SPACE), null);
  assert.equal(gradioVideo([{ video: { path: '/tmp/a.mp4' } }], SPACE).url, `${SPACE}/gradio_api/file=/tmp/a.mp4`);
  assert.equal(gradioVideo([{ url: `${SPACE}/gradio_api/file=/tmp/b.mp4`, path: '/tmp/b.mp4' }], SPACE).url, `${SPACE}/gradio_api/file=/tmp/b.mp4`);
  assert.deepEqual(ltxData({ prompt: 'p', aspect: '1:1', duration: 2 }, null).slice(4, 8), [512, 512, 'text-to-video', 2]);
});

test('vídeo grátis: identificador adulterado não alcança outro endereço', async () => {
  const env = makeEnv({ FETCH: spaceFake() });
  const bad = Buffer.from(JSON.stringify({ p: 'hf', m: 'ltx-free', a: '../../evil', ev: 'ev-1234567' })).toString('base64url');
  const r = await callJson(env, 'GET', `/v1/video/${bad}`);
  assert.equal(r.status, 400);
});

// --- Conversa grátis (beta) ---------------------------------------------------------------------

function sseStream(events) {
  const enc = new TextEncoder();
  return new ReadableStream({
    start(c) {
      for (const e of events) c.enqueue(enc.encode(`data: ${typeof e === 'string' ? e : JSON.stringify(e)}\n\n`));
      c.close();
    },
  });
}

async function chatText(res) {
  const raw = await res.text();
  let text = '';
  let usage = null;
  for (const line of raw.split('\n')) {
    if (!line.startsWith('data: ') || line === 'data: [DONE]') continue;
    const o = JSON.parse(line.slice(6));
    // Como o app e a Web: OpenAI (choices.delta.content) ou o formato do Workers AI (response).
    text += o.choices?.[0]?.delta?.content || (typeof o.response === 'string' ? o.response : '');
    if (o.usage) usage = o.usage;
  }
  return { text, usage, raw };
}

test('conversa grátis: aparece primeiro na lista, com ou sem OpenRouter', async () => {
  const only = await callJson(makeEnv({ AI: fakeAI({}) }), 'GET', '/v1/models');
  assert.equal(only.status, 200);
  assert.deepEqual(only.body.models.map((m) => m.id), ['free/gpt-oss-20b']);
  assert.equal(only.body.models[0].free, true);
  assert.equal(only.body.models[0].priceIn, 0);
  assert.ok(only.body.models[0].roles.includes('chat'));
  const f = fakeFetch([['GET', /openrouter\.ai\/api\/v1\/models$/, () => OPENROUTER_MODELS]]);
  const both = await callJson(makeEnv({ AI: fakeAI({}), FETCH: f, OPENROUTER_API_KEY: 'or' }), 'GET', '/v1/models');
  assert.equal(both.body.models[0].id, 'free/gpt-oss-20b');
  assert.ok(both.body.models.length > 1);
  assert.equal((await callJson(makeEnv(), 'GET', '/v1/models')).status, 501);
});

test('conversa grátis: repassa os pedaços sem reprocessar (pouca CPU) e conta a cota pelo uso', async () => {
  const ai = fakeAI(() => sseStream([{ response: 'Olá! ' }, { response: '2 + 2 = 4.' }, { response: '', usage: { prompt_tokens: 100, completion_tokens: 20 } }, '[DONE]']));
  const kv = new FakeKV();
  const env = makeEnv({ AI: ai, LILY_USAGE: kv });
  const { res } = await call(env, 'POST', '/v1/chat', { model: 'free/gpt-oss-20b', messages: [{ role: 'system', content: 'Seja breve.' }, { role: 'user', content: 'Quanto é 2 + 2?' }] });
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('x-lily-model'), 'free/gpt-oss-20b');
  const out = await chatText(res);
  assert.equal(out.text, 'Olá! 2 + 2 = 4.');
  assert.equal(out.usage.completion_tokens, 20);
  // Os bytes saem iguais aos do Workers AI (nada é reescrito pedaço a pedaço).
  assert.match(out.raw, /"response":"Olá! "/);
  assert.equal(ai.calls[0].model, '@cf/openai/gpt-oss-20b');
  assert.equal(ai.calls[0].input.stream, true);
  assert.equal(ai.calls[0].input.messages.at(-1).content, 'Quanto é 2 + 2?');
  const used = Number(await kv.get(`neurons:${new Date().toISOString().slice(0, 10)}`));
  assert.ok(used > 2 && used < 3, `neurons: ${used}`);
  const st = await callJson(env, 'GET', '/v1/status');
  assert.equal(st.body.features.chatFree, true);
  assert.equal(st.body.free.chat, 'L.I.L.Y Online (grátis, beta)');
});

test('conversa grátis: entende o formato OpenAI (choices) e tenta de novo se recusar parâmetros', async () => {
  let n = 0;
  const ai = {
    calls: [],
    async run(model, input) {
      this.calls.push(input);
      if (n++ === 0) throw new Error('AiError: Invalid input: max_tokens is not allowed');
      return sseStream([{ choices: [{ delta: { reasoning_content: 'pensando…' } }] }, { choices: [{ delta: { content: 'Brasília.' } }] }, '[DONE]']);
    },
  };
  const { res } = await call(makeEnv({ AI: ai }), 'POST', '/v1/chat', { model: 'free/gpt-oss-20b', messages: [{ role: 'user', content: 'Capital do Brasil?' }] });
  const out = await chatText(res);
  assert.equal(out.text, 'Brasília.');
  assert.equal(ai.calls.length, 2);
  assert.equal(ai.calls[1].max_tokens, undefined);
});

test('cota grátis compartilhada: perto do limite recusa conversa e imagem antes de chamar o Workers AI', async () => {
  const kv = new FakeKV();
  await kv.put(`neurons:${new Date().toISOString().slice(0, 10)}`, '9490');
  const ai = fakeAI({ image: b64(JPEG) });
  const env = makeEnv({ AI: ai, LILY_USAGE: kv });
  const chat = await callJson(env, 'POST', '/v1/chat', { model: 'free/gpt-oss-20b', messages: [{ role: 'user', content: 'oi' }] });
  assert.equal(chat.status, 429);
  assert.match(chat.body.error, /cota grátis de hoje/);
  const img = await callJson(env, 'POST', '/v1/image', { prompt: 'x' });
  assert.equal(img.status, 429);
  assert.equal(ai.calls.length, 0);
});

test('conversa grátis: resposta inteira (sem stream) vira pedaços com custo zero', async () => {
  const ai = fakeAI({ response: 'Resposta inteira.', usage: { prompt_tokens: 10, completion_tokens: 4 } });
  const { res } = await call(makeEnv({ AI: ai }), 'POST', '/v1/chat', { model: 'free/gpt-oss-20b', messages: [{ role: 'user', content: 'oi' }] });
  const out = await chatText(res);
  assert.equal(out.text, 'Resposta inteira.');
  assert.equal(out.usage.cost, 0);
});

