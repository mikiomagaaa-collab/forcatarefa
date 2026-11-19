// Monta a pasta public/ que o Worker serve: a L.I.L.Y Web (../web) e os documentos legais (../legal).
import { cpSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const out = join(root, 'public');
rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
cpSync(join(root, '..', 'web'), out, { recursive: true, filter: (src) => !src.includes(`${join('web', 'test')}`) });
cpSync(join(root, '..', 'legal'), join(out, 'legal'), { recursive: true });
for (const f of ['index.html', 'js/app.js', 'legal/TERMOS-DE-USO.md']) if (!existsSync(join(out, f))) throw new Error(`faltou ${f}`);
console.log('public/ pronta: L.I.L.Y Web + legal');
