import { config } from './config.js';

export const backendReady = /^https:\/\/[a-z0-9-]+\.supabase\.co$/.test(config.supabaseUrl) && Boolean(config.supabasePublicKey);
export const participationReady = backendReady && Boolean(config.turnstileSiteKey);
export function status(element, message, kind = '') {
  element.textContent = message;
  element.className = `form-status ${kind}`.trim();
  element.hidden = false;
}
export function safeStorage(kind = 'localStorage') {
  return {
    get(key) { try { return window[kind].getItem(key); } catch { return null; } },
    set(key, value) { try { window[kind].setItem(key, value); return true; } catch { return false; } },
    remove(key) { try { window[kind].removeItem(key); } catch {} }
  };
}
export function sessionId() {
  const storage = safeStorage();
  let value = storage.get('ft-participation-session');
  if (!/^[a-f0-9]{64}$/.test(value || '')) {
    value = Array.from(crypto.getRandomValues(new Uint8Array(32)), n => n.toString(16).padStart(2, '0')).join('');
    if (!storage.set('ft-participation-session', value)) throw new Error('Permita o armazenamento deste site no navegador para participar.');
  }
  return value;
}
export async function request(path, { token, method = 'GET', body, headers: extra = {} } = {}) {
  if (!backendReady) throw new Error('O serviço ainda não está configurado. Tente novamente mais tarde.');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  try {
    const headers = { apikey: config.supabasePublicKey, ...extra };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${config.supabaseUrl}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal, credentials: 'omit', cache: 'no-store' });
    const text = await response.text();
    let data;
    try { data = text ? JSON.parse(text) : null; } catch { throw new Error('O serviço retornou uma resposta inesperada.'); }
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) throw new Error(data?.publicMessage || 'Acesso não autorizado ou sessão encerrada. Entre novamente.');
      if (response.status === 429) throw new Error('Muitas tentativas. Aguarde alguns minutos antes de tentar novamente.');
      throw new Error(data?.publicMessage || 'Não foi possível concluir. Tente novamente mais tarde.');
    }
    return data;
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('O serviço demorou para responder. Tente novamente.');
    if (error instanceof TypeError) throw new Error('Não foi possível conectar. Confira sua conexão e tente novamente.');
    throw error;
  } finally { clearTimeout(timer); }
}

let captchaPromise;
const captchaIds = new Map();
export async function mountCaptcha(targetId, action) {
  if (!participationReady) return;
  if (!captchaPromise) {
    captchaPromise = new Promise((resolve, reject) => {
      if (window.turnstile) return resolve();
      const script = document.createElement('script');
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
      script.async = true;
      script.onload = resolve;
      script.onerror = () => reject(new Error('Não foi possível carregar a verificação de segurança. Recarregue a página.'));
      document.head.append(script);
    });
  }
  await captchaPromise;
  if (captchaIds.has(targetId)) return;
  const widget = window.turnstile.render(`#${targetId}`, { sitekey: config.turnstileSiteKey, action, theme: 'light', 'response-field': false });
  captchaIds.set(targetId, widget);
}
export function captchaToken(targetId) {
  const widget = captchaIds.get(targetId);
  const token = widget === undefined ? '' : window.turnstile?.getResponse(widget);
  if (!token) throw new Error('Conclua a verificação de segurança antes de continuar.');
  return token;
}
export function resetCaptcha(targetId) {
  const widget = captchaIds.get(targetId);
  if (widget !== undefined) window.turnstile?.reset(widget);
}
export function renderText(tag, text, className = '') {
  const element = document.createElement(tag);
  element.textContent = text;
  if (className) element.className = className;
  return element;
}
