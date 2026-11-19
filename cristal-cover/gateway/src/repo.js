// O repositório autorizado do Codex (GitHub). O token fica no gateway (GITHUB_TOKEN) e só vale para
// os repositórios em GITHUB_REPOS ("dono/nome,dono/outro"). O Codex nunca escreve no ramo principal:
// cada sessão vai para um ramo lily-codex/..., e os testes rodam no GitHub Actions
// (.github/workflows/lily-codex-check.yml) nesse ramo.

import { GatewayError, json, providerError, readJson, upstream } from './http.js';

const API = 'https://api.github.com';
const SKIP = /(^|\/)(\.git|\.gradle|\.idea|build|node_modules|dist|\.next|\.kotlin|__pycache__|\.venv|venv)\//;
const BRANCH = /^lily-codex\/[A-Za-z0-9._-]{1,60}$/;
const TASKS = /^[A-Za-z0-9:_. -]{1,300}$/;
const RUNNERS = ['gradle', 'npm', 'pytest'];

export function allowedRepos(env) {
  return String(env.GITHUB_REPOS || '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => /^[\w.-]+\/[\w.-]+$/.test(s));
}

function repoOf(env, value) {
  const repo = String(value || '');
  if (!env.GITHUB_TOKEN) throw new GatewayError(501, 'Repositórios não configurados no gateway (falta GITHUB_TOKEN).');
  if (!allowedRepos(env).includes(repo)) throw new GatewayError(403, `Repositório não autorizado no gateway: ${repo || '(nenhum)'}.`);
  return repo;
}

function gh(env) {
  const f = upstream(env);
  return async (path, init = {}) => {
    const res = await f(`${API}${path}`, {
      ...init,
      headers: {
        authorization: `Bearer ${env.GITHUB_TOKEN}`,
        accept: 'application/vnd.github+json',
        'x-github-api-version': '2022-11-28',
        'user-agent': 'lily-gateway',
        ...(init.body ? { 'content-type': 'application/json' } : {}),
        ...(init.headers || {}),
      },
    });
    return res;
  };
}

async function ok(res, what = 'GitHub') {
  if (!res.ok) throw await providerError(what, res);
  return res.status === 204 ? null : res.json();
}

function decodeBase64Utf8(b64) {
  const bin = atob(String(b64).replace(/\s/g, ''));
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  return new TextDecoder('utf-8', { fatal: false }).decode(bytes);
}

export async function handleRepos(env) {
  if (!env.GITHUB_TOKEN) return json({ repos: [] });
  const call = gh(env);
  const repos = [];
  for (const name of allowedRepos(env)) {
    const res = await call(`/repos/${name}`);
    if (!res.ok) {
      repos.push({ repo: name, available: false, error: `GitHub respondeu ${res.status}` });
      continue;
    }
    const r = await res.json();
    repos.push({ repo: name, available: true, defaultBranch: r.default_branch, private: !!r.private });
  }
  return json({ repos, workflow: env.CODEX_WORKFLOW || 'lily-codex-check.yml' });
}

export async function handleTree(url, env) {
  const repo = repoOf(env, url.searchParams.get('repo'));
  const ref = url.searchParams.get('ref') || 'HEAD';
  const data = await ok(await gh(env)(`/repos/${repo}/git/trees/${encodeURIComponent(ref)}?recursive=1`));
  const files = (data.tree || [])
    .filter((t) => t.type === 'blob' && !SKIP.test(`${t.path}`) && !SKIP.test(`/${t.path}`))
    .slice(0, 6000)
    .map((t) => ({ path: t.path, size: t.size || 0 }));
  return json({ repo, ref, sha: data.sha, truncated: !!data.truncated || files.length >= 6000, files });
}

export async function handleFile(url, env) {
  const repo = repoOf(env, url.searchParams.get('repo'));
  const ref = url.searchParams.get('ref') || '';
  const path = String(url.searchParams.get('path') || '');
  if (!path || path.includes('..')) throw new GatewayError(400, 'Caminho inválido.');
  const q = ref ? `?ref=${encodeURIComponent(ref)}` : '';
  const res = await gh(env)(`/repos/${repo}/contents/${path.split('/').map(encodeURIComponent).join('/')}${q}`);
  if (res.status === 404) return json({ path, exists: false });
  const data = await ok(res);
  if (Array.isArray(data) || data.type !== 'file') throw new GatewayError(400, 'Isso é uma pasta, não um arquivo.');
  if ((data.size || 0) > 400_000) throw new GatewayError(413, `Arquivo grande demais para o Codex (${Math.round(data.size / 1024)} KB).`);
  return json({ path, exists: true, sha: data.sha, content: decodeBase64Utf8(data.content || '') });
}

/**
 * Grava as mudanças num ramo lily-codex/... (cria a partir de [base] ou continua o ramo existente).
 * files: [{path, content}] (content null = apagar).
 */
export async function handleCommit(request, env) {
  const body = await readJson(request);
  const repo = repoOf(env, body.repo);
  const branch = String(body.branch || '');
  if (!BRANCH.test(branch)) throw new GatewayError(400, 'O Codex só grava em ramos lily-codex/... (nunca no principal).');
  const files = Array.isArray(body.files) ? body.files : [];
  if (!files.length || files.length > 60) throw new GatewayError(400, 'Mande de 1 a 60 arquivos.');
  for (const f of files) {
    if (typeof f.path !== 'string' || !f.path || f.path.startsWith('/') || f.path.includes('..') || SKIP.test(`/${f.path}`)) {
      throw new GatewayError(400, `Caminho não permitido: ${f.path}`);
    }
    if (f.content !== null && typeof f.content !== 'string') throw new GatewayError(400, `Conteúdo inválido em ${f.path}`);
    if (/^\.github\/workflows\//.test(f.path)) throw new GatewayError(403, 'O Codex não altera workflows do GitHub Actions.');
  }
  const message = String(body.message || 'L.I.L.Y Codex').slice(0, 2000);
  const call = gh(env);
  const existing = await call(`/repos/${repo}/git/ref/heads/${branch}`);
  let parent;
  let created = false;
  if (existing.ok) {
    parent = (await existing.json()).object.sha;
  } else if (existing.status === 404) {
    const base = String(body.base || '');
    if (!base) throw new GatewayError(400, 'Informe o ramo de origem (base).');
    parent = (await ok(await call(`/repos/${repo}/git/ref/heads/${encodeURIComponent(base)}`))).object.sha;
    created = true;
  } else {
    throw await providerError('GitHub', existing);
  }
  const commit = await ok(await call(`/repos/${repo}/git/commits/${parent}`));
  const tree = await ok(
    await call(`/repos/${repo}/git/trees`, {
      method: 'POST',
      body: JSON.stringify({
        base_tree: commit.tree.sha,
        tree: files.map((f) => (f.content === null ? { path: f.path, mode: '100644', type: 'blob', sha: null } : { path: f.path, mode: '100644', type: 'blob', content: f.content })),
      }),
    }),
  );
  const next = await ok(
    await call(`/repos/${repo}/git/commits`, {
      method: 'POST',
      body: JSON.stringify({ message: `${message}\n\nFeito pelo L.I.L.Y Codex (aprovado no aparelho).`, tree: tree.sha, parents: [parent] }),
    }),
  );
  if (created) {
    await ok(await call(`/repos/${repo}/git/refs`, { method: 'POST', body: JSON.stringify({ ref: `refs/heads/${branch}`, sha: next.sha }) }));
  } else {
    await ok(await call(`/repos/${repo}/git/refs/heads/${branch}`, { method: 'PATCH', body: JSON.stringify({ sha: next.sha, force: false }) }));
  }
  return json({ repo, branch, commit: next.sha, parent, created, url: `https://github.com/${repo}/tree/${branch}` });
}

/** Dispara os testes do projeto no ramo (workflow_dispatch). */
export async function handleCheckStart(request, env) {
  const body = await readJson(request);
  const repo = repoOf(env, body.repo);
  const branch = String(body.branch || '');
  if (!BRANCH.test(branch)) throw new GatewayError(400, 'Os testes do Codex rodam só em ramos lily-codex/...');
  const runner = String(body.runner || 'gradle');
  if (!RUNNERS.includes(runner)) throw new GatewayError(400, `Executor de testes desconhecido: ${runner}.`);
  const tasks = String(body.tasks || '').trim();
  if (!TASKS.test(tasks)) throw new GatewayError(400, 'Tarefas de teste inválidas (use nomes como :app:testDebugUnitTest).');
  const workdir = String(body.workdir || '.');
  if (!/^[\w./-]{1,100}$/.test(workdir) || workdir.includes('..')) throw new GatewayError(400, 'Pasta de trabalho inválida.');
  const workflow = env.CODEX_WORKFLOW || 'lily-codex-check.yml';
  const startedAt = new Date().toISOString();
  await ok(
    await gh(env)(`/repos/${repo}/actions/workflows/${workflow}/dispatches`, {
      method: 'POST',
      body: JSON.stringify({ ref: branch, inputs: { runner, tasks, workdir } }),
    }),
  );
  return json({ repo, branch, workflow, runner, startedAt });
}

/** Só as linhas úteis do fim do log (sem os carimbos de hora do Actions). */
export function logTail(text, lines = 120) {
  const clean = String(text)
    .split('\n')
    .map((l) => l.replace(/^\d{4}-\d{2}-\d{2}T[\d:.]+Z\s?/, ''))
    .filter((l) => l.trim() && !/^##\[(group|endgroup)\]/.test(l));
  const failIdx = clean.findIndex((l) => /FAILED|BUILD FAILED|error:|Exception|AssertionError|e: /.test(l));
  const start = failIdx >= 0 ? Math.max(0, Math.min(failIdx - 20, clean.length - lines)) : Math.max(0, clean.length - lines);
  return clean.slice(start, start + lines).join('\n');
}

export async function handleCheckStatus(url, env) {
  const repo = repoOf(env, url.searchParams.get('repo'));
  const branch = String(url.searchParams.get('branch') || '');
  if (!BRANCH.test(branch)) throw new GatewayError(400, 'Ramo inválido.');
  const since = Date.parse(url.searchParams.get('since') || '') || 0;
  const workflow = env.CODEX_WORKFLOW || 'lily-codex-check.yml';
  const call = gh(env);
  const runs = await ok(await call(`/repos/${repo}/actions/workflows/${workflow}/runs?branch=${encodeURIComponent(branch)}&event=workflow_dispatch&per_page=5`));
  const run = (runs.workflow_runs || []).find((r) => Date.parse(r.created_at) >= since - 60_000);
  if (!run) return json({ status: 'waiting' });
  if (run.status !== 'completed') return json({ status: run.status === 'queued' ? 'queued' : 'running', url: run.html_url, runId: run.id });
  const result = { status: 'completed', conclusion: run.conclusion, url: run.html_url, runId: run.id, failures: [] };
  if (run.conclusion !== 'success') {
    const jobs = await ok(await call(`/repos/${repo}/actions/runs/${run.id}/jobs`));
    for (const job of (jobs.jobs || []).filter((j) => j.conclusion && j.conclusion !== 'success' && j.conclusion !== 'skipped').slice(0, 3)) {
      const logRes = await call(`/repos/${repo}/actions/jobs/${job.id}/logs`);
      const text = logRes.ok ? await logRes.text() : `(sem log: GitHub respondeu ${logRes.status})`;
      result.failures.push({ job: job.name, log: logTail(text) });
    }
  }
  return json(result);
}
