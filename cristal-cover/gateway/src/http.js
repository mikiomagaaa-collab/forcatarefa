// Utilidades HTTP do gateway: respostas JSON, erros com mensagem para o app e o fetch injetável
// (os testes trocam o fetch por provedores simulados).

export class GatewayError extends Error {
  constructor(status, message, detail) {
    super(message);
    this.status = status;
    this.publicMessage = message;
    this.detail = detail;
  }
}

export function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...headers },
  });
}

/** O fetch usado para falar com os provedores (os testes injetam um falso em env.FETCH). */
export function upstream(env) {
  return env.FETCH || fetch;
}

export async function readJson(request, limit = 6_000_000) {
  const text = await request.text();
  if (text.length > limit) throw new GatewayError(413, 'Pedido grande demais.');
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch {
    throw new GatewayError(400, 'O pedido não é um JSON válido.');
  }
}

/** Erro de um provedor convertido numa mensagem que a pessoa entende (sem vazar chaves). */
export async function providerError(provider, res) {
  let detail = '';
  try {
    detail = (await res.text()).slice(0, 400);
  } catch {
    detail = '';
  }
  const status = res.status;
  const message =
    status === 401 || status === 403
      ? `${provider} recusou a chave configurada no gateway.`
      : status === 402
        ? `${provider} está sem créditos. Adicione créditos na conta do provedor.`
        : status === 429
          ? `${provider} pediu para esperar (limite de uso). Tente de novo em instantes.`
          : status === 404
            ? `${provider} não encontrou o modelo ou o recurso pedido.`
            : `${provider} respondeu com erro ${status}.`;
  return new GatewayError(status >= 500 ? 502 : status, message, detail);
}

/** Comparação de tempo constante (a chave de acesso não vaza pelo tempo de resposta). */
export function sameSecret(given, expected) {
  if (typeof given !== 'string' || typeof expected !== 'string' || !expected) return false;
  let diff = given.length === expected.length ? 0 : 1;
  for (let i = 0; i < expected.length; i++) diff |= (given.charCodeAt(i) || 0) ^ expected.charCodeAt(i);
  return diff === 0;
}

export function base64url(text) {
  const bytes = new TextEncoder().encode(text);
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function fromBase64url(id) {
  const b64 = id.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((id.length + 3) % 4);
  const bin = atob(b64);
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
