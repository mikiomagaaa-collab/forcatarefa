// Codex na L.I.L.Y Web: pasta do computador (Chrome/Edge) ou repositório pelo gateway.

import { Session, MODES, VERIFICATION, DirWorkspace, RepoWorkspace, branchName } from '../core/agent.js';
import { label } from '../core/routers.js';
import { usd } from '../core/gateway.js';
import * as store from '../core/store.js';
import { h, clear, icon, toast, chips, field, toggle } from './dom.js';

let folder = null; // FileSystemDirectoryHandle escolhido nesta aba (o navegador não guarda sem permissão)
let session = null;

export function renderCodex(view, ctx, preset) {
  const { app } = ctx;
  ctx.setTitle('Codex');
  const col = h('div.column');
  view.append(col);

  if (!app.onlineReady()) {
    col.append(h('div.card', h('h2', 'Conecte o serviço online'), h('p.muted', 'O Codex usa modelos de programação grandes pelo seu L.I.L.Y Gateway. O código que ele ler é enviado ao gateway e ao provedor do modelo.'), h('button.primary', { type: 'button', onclick: () => ctx.go('settings') }, 'Configurar')));
    return;
  }
  if (session) return showSession(col, ctx);

  let mode = /erro|exception|failed|e: /i.test(preset) ? 'REPAIR' : 'TASK';
  let source = folder ? 'folder' : 'repo';
  let manual = null;
  const repoState = store.load('codex.repo', { repo: '', base: 'main', runner: 'gradle', tasks: 'test', workdir: '.', tests: true });
  const task = h('textarea', { rows: 4, placeholder: 'Ex.: adicione um botão de compartilhar na tela de vídeos' });
  const error = h('textarea', { rows: 6, placeholder: 'Cole a mensagem de erro, o log do build ou o relatório do L.I.L.Y Diagnóstico' });
  if (mode === 'REPAIR') error.value = preset;
  else task.value = preset;

  const body = h('div');
  function draw() {
    clear(body);
    const project = h('div.card');
    project.append(h('h2', 'Projeto autorizado'), h('p.faint', 'O Codex só lê e altera o que você autorizar aqui.'));
    project.append(chips([['folder', 'Pasta do computador'], ['repo', 'Repositório (GitHub)']], source, (v) => { source = v; draw(); }));
    if (source === 'folder') {
      const supported = 'showDirectoryPicker' in globalThis;
      project.append(
        supported
          ? h('div.row', h('button.ghost', { type: 'button', onclick: pick }, icon('folder'), folder ? ` ${folder.name}` : ' Escolher pasta'), h('span.faint', 'As mudanças aprovadas são gravadas na pasta. Os testes do projeto não rodam no navegador: a verificação é de sintaxe.'))
          : h('p.warn', 'Este navegador não permite escolher pastas (use o Chrome ou o Edge no computador). Use um repositório pelo gateway.'),
      );
    } else {
      const repos = (app.status?.repos || []).map((r) => [r, r]);
      if (!repos.length) project.append(h('p.warn', 'Nenhum repositório liberado no gateway (GITHUB_TOKEN e GITHUB_REPOS).'));
      else {
        if (!repoState.repo) repoState.repo = repos[0][0];
        project.append(chips(repos, repoState.repo, (v) => { repoState.repo = v; draw(); }));
        const base = h('input', { value: repoState.base, oninput: (e) => (repoState.base = e.target.value.trim() || 'main') });
        project.append(field('Ramo de origem', base));
        project.append(toggle(repoState.tests, (v) => { repoState.tests = v; draw(); }, 'Rodar os testes do projeto (GitHub Actions)', 'O Codex grava num ramo lily-codex/… (o principal nunca é tocado) e roda os testes lá'));
        if (repoState.tests) {
          project.append(chips([['gradle', 'gradle'], ['npm', 'npm'], ['pytest', 'pytest']], repoState.runner, (v) => { repoState.runner = v; draw(); }));
          project.append(field('Tarefas', h('input', { value: repoState.tasks, oninput: (e) => (repoState.tasks = e.target.value) })));
          project.append(field('Pasta do projeto no repositório', h('input', { value: repoState.workdir, oninput: (e) => (repoState.workdir = e.target.value) })));
        }
      }
    }
    const what = h('div.card.strong');
    what.append(h('h2', 'O que fazer'), chips(Object.entries(MODES).map(([k, v]) => [k, v.label]), mode, (v) => { mode = v; draw(); }), h('p.faint', MODES[mode].detail));
    what.append(field(mode === 'REPAIR' ? 'O que deveria acontecer (opcional)' : mode === 'EXPLAIN' ? 'O que você quer entender' : mode === 'SECURITY' ? 'Foco da revisão (opcional)' : 'A tarefa', task));
    if (mode === 'REPAIR') what.append(field('O erro ou o log', error));
    const models = [['', 'Automático'], ...app.models.map((m) => [m.id, `${label(m)} · ${m.vendor}`])];
    what.append(h('span.faint', 'Modelo'), chips(models, manual || '', (v) => { manual = v || null; draw(); }));
    what.append(toggle(app.settings.codexWeb, (v) => app.saveSettings({ codexWeb: v }), 'Pesquisar na internet', 'Documentação oficial, erros conhecidos e mudanças de versão, com as fontes'));
    what.append(toggle(app.settings.codexAuto, (v) => app.saveSettings({ codexAuto: v }), 'Aplicar correções simples sozinho', 'Até 12 linhas em um arquivo, sem problemas na verificação. Dá para desfazer.'));
    what.append(h('div.row', h('button.primary', { type: 'button', onclick: begin }, 'Começar')));
    what.append(h('p.faint', 'Os arquivos que o Codex ler vão para o gateway e para o provedor do modelo escolhido (processamento externo). Nada é gravado sem você aprovar o diff.'));
    body.append(project, what);
  }

  async function pick() {
    try {
      folder = await globalThis.showDirectoryPicker({ mode: 'readwrite' });
      draw();
    } catch {
      // cancelado
    }
  }

  function begin() {
    let workspace;
    if (source === 'folder') {
      if (!folder) return toast('Escolha a pasta do projeto.', 'error');
      workspace = new DirWorkspace(folder);
    } else {
      if (!repoState.repo) return toast('Escolha um repositório.', 'error');
      store.save('codex.repo', repoState);
      workspace = new RepoWorkspace(app.gateway, repoState.repo, repoState.base || 'main', branchName(), repoState.tests ? { runner: repoState.runner, tasks: repoState.tasks.trim() || 'test', workdir: repoState.workdir.trim() || '.' } : null);
    }
    if (mode === 'TASK' && !task.value.trim()) return toast('Descreva a tarefa.', 'error');
    if (mode === 'REPAIR' && !error.value.trim()) return toast('Cole o erro ou o log.', 'error');
    session = new Session({
      workspace,
      mode,
      task: task.value.trim(),
      errorText: error.value.trim(),
      models: () => app.gateway.codeModels(),
      llmFor: (m) => ({ complete: async (messages) => { const r = await app.gateway.chat(m.id, messages, { temperature: 0.1, maxTokens: 6000 }); return { text: r.text, costUsd: r.usage?.costUsd }; } }),
      gateway: app.gateway,
      profile: app.settings.profile,
      manual,
      web: app.settings.codexWeb,
      reviewEnabled: app.settings.codexReview,
      autoLines: app.settings.codexAuto ? 12 : null,
      fetchPage: async (url) => {
        const r = await fetch(url);
        const html = await r.text();
        return new DOMParser().parseFromString(html, 'text/html').body.innerText.replace(/\n{3,}/g, '\n\n');
      },
      presence: (phase, s) => {
        if (['PREPARING', 'WORKING', 'REVIEWING', 'APPLYING', 'VALIDATING'].includes(phase)) app.presence.working('codex', `Codex: ${phaseLabel(phase).toLowerCase()}`);
        else if (phase === 'DONE') app.presence.finished('codex', !['TESTS_FAILED', 'NOT_FIXED'].includes(s.verification), 'Codex concluiu');
        else if (phase === 'FAILED') app.presence.finished('codex', false, 'Codex não concluiu');
        else app.presence.clear('codex');
      },
    });
    clear(col);
    showSession(col, ctx);
    session.start();
  }

  col.append(body);
  draw();
}

function phaseLabel(p) {
  return { IDLE: 'Pronto', PREPARING: 'Escolhendo o modelo', WORKING: 'Trabalhando', REVIEWING: 'Revisão independente', AWAITING_APPROVAL: 'Esperando sua aprovação', APPLYING: 'Aplicando', VALIDATING: 'Validando', DONE: 'Concluído', FAILED: 'Não concluído', CANCELLED: 'Cancelado' }[p] || p;
}

function showSession(col, ctx) {
  const s = session;
  const draw = () => {
    clear(col);
    const busy = ['PREPARING', 'WORKING', 'REVIEWING', 'APPLYING', 'VALIDATING'].includes(s.phase);
    const head = h('div.card.strong', h('div.row', h('canvas.mini-avatar', { width: 48, height: 48 }), h('div.grow', h('h2', phaseLabel(s.phase)), h('div.faint', [MODES[s.mode].label, s.workspace.name, s.pick ? `${label(s.pick.model)} (${s.pick.model.vendor})` : null, s.attempt > 1 ? `tentativa ${s.attempt}` : null, s.cost ? usd(s.cost) : null].filter(Boolean).join(' · ')))));
    if (busy) head.append(h('div.progress.indeterminate', h('div')));
    col.append(head);
    const av = ctx.app.avatar(head.querySelector('canvas'), 'FACE');
    ctx.onCleanup(() => av.dispose());
    const tl = h('div.timeline');
    s.events.forEach((e) => tl.append(eventEl(e)));
    col.append(h('div.card', h('h3', 'Linha do tempo'), tl));
    if (s.diffs.length) {
      const changes = h('div.card', h('h3', `Mudanças (${s.diffs.length} ${s.diffs.length === 1 ? 'arquivo' : 'arquivos'})`));
      if (s.review) changes.append(h(`p.${s.review.ok ? 'ok' : 'warn'}`, s.review.ok ? `Revisão independente (${s.review.reviewer}): sem problemas` : `Revisão independente (${s.review.reviewer}): pontos de atenção`), ...s.review.issues.map((i) => h('p.faint', '• ' + i)));
      s.diffs.forEach((d) => changes.append(h('div.row', h('button.ghost', { type: 'button', onclick: () => ctx.openPanel(diffPanel(d, ctx)) }, d.path), h('span.faint', d.created ? 'novo' : d.deleted ? 'apagado' : `+${d.added} −${d.removed}`))));
      col.append(changes);
      if (s.phase === 'AWAITING_APPROVAL') ctx.openPanel(diffPanel(s.diffs[0], ctx));
    }
    if (s.phase === 'AWAITING_APPROVAL') {
      const adjust = h('input', { placeholder: 'Ex.: mantenha o nome antigo da função' });
      col.append(
        h(
          'div.card.strong',
          h('p.faint', 'Antes de gravar, um ponto de restauração é criado. Dá para desfazer depois.'),
          h('div.row', h('button.primary', { type: 'button', onclick: () => s.decide({ type: 'approve' }) }, 'Aprovar e aplicar'), h('button.ghost', { type: 'button', onclick: () => s.decide({ type: 'reject' }) }, 'Descartar')),
          field('Pedir um ajuste', adjust),
          h('button.ghost', { type: 'button', onclick: () => adjust.value.trim() && s.decide({ type: 'adjust', request: adjust.value.trim() }) }, 'Enviar ajuste'),
        ),
      );
    }
    if (s.applied) {
      const v = s.verification;
      col.append(
        h(
          'div.card',
          h('h3', 'Validação'),
          h(`p.${['TESTS_PASSED', 'ERROR_GONE'].includes(v) ? 'ok' : v === 'SYNTAX_ONLY' ? 'warn' : v === 'NONE' ? 'muted' : 'danger'}`, VERIFICATION[v]),
          h('p.faint', `Aplicado ${s.applied.where}`),
          s.checkUrl ? h('a', { href: s.checkUrl, target: '_blank', rel: 'noopener' }, 'Ver os testes no GitHub') : null,
          s.restore && !busy ? h('div.row', h('button.ghost', { type: 'button', onclick: () => s.undo().catch((e) => toast(e.message, 'error')) }, 'Desfazer')) : null,
        ),
      );
    }
    if (s.report) {
      const r = s.report;
      col.append(
        h(
          'div.card',
          h('h3', 'Relatório'),
          r.summary ? h('p', r.summary) : null,
          r.changes ? h('p.muted', r.changes) : null,
          r.verified.length ? [h('p.ok', 'Verificado'), ...r.verified.map((x) => h('p.faint', '✓ ' + x))] : null,
          r.hypotheses.length ? [h('p.warn', 'Hipóteses (não confirmadas)'), ...r.hypotheses.map((x) => h('p.faint', '? ' + x))] : null,
          r.risks.length ? [h('p.danger', 'Possíveis regressões'), ...r.risks.map((x) => h('p.faint', '! ' + x))] : null,
          r.sources.length ? [h('p', 'Fontes'), ...r.sources.map((u) => (/^https:\/\//.test(u) ? h('p.faint', h('a', { href: u, target: '_blank', rel: 'noopener noreferrer' }, u)) : h('p.faint', u)))] : null,
        ),
      );
    }
    col.append(h('div.row', busy ? h('button.ghost', { type: 'button', onclick: () => { s.cancelled = true; toast('O Codex para depois do passo atual.'); } }, 'Parar') : null, !busy && s.phase !== 'AWAITING_APPROVAL' ? h('button.primary', { type: 'button', onclick: () => { session = null; ctx.closePanel(); clear(col); renderCodex(col.parentElement ? clear(col.parentElement) : col, ctx, ''); } }, 'Nova tarefa') : null));
  };
  s.onChange = () => requestAnimationFrame(draw);
  draw();
  ctx.onCleanup(() => (s.onChange = null));
}

function eventEl(e) {
  const marks = { plan: '◆', thought: '…', action: '›', web: '◎', staged: '±', checks: e.ok ? '✓' : '✗', note: '•', failure: '✗' };
  return h(
    `div.event${e.type === 'thought' ? '.thought' : e.type === 'failure' ? '.fail' : ''}`,
    h('span.mark', marks[e.type] || '•'),
    h(
      'div',
      e.text,
      e.detail ? h('div.detail', e.detail) : null,
      e.type === 'staged' ? h('div.detail', `+${e.added} −${e.removed}`) : null,
      e.type === 'web' ? (e.sources || []).slice(0, 5).map((x) => h('div.detail', h('a', { href: x.url, target: '_blank', rel: 'noopener noreferrer' }, x.title || x.url))) : null,
    ),
  );
}

function diffPanel(d, ctx) {
  const body = h('div.diff');
  d.hunks.forEach((hk) => {
    body.append(h('div.hunk', hk.header));
    hk.lines.forEach((l) => body.append(h(`div${l.kind === '+' ? '.add' : l.kind === '-' ? '.del' : ''}`, `${l.kind}${l.text}`)));
  });
  return h('div', h('header', h('strong', d.path), h('button.icon-btn', { type: 'button', 'aria-label': 'Fechar', onclick: () => ctx.closePanel() }, '×')), h('p.faint', d.created ? 'Arquivo novo' : d.deleted ? 'Arquivo apagado' : `+${d.added} −${d.removed} linhas`), body);
}
