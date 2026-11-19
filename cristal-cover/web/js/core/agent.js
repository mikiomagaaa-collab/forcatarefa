// L.I.L.Y Codex no navegador: o mesmo agente e a mesma sessão do app (ações em JSON, mudanças
// preparadas, revisão independente, aprovação, ponto de restauração, testes no CI e auto-reparo).

import { fileDiff, unified } from './diff.js';
import { checkFile, languageOf, parseError } from './languages.js';
import { classify, pickCode, label } from './routers.js';

export const SKIP = /(^|\/)(\.git|\.gradle|\.idea|build|node_modules|dist|\.next|\.kotlin|__pycache__|\.venv|venv|\.dart_tool|Pods)(\/|$)/;

export function safePath(p) {
  const clean = String(p || '').trim().replace(/^\/+/, '').replace(/\\/g, '/');
  if (!clean || clean.split('/').some((s) => s === '..' || s === '.') || SKIP.test(clean)) throw new Error(`Caminho não permitido: ${p}`);
  return clean;
}

export const SYSTEM = (web) => `
You are L.I.L.Y Codex, a careful software engineering agent working inside a project the user AUTHORIZED.
You act ONLY through JSON actions. Reply with exactly ONE JSON object per turn and nothing else.
Every action may include "thought": one short sentence (Brazilian Portuguese) explaining why.

Actions:
{"action":"list","path":"dir"}                                  list files under a directory
{"action":"read","path":"file","from":1,"to":400}               numbered lines (plus related files)
{"action":"search","query":"text or regex","path":"optional dir"}
${web ? `{"action":"web_search","query":"..."}                             official docs, API references, known errors
{"action":"fetch","url":"https://..."}                          read a documentation page` : '(web search is disabled for this session)'}
{"action":"edit","path":"file","find":"exact existing text","replace":"new text"}   find must match exactly once
{"action":"write","path":"file","content":"complete file"}     create a file or rewrite a small one
{"action":"delete","path":"file"}
{"action":"check"}                                               syntax checks on the files you changed
{"action":"finish","summary":"...","changes":"...","verified":["..."],"hypotheses":["..."],"risks":["..."],"sources":["https://..."]}

Rules:
- Understand before editing: read the files you will change and the related ones; search for usages.
- Never invent APIs, functions, parameters or library versions. If you are not sure, search the project${web ? ' or the web' : ''}, or put it under "hypotheses".
- Keep changes minimal, focused and in the project's existing style. Prefer "edit" to "write" for existing files.
- After editing, run "check" and fix what it reports. Compiling is not proof: list in "risks" what only tests can confirm.
- Edits are staged; the user reviews a diff before anything is written. Never touch secrets, signing keys or CI workflows.
- "verified" lists only facts you confirmed by reading files, tool output or cited sources. Everything else goes to "hypotheses".
- Write summary, changes, verified, hypotheses and risks in Brazilian Portuguese.
`.trim();

/** O primeiro objeto JSON com "action" (aceita ```json e texto em volta). */
export function parseAction(text) {
  let start = text.indexOf('{');
  while (start >= 0) {
    let depth = 0;
    let inStr = false;
    for (let i = start; i < text.length; i++) {
      const c = text[i];
      if (inStr) {
        if (c === '\\') i++;
        else if (c === '"') inStr = false;
      } else if (c === '"') inStr = true;
      else if (c === '{') depth++;
      else if (c === '}' && --depth === 0) {
        try {
          const obj = JSON.parse(text.slice(start, i + 1));
          if (obj && obj.action) return obj;
        } catch {
          // continua
        }
        break;
      }
    }
    start = text.indexOf('{', start + 1);
  }
  return null;
}

export class Staged {
  constructor() {
    this.originals = new Map();
    this.current = new Map();
  }
  has(p) {
    return this.current.has(p);
  }
  get(p) {
    return this.current.get(p);
  }
  stage(p, before, after) {
    if (!this.originals.has(p)) this.originals.set(p, before);
    this.current.set(p, after);
    if (this.originals.get(p) === after) {
      this.originals.delete(p);
      this.current.delete(p);
    }
  }
  changes() {
    return [...this.current.entries()].map(([path, after]) => ({ path, before: this.originals.get(path) ?? null, after }));
  }
  get size() {
    return this.current.size;
  }
}

function related(path, text, all) {
  const out = new Set();
  const base = path.split('/').pop().replace(/\.[^.]+$/, '');
  for (const m of text.matchAll(/^\s*import\s+([\w.]+)/gm)) {
    const parts = m[1].split('.');
    const cls = parts.pop();
    const pkg = parts.join('/');
    const hit = all.find((f) => f.endsWith(`${pkg}/${cls}.kt`) || f.endsWith(`${pkg}/${cls}.java`));
    if (hit) out.add(hit);
  }
  for (const m of text.matchAll(/(?:from\s+|require\()\s*['"](\.{1,2}\/[^'"]+)['"]/g)) {
    const dir = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '';
    const parts = [];
    for (const s of (dir ? dir + '/' + m[1] : m[1]).split('/')) {
      if (s === '..') parts.pop();
      else if (s && s !== '.') parts.push(s);
    }
    const target = parts.join('/');
    const hit = all.find((f) => f === target || f.startsWith(target + '.') || f === target + '/index.js');
    if (hit) out.add(hit);
  }
  all.filter((f) => f !== path && [`${base}Test`, `${base}.test`, `test_${base}`, base].includes(f.split('/').pop().replace(/\.[^.]+$/, ''))).forEach((f) => out.add(f));
  return [...out].filter((f) => f !== path).slice(0, 6);
}

export class Agent {
  /** llm.complete(messages) → { text, costUsd }; workspace: files/read/apply; gateway: search (opcional). */
  constructor({ workspace, llm, gateway, web, emit, fetchPage }) {
    Object.assign(this, { workspace, llm, gateway, web, emit, fetchPage });
    this.staged = new Staged();
    this.cost = 0;
    this.filesCache = null;
  }

  async files() {
    if (!this.filesCache) this.filesCache = await this.workspace.files();
    return this.filesCache;
  }

  async contentOf(p) {
    return this.staged.has(p) ? this.staged.get(p) : this.workspace.read(p);
  }

  async run(task, context, maxSteps) {
    const all = await this.files();
    const langs = {};
    all.forEach((f) => {
      const l = languageOf(f.path);
      if (l) langs[l.name] = (langs[l.name] || 0) + 1;
    });
    const top = Object.entries(langs).sort((a, b) => b[1] - a[1]).slice(0, 4);
    this.emit({ type: 'plan', text: `Projeto: ${all.length} ${all.length === 1 ? 'arquivo' : 'arquivos'} · ${top.map(([n, c]) => `${n} (${c})`).join(', ')}` });
    const folders = {};
    all.forEach((f) => {
      const parts = f.path.split('/');
      const k = parts.length <= 1 ? '.' : parts.slice(0, Math.min(parts.length - 1, 3)).join('/');
      folders[k] = (folders[k] || 0) + 1;
    });
    const messages = [
      { role: 'system', content: SYSTEM(this.web && !!this.gateway) },
      {
        role: 'user',
        content: `PROJECT: ${this.workspace.name} (${this.workspace.kindLabel})\nLanguages: ${top.map(([n, c]) => `${n}=${c}`).join(', ')}\nFolders:\n${Object.entries(folders).sort().slice(0, 60).map(([k, v]) => `${k}/ (${v})`).join('\n')}\n\n${context ? context + '\n\n' : ''}TASK (from the user, Brazilian Portuguese):\n${task}`,
      },
    ];
    let invalid = 0;
    for (let step = 1; step <= maxSteps; step++) {
      const reply = await this.llm.complete(messages);
      if (reply.costUsd) this.cost += reply.costUsd;
      const action = parseAction(reply.text);
      messages.push({ role: 'assistant', content: reply.text.slice(0, 12000) });
      if (!action) {
        if (++invalid >= 3) {
          this.emit({ type: 'failure', text: 'O modelo não seguiu o formato de ações três vezes seguidas.' });
          return null;
        }
        messages.push({ role: 'user', content: 'Your reply was not a single valid JSON action. Reply with exactly one JSON object as specified.' });
        continue;
      }
      invalid = 0;
      if (action.thought) this.emit({ type: 'thought', text: action.thought });
      if (action.action === 'finish') return finishOf(action);
      let result;
      try {
        result = await this.execute(action);
      } catch (e) {
        result = `ERROR: ${e.message}`;
      }
      messages.push({ role: 'user', content: `RESULT of ${action.action} (step ${step}/${maxSteps}):\n${String(result).slice(0, 14000)}` });
      if (messages.reduce((n, m) => n + m.content.length, 0) > 180000) {
        for (let i = 2; i < messages.length - 6; i++) if (messages[i].role === 'user' && messages[i].content.length > 1500) messages[i] = { role: 'user', content: messages[i].content.slice(0, 600) + '\n[... resultado antigo resumido ...]' };
      }
    }
    this.emit({ type: 'failure', text: `Chegou ao limite de ${maxSteps} passos sem terminar.` });
    return null;
  }

  async execute(a) {
    switch (a.action) {
      case 'list': {
        const dir = String(a.path || '').replace(/^\/+|\/+$/g, '');
        const prefix = dir ? dir + '/' : '';
        const list = (await this.files()).filter((f) => f.path.startsWith(prefix)).slice(0, 250);
        this.emit({ type: 'action', text: `Listou ${dir || 'o projeto'}`, detail: `${list.length} ${list.length === 1 ? 'arquivo' : 'arquivos'}` });
        return list.map((f) => `${f.path} (${f.size} B)`).join('\n') || '(nada aqui)';
      }
      case 'read': {
        const p = safePath(a.path);
        const text = await this.contentOf(p);
        if (text == null) return `File not found: ${p}`;
        const ls = text.split('\n');
        const from = Math.min(Math.max(1, a.from || 1), Math.max(1, ls.length));
        const to = Math.min(Math.max(from, a.to || from + 399), ls.length);
        this.emit({ type: 'action', text: `Leu ${p}`, detail: `linhas ${from}–${to} de ${ls.length}` });
        let out = '';
        for (let n = from; n <= to; n++) out += `${n}: ${ls[n - 1]}\n`;
        if (to < ls.length) out += `... (${ls.length - to} more lines)\n`;
        const rel = related(p, text, (await this.files()).map((f) => f.path));
        if (rel.length) out += `Related files: ${rel.join(', ')}`;
        return out;
      }
      case 'search': {
        const q = String(a.query || '');
        if (!q) return 'empty query';
        const dir = String(a.path || '').replace(/^\/+|\/+$/g, '');
        let re = null;
        try {
          re = new RegExp(q);
        } catch {
          re = null;
        }
        const hits = [];
        for (const f of (await this.files()).filter((f) => (!dir || f.path.startsWith(dir + '/')) && f.size < 200000 && languageOf(f.path)).slice(0, 1500)) {
          const text = await this.contentOf(f.path);
          if (text == null) continue;
          text.split('\n').forEach((l, i) => {
            if (hits.length < 40 && (re ? re.test(l) : l.toLowerCase().includes(q.toLowerCase()))) hits.push(`${f.path}:${i + 1}: ${l.trim().slice(0, 160)}`);
          });
          if (hits.length >= 40) break;
        }
        this.emit({ type: 'action', text: `Buscou "${q}"`, detail: `${hits.length} ${hits.length === 1 ? 'resultado' : 'resultados'}` });
        return hits.join('\n') || '(no matches)';
      }
      case 'web_search': {
        if (!this.web || !this.gateway) return 'Web search is disabled by the user. Rely on the project files and list unverified points as hypotheses.';
        const r = await this.gateway.search(String(a.query || ''));
        this.emit({ type: 'web', text: `Pesquisou: ${a.query}`, sources: r.results || [] });
        return (r.results || []).map((x) => `${x.title}\n${x.url}\n${x.snippet}`).join('\n\n') || '(no results)';
      }
      case 'fetch': {
        if (!this.web) return 'Web access is disabled by the user.';
        const url = String(a.url || '');
        if (!url.startsWith('https://')) return 'only https URLs';
        if (!this.fetchPage) return 'Fetching pages is not available here.';
        const text = await this.fetchPage(url);
        this.emit({ type: 'web', text: 'Leu a página', sources: [{ title: url.slice(0, 80), url, snippet: '' }] });
        return text.slice(0, 12000);
      }
      case 'edit': {
        const p = safePath(a.path);
        const before = await this.contentOf(p);
        if (before == null) return `File not found: ${p}. Use write to create it.`;
        const find = String(a.find ?? '');
        if (!find) return 'find must not be empty';
        const count = before.split(find).length - 1;
        if (count === 0) return `find text not found in ${p}. Read the file again and copy the exact text (including indentation).`;
        if (count > 1) return `find text appears ${count} times in ${p}. Include more surrounding lines so it is unique.`;
        return this.stage(p, before.replace(find, () => String(a.replace ?? '')));
      }
      case 'write':
        return this.stage(safePath(a.path), String(a.content ?? ''));
      case 'delete': {
        const p = safePath(a.path);
        if ((await this.contentOf(p)) == null) return `File not found: ${p}`;
        return this.stage(p, null);
      }
      case 'check': {
        const { ok, report } = this.checks();
        this.emit({ type: 'checks', ok, text: ok ? 'Verificação: sem problemas nos arquivos alterados' : 'Verificação encontrou problemas' });
        return report;
      }
      default:
        return `Unknown action "${a.action}". Valid: list, read, search, web_search, fetch, edit, write, delete, check, finish.`;
    }
  }

  async stage(p, after) {
    const before = this.staged.has(p) ? this.staged.changes().find((c) => c.path === p).before : await this.workspace.read(p);
    this.staged.stage(p, before, after);
    const d = fileDiff(p, before, after);
    this.emit({ type: 'staged', text: after == null ? `Preparou apagar ${p}` : before == null ? `Preparou criar ${p}` : `Preparou mudança em ${p}`, path: p, added: d.added, removed: d.removed });
    const c = after != null ? checkFile(p, after) : null;
    let out = `Staged (not yet applied). +${d.added} -${d.removed} lines.\n`;
    if (c?.problems.length) out += 'Syntax check found problems:\n' + c.problems.map((x) => `${x.path}:${x.line}: ${x.message}`).join('\n');
    else if (c?.checked) out += `Syntax check (${c.kind}): ok.`;
    return out;
  }

  checks() {
    const results = this.staged.changes().filter((c) => c.after != null).map((c) => checkFile(c.path, c.after));
    const problems = results.flatMap((r) => r.problems);
    let report = results.length ? '' : 'No staged files to check.\n';
    results.filter((r) => r.checked).forEach((r) => (report += `${r.path}: ${r.problems.length ? r.problems.length + ' problem(s)' : 'ok'} (${r.kind})\n`));
    problems.forEach((x) => (report += `${x.path}:${x.line}: ${x.message}\n`));
    report += 'Note: project tests run only after approval (CI) — compiling alone is not proof.';
    return { ok: problems.length === 0, report };
  }
}

function finishOf(a) {
  const arr = (v) => (Array.isArray(v) ? v.map(String).filter(Boolean) : v ? [String(v)] : []);
  return { summary: String(a.summary || ''), changes: String(a.changes || ''), verified: arr(a.verified), hypotheses: arr(a.hypotheses), risks: arr(a.risks), sources: arr(a.sources) };
}

export const MODES = {
  TASK: { label: 'Tarefa', detail: 'Escrever, alterar ou refatorar código' },
  REPAIR: { label: 'Consertar erro', detail: 'Cole o erro ou o log: o Codex acha a causa, corrige e confere' },
  EXPLAIN: { label: 'Explicar', detail: 'Entender um arquivo, uma parte ou o projeto' },
  SECURITY: { label: 'Revisar segurança', detail: 'Procura vulnerabilidades e segredos expostos' },
};

export const VERIFICATION = {
  NONE: 'Nada aplicado',
  SYNTAX_ONLY: 'Só sintaxe: os testes do projeto rodam no repositório (GitHub Actions)',
  TESTS_PASSED: 'Testes do projeto passaram',
  TESTS_FAILED: 'Testes do projeto falharam',
  ERROR_GONE: 'O erro não aparece mais e os testes passaram',
  NOT_FIXED: 'O erro continua',
};

/**
 * Uma sessão do Codex. onChange() avisa a tela; approve()/reject()/adjust() respondem ao diff.
 * Máximo de 3 tentativas no auto-reparo, e para se o mesmo erro voltar.
 */
export class Session {
  constructor(opts) {
    Object.assign(this, { pollMs: 10000, timeoutMs: 25 * 60000, sleep: (ms) => new Promise((r) => setTimeout(r, ms)) }, opts);
    this.events = [];
    this.phase = 'IDLE';
    this.diffs = [];
    this.report = null;
    this.review = null;
    this.verification = 'NONE';
    this.applied = null;
    this.restore = null;
    this.attempt = 0;
    this.cost = 0;
    this.pick = null;
    this.checkUrl = null;
    this.autoApplied = false;
    this.cancelled = false;
  }

  emit(e) {
    this.events.push(e);
    this.onChange?.();
  }

  set(phase) {
    this.phase = phase;
    this.presence?.(phase, this);
    this.onChange?.();
  }

  decide(d) {
    this.decision?.(d);
  }

  async start() {
    try {
      await this.run();
    } catch (e) {
      this.emit({ type: 'failure', text: e.message || String(e) });
      this.set('FAILED');
    }
  }

  async run() {
    this.set('PREPARING');
    const models = await this.models();
    if (!models.length) {
      this.emit({ type: 'failure', text: 'O Codex precisa do serviço online (modelos de programação). Conecte o L.I.L.Y Gateway em Configurações.' });
      return this.set('FAILED');
    }
    const roleKey = this.mode === 'REPAIR' ? 'DEBUG' : this.mode === 'EXPLAIN' ? 'EXPLAIN' : this.mode === 'SECURITY' ? 'SECURITY' : classify(this.task);
    const fileList = async () => (await this.workspace.files()).map((f) => f.path);
    let error = this.mode === 'REPAIR' && this.errorText ? parseError(this.errorText, await fileList()) : null;
    const seen = new Map();
    if (error) seen.set(error.signature, 1);
    const max = this.mode === 'REPAIR' ? 3 : 1;
    let extra = '';
    while (this.attempt < max) {
      if (this.cancelled) return this.set('CANCELLED');
      this.attempt++;
      const pick = pickCode(roleKey, models, this.profile, { manual: this.manual, escalate: this.attempt > 1 });
      this.pick = pick;
      this.emit({ type: 'plan', text: `${this.attempt > 1 ? `Tentativa ${this.attempt} · ` : ''}Modelo: ${label(pick.model)} (${pick.model.vendor}) · ${pick.reasons.join(' ')}` });
      this.set('WORKING');
      const agent = new Agent({ workspace: this.workspace, llm: this.llmFor(pick.model), gateway: this.gateway, web: this.web, emit: (e) => this.emit(e), fetchPage: this.fetchPage });
      let ctx = '';
      if (error) {
        ctx += `ERROR TO FIX (type ${error.type}): ${error.message}\n`;
        if (error.locations.length) ctx += `Locations found in the project: ${error.locations.map(([p, l]) => `${p}:${l}`).join(', ')}\n`;
        ctx += `Raw log (tail):\n${error.raw.slice(-6000)}\nSteps: 1) understand the error and the logs 2) open the files and lines above 3) investigate related files 4) find the most probable cause 5) make the smallest correct fix 6) run check.\n`;
      }
      ctx += extra;
      const taskText = this.mode === 'REPAIR' ? this.task || 'Corrija o erro abaixo.' : this.mode === 'EXPLAIN' ? `Explique (não altere arquivos): ${this.task || 'o projeto'}` : this.mode === 'SECURITY' ? `Revise a segurança (vulnerabilidades, segredos expostos, permissões excessivas). Só proponha mudanças se forem seguras e pequenas. ${this.task}` : this.task;
      const finish = await agent.run(taskText, ctx, roleKey === 'QUICK_FIX' || roleKey === 'EXPLAIN' ? 14 : 28);
      this.cost += agent.cost;
      this.report = finish;
      const changes = agent.staged.changes();
      this.diffs = changes.map((c) => fileDiff(c.path, c.before, c.after));
      if (!finish && !changes.length) return this.set('FAILED');
      if (!changes.length) {
        this.emit({ type: 'note', text: this.mode === 'REPAIR' ? 'O Codex não propôs mudanças: veja o relatório.' : 'Nenhum arquivo alterado.' });
        return this.set('DONE');
      }
      const syntaxOk = agent.checks().ok;
      if (this.reviewEnabled) {
        this.set('REVIEWING');
        const rp = pickCode('REVIEW', models, this.profile, { avoidVendor: pick.model.vendor });
        if (rp && rp.model.id !== pick.model.id) {
          try {
            this.review = await this.reviewWith(this.llmFor(rp.model), label(rp.model), taskText, finish, changes);
            this.emit({ type: 'note', text: this.review.ok ? `Revisão (${this.review.reviewer}): sem problemas` : `Revisão (${this.review.reviewer}): ${this.review.issues.length} ponto(s) de atenção` });
          } catch {
            this.review = null;
          }
        }
      }
      const lines = this.diffs.reduce((n, d) => n + d.added + d.removed, 0);
      const auto = this.autoLines != null && changes.length === 1 && lines <= this.autoLines && syntaxOk && this.review?.ok !== false && this.mode !== 'SECURITY';
      let d;
      if (auto) {
        this.autoApplied = true;
        this.emit({ type: 'note', text: `Correção simples aplicada sozinha (modo automático, ${lines} linhas). Dá para desfazer.` });
        d = { type: 'approve' };
      } else {
        d = await new Promise((resolve) => {
          this.decision = resolve;
          this.set('AWAITING_APPROVAL');
        });
      }
      if (d.type === 'reject') {
        this.emit({ type: 'note', text: 'Mudanças descartadas. Nada foi gravado.' });
        this.diffs = [];
        return this.set('DONE');
      }
      if (d.type === 'adjust') {
        extra = `The user reviewed your previous proposal and asked: ${d.request}\nPrevious diff:\n${this.diffs.map(unified).join('\n').slice(0, 8000)}`;
        this.attempt--;
        continue;
      }
      this.set('APPLYING');
      this.restore = changes;
      const result = await this.workspace.apply(changes, `${MODES[this.mode].label}: ${(this.task || error?.message || 'Codex').slice(0, 72)}`);
      this.applied = result;
      this.emit({ type: 'note', text: `Aplicado ${result.where}. Ponto de restauração criado.` });
      this.set('VALIDATING');
      if (this.workspace.kind === 'repo' && this.workspace.tests && this.gateway) {
        const out = await this.runCi();
        if (!out) {
          this.verification = syntaxOk ? 'SYNTAX_ONLY' : 'TESTS_FAILED';
          this.emit({ type: 'note', text: 'Os testes do projeto não terminaram a tempo: a verificação ficou só na sintaxe.' });
          break;
        }
        if (out.ok) {
          const still = error && out.log.includes(error.message.slice(0, 60));
          this.verification = !error ? 'TESTS_PASSED' : still ? 'NOT_FIXED' : 'ERROR_GONE';
          if (!still) break;
        } else {
          const next = parseError(out.log, await fileList());
          const count = (seen.get(next.signature) || 0) + 1;
          seen.set(next.signature, count);
          this.verification = this.mode === 'REPAIR' ? 'NOT_FIXED' : 'TESTS_FAILED';
          if (this.mode !== 'REPAIR') break;
          if (count >= 2) {
            this.emit({ type: 'failure', text: 'O mesmo erro voltou depois de duas tentativas diferentes. Parei para não ficar em ciclo: veja o relatório e o diff.' });
            break;
          }
          this.emit({ type: 'note', text: `Os testes ainda falham (${next.type}). Nova tentativa com outra abordagem.` });
          extra = `Your previous fix was applied but the project tests still fail. Do NOT repeat the same approach (${(finish?.changes || '').slice(0, 300)}). New failure:\n${out.log.slice(-5000)}`;
          error = next;
          continue;
        }
      } else {
        this.verification = syntaxOk ? 'SYNTAX_ONLY' : 'TESTS_FAILED';
        this.emit({ type: 'note', text: syntaxOk ? 'Sintaxe conferida. Os testes do projeto precisam do repositório com o GitHub Actions (ou do seu terminal).' : 'A verificação de sintaxe encontrou problemas nos arquivos aplicados: veja o diff ou desfaça.' });
        break;
      }
    }
    this.set('DONE');
  }

  async runCi() {
    const ws = this.workspace;
    const started = await this.gateway.checkStart(ws.repo, ws.branch, ws.tests.runner, ws.tests.tasks, ws.tests.workdir);
    this.emit({ type: 'action', text: 'Rodando os testes do projeto no GitHub Actions', detail: `${ws.tests.runner} ${ws.tests.tasks}` });
    const deadline = Date.now() + this.timeoutMs;
    while (Date.now() < deadline) {
      await this.sleep(this.pollMs);
      let s;
      try {
        s = await this.gateway.checkStatus(ws.repo, ws.branch, started.startedAt || new Date().toISOString());
      } catch {
        continue;
      }
      if (s.url) this.checkUrl = s.url;
      if (s.status === 'completed') {
        const ok = s.conclusion === 'success';
        this.emit({ type: 'checks', ok, text: ok ? 'Testes do projeto passaram' : `Testes do projeto falharam (${s.conclusion})` });
        return { ok, log: (s.failures || []).map((f) => `[${f.job}]\n${f.log}`).join('\n\n') };
      }
    }
    return null;
  }

  async reviewWith(llm, reviewer, task, finish, changes) {
    const diff = changes.map((c) => unified(fileDiff(c.path, c.before, c.after))).join('\n').slice(0, 30000);
    const r = await llm.complete([
      { role: 'system', content: 'You are an independent senior code reviewer. Check the proposed diff for bugs, regressions, security problems and invented APIs. Reply ONLY with JSON: {"ok": true|false, "issues": ["short issue in Brazilian Portuguese", ...]}. ok=false only for real problems.' },
      { role: 'user', content: `Task: ${task}\nAuthor summary: ${finish?.summary || ''}\n\nDiff:\n${diff}` },
    ]);
    if (r.costUsd) this.cost += r.costUsd;
    const s = r.text;
    try {
      const obj = JSON.parse(s.slice(s.indexOf('{'), s.lastIndexOf('}') + 1));
      return { reviewer, ok: obj.ok !== false, issues: (obj.issues || []).map(String) };
    } catch {
      return { reviewer, ok: true, issues: [`Revisão sem formato: ${s.slice(0, 200)}`] };
    }
  }

  async undo() {
    if (!this.restore) return;
    const inverse = this.restore.map((c) => ({ path: c.path, before: c.after, after: c.before }));
    const r = await this.workspace.apply(inverse, 'Desfaz a mudança do Codex');
    this.emit({ type: 'note', text: `Desfeito ${r.where}. Os arquivos voltaram ao que eram antes.` });
    this.restore = null;
    this.verification = 'NONE';
    this.onChange?.();
  }
}

export function branchName() {
  return `lily-codex/${new Date().toISOString().slice(0, 10)}-${Math.random().toString(36).slice(2, 8)}`;
}

/** Repositório pelo gateway: lê do ramo do Codex (se existir) ou da origem; grava num commit no ramo. */
export class RepoWorkspace {
  constructor(gateway, repo, base, branch, tests) {
    Object.assign(this, { gateway, repo, base, branch, tests, kind: 'repo', name: repo, kindLabel: 'Repositório (GitHub)' });
    this.ref = base;
    this.listing = null;
  }
  async files() {
    if (this.listing) return this.listing;
    let t;
    try {
      t = await this.gateway.tree(this.repo, this.branch);
      this.ref = this.branch;
    } catch {
      t = await this.gateway.tree(this.repo, this.base);
      this.ref = this.base;
    }
    this.listing = (t.files || []).map((f) => ({ path: f.path, size: f.size || 0 }));
    return this.listing;
  }
  async read(p) {
    await this.files();
    const f = await this.gateway.file(this.repo, this.ref, safePath(p));
    return f.exists ? f.content : null;
  }
  async apply(changes, message) {
    const r = await this.gateway.commit(this.repo, this.base, this.branch, message, changes.map((c) => ({ path: safePath(c.path), content: c.after })));
    this.ref = this.branch;
    this.listing = null;
    return { where: `no ramo ${r.branch} (commit ${String(r.commit).slice(0, 7)})`, url: r.url };
  }
}

/** Uma pasta do computador (File System Access API: Chrome, Edge e derivados). */
export class DirWorkspace {
  constructor(handle) {
    Object.assign(this, { handle, kind: 'folder', name: handle.name, kindLabel: 'Pasta do computador', tests: null });
  }
  async files() {
    const out = [];
    const walk = async (dir, prefix) => {
      for await (const [name, h] of dir.entries()) {
        if (out.length >= 8000) return;
        const path = prefix ? `${prefix}/${name}` : name;
        if (h.kind === 'directory') {
          if (!SKIP.test(path + '/')) await walk(h, path);
        } else {
          const f = await h.getFile();
          out.push({ path, size: f.size });
        }
      }
    };
    await walk(this.handle, '');
    return out;
  }
  async fileHandle(p, create) {
    const parts = safePath(p).split('/');
    let dir = this.handle;
    for (const part of parts.slice(0, -1)) dir = await dir.getDirectoryHandle(part, { create });
    return { dir, name: parts[parts.length - 1], file: await dir.getFileHandle(parts[parts.length - 1], { create }).catch(() => null) };
  }
  async read(p) {
    try {
      const { file } = await this.fileHandle(p, false);
      if (!file) return null;
      const f = await file.getFile();
      return f.size > 400000 ? null : await f.text();
    } catch {
      return null;
    }
  }
  async apply(changes) {
    for (const c of changes) {
      if (c.after == null) {
        const { dir, name } = await this.fileHandle(c.path, false);
        await dir.removeEntry(name).catch(() => {});
        continue;
      }
      const { file } = await this.fileHandle(c.path, true);
      const w = await file.createWritable();
      await w.write(c.after);
      await w.close();
    }
    return { where: `na pasta ${this.name}` };
  }
}

/** Projeto em memória (testes). */
export class MemoryWorkspace {
  constructor(files, name = 'memória') {
    Object.assign(this, { map: new Map(Object.entries(files)), kind: 'memory', name, kindLabel: 'Memória', tests: null });
  }
  async files() {
    return [...this.map.entries()].filter(([p]) => !SKIP.test(p)).map(([path, c]) => ({ path, size: c.length }));
  }
  async read(p) {
    return this.map.has(p) ? this.map.get(p) : null;
  }
  async apply(changes) {
    for (const c of changes) c.after == null ? this.map.delete(c.path) : this.map.set(c.path, c.after);
    return { where: 'na memória' };
  }
}
