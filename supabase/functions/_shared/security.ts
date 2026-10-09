export class PublicError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
const env = (key: string) => {
  const value = Deno.env.get(key);
  if (!value) throw new PublicError(503, 'O serviço está temporariamente indisponível.');
  return value;
};
export function headers(origin: string) {
  return { 'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Headers': 'content-type,apikey,authorization', 'Access-Control-Allow-Methods': 'POST,OPTIONS', 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'Vary': 'Origin', 'X-Content-Type-Options': 'nosniff' };
}
export function reply(origin: string, data: unknown, code = 200) { return new Response(JSON.stringify(data), { status: code, headers: headers(origin) }); }
export async function db(path: string, body?: unknown, method = body === undefined ? 'GET' : 'POST', userToken?: string) {
  const key = env('SUPABASE_SERVICE_ROLE_KEY');
  const response = await fetch(`${env('SUPABASE_URL')}/rest/v1/${path}`, {
    method,
    headers: { apikey: key, Authorization: `Bearer ${userToken || key}`, 'Content-Type': 'application/json', Prefer: 'return=minimal' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10000)
  });
  if (!response.ok) throw new PublicError(503, 'Não foi possível concluir. Tente novamente mais tarde.');
  const text = await response.text(); return text ? JSON.parse(text) : null;
}
async function digest(value: string) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey('raw', encoder.encode(env('ABUSE_HASH_SECRET')), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign('HMAC', key, encoder.encode(value));
  return Array.from(new Uint8Array(signature), value => value.toString(16).padStart(2, '0')).join('');
}
export function plain(value: unknown, min: number, max: number, field: string) {
  if (typeof value !== 'string') throw new PublicError(400, `Confira o campo ${field}.`);
  const text = value.trim();
  if (text.length < min || text.length > max || /[<>\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(text)) throw new PublicError(400, `Confira o campo ${field}; não envie HTML ou scripts.`);
  return text;
}
async function log(type: string, hash: string) {
  const allowed = await db('rpc/consume_rate', { bucket_key: `log:${type}:${hash}`, max_hits: 1, window_seconds: 600 });
  if (allowed) await db('security_events', { event_type: type, session_hash: hash });
}
export async function prepare(req: Request, action: string, maxSession: number) {
  const origin = req.headers.get('origin') || '';
  const allowed = env('ALLOWED_ORIGINS').split(',').map(value => value.trim());
  if (!allowed.includes(origin)) throw new PublicError(403, 'Origem não autorizada.');
  if (req.method === 'OPTIONS') return { response: new Response(null, { status: 204, headers: headers(origin) }) };
  if (req.method !== 'POST') throw new PublicError(405, 'Método não permitido.');
  if (!req.headers.get('content-type')?.startsWith('application/json')) throw new PublicError(415, 'Formato inválido.');
  if (Number(req.headers.get('content-length') || 0) > 20000) throw new PublicError(413, 'Mensagem muito grande.');
  const reader = req.body?.getReader();
  if (!reader) throw new PublicError(400, 'Dados inválidos.');
  const chunks: Uint8Array[] = []; let size = 0;
  while (true) {
    const { value, done } = await reader.read(); if (done) break;
    size += value.length;
    if (size > 20000) { await reader.cancel(); throw new PublicError(413, 'Mensagem muito grande.'); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  const raw = new TextDecoder().decode(bytes);
  let input;
  try { input = JSON.parse(raw); } catch { throw new PublicError(400, 'Dados inválidos.'); }
  if (!input || typeof input !== 'object' || Array.isArray(input) || typeof input.session !== 'string' || !/^[a-f0-9]{64}$/.test(input.session)) throw new PublicError(400, 'Sessão inválida. Recarregue a página.');
  const hash = await digest(`session:${input.session}`);
  const ip = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() || 'unknown';
  const ipHash = await digest(`ip:${ip}`);
  const sessionAllowed = await db('rpc/consume_rate', { bucket_key: `${action}:session:${hash}`, max_hits: maxSession, window_seconds: 600 });
  const ipAllowed = await db('rpc/consume_rate', { bucket_key: `${action}:ip:${ipHash}`, max_hits: action === 'admin_login' ? 10 : 1000, window_seconds: 600 });
  if (!sessionAllowed || !ipAllowed) { await log('rate_limited', hash); throw new PublicError(429, 'Muitas tentativas. Aguarde alguns minutos.'); }
  const blocks = await db(`session_suspensions?session_hash=eq.${hash}&suspended_until=gt.${encodeURIComponent(new Date().toISOString())}&select=session_hash`);
  if (blocks.length) { await log('suspended', hash); throw new PublicError(403, 'Os envios desta sessão estão temporariamente suspensos.'); }
  if (typeof input.captcha !== 'string' || input.captcha.length > 2048) throw new PublicError(400, 'Conclua a verificação de segurança.');
  const response = await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ secret: env('TURNSTILE_SECRET_KEY'), response: input.captcha, remoteip: ip === 'unknown' ? undefined : ip }), signal: AbortSignal.timeout(10000)
  });
  if (!response.ok) throw new PublicError(503, 'A verificação de segurança está indisponível.');
  const verification = await response.json();
  const hosts = allowed.map(url => new URL(url).hostname);
  if (!verification.success || verification.action !== action || !hosts.includes(verification.hostname)) { await log('invalid_captcha', hash); throw new PublicError(400, 'A verificação expirou ou não é válida. Tente novamente.'); }
  let userId = null;
  const authorization = req.headers.get('authorization');
  if (authorization) {
    const userResponse = await fetch(`${env('SUPABASE_URL')}/auth/v1/user`, { headers: { apikey: env('SUPABASE_ANON_KEY'), Authorization: authorization }, signal: AbortSignal.timeout(10000) });
    if (!userResponse.ok) throw new PublicError(401, 'Sessão expirada. Entre novamente.');
    const user = await userResponse.json(); userId = user.id;
    const controls = await db(`account_controls?user_id=eq.${userId}&select=blocked,suggestions_suspended_until`);
    if (controls[0]?.blocked || (action === 'suggestion' && Date.parse(controls[0]?.suggestions_suspended_until || '') > Date.now())) throw new PublicError(403, 'Os envios desta conta estão suspensos.');
  }
  return { input, hash, origin, userId };
}
export function serve(handler: (req: Request) => Promise<Response>) {
  Deno.serve(async req => {
    try { return await handler(req); }
    catch (error) {
      const origin = req.headers.get('origin') || '';
      const allowed = (Deno.env.get('ALLOWED_ORIGINS') || '').split(',').map(value => value.trim());
      return reply(allowed.includes(origin) ? origin : 'null', { publicMessage: error instanceof PublicError ? error.message : 'O serviço está temporariamente indisponível. Tente novamente.' }, error instanceof PublicError ? error.status : 503);
    }
  });
}
export { env, log };
