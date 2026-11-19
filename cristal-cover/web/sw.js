// Service worker da L.I.L.Y Web: deixa o app instalável no computador e abrindo sem internet.
//
// - O "casco" do app (HTML, CSS, JavaScript, ícones e documentos) fica guardado no aparelho e é
//   atualizado em segundo plano. Uma versão nova só entra quando a pessoa toca em "Atualizar" (nunca
//   recarrega no meio de uma conversa).
// - Nada do serviço online passa por aqui: /v1/* (conversa, vídeo, Codex) sempre vai direto à rede e
//   nunca fica guardado. Os modelos do navegador (WebLLM) têm o cache próprio da biblioteca.

const VERSION = 'lily-web-1.4.1';
const SHELL = [
  './',
  'index.html',
  'styles.css',
  'icon.svg',
  'manifest.webmanifest',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/maskable-512.png',
  'js/app.js',
  'js/core/agent.js',
  'js/core/desktop.js',
  'js/core/diff.js',
  'js/core/gateway.js',
  'js/core/languages.js',
  'js/core/local.js',
  'js/core/markdown.js',
  'js/core/presence.js',
  'js/core/routers.js',
  'js/core/store.js',
  'js/ui/avatar.js',
  'js/ui/codex.js',
  'js/ui/dom.js',
  'js/ui/settings.js',
  'js/ui/video.js',
  'legal/TERMOS-DE-USO.md',
  'legal/PRIVACIDADE.md',
  'legal/CREDITOS.md',
];

const sw = globalThis;

sw.addEventListener?.('install', (event) => {
  event.waitUntil(
    caches.open(VERSION).then(async (cache) => {
      // Um arquivo que falhar (ex.: documento ausente) não impede o resto.
      await Promise.all(SHELL.map((path) => cache.add(new Request(path, { cache: 'reload' })).catch(() => {})));
    }),
  );
});

sw.addEventListener?.('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(keys.filter((k) => k.startsWith('lily-web-') && k !== VERSION).map((k) => caches.delete(k)));
      await sw.clients.claim();
    })(),
  );
});

// A página pede para a versão nova entrar (botão "Atualizar").
sw.addEventListener?.('message', (event) => {
  if (event.data === 'atualizar') sw.skipWaiting();
});

/** O serviço online e o que é de outro endereço nunca passam pelo cache. */
function bypass(url, method, origin) {
  return method !== 'GET' || url.origin !== origin || url.pathname.startsWith('/v1/') || url.pathname === '/health';
}

sw.addEventListener?.('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (bypass(url, req.method, sw.location.origin)) return;
  // Páginas: rede primeiro (versão nova quando há internet), o app guardado quando não há.
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          if (res.ok) caches.open(VERSION).then((c) => c.put('index.html', res.clone()));
          return res;
        })
        .catch(async () => (await caches.match('index.html')) || (await caches.match('./')) || Response.error()),
    );
    return;
  }
  // Arquivos do app: o guardado na hora (rápido e sem internet) e atualiza por trás.
  event.respondWith(
    caches.open(VERSION).then(async (cache) => {
      const cached = await cache.match(req, { ignoreSearch: true });
      const fresh = fetch(req)
        .then((res) => {
          if (res.ok) cache.put(req, res.clone());
          return res;
        })
        .catch(() => null);
      if (cached) {
        event.waitUntil(fresh);
        return cached;
      }
      return (await fresh) || Response.error();
    }),
  );
});
