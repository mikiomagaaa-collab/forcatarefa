// Configurações, documentos e diagnóstico da L.I.L.Y Web.

import * as store from '../core/store.js';
import * as local from '../core/local.js';
import { usd } from '../core/gateway.js';
import { label, tierName } from '../core/routers.js';
import { render } from '../core/markdown.js';
import { h, clear, toast, chips, field, toggle } from './dom.js';
import { desktop, SHORTCUTS } from '../core/desktop.js';

export function renderSettings(view, ctx) {
  const { app } = ctx;
  ctx.setTitle('Configurações');
  const col = h('div.column');
  view.append(col);
  const draw = () => {
    clear(col);
    const s = app.settings;

    // App no computador
    const pc = h('div.card');
    pc.append(
      h('h2', 'App no computador'),
      h(
        'p.faint',
        desktop.installed
          ? 'Instalada: a L.I.L.Y abre em janela própria, pelo ícone da área de trabalho ou do menu Iniciar, e funciona sem internet com um modelo no navegador.'
          : 'Instale para abrir a L.I.L.Y em janela própria, pelo ícone da área de trabalho ou do menu Iniciar. Sem internet, ela abre e conversa com um modelo do navegador.',
      ),
    );
    if (desktop.updateReady) pc.append(h('button.primary', { type: 'button', onclick: () => desktop.applyUpdate() }, 'Atualizar para a versão nova'));
    else if (!desktop.installed && desktop.canInstall) pc.append(h('button.primary', { type: 'button', onclick: async () => { if (await desktop.install()) toast('L.I.L.Y instalada no computador.'); draw(); } }, 'Instalar no computador'));
    else if (!desktop.installed) pc.append(h('p.faint', 'No Chrome ou no Edge: menu (⋮) > "Instalar L.I.L.Y" (ou o ícone de instalar na barra de endereço). No Safari do Mac: Arquivo > Adicionar ao Dock.'));
    pc.append(h('h3', 'Atalhos de teclado'), h('div.shortcuts', ...SHORTCUTS.map(([k, v]) => h('div.row', h('kbd', k), h('span.faint', v)))));
    col.append(pc);

    // Serviço online
    const url = h('input', { value: s.gatewayUrl, placeholder: location.origin.startsWith('http') ? `Vazio = este site (${location.origin})` : 'https://lily-gateway.seu-nome.workers.dev' });
    const key = h('input', { type: 'password', placeholder: store.accessKey() ? 'Guardada nesta sessão (deixe vazio para manter)' : 'A LILY_ACCESS_KEY do seu gateway', autocomplete: 'off' });
    const remember = h('input', { type: 'checkbox', checked: s.rememberKey || null });
    const status = h('div');
    const online = h('div.card');
    online.append(
      h('h2', 'Serviço online (L.I.L.Y Gateway)'),
      h('p.faint', 'O mesmo gateway do app no celular. Ele guarda as chaves dos provedores; aqui fica só o endereço e a chave de acesso.'),
      field('Endereço do gateway', url),
      field('Chave de acesso', key),
      h('label.row.faint', remember, 'Lembrar a chave neste computador (senão, só nesta aba)'),
      h(
        'div.row',
        h('button.primary', { type: 'button', onclick: async () => {
          const u = url.value.trim().replace(/\/+$/, '');
          if (u && !/^https:\/\//.test(u) && !/^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/.test(u)) return toast('Use o endereço https do gateway.', 'error');
          app.saveSettings({ gatewayUrl: u, rememberKey: remember.checked });
          if (key.value.trim()) store.setAccessKey(key.value.trim(), remember.checked);
          const st = await app.refreshGateway();
          toast(st?.error ? st.error : `Conectado ao gateway ${st?.version || ''}`, st?.error ? 'error' : '');
          draw();
        } }, 'Salvar e testar'),
        store.accessKey() ? h('button.ghost', { type: 'button', onclick: () => { store.clearAccessKey(); app.status = null; app.models = []; draw(); } }, 'Remover a chave') : null,
      ),
      status,
    );
    const st = app.status;
    if (st && !st.error) {
      const line = (ok, t, d) => h('div.row', h(`span.${ok ? 'ok' : 'faint'}`, ok ? '✓' : '—'), h('div', h('strong', t), h('div.faint', d)));
      status.append(
        h('h3', 'O que o seu gateway oferece'),
        line(st.features.chatFree, 'Conversa grátis (beta)', st.features.chatFree ? `${st.free?.chat || 'gpt-oss-20b'} pelo Workers AI` : 'Publique o gateway 1.2 ou mais novo'),
        line(st.features.code, 'Modelos de programação e conversa', st.features.code ? `${st.codeModels} modelos (OpenRouter)` : st.codeError || 'Falta OPENROUTER_API_KEY (opcional, pago)'),
        line(st.features.video, 'Geração de vídeo', st.features.video ? 'fal.ai' : 'Falta FAL_KEY'),
        line(st.features.search, 'Pesquisa na internet (Codex)', st.searchProvider || 'Falta TAVILY_API_KEY ou BRAVE_API_KEY'),
        line(st.features.repos, 'Repositórios do Codex', st.repos.length ? st.repos.join(', ') : 'Falta GITHUB_TOKEN e GITHUB_REPOS'),
        h(`p.${st.budget.enforced ? 'faint' : 'warn'}`, st.budget.enforced ? `Gasto hoje: ${usd(st.budget.spentTodayUsd || 0)} de ${usd(st.budget.dailyUsd)}` : `Limite diário de ${usd(st.budget.dailyUsd)} configurado, mas não garantido: ligue o KV LILY_USAGE no gateway.`),
      );
      if (app.models.length) {
        status.append(h('h3', 'Modelos online (lidos agora no provedor)'));
        app.models.forEach((m) => status.append(h('div.row', h('div.grow', h('strong', label(m)), h('div.faint', `${m.vendor} · ${tierName(m.tier)} · ${Math.round(m.context / 1000)}k de contexto`)), h('span.faint', m.free ? 'Grátis (cota diária)' : `${usd(m.priceIn)} / ${usd(m.priceOut)}`))));
        status.append(h('p.faint', 'Preço por milhão de tokens (entrada / saída), informado pelo provedor.'));
      }
    } else if (st?.error) status.append(h('p.warn', st.error));
    col.append(online);

    col.append(
      h(
        'div.card',
        h('h2', 'Privacidade'),
        toggle(s.consent, (v) => { app.saveSettings({ consent: v }); draw(); }, 'Permitir processamento externo', 'O que você mandar para um recurso online (texto, código, imagem de referência) sai do computador e passa pelo gateway e pelo provedor. Cada tela avisa quando algo é online.'),
        h('h3', 'Na conversa'),
        chips([['OFF', 'Só no navegador'], ['FALLBACK', 'Online quando faltar'], ['PREFER', 'Online primeiro']], s.online, (v) => { app.saveSettings({ online: v }); draw(); }),
        h('h3', 'Perfil do roteador'),
        chips([['ECO', 'Eco'], ['BALANCED', 'Equilibrado'], ['QUALITY', 'Qualidade']], s.profile, (v) => { app.saveSettings({ profile: v }); draw(); }),
        toggle(s.memoryEnabled, (v) => app.saveSettings({ memoryEnabled: v }), 'Usar memórias', 'Só o que você pediu para lembrar (nunca senhas, CPF ou cartões)'),
        toggle(s.speakAnswers, (v) => app.saveSettings({ speakAnswers: v }), 'Ler as respostas em voz alta'),
      ),
    );

    // Modelos no navegador
    const localCard = h('div.card', h('h2', 'IA no seu computador'), h('p.faint', 'Modelos que rodam dentro do navegador com WebGPU (WebLLM). Nada sai do computador. O primeiro download leva alguns minutos e fica no cache do navegador.'));
    if (!local.supported()) localCard.append(h('p.warn', 'Este navegador não tem WebGPU. Use o Chrome ou o Edge atualizados num computador com placa de vídeo.'));
    else {
      const list = h('div', h('p.faint', 'Carregando a lista de modelos…'));
      const progress = h('div');
      localCard.append(list, progress);
      local.models().then((ms) => {
        clear(list);
        ms.forEach((m) => list.append(h('div.row', h('div.grow', h('strong', m.name), h('div.faint', `${m.note} · ~${Math.round(m.vramMB / 100) / 10} GB de memória de vídeo`)), h(local.current() === m.id ? 'span.ok' : 'button.ghost', local.current() === m.id ? { } : { type: 'button', onclick: async (e) => {
          e.target.disabled = true;
          const bar = h('div.progress', h('div', { style: { width: '0%' } }));
          const txt = h('p.faint', 'Preparando…');
          clear(progress).append(bar, txt);
          try {
            await local.load(m.id, (f, t) => { bar.firstChild.style.width = `${Math.round(f * 100)}%`; txt.textContent = t; });
            app.saveSettings({ localModel: m.id });
            toast(`${m.name} pronto no navegador`);
            draw();
          } catch (err) {
            txt.textContent = err.message;
            e.target.disabled = false;
          }
        } }, local.current() === m.id ? 'Carregado' : 'Usar este'))));
      }).catch((e) => { clear(list).append(h('p.warn', `Não consegui carregar a biblioteca do navegador: ${e.message}`)); });
    }
    col.append(localCard);

    // Avatar e aparência
    const a = s.avatar;
    const setA = (c) => app.saveSettings({ avatar: { ...app.settings.avatar, ...c } });
    const preview = h('canvas', { width: 160, height: 160, style: { width: '160px', height: '160px' } });
    col.append(
      h(
        'div.card',
        h('h2', 'Avatar e aparência'),
        h('div.row', preview, h('div.grow',
          toggle(a.enabled, (v) => setA({ enabled: v }), 'Mostrar a avatar flutuante durante tarefas'),
          h('h3', 'Estilo'), chips([['HOLO', 'Holográfica'], ['GLASS', 'Liquid Glass'], ['MINIMAL', 'Minimalista']], a.style, (v) => { setA({ style: v }); draw(); }),
          h('h3', 'Forma'), chips([['BUST', 'Busto'], ['FACE', 'Rosto'], ['ORB', 'Orbe']], a.presentation, (v) => { setA({ presentation: v }); draw(); }),
          h('h3', 'Animações'), chips([['FULL', 'Completas'], ['REDUCED', 'Suaves'], ['OFF', 'Paradas']], a.motion, (v) => { setA({ motion: v }); draw(); }),
        )),
        h('h3', 'Cor'),
        h('input', { type: 'range', min: -1, max: 359, value: a.hue, 'aria-label': 'Matiz da avatar (-1 = tema)', oninput: (e) => setA({ hue: Number(e.target.value) }) }),
        h('h3', 'Tema'),
        chips([['system', 'Do sistema'], ['dark', 'Escuro'], ['light', 'Claro']], s.theme, (v) => { app.saveSettings({ theme: v }); draw(); }),
        h('p.faint', 'A avatar só mostra o que a L.I.L.Y já está fazendo por pedido seu. Ela nunca liga a câmera nem o microfone.'),
      ),
    );
    const av = app.avatar(preview);
    ctx.onCleanup(() => av.dispose());

    // Memórias e diagnóstico
    const mems = store.memories.list();
    const diag = store.diagnostics.list();
    col.append(
      h('div.card', h('h2', 'Memórias'), mems.length ? mems.map((m) => h('div.row', h('span.grow', m.text), h('button.ghost', { type: 'button', onclick: () => { store.memories.remove(m.id); draw(); } }, 'Apagar'))) : h('p.faint', 'Nada guardado. Use "Lembrar" numa resposta.')),
      h(
        'div.card',
        h('h2', 'Diagnóstico desta página'),
        h('p.faint', 'Erros guardados só neste navegador, para você copiar e mandar se algo falhar.'),
        diag.length ? h('pre.faint', diag.slice(0, 10).map((d) => `${d.at} · ${d.kind}: ${d.message}${d.where ? ' (' + d.where + ')' : ''}`).join('\n')) : h('p.ok', 'Nenhum erro registrado.'),
        h('div.row', h('button.ghost', { type: 'button', onclick: () => navigator.clipboard.writeText(JSON.stringify({ versao: '1.0.0', navegador: navigator.userAgent, webgpu: local.supported(), erros: diag }, null, 2)).then(() => toast('Relatório copiado')) }, 'Copiar relatório'), h('button.ghost', { type: 'button', onclick: () => { store.diagnostics.clear(); draw(); } }, 'Apagar')),
      ),
      h('div.card', h('h2', 'Sobre'), h('p', 'L.I.L.Y Web 1.0 · PROJECT LIQUID FLIP · Apache License 2.0'), h('div.row', h('a.ghost', { href: '#/legal/termos' }, 'Termos de Uso'), h('a.ghost', { href: '#/legal/privacidade' }, 'Privacidade'), h('a.ghost', { href: '#/legal/creditos' }, 'Créditos'))),
    );
  };
  draw();
}

const DOCS = { termos: ['Termos de Uso', 'TERMOS-DE-USO.md'], privacidade: ['Política de Privacidade', 'PRIVACIDADE.md'], creditos: ['Créditos', 'CREDITOS.md'] };

export async function renderLegal(view, ctx, doc) {
  const [title, file] = DOCS[doc] || DOCS.termos;
  ctx.setTitle(title);
  const card = h('div.card.rich', h('p.faint', 'Carregando…'));
  view.append(h('div.column', card));
  try {
    const r = await fetch(`legal/${file}`);
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    card.innerHTML = render(await r.text());
  } catch (e) {
    card.innerHTML = '';
    card.append(h('p.warn', `Não consegui abrir o documento (${e.message}). Ele também está em legal/${file} no projeto.`));
  }
}
