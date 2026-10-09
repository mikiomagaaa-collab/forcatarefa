import { serve, prepare, env, reply, PublicError, log } from '../_shared/security.ts';
serve(async req => {
  const state = await prepare(req, 'admin_login', 5);
  if (state.response) return state.response;
  const { input, hash, origin } = state;
  if (typeof input.password !== 'string' || input.password.length < 12 || input.password.length > 128) throw new PublicError(400, 'Utilize a senha forte da conta autorizada.');
  const email = input.email || env('ADMIN_EMAIL');
  if (typeof email !== 'string' || email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new PublicError(400, 'Informe uma conta válida.');
  const response = await fetch(`${env('SUPABASE_URL')}/auth/v1/token?grant_type=password`, {
    method: 'POST', headers: { apikey: env('SUPABASE_ANON_KEY'), 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: input.password }), signal: AbortSignal.timeout(10000)
  });
  if (!response.ok) { await log('login_denied', hash); throw new PublicError(401, 'Não foi possível autenticar a conta autorizada.'); }
  const session = await response.json();
  const authorization = await fetch(`${env('SUPABASE_URL')}/rest/v1/rpc/is_admin`, {
    method: 'POST', headers: { apikey: env('SUPABASE_ANON_KEY'), Authorization: `Bearer ${session.access_token}`, 'Content-Type': 'application/json' }, body: '{}', signal: AbortSignal.timeout(10000)
  });
  if (!authorization.ok || await authorization.json() !== true) throw new PublicError(403, 'Esta conta não está autorizada a administrar o site.');
  return reply(origin, { access_token: session.access_token, refresh_token: session.refresh_token, expires_in: session.expires_in });
});
