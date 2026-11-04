// Export the API contracts as JSON Schemas (for the Spring Boot contract tests) and the integration catalog.
import { writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { z } from 'zod';
import { contracts, INTEGRATIONS } from '../src/index.ts';

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, process.argv[2] ?? '../../../backend');
const js = (s: unknown) => (s ? z.toJSONSchema(s as z.ZodType, { io: 'output', unrepresentable: 'any' }) : null);
const inJs = (s: unknown) => (s ? z.toJSONSchema(s as z.ZodType, { io: 'input', unrepresentable: 'any' }) : null);
const endpoints: Record<string, unknown> = {};
for (const [group, eps] of Object.entries(contracts)) {
  for (const [name, e] of Object.entries(eps as Record<string, any>)) {
    endpoints[`${group}.${name}`] = { method: e.method, path: e.path, auth: e.auth, status: e.status ?? 200, summary: e.summary, params: inJs(e.params), query: inJs(e.query), body: inJs(e.body), response: js(e.response) };
  }
}
mkdirSync(resolve(out, 'src/test/resources/contracts'), { recursive: true });
writeFileSync(resolve(out, 'src/test/resources/contracts/endpoints.json'), JSON.stringify(endpoints, null, 1));
writeFileSync(resolve(out, 'src/main/resources/integrations.json'), JSON.stringify(INTEGRATIONS, null, 2));
console.info(`exported ${Object.keys(endpoints).length} endpoints + ${INTEGRATIONS.length} integrations`);
