import { readFile, readdir, access } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const data = JSON.parse(await readFile(resolve(root, 'assets/data/proposals.json'), 'utf8'));
assert.equal(data.proposals.length, 35);
assert.deepEqual(data.proposals.map(p => p.id), Array.from({ length: 35 }, (_, i) => i + 1));
const labels=JSON.parse(await readFile(resolve(root,'assets/data/classifications.json'),'utf8'));
assert.equal(labels.length,35);
assert.deepEqual(labels.filter(p=>p.priority).map(p=>p.proposal_id),[5,7,17,19,27]);
assert.deepEqual(labels.filter(p=>p.early).map(p=>p.proposal_id),[5,6,10,13,20,21,22,27]);
assert.deepEqual(data.proposals.filter(p => p.approved).map(p => p.id), [8]);
assert.equal(data.categories.length, 6);
assert.equal(data.proposals.slice(30).every(p => p.category === 6 && !p.priority && !p.approved), true);
assert.equal(JSON.parse(await readFile(resolve(root, 'assets/data/team.json'), 'utf8')).length, 0);
const source = await readFile(resolve(root, 'index.html'), 'utf8');
assert(!source.includes('Portal Marasca'));
assert(source.includes('VOU VOTAR NO FORÇA TAREFA!'));
for (const file of ['index.html', 'admin/index.html', 'propostas.html', 'privacidade.html', 'proposta.html', 'projetos.html', 'faq.html', 'eleicoes.html']) {
  const html = await readFile(resolve(root, file), 'utf8');
  for (const [, path] of html.matchAll(/(?:src|href)="([^"#]+)"/g)) {
    if (/^(?:https?:|mailto:|data:)/.test(path)) continue;
    await access(resolve(root, dirname(file), path.split(/[?#]/)[0]));
  }
}
for (const file of await readdir(resolve(root, 'assets/js'))) if (file.endsWith('.js')) execFileSync(process.execPath, ['--check', resolve(root, 'assets/js', file)]);
console.log('Conteúdo, 35 propostas, prioridades, arquivos locais e sintaxe JavaScript verificados.');
