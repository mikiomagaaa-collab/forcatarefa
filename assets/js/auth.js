import { request, safeStorage } from './api.js';
const storage = safeStorage('sessionStorage');
let refreshPromise;
export function readSession() {
  try { return JSON.parse(storage.get('ft-admin-session') || 'null'); } catch { return null; }
}
export function saveSession(data) {
  if (!data?.access_token || !data?.refresh_token) throw new Error('A autenticação não retornou uma sessão válida.');
  const session = { access_token: data.access_token, refresh_token: data.refresh_token, expires_at: Math.floor(Date.now() / 1000) + data.expires_in };
  if (!storage.set('ft-admin-session', JSON.stringify(session))) throw new Error('Permita o armazenamento da sessão neste navegador para entrar.');
  return session;
}
export async function adminToken() {
  const session = readSession();
  if (!session) throw new Error('Entre com uma conta autorizada para acessar a gestão.');
  if (session.expires_at > Date.now() / 1000 + 60) return session.access_token;
  if (!refreshPromise) {
    refreshPromise = request('/auth/v1/token?grant_type=refresh_token', { method: 'POST', body: { refresh_token: session.refresh_token } })
      .then(saveSession).catch(error => { storage.remove('ft-admin-session'); throw error; }).finally(() => { refreshPromise = null; });
  }
  return (await refreshPromise).access_token;
}
export async function adminRequest(path, options = {}) {
  return request(path, { ...options, token: await adminToken() });
}
export async function signOut() {
  try { const session = readSession(); if (session) await request('/auth/v1/logout', { method: 'POST', token: session.access_token }); }
  finally { storage.remove('ft-admin-session'); }
}
