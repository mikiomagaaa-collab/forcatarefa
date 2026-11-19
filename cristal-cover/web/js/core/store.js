// O que a L.I.L.Y Web guarda: tudo neste navegador (localStorage). Nada vai para servidores do projeto.

const PREFIX = 'lily.';

export function load(key, fallback) {
  try {
    const v = globalThis.localStorage?.getItem(PREFIX + key);
    return v == null ? fallback : JSON.parse(v);
  } catch {
    return fallback;
  }
}

export function save(key, value) {
  try {
    globalThis.localStorage?.setItem(PREFIX + key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

export function remove(key) {
  try {
    globalThis.localStorage?.removeItem(PREFIX + key);
  } catch {
    // nada
  }
}

export const DEFAULT_SETTINGS = {
  gatewayUrl: '',
  rememberKey: false,
  consent: false,
  online: 'FALLBACK',
  profile: 'BALANCED',
  theme: 'system',
  localModel: null,
  memoryEnabled: true,
  speakAnswers: false,
  avatar: { enabled: true, style: 'HOLO', presentation: 'BUST', hue: -1, motion: 'FULL' },
  codexWeb: true,
  codexReview: true,
  codexAuto: false,
};

export function settings() {
  return { ...DEFAULT_SETTINGS, ...load('settings', {}), avatar: { ...DEFAULT_SETTINGS.avatar, ...(load('settings', {}).avatar || {}) } };
}

export function saveSettings(s) {
  save('settings', s);
}

/** A chave de acesso fica só nesta aba, a não ser que a pessoa peça para lembrar neste computador. */
export function accessKey() {
  try {
    return globalThis.sessionStorage?.getItem(PREFIX + 'key') || globalThis.localStorage?.getItem(PREFIX + 'key') || '';
  } catch {
    return '';
  }
}

export function setAccessKey(key, remember) {
  try {
    globalThis.sessionStorage?.setItem(PREFIX + 'key', key);
    if (remember) globalThis.localStorage?.setItem(PREFIX + 'key', key);
    else globalThis.localStorage?.removeItem(PREFIX + 'key');
  } catch {
    // nada
  }
}

export function clearAccessKey() {
  try {
    globalThis.sessionStorage?.removeItem(PREFIX + 'key');
    globalThis.localStorage?.removeItem(PREFIX + 'key');
  } catch {
    // nada
  }
}

export function uid() {
  return Math.random().toString(36).slice(2, 10) + Date.now().toString(36);
}

export const conversations = {
  list: () => load('conversas', []),
  get: (id) => load('conversa.' + id, null),
  put(conv) {
    save('conversa.' + conv.id, conv);
    const list = conversations.list().filter((c) => c.id !== conv.id);
    list.unshift({ id: conv.id, title: conv.title, updated: Date.now() });
    save('conversas', list.slice(0, 200));
  },
  remove(id) {
    remove('conversa.' + id);
    save('conversas', conversations.list().filter((c) => c.id !== id));
  },
};

export const memories = {
  list: () => load('memorias', []),
  add(text) {
    const t = String(text).trim();
    // Como no app: nada de senhas, documentos ou cartões.
    if (!t || /senha|password|cpf|\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b|\b(?:\d[ -]*?){13,19}\b|token|c[oó]digo de verifica/i.test(t)) return false;
    save('memorias', [{ id: uid(), text: t.slice(0, 500), at: Date.now() }, ...memories.list()].slice(0, 100));
    return true;
  },
  remove(id) {
    save('memorias', memories.list().filter((m) => m.id !== id));
  },
};

export const videos = {
  list: () => load('videos', []),
  put(job) {
    save('videos', [job, ...videos.list().filter((j) => j.id !== job.id)].slice(0, 60));
  },
  remove(id) {
    save('videos', videos.list().filter((j) => j.id !== id));
  },
};

/** Erros desta página (o "Diagnóstico" da versão web). */
export const diagnostics = {
  list: () => load('diagnostico', []),
  add(entry) {
    save('diagnostico', [{ at: new Date().toISOString(), ...entry }, ...diagnostics.list()].slice(0, 50));
  },
  clear: () => save('diagnostico', []),
};
