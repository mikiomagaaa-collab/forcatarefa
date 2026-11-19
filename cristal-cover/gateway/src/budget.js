// Limite de gasto diário. Com o KV ligado (binding LILY_USAGE), o gasto do dia fica guardado e um
// pedido que passaria do limite é recusado antes de chegar ao provedor. Sem o KV, o limite não pode
// ser garantido: o /v1/status avisa.

import { GatewayError } from './http.js';

function today() {
  return new Date().toISOString().slice(0, 10);
}

export function dailyLimit(env) {
  const n = Number(env.DAILY_BUDGET_USD ?? '3');
  return Number.isFinite(n) && n >= 0 ? n : 3;
}

export async function spentToday(env) {
  if (!env.LILY_USAGE) return null;
  const v = await env.LILY_USAGE.get(`spent:${today()}`);
  return v ? Number(v) || 0 : 0;
}

/** Recusa se [estimate] (US$) passaria do limite do dia. */
export async function ensureBudget(env, estimate) {
  const spent = await spentToday(env);
  if (spent == null) return;
  const limit = dailyLimit(env);
  if (spent + Math.max(0, estimate) > limit) {
    throw new GatewayError(
      402,
      `Limite diário do gateway atingido (US$ ${spent.toFixed(2)} de US$ ${limit.toFixed(2)}). Aumente DAILY_BUDGET_USD ou espere até amanhã (UTC).`,
    );
  }
}

export async function addSpend(env, usd) {
  if (!env.LILY_USAGE || !(usd > 0)) return;
  const key = `spent:${today()}`;
  const current = Number((await env.LILY_USAGE.get(key)) || 0) || 0;
  // Guarda por 3 dias (o histórico curto basta para o /v1/status).
  await env.LILY_USAGE.put(key, String(+(current + usd).toFixed(6)), { expirationTtl: 3 * 86400 });
}
