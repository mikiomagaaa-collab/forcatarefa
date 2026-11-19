// Diff por linhas (o mesmo algoritmo do app): começo e fim iguais, maior subsequência comum no meio.

const MAX_CELLS = 4_000_000;

export function lines(text) {
  if (text == null || text === '') return [];
  return text.replace(/\n$/, '').split('\n');
}

export function script(a, b) {
  let prefix = 0;
  while (prefix < a.length && prefix < b.length && a[prefix] === b[prefix]) prefix++;
  let suffix = 0;
  while (suffix < a.length - prefix && suffix < b.length - prefix && a[a.length - 1 - suffix] === b[b.length - 1 - suffix]) suffix++;
  const am = a.slice(prefix, a.length - suffix);
  const bm = b.slice(prefix, b.length - suffix);
  const out = [];
  for (let i = 0; i < prefix; i++) out.push({ kind: ' ', text: a[i], oldLine: i + 1, newLine: i + 1 });
  if (am.length * bm.length > MAX_CELLS) {
    am.forEach((s, i) => out.push({ kind: '-', text: s, oldLine: prefix + i + 1, newLine: 0 }));
    bm.forEach((s, j) => out.push({ kind: '+', text: s, oldLine: 0, newLine: prefix + j + 1 }));
  } else {
    const n = am.length;
    const m = bm.length;
    const lcs = Array.from({ length: n + 1 }, () => new Int32Array(m + 1));
    for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) lcs[i][j] = am[i] === bm[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    let i = 0;
    let j = 0;
    while (i < n || j < m) {
      if (i < n && j < m && am[i] === bm[j]) {
        out.push({ kind: ' ', text: am[i], oldLine: prefix + i + 1, newLine: prefix + j + 1 });
        i++;
        j++;
      } else if (i < n && (j === m || lcs[i + 1][j] >= lcs[i][j + 1])) {
        out.push({ kind: '-', text: am[i], oldLine: prefix + i + 1, newLine: 0 });
        i++;
      } else {
        out.push({ kind: '+', text: bm[j], oldLine: 0, newLine: prefix + j + 1 });
        j++;
      }
    }
  }
  const baseA = a.length - suffix;
  const baseB = b.length - suffix;
  for (let k = 0; k < suffix; k++) out.push({ kind: ' ', text: a[baseA + k], oldLine: baseA + k + 1, newLine: baseB + k + 1 });
  return out;
}

export function hunks(sc, context = 3) {
  const changed = [];
  sc.forEach((l, i) => l.kind !== ' ' && changed.push(i));
  if (!changed.length) return [];
  const groups = [];
  let start = Math.max(0, changed[0] - context);
  let end = Math.min(sc.length - 1, changed[0] + context);
  for (const idx of changed.slice(1)) {
    if (idx - context <= end + 1) end = Math.min(sc.length - 1, idx + context);
    else {
      groups.push([start, end]);
      start = Math.max(0, idx - context);
      end = Math.min(sc.length - 1, idx + context);
    }
  }
  groups.push([start, end]);
  return groups.map(([s, e]) => {
    const ls = sc.slice(s, e + 1);
    const before = sc.slice(0, s);
    const oldStart = ls.find((l) => l.oldLine > 0)?.oldLine ?? before.filter((l) => l.oldLine > 0).pop()?.oldLine ?? 0;
    const newStart = ls.find((l) => l.newLine > 0)?.newLine ?? before.filter((l) => l.newLine > 0).pop()?.newLine ?? 0;
    const oldCount = ls.filter((l) => l.kind !== '+').length;
    const newCount = ls.filter((l) => l.kind !== '-').length;
    return { header: `@@ -${oldStart},${oldCount} +${newStart},${newCount} @@`, lines: ls };
  });
}

export function fileDiff(path, before, after, context = 3) {
  const sc = script(lines(before), lines(after));
  return {
    path,
    hunks: hunks(sc, context),
    added: sc.filter((l) => l.kind === '+').length,
    removed: sc.filter((l) => l.kind === '-').length,
    created: before == null,
    deleted: after == null,
  };
}

export function unified(d) {
  let s = `--- ${d.created ? '/dev/null' : 'a/' + d.path}\n+++ ${d.deleted ? '/dev/null' : 'b/' + d.path}\n`;
  for (const h of d.hunks) {
    s += h.header + '\n';
    for (const l of h.lines) s += l.kind + l.text + '\n';
  }
  return s;
}
