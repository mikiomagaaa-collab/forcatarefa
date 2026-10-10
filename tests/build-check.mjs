import assert from 'node:assert/strict';
import { readdir, readFile, access } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const root = new URL('../dist/', import.meta.url);
async function collect(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = new URL(entry.name + (entry.isDirectory() ? '/' : ''), directory);
    if (entry.isDirectory()) files.push(...await collect(file));
    else if (/\.(?:html|js)$/.test(entry.name)) files.push(file);
  }
  return files;
}
let count = 0;
const versions = new Set();
for (const file of await collect(root)) {
  const text = await readFile(file, 'utf8');
  const refs = file.pathname.endsWith('.html')
    ? [...text.matchAll(/(?:src|href)=["']([^"']+)["']/g)].map(m => m[1]).filter(r => !/^(?:https?:|\/\/)/.test(r) && /\.(?:css|js)(?:\?|$)/.test(r))
    : [...text.matchAll(/\b(?:from|import)\s*["'](\.{1,2}\/[^"']+)["']/g)].map(m => m[1]).filter(r => /\.js(?:\?|$)/.test(r));
  for (const ref of refs) {
    const version = ref.match(/\?v=([a-f0-9]{12})$/)?.[1];
    assert(version, `${fileURLToPath(file)}: ${ref}`);
    versions.add(version);
    const target = new URL(ref, file);
    target.search = '';
    await access(target);
    count++;
  }
}
assert.equal(versions.size, 1, 'Estilos e dependências devem compartilhar a versão da publicação.');
console.log(`${count} referências de estilos e módulos publicadas com versão consistente e destino existente.`);
