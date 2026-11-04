#!/usr/bin/env node
// Merge keys into a dictionary pair. Usage: node scripts/i18n-add.mjs [--ns features/admin] < keys.json
// Input JSON: { "key": ["বাংলা", "English"] }. Default namespace = core (src/lib/i18n).
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../src');
const nsIdx = process.argv.indexOf('--ns');
const dir = nsIdx > 0 ? resolve(root, process.argv[nsIdx + 1], 'i18n') : resolve(root, 'lib/i18n');
mkdirSync(dir, { recursive: true });
const input = JSON.parse(readFileSync(0, 'utf8'));
for (const [i, lang] of [[0, 'bn'], [1, 'en']]) {
  const p = resolve(dir, `${lang}.json`);
  const d = existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : {};
  for (const [k, v] of Object.entries(input)) d[k] = v[i];
  writeFileSync(p, JSON.stringify(d, null, 2) + '\n');
}
const idx = resolve(dir, 'index.ts');
if (nsIdx > 0 && !existsSync(idx)) {
  writeFileSync(idx, "import { registerMessages } from '@/lib/i18n';\nimport bn from './bn.json';\nimport en from './en.json';\n\nregisterMessages({ bn, en });\n");
}
console.info(`i18n(${nsIdx > 0 ? process.argv[nsIdx + 1] : 'core'}): +${Object.keys(input).length} keys`);
