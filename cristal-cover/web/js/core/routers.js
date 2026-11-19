// Os roteadores da L.I.L.Y (os mesmos critérios do app Android): conversa, programação e vídeo.

export const PROFILES = {
  ECO: { label: 'Eco', detail: 'Rápidos e baratos' },
  BALANCED: { label: 'Equilibrado', detail: 'Os fortes do dia a dia' },
  QUALITY: { label: 'Qualidade', detail: 'Os de raciocínio máximo' },
};

export function typicalUsd(m) {
  return (2000 * m.priceIn + 800 * m.priceOut) / 1e6;
}

/** O modelo online para uma tarefa de texto (mesma regra do Router.pickRemote do app). */
export function pickRemote(models, profile, role = 'chat', light = false) {
  const withRole = models.filter((m) => (m.roles || []).includes(role));
  if (!withRole.length) return null;
  const order = light || profile === 'ECO' ? ['fast', 'strong', 'frontier'] : profile === 'QUALITY' ? ['frontier', 'strong', 'fast'] : ['strong', 'fast', 'frontier'];
  for (const tier of order) {
    const inTier = withRole.filter((m) => m.tier === tier);
    if (inTier.length) return inTier.reduce((a, b) => (typicalUsd(b) < typicalUsd(a) ? b : a));
  }
  return withRole.reduce((a, b) => (typicalUsd(b) < typicalUsd(a) ? b : a));
}

export const CODE_ROLES = {
  QUICK_FIX: { label: 'Correção simples', role: 'quick', tiers: ['fast', 'strong'] },
  GENERATE: { label: 'Escrever código', role: 'generate', tiers: ['strong', 'frontier'] },
  REFACTOR: { label: 'Refatoração', role: 'refactor', tiers: ['strong', 'frontier'] },
  DEBUG: { label: 'Depuração', role: 'debug', tiers: ['strong', 'frontier'] },
  ARCHITECTURE: { label: 'Arquitetura', role: 'architecture', tiers: ['frontier', 'strong'] },
  DOCS: { label: 'Documentação', role: 'docs', tiers: ['fast', 'strong'] },
  SECURITY: { label: 'Revisão de segurança', role: 'security', tiers: ['frontier', 'strong'] },
  REVIEW: { label: 'Revisão final', role: 'review', tiers: ['frontier', 'strong'] },
  EXPLAIN: { label: 'Explicar código', role: 'explain', tiers: ['fast', 'strong'] },
};

const RULES = [
  ['SECURITY', /segurança|seguran|vulnerab|security|injection|xss|csrf|segredo|secret|token vazad|permiss/i],
  ['ARCHITECTURE', /arquitetura|architecture|projetar|design do sistema|estrutura do projeto|módulos|camadas|escal/i],
  ['DEBUG', /erro|error|exception|crash|fecha|falha|bug|não funciona|nao funciona|stack ?trace|depura|debug|fails?|travando/i],
  ['REFACTOR', /refator|refactor|reorganiz|limp(ar|eza) (o )?código|simplific|extrair|renomear/i],
  ['DOCS', /document|readme|comentári|docstring|kdoc|javadoc|guia/i],
  ['EXPLAIN', /^(explique|explica|o que (faz|é)|como funciona|entender|explain|what does)/i],
  ['QUICK_FIX', /typo|corrigir (o )?nome|trocar (a |o )?(cor|texto|nome)|ajuste rápido|pequen/i],
];

export function classify(task, repair = false) {
  if (repair) return 'DEBUG';
  const t = String(task || '').trim();
  for (const [role, re] of RULES) if (re.test(t)) return role;
  return 'GENERATE';
}

/** Um modelo de programação (nunca vários sem precisar); revisão por outro fornecedor. */
export function pickCode(roleKey, models, profile, { manual = null, escalate = false, avoidVendor = null } = {}) {
  const role = CODE_ROLES[roleKey];
  if (manual) {
    const m = models.find((x) => x.id === manual);
    if (m) return { model: m, role: roleKey, reasons: [`Escolhido por você: ${label(m)}.`] };
  }
  let withRole = models.filter((m) => (m.roles || []).includes(role.role));
  if (!withRole.length) withRole = models;
  let pool = avoidVendor ? withRole.filter((m) => m.vendor !== avoidVendor) : withRole;
  if (!pool.length) pool = withRole;
  if (!pool.length) return null;
  let tiers = role.tiers;
  if (escalate || profile === 'QUALITY') tiers = ['frontier', ...tiers.filter((t) => t !== 'frontier')];
  if (profile === 'ECO' && !escalate && roleKey !== 'ARCHITECTURE' && roleKey !== 'SECURITY') tiers = ['fast', ...tiers.filter((t) => t !== 'fast')];
  for (const tier of tiers) {
    const inTier = pool.filter((m) => m.tier === tier);
    if (!inTier.length) continue;
    const sorted = [...inTier].sort((a, b) => (roleKey === 'QUICK_FIX' ? (a.speed === 'fast' ? 0 : 1) - (b.speed === 'fast' ? 0 : 1) : 0) || typicalUsd(a) - typicalUsd(b));
    const chosen = sorted[0];
    const reasons = [`${role.label}: nível ${tierName(tier)}${escalate ? ' (a tentativa anterior não resolveu)' : ''}.`];
    if (avoidVendor && chosen.vendor !== avoidVendor) reasons.push(`Revisão por outro fornecedor (${chosen.vendor}), independente de quem escreveu.`);
    reasons.push(`Entre os iguais, o de menor custo: ${label(chosen)}.`);
    return { model: chosen, role: roleKey, reasons };
  }
  const fallback = pool.reduce((a, b) => (typicalUsd(b) < typicalUsd(a) ? b : a));
  return { model: fallback, role: roleKey, reasons: [`${role.label}: o único disponível agora (${label(fallback)}).`] };
}

export function tierName(t) {
  return t === 'frontier' ? 'raciocínio máximo' : t === 'strong' ? 'forte' : 'rápido';
}

export function label(m) {
  const n = m.name || m.id;
  return n.includes(': ') ? n.slice(n.indexOf(': ') + 2) : n;
}

export const VIDEO_QUALITY = {
  FAST: { label: 'Rápido', detail: 'O mais barato e rápido para a tarefa' },
  BALANCED: { label: 'Equilibrado', detail: 'Boa qualidade sem o preço máximo' },
  BEST: { label: 'Máximo', detail: 'O melhor modelo disponível (mais caro)' },
};

function perSecond(m) {
  return m.fromUsd / Math.max(1, Math.min(...(m.durations?.length ? m.durations : [5])));
}

/** O modelo de vídeo pela tarefa (imagem? som?) e pelo nível pedido. */
export function pickVideo(models, needsImage, wantsAudio, quality) {
  const able = models.filter((m) => (needsImage ? m.modes.includes('image') : m.modes.includes('text')) && (!wantsAudio || m.audio));
  if (!able.length) return null;
  // Um modelo grátis que faz o pedido vem primeiro (Rápido e Equilibrado); o Máximo e o som vão aos pagos.
  const free = able.filter((m) => m.free);
  const paid = able.filter((m) => !m.free);
  if (free.length && (quality !== 'BEST' || !paid.length)) {
    const pick = [...free].sort((a, b) => b.quality - a.quality)[0];
    return { model: pick, reason: `${needsImage ? 'Anima a sua imagem' : 'Cria a partir da descrição'} · grátis: ${pick.name}` };
  }
  let chosen;
  if (quality === 'FAST') chosen = [...able].sort((a, b) => perSecond(a) - perSecond(b) || (a.speed === 'fast' ? -1 : 1))[0];
  else if (quality === 'BEST') chosen = [...able].sort((a, b) => b.quality - a.quality || perSecond(a) - perSecond(b))[0];
  else chosen = [...able].sort((a, b) => b.quality - 8 * perSecond(b) - (a.quality - 8 * perSecond(a)))[0];
  const reason = `${needsImage ? 'Anima a sua imagem' : 'Cria a partir da descrição'}${wantsAudio ? ', com som' : ''} · ${VIDEO_QUALITY[quality].label.toLowerCase()}: ${chosen.name}`;
  return { model: chosen, reason };
}
