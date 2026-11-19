// A L.I.L.Y Web como app de computador: instalar (Chrome e Edge criam o ícone na área de trabalho e no
// menu Iniciar, com janela própria), funcionar sem internet (service worker) e atalhos de teclado.

const listeners = new Set();
let deferred = null;
let waiting = null;

export const desktop = {
  /** O navegador ofereceu a instalação (Chrome/Edge no computador). */
  get canInstall() {
    return !!deferred;
  },
  /** Aberta como app instalado (janela própria). */
  get installed() {
    return matches('(display-mode: standalone)') || matches('(display-mode: window-controls-overlay)') || globalThis.navigator?.standalone === true;
  },
  /** Há uma versão nova pronta (entra quando a pessoa tocar em Atualizar). */
  get updateReady() {
    return !!waiting;
  },
  onChange(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  async install() {
    if (!deferred) return false;
    const prompt = deferred;
    deferred = null;
    prompt.prompt();
    const choice = await prompt.userChoice.catch(() => null);
    notify();
    return choice?.outcome === 'accepted';
  },
  /** Ativa a versão nova e recarrega (só quando a pessoa pede). */
  applyUpdate() {
    if (!waiting) return;
    globalThis.navigator.serviceWorker?.addEventListener('controllerchange', () => globalThis.location.reload(), { once: true });
    waiting.postMessage('atualizar');
  },
};

function matches(query) {
  try {
    return !!globalThis.matchMedia?.(query).matches;
  } catch {
    return false;
  }
}

function notify() {
  listeners.forEach((fn) => {
    try {
      fn(desktop);
    } catch {
      // um ouvinte com erro não atrapalha os outros
    }
  });
}

/** Liga a instalação e o modo sem internet. Seguro em qualquer navegador (sem suporte, não faz nada). */
export async function setupDesktop() {
  const w = globalThis;
  w.addEventListener?.('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferred = e;
    notify();
  });
  w.addEventListener?.('appinstalled', () => {
    deferred = null;
    notify();
  });
  const sw = w.navigator?.serviceWorker;
  if (!sw || !w.isSecureContext) return null;
  try {
    const reg = await sw.register('sw.js', { scope: './' });
    const track = (worker) => {
      if (!worker) return;
      worker.addEventListener('statechange', () => {
        if (worker.state === 'installed' && sw.controller) {
          waiting = worker;
          notify();
        }
      });
    };
    if (reg.waiting && sw.controller) {
      waiting = reg.waiting;
      notify();
    }
    track(reg.installing);
    reg.addEventListener('updatefound', () => track(reg.installing));
    // Procura versão nova de hora em hora com o app aberto.
    setInterval(() => reg.update().catch(() => {}), 60 * 60 * 1000);
    return reg;
  } catch {
    return null;
  }
}

/**
 * Atalhos de teclado do app no computador. handlers: { newChat, goTo(index), focusInput, stop }.
 * Ctrl/⌘+K nova conversa · Alt+1…5 telas · / escrever · Esc parar a resposta.
 */
export function keyboardShortcuts(handlers) {
  const onKey = (e) => {
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(e.target?.tagName || '') || e.target?.isContentEditable;
    if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      handlers.newChat?.();
    } else if (e.altKey && !e.ctrlKey && !e.metaKey && /^[1-5]$/.test(e.key)) {
      e.preventDefault();
      handlers.goTo?.(Number(e.key) - 1);
    } else if (e.key === '/' && !typing && !e.ctrlKey && !e.metaKey && !e.altKey) {
      e.preventDefault();
      handlers.focusInput?.();
    } else if (e.key === 'Escape') {
      if (handlers.stop?.()) e.preventDefault();
    }
  };
  globalThis.addEventListener?.('keydown', onKey);
  return () => globalThis.removeEventListener?.('keydown', onKey);
}

export const SHORTCUTS = [
  ['Ctrl + K', 'Nova conversa'],
  ['Alt + 1 a 5', 'Conversa, Codex, Vídeo, Ferramentas, Configurações'],
  ['/', 'Escrever uma mensagem'],
  ['Esc', 'Parar a resposta'],
  ['Enter / Shift + Enter', 'Enviar / nova linha'],
];
