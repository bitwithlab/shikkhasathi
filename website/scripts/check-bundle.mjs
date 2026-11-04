#!/usr/bin/env node
// Enforces CLAUDE.md §5.3 JS budgets (gzip) using the Vite manifest's real import graph:
//   landing (entry + landing route) ≤ 150 KB · student shell + /home ≤ 200 KB · any lazy chunk ≤ 100 KB
import { readFileSync, existsSync, writeFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(root, 'dist');
const mf = join(dist, '.vite/manifest.json');
if (!existsSync(mf)) {
  console.error('build the client first (manifest missing)');
  process.exit(1);
}
const manifest = JSON.parse(readFileSync(mf, 'utf8'));
const size = new Map();
const kb = (file) => {
  if (!size.has(file)) size.set(file, gzipSync(readFileSync(join(dist, file))).length / 1024);
  return size.get(file);
};
/** Static import closure (what the browser must download before the route renders). */
function closure(keys) {
  const seen = new Set();
  const walk = (k) => {
    const e = manifest[k];
    if (!e || seen.has(e.file)) return;
    seen.add(e.file);
    (e.imports ?? []).forEach(walk);
  };
  keys.forEach(walk);
  return [...seen].filter((f) => f.endsWith('.js'));
}
const entry = Object.keys(manifest).find((k) => manifest[k].isEntry);
const key = (suffix) => Object.keys(manifest).find((k) => k.endsWith(suffix));
const total = (files) => files.reduce((a, f) => a + kb(f), 0);

const routes = {
  landing: closure([entry, key('features/marketplace/LandingPage.tsx')]),
  home: closure([entry, key('app/shells/StudentShell.tsx'), key('features/student/home/HomePage.tsx')]),
};
let failed = false;
const results = [];
const report = (name, v, max) => {
  const ok = v <= max;
  if (!ok) failed = true;
  results.push({ name, kb: +v.toFixed(1), budget: max, ok });
  console.info(`${ok ? '✓' : '✗'} ${name}: ${v.toFixed(1)} KB gzip (budget ${max})`);
};
report('landing (initial)', total(routes.landing), 150);
report('student shell + /home', total(routes.home), 200);
const lazy = Object.values(manifest).filter((e) => e.file.endsWith('.js') && !e.isEntry).map((e) => [e.file, kb(e.file)]);
for (const [f, v] of lazy) if (v > 100) report(`chunk ${f}`, v, 100);
const largest = lazy.sort((a, b) => b[1] - a[1]).slice(0, 6);
console.info('largest chunks:', largest.map(([f, v]) => `${f.replace('assets/', '')} ${v.toFixed(1)}KB`).join(', '));
writeFileSync(join(dist, 'bundle-report.json'), JSON.stringify({ results, largest }, null, 2));
process.exit(failed ? 1 : 0);
