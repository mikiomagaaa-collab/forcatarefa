import { cp, mkdir, rm, readdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(root, 'dist');
if (output !== resolve(root, 'dist')) throw new Error('Invalid output path');
await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });
for (const file of ['index.html', 'calendario.html', 'propostas.html', 'privacidade.html', 'proposta.html', 'projetos.html', 'faq.html', 'eleicoes.html', 'gestao.html', 'catalogo.html', 'planejamento.html', 'historia.html', 'gremio.html', 'bem-estar.html', 'infraestrutura.html', '404.html', 'assets', 'admin', '.nojekyll']) await cp(resolve(root, file), resolve(output, file), { recursive: true });
async function collect(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) files.push(...await collect(path));
    else if (/\.(?:html|css|js)$/.test(entry.name)) files.push(path);
  }
  return files.sort();
}
const files = await collect(output);
const contents = await Promise.all(files.map(async path => (await readFile(path, 'utf8')).replace(/\r\n/g, '\n')));
const hash = createHash('sha256').update('ft-assets-v1');
files.forEach((path, index) => hash.update(path.slice(output.length).replaceAll('\\', '/')).update('\0').update(contents[index]).update('\0'));
const version = hash.digest('hex').slice(0, 12);
for (let index = 0; index < files.length; index++) {
  let text = contents[index];
  if (files[index].endsWith('.html')) text = text.replace(/((?:src|href)=["'])(?!https?:|\/\/)([^"']+\.(?:css|js))(["'])/g, `$1$2?v=${version}$3`);
  if (files[index].endsWith('.js')) text = text.replace(/(\b(?:from|import)\s*["'])(\.{1,2}\/[^"']+\.js)(["'])/g, `$1$2?v=${version}$3`);
  await writeFile(files[index], text);
}
console.log(`Versão dos estilos e módulos: ${version}. Atualizações renovam o cache sem apagar preferências.`);
console.log('Site estático pronto em dist. Dados de servidor e documentação não são publicados no Pages.');
