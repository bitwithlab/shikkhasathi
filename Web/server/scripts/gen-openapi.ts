// Generates OpenAPI 3.1 from the shared Zod contracts (single source of truth) → docs/api/openapi.json
// and Web/shared/openapi.json (consumed by the mobile app).
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildOpenApi } from '../src/openapi';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const doc = buildOpenApi();
for (const p of [resolve(root, 'docs/api/openapi.json'), resolve(root, 'Web/shared/openapi.json')]) {
  mkdirSync(dirname(p), { recursive: true });
  writeFileSync(p, JSON.stringify(doc, null, 2) + '\n');
}
console.info(`openapi: ${Object.keys(doc.paths).length} paths`);
