// Linguagens e verificadores do Codex (os mesmos do app): parênteses, textos e comentários,
// JSON, XML/HTML, YAML e Python. Novas linguagens entram com uma linha em LANGUAGES.

const PAIRS = { ')': '(', ']': '[', '}': '{' };

function regexStart(text, i) {
  if (i + 1 < text.length && (text[i + 1] === '/' || text[i + 1] === '*')) return false;
  let j = i - 1;
  while (j >= 0 && (text[j] === ' ' || text[j] === '\t')) j--;
  if (j < 0 || text[j] === '\n') return true;
  if ('(,=:[!&|?{};+-*%<>~^'.includes(text[j])) return true;
  const word = text.slice(0, j + 1).match(/[A-Za-z]+$/)?.[0];
  return ['return', 'typeof', 'case', 'in', 'of', 'delete', 'void', 'throw'].includes(word);
}

export function checkDelimiters(path, text, syn) {
  const problems = [];
  const stack = [];
  const interp = [];
  let i = 0;
  let line = 1;
  const at = (s) => text.startsWith(s, i);
  const push = (l, m) => problems.push({ path, line: l, message: m });
  while (i < text.length && problems.length < 20) {
    const ch = text[i];
    if (ch === '\n') {
      line++;
      i++;
    } else if (syn.lineComment && at(syn.lineComment)) {
      while (i < text.length && text[i] !== '\n') i++;
    } else if (syn.block && at(syn.block[0])) {
      const start = line;
      let depth = 1;
      i += syn.block[0].length;
      while (i < text.length && depth > 0) {
        if (text[i] === '\n') line++;
        if (syn.nested && at(syn.block[0])) { depth++; i += syn.block[0].length; continue; }
        if (at(syn.block[1])) { depth--; i += syn.block[1].length; continue; }
        i++;
      }
      if (depth > 0) push(start, 'Comentário de bloco sem fechar');
    } else if ((syn.raw || syn.triple) && (at('"""') || (syn.triple && at("'''")))) {
      const q = text.slice(i, i + 3);
      const start = line;
      i += 3;
      let closed = false;
      while (i < text.length) {
        if (text[i] === '\n') line++;
        if (at(q)) { i += 3; closed = true; break; }
        i++;
      }
      if (!closed) push(start, `Texto de várias linhas (${q}) sem fechar`);
    } else if (ch === '"' || (ch === "'" && (syn.singleQuote || syn.templates)) || (ch === '`' && syn.templates)) {
      const start = line;
      const quote = ch;
      i++;
      let closed = false;
      while (i < text.length) {
        const c = text[i];
        if (c === '\\') { i += 2; continue; }
        if (c === '\n') {
          if (quote !== '`') break;
          line++;
        }
        if (quote === '"' && syn.raw && at('${')) {
          let depth = 1;
          i += 2;
          while (i < text.length && depth > 0) {
            if (text[i] === '{') depth++;
            else if (text[i] === '}') depth--;
            else if (text[i] === '"') { i++; while (i < text.length && text[i] !== '"' && text[i] !== '\n') { if (text[i] === '\\') i++; i++; } }
            i++;
          }
          continue;
        }
        if (quote === '`' && at('${')) { interp.push(0); i += 2; closed = true; break; }
        if (c === quote) { i++; closed = true; break; }
        i++;
      }
      if (!closed) push(start, `Texto (${quote}) sem fechar na linha`);
    } else if (ch === "'" && syn.charLiterals) {
      const end = text.indexOf("'", i + 1);
      if (end >= i + 2 && end <= i + 8 && !text.slice(i, end).includes('\n')) i = end + 1;
      else { push(line, 'Apóstrofo sem fechar'); i++; }
    } else if (ch === '/' && syn.templates && regexStart(text, i)) {
      i++;
      let inClass = false;
      while (i < text.length && text[i] !== '\n') {
        const c = text[i];
        if (c === '\\') { i += 2; continue; }
        if (c === '[') inClass = true;
        if (c === ']') inClass = false;
        if (c === '/' && !inClass) { i++; break; }
        i++;
      }
    } else if (ch === '(' || ch === '[' || ch === '{') {
      stack.push([ch, line]);
      if (ch === '{' && interp.length) interp[interp.length - 1]++;
      i++;
    } else if (ch === ')' || ch === ']' || ch === '}') {
      if (ch === '}' && interp.length && interp[interp.length - 1] === 0) {
        interp.pop();
        i++;
        let closed = false;
        while (i < text.length) {
          const c = text[i];
          if (c === '\\') { i += 2; continue; }
          if (c === '\n') line++;
          if (at('${')) { interp.push(0); i += 2; closed = true; break; }
          if (c === '`') { i++; closed = true; break; }
          i++;
        }
        if (!closed) push(line, 'Template (`) sem fechar');
        continue;
      }
      if (ch === '}' && interp.length) interp[interp.length - 1]--;
      const open = stack[stack.length - 1];
      if (!open || open[0] !== PAIRS[ch]) {
        push(line, `"${ch}" sem a abertura correspondente` + (open ? ` (aberto: "${open[0]}" na linha ${open[1]})` : ''));
        if (open && stack.some((s) => s[0] === PAIRS[ch])) {
          while (stack.length && stack[stack.length - 1][0] !== PAIRS[ch]) stack.pop();
          stack.pop();
        }
      } else stack.pop();
      i++;
    } else i++;
  }
  stack.slice(-5).forEach(([c, l]) => push(l, `"${c}" aberto e nunca fechado`));
  return problems.slice(0, 20);
}

function checkJson(path, text) {
  try {
    JSON.parse(text);
    return [];
  } catch (e) {
    const pos = Number((e.message.match(/position (\d+)/) || [])[1]);
    const line = Number.isFinite(pos) ? text.slice(0, pos).split('\n').length : 1;
    return [{ path, line, message: `JSON inválido: ${e.message.slice(0, 160)}` }];
  }
}

const VOID = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr', '!doctype', '?xml']);
const OPTIONAL = new Set(['p', 'li', 'td', 'th', 'tr', 'option', 'thead', 'tbody', 'tfoot', 'dt', 'dd']);

function checkTags(path, text, html) {
  const clean = text
    .replace(/<!--[\s\S]*?-->/g, (m) => m.replace(/[^\n]/g, ' '))
    .replace(/(<(script|style)\b[^>]*>)([\s\S]*?)(<\/\2\s*>)/gi, (m, a, _t, body, b) => a + body.replace(/[^\n]/g, ' ') + b);
  const stack = [];
  const problems = [];
  const re = /<(\/?)([A-Za-z!?][\w:.-]*)([^>]*?)(\/?)>/g;
  let m;
  while ((m = re.exec(clean))) {
    const closing = m[1] === '/';
    const tag = html ? m[2].toLowerCase() : m[2];
    const line = clean.slice(0, m.index).split('\n').length;
    if ((html && VOID.has(tag)) || m[4] === '/' || tag.startsWith('!') || tag.startsWith('?')) continue;
    if (!closing) { stack.push([tag, line]); continue; }
    if (html) while (stack.length && stack[stack.length - 1][0] !== tag && OPTIONAL.has(stack[stack.length - 1][0])) stack.pop();
    if (!stack.length || stack[stack.length - 1][0] !== tag) problems.push({ path, line, message: `</${tag}> sem a abertura correspondente` });
    else stack.pop();
  }
  stack.filter(([t]) => !(html && (OPTIONAL.has(t) || ['html', 'head', 'body'].includes(t)))).forEach(([t, l]) => problems.push({ path, line: l, message: `<${t}> aberta e nunca fechada` }));
  return problems.slice(0, 20);
}

function checkYaml(path, text) {
  const problems = [];
  text.split('\n').forEach((raw, i) => {
    if (!raw.trim() || raw.trim().startsWith('#')) return;
    const indent = raw.match(/^[ \t]*/)[0];
    if (indent.includes('\t')) problems.push({ path, line: i + 1, message: 'Tabulação na indentação (YAML só aceita espaços)' });
  });
  return problems.slice(0, 20);
}

function checkPython(path, text) {
  const problems = [];
  const ls = text.split('\n');
  let tabs = false;
  let spaces = false;
  ls.forEach((raw, i) => {
    if (!raw.trim()) return;
    const indent = raw.match(/^[ \t]*/)[0];
    if (indent.includes('\t')) tabs = true;
    if (indent.includes(' ')) spaces = true;
    const code = raw.trim().replace(/\s+#.*$/, '');
    if (code.endsWith(':') && /^(def|class|if|elif|else|for|while|try|except|finally|with|async def|async for|async with|match|case)\b/.test(code)) {
      const next = ls.slice(i + 1).find((l) => l.trim() && !l.trim().startsWith('#'));
      if (next != null && next.match(/^[ \t]*/)[0].length <= indent.length) problems.push({ path, line: i + 1, message: 'Bloco depois de ":" sem indentação' });
    }
  });
  if (tabs && spaces) problems.push({ path, line: 1, message: 'Mistura de tabulações e espaços na indentação' });
  return problems.concat(checkDelimiters(path, text, { lineComment: '#', singleQuote: true, triple: true })).slice(0, 20);
}

const C_LIKE = (raw, templates, nested) => (p, t) => checkDelimiters(p, t, { lineComment: '//', block: ['/*', '*/'], nested, raw, templates, charLiterals: !templates, singleQuote: templates });

export const LANGUAGES = [
  { id: 'kotlin', name: 'Kotlin', ext: ['kt', 'kts'], check: C_LIKE(true, false, true), kind: 'chaves, parênteses e textos' },
  { id: 'java', name: 'Java', ext: ['java'], check: C_LIKE(false, false, false), kind: 'chaves, parênteses e textos' },
  { id: 'javascript', name: 'JavaScript', ext: ['js', 'mjs', 'cjs', 'jsx'], check: C_LIKE(false, true, false), kind: 'chaves, parênteses e textos' },
  { id: 'typescript', name: 'TypeScript', ext: ['ts', 'tsx', 'mts', 'cts'], check: C_LIKE(false, true, false), kind: 'chaves, parênteses e textos' },
  { id: 'groovy', name: 'Gradle (Groovy)', ext: ['gradle', 'groovy'], check: (p, t) => checkDelimiters(p, t, { lineComment: '//', block: ['/*', '*/'], singleQuote: true, triple: true }), kind: 'chaves, parênteses e textos' },
  { id: 'css', name: 'CSS', ext: ['css', 'scss'], check: (p, t) => checkDelimiters(p, t, { lineComment: p.endsWith('.scss') ? '//' : null, block: ['/*', '*/'], singleQuote: true }), kind: 'chaves e textos' },
  { id: 'xml', name: 'XML', ext: ['xml', 'svg', 'xsd'], check: (p, t) => checkTags(p, t, false), kind: 'XML bem formado' },
  { id: 'html', name: 'HTML', ext: ['html', 'htm'], check: (p, t) => checkTags(p, t, true), kind: 'tags abertas e fechadas' },
  { id: 'json', name: 'JSON', ext: ['json'], check: checkJson, kind: 'JSON válido' },
  { id: 'yaml', name: 'YAML', ext: ['yml', 'yaml'], check: checkYaml, kind: 'estrutura básica' },
  { id: 'python', name: 'Python', ext: ['py'], check: checkPython, kind: 'indentação, parênteses e textos' },
  { id: 'sql', name: 'SQL', ext: ['sql'], check: (p, t) => checkDelimiters(p, t, { lineComment: '--', block: ['/*', '*/'], singleQuote: true }), kind: 'parênteses e textos' },
  { id: 'toml', name: 'TOML', ext: ['toml'] },
  { id: 'markdown', name: 'Markdown', ext: ['md'] },
  { id: 'shell', name: 'Shell', ext: ['sh', 'bash'] },
];

export function languageOf(path) {
  const ext = path.split('/').pop().split('.').pop().toLowerCase();
  return LANGUAGES.find((l) => l.ext.includes(ext)) || null;
}

export function checkFile(path, text) {
  const lang = languageOf(path);
  if (!lang || !lang.check) return { path, language: lang?.name || '?', checked: false, problems: [], kind: 'sem verificador' };
  let problems;
  try {
    problems = lang.check(path, text);
  } catch (e) {
    problems = [{ path, line: 1, message: `Verificador falhou: ${e.message}` }];
  }
  return { path, language: lang.name, checked: true, problems, kind: lang.kind };
}

// --- Erros -------------------------------------------------------------------------------------

function matchPath(path, files) {
  const clean = path.replace(/^file:\/\//, '').replace(/\\/g, '/');
  return files.find((f) => clean.endsWith('/' + f) || clean === f) || files.find((f) => clean.split('/').pop().length > 3 && f.endsWith('/' + clean.split('/').pop())) || null;
}

export function normalizeMessage(m) {
  return m.replace(/0x[0-9a-fA-F]+/g, '0x#').replace(/\d+/g, '#').slice(0, 120);
}

/** Erros de compilação, pilhas e falhas de teste → arquivos e linhas do projeto. */
export function parseError(raw, files) {
  const text = String(raw).slice(0, 40000);
  const locs = [];
  const add = (p, l) => {
    if (p && !locs.some((x) => x[0] === p && x[1] === l)) locs.push([p, l]);
  };
  for (const m of text.matchAll(/e:\s+(?:file:\/\/)?(\S+?\.(?:kt|kts|java)):(\d+)/g)) add(matchPath(m[1], files), Number(m[2]));
  for (const m of text.matchAll(/([\w./-]+\.(?:java|ts|tsx|js|mjs|kt|py|xml|gradle|kts))[:(](\d+)/g)) add(matchPath(m[1], files), Number(m[2]));
  for (const m of text.matchAll(/at\s+([\w.$]+)\.[\w$<>]+\((\w+\.(?:kt|java)):(\d+)\)/g)) {
    const pkg = m[1].split('$')[0].split('.').slice(0, -1).join('/');
    add(files.find((f) => f.endsWith(`${pkg}/${m[2]}`)) || null, Number(m[3]));
  }
  for (const m of text.matchAll(/File "([^"]+\.py)", line (\d+)/g)) add(matchPath(m[1], files), Number(m[2]));
  const ex = text.match(/([A-Za-z_][\w.]*(?:Exception|Error))(?::\s*(.+))?/);
  const type = ex ? ex[1].split('.').pop() : /^e:\s/m.test(text) ? 'Erro de compilação' : 'Erro';
  const message = (ex?.[2] || text.match(/e:\s+\S+\s+(.+)/)?.[1] || text.split('\n').find((l) => l.trim()) || '').trim().slice(0, 300);
  const first = locs[0];
  return { signature: `${type}@${first ? first[0].split('/').pop() : '?'}:${normalizeMessage(message)}`, type, message, locations: locs.slice(0, 10), raw: text };
}
