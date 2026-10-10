import ts from 'typescript';
import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir, rm } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const runtime = resolve(root, '.test-runtime');
await mkdir(runtime, { recursive: true });
const env = {
  SUPABASE_URL: 'https://test-only.supabase.co', SUPABASE_ANON_KEY: 'TEST_ONLY_PUBLIC_KEY',
  SUPABASE_SERVICE_ROLE_KEY: 'TEST_ONLY_SERVER_KEY', PUBLIC_SITE_ANON_KEY: 'TEST_ONLY_SITE_PUBLIC_KEY', ALLOWED_ORIGINS: 'https://test-only.example',
  ADMIN_EMAIL: 'test-only@example.invalid', TURNSTILE_SECRET_KEY: 'TEST_ONLY_CAPTCHA_SECRET', ABUSE_HASH_SECRET: 'TEST_ONLY_HASH_SECRET_NOT_FOR_PRODUCTION_32_BYTES'
};
let handler; let counter = 0; let sessionBlocked = false; let authAllowed = true; let insertFailed = false; let userChecks = 0;
const buckets = new Map(); const seenCaptcha = new Set(); const intentions = new Set(); const suggestions = [];
const realFetch = globalThis.fetch;
globalThis.Deno = { env: { get: key => env[key] }, serve: callback => { handler = callback; } };
globalThis.fetch = async (url, options = {}) => {
  const body = options.body ? JSON.parse(options.body) : {};
  let value = null; let code = 200;
  if (url.includes('siteverify')) {
    const success = body.response?.startsWith('VALID:') && !seenCaptcha.has(body.response);
    seenCaptcha.add(body.response);
    value = { success, action: body.response?.split(':')[1], hostname: 'test-only.example' };
  } else if (url.includes('/auth/v1/user')) { userChecks++; code=401; value={}; }
  else if (url.includes('/auth/v1/token')) value = { access_token: 'TEST_TOKEN', refresh_token: 'TEST_REFRESH', expires_in: 3600 };
  else if (url.includes('/rpc/is_admin')) value = authAllowed;
  else if (url.includes('/rpc/consume_rate')) { const hits = (buckets.get(body.bucket_key) || 0) + 1; buckets.set(body.bucket_key, hits); value = hits <= body.max_hits; }
  else if (url.includes('/session_suspensions?')) value = sessionBlocked ? [{ session_hash: 'blocked' }] : [];
  else if (url.includes('/rpc/record_intention')) { value = !intentions.has(body.target_hash); intentions.add(body.target_hash); }
  else if (url.includes('/rpc/record_site_visit')) value=true;
  else if (url.endsWith('/suggestions')) { if (insertFailed) code = 500; else suggestions.push(body); }
  return new Response(value === null ? '' : JSON.stringify(value), { status: code });
};
async function compile(file, target) {
  await mkdir(dirname(target), { recursive: true });
  const input = await readFile(file, 'utf8');
  const result = ts.transpileModule(input, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } });
  await writeFile(target, result.outputText.replaceAll("../_shared/security.ts", "../_shared/security.js"));
}
async function load(name) {
  const target = resolve(runtime, name, 'index.js');
  await compile(resolve(root, 'supabase/functions', name, 'index.ts'), target);
  await import(pathToFileURL(target).href); return handler;
}
function input(action, extra = {}) {
  return { session: String(++counter).padStart(64,'0'), captcha: `VALID:${action}:${counter}`, ...extra };
}
async function call(fn, body, options = {}) {
  const response = await fn(new Request('https://function.example/test', { method: options.method || 'POST', headers: { origin: options.origin || 'https://test-only.example', 'Content-Type': 'application/json', 'x-forwarded-for': '192.0.2.1', ...(options.authorization ? {Authorization:options.authorization} : {}) }, body: options.method === 'OPTIONS' ? undefined : JSON.stringify(body) }));
  return { code: response.status, data: response.status === 204 ? null : await response.json() };
}
let checks = 0;
try {
  await compile(resolve(root, 'supabase/functions/_shared/security.ts'), resolve(runtime,'_shared/security.js'));
  const suggest = await load('submit-suggestion');
  const base = { name:'',classroom:'',category:'Outro',message:'Uma sugestão de teste',website:'' };
  let result = await call(suggest, input('suggestion',base)); assert.equal(result.code,201); assert.equal(result.data.received,true); assert.equal(suggestions.length,1); checks+=3;
  result = await call(suggest,input('suggestion',{...base,message:'<script>alert(1)</script>'})); assert.equal(result.code,400); assert.equal(suggestions.length,1); checks+=2;
  result = await call(suggest,input('suggestion',{...base,category:'Categoria falsa'})); assert.equal(result.code,400); checks++;
  result = await call(suggest,input('suggestion',{...base,website:'spam'})); assert.equal(result.code,400); checks++;
  result = await call(suggest,input('suggestion',base),{origin:'https://attacker.example'}); assert.equal(result.code,403); checks++;
  result = await call(suggest,input('suggestion',{...base,captcha:'INVALID'})); assert.equal(result.code,400); checks++;
  result = await call(suggest,input('suggestion',{...base,message:'x'.repeat(21000)})); assert.equal(result.code,413); checks++;
  insertFailed=true; result=await call(suggest,input('suggestion',base)); assert.equal(result.code,503); assert.notEqual(result.data.received,true); insertFailed=false; checks+=2;
  sessionBlocked=true; result=await call(suggest,input('suggestion',base)); assert.equal(result.code,403); sessionBlocked=false; checks++;
  const replay=input('suggestion',base); result=await call(suggest,replay); assert.equal(result.code,201); result=await call(suggest,replay); assert.equal(result.code,400); checks+=2;
  const vote=await load('register-intention'); const first=input('intention'); result=await call(vote,first); assert.equal(result.data.recorded,true); checks++;
  result=await call(vote,{...first,captcha:`VALID:intention:unique-${++counter}`}); assert.equal(result.data.recorded,false); assert.equal(intentions.size,1); checks+=2;
  const limited=input('intention'); for(let i=0;i<3;i++) await call(vote,{...limited,captcha:`VALID:intention:rate-${++counter}`}); result=await call(vote,{...limited,captcha:`VALID:intention:rate-${++counter}`}); assert.equal(result.code,429); checks++;
  const login=await load('admin-login'); result=await call(login,input('admin_login',{password:'short'})); assert.equal(result.code,400); checks++;
  authAllowed=false; result=await call(login,input('admin_login',{password:'TEST_ONLY_STRONG_PASSWORD'})); assert.equal(result.code,403); checks++;
  authAllowed=true; result=await call(login,input('admin_login',{password:'TEST_ONLY_STRONG_PASSWORD'})); assert.equal(result.code,200); assert.equal(result.data.access_token,'TEST_TOKEN'); checks+=2;
  result=await call(login,{}, {method:'OPTIONS'}); assert.equal(result.code,204); checks++;
  assert.equal(suggestions.every(row=>!row.session_hash.includes('192.0.2.1')&&row.session_hash.length===64),true); checks++;
  result=await call(vote,input('intention'),{authorization:`Bearer ${env.SUPABASE_ANON_KEY}`}); assert.equal(result.code,200); assert.equal(userChecks,0); checks+=2;
  result=await call(vote,input('intention'),{authorization:`Bearer ${env.PUBLIC_SITE_ANON_KEY}`}); assert.equal(result.code,200); assert.equal(userChecks,0); checks+=2;
  result=await call(login,input('admin_login',{password:'TEST_ONLY_STRONG_PASSWORD'}),{authorization:`Bearer ${env.PUBLIC_SITE_ANON_KEY}`}); assert.equal(result.code,200); assert.equal(userChecks,0); checks+=2;
  result=await call(vote,input('intention'),{authorization:'Bearer INVALID_USER_TOKEN'}); assert.equal(result.code,401); assert.equal(userChecks,1); checks+=2;
  const visit=await load('record-visit');const visitInput={visit:'00000000-0000-4000-8000-000000000111',browser:'00000000-0000-4000-8000-000000000222',page:'index.html',seconds:0};
  result=await call(visit,visitInput);assert.equal(result.code,200);assert.equal(result.data.received,true);checks+=2;
  for(const changes of [{seconds:-1},{seconds:999999},{seconds:1.5},{page:'admin/index.html'},{browser:'private-name'},{visit:'invalid'}]){result=await call(visit,{...visitInput,...changes});assert.equal(result.code,400);checks++;}
  result=await call(visit,visitInput,{origin:'https://attacker.example'});assert.equal(result.code,403);checks++;
  result=await call(visit,{}, {method:'OPTIONS'});assert.equal(result.code,204);checks++;
  result=await call(visit,{...visitInput,extra:'x'.repeat(2100)});assert.equal(result.code,413);checks++;
  for(let i=0;i<23;i++)await call(visit,visitInput);result=await call(visit,visitInput);assert.equal(result.code,429);checks++;
  console.log(`${checks} verificações das funções aprovadas, usando serviços simulados: validação, CAPTCHA, falhas, duplicação, limites e login autorizado.`);
} finally {
  globalThis.fetch=realFetch; delete globalThis.Deno;
  await rm(runtime,{recursive:true,force:true});
}
