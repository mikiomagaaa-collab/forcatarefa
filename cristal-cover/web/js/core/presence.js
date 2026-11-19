// Presença da L.I.L.Y (as mesmas regras do app): cada módulo informa o que está fazendo e a avatar
// mostra o mais importante. Sucesso e erro aparecem por alguns segundos.

export const MODES = {
  IDLE: 'Pronta',
  LISTENING: 'Ouvindo',
  THINKING: 'Pensando',
  SPEAKING: 'Falando',
  WORKING: 'Trabalhando',
  SUCCESS: 'Concluído',
  ERROR: 'Algo deu errado',
};

const RANK = { LISTENING: 7, SPEAKING: 6, ERROR: 5, SUCCESS: 4, THINKING: 3, WORKING: 2, IDLE: 0 };
export const TRANSIENT_MS = 2600;

export class Presence {
  constructor(clock = () => Date.now()) {
    this.clock = clock;
    this.entries = new Map();
    this.listeners = new Set();
    this.state = { mode: 'IDLE', label: null, progress: null, level: 0, source: null, active: 0 };
    this.timer = null;
  }

  subscribe(fn) {
    this.listeners.add(fn);
    fn(this.state);
    return () => this.listeners.delete(fn);
  }

  put(source, p, until = null) {
    const prev = this.entries.get(source);
    const since = prev && prev.p.mode === p.mode ? prev.since : this.clock();
    this.entries.set(source, { p: { ...p, source }, since, until });
    this.recompute();
  }

  listening(source, level = 0) {
    this.put(source, { mode: 'LISTENING', label: 'Ouvindo', level: Math.max(0, Math.min(1, level)) });
  }
  speaking(source, level) {
    this.put(source, { mode: 'SPEAKING', label: 'Falando', level: Math.max(0, Math.min(1, level)) });
  }
  thinking(source, label = 'Pensando') {
    this.put(source, { mode: 'THINKING', label });
  }
  working(source, label, progress = null) {
    this.put(source, { mode: 'WORKING', label, progress: progress == null ? null : Math.max(0, Math.min(1, progress)) });
  }
  finished(source, success, label) {
    const now = this.clock();
    this.entries.set(source, { p: { mode: success ? 'SUCCESS' : 'ERROR', label: label || (success ? 'Concluído' : 'Não deu certo'), source }, since: now, until: now + TRANSIENT_MS });
    this.recompute();
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.recompute(), TRANSIENT_MS + 20);
  }
  clear(source) {
    if (this.entries.delete(source)) this.recompute();
  }

  recompute() {
    const now = this.clock();
    for (const [k, e] of this.entries) if (e.until != null && e.until <= now) this.entries.delete(k);
    const live = [...this.entries.values()];
    const top = live.sort((a, b) => RANK[b.p.mode] - RANK[a.p.mode] || a.since - b.since)[0];
    const active = live.filter((e) => e.p.mode === 'WORKING' || e.p.mode === 'THINKING').length;
    this.state = top ? { mode: 'IDLE', label: null, progress: null, level: 0, ...top.p, active } : { mode: 'IDLE', label: null, progress: null, level: 0, source: null, active: 0 };
    this.listeners.forEach((fn) => fn(this.state));
  }
}

// --- Expressões (as mesmas do app) -------------------------------------------------------------

export function target(mode) {
  const base = { eyeOpen: 1, smile: 0.35, browRaise: 0, browTilt: 0, gazeX: 0, gazeY: 0, tilt: 0, happyEyes: 0, squint: 0 };
  switch (mode) {
    case 'LISTENING': return { ...base, eyeOpen: 1.12, smile: 0.2, browRaise: 0.35, gazeY: 0.05, tilt: -5 };
    case 'THINKING': return { ...base, eyeOpen: 0.95, smile: 0.05, browRaise: 0.45, browTilt: 0.15, gazeX: -0.65, gazeY: -0.7, tilt: 3 };
    case 'SPEAKING': return { ...base, eyeOpen: 1.02, smile: 0.4, browRaise: 0.15 };
    case 'WORKING': return { ...base, eyeOpen: 0.88, smile: 0.1, browRaise: -0.15, gazeY: 0.35, squint: 0.45 };
    case 'SUCCESS': return { ...base, smile: 1, browRaise: 0.4, happyEyes: 1, tilt: 4 };
    case 'ERROR': return { ...base, eyeOpen: 0.9, smile: -0.55, browRaise: 0.2, browTilt: 1, gazeX: 0.15, gazeY: 0.55, tilt: -2 };
    default: return base;
  }
}

function hash(n, seed) {
  let x = BigInt.asIntN(64, BigInt(n) * 374761393n + BigInt(seed) * 668265263n);
  x = BigInt.asIntN(64, (x ^ BigInt.asUintN(64, x) >> 13n) * 1274126177n);
  return Number((x ^ BigInt.asUintN(64, x) >> 16n) & 0xffffn) / 0xffff;
}

export function blink(t, seed = 7) {
  const win = 4200;
  const slot = Math.floor(t / win);
  const at = Math.floor(hash(slot, seed) * 3000) + 600;
  const local = t - slot * win;
  const shape = (start) => {
    const d = local - start;
    return d < 0 || d > 170 ? 0 : Math.sin((Math.PI * d) / 170);
  };
  return Math.max(shape(at), hash(slot, seed + 1) > 0.78 ? shape(at + 260) : 0);
}

export const breathing = (t) => Math.sin((2 * Math.PI * t) / 4800);

export function mouthOpen(mode, level, t) {
  if (mode !== 'SPEAKING') return 0;
  const syl = Math.abs(Math.sin((2 * Math.PI * t) / 165));
  return (0.18 + 0.82 * Math.max(0, Math.min(1, level))) * (0.35 + 0.65 * syl);
}
