// The Spring Boot backend is the source of truth for the API. Its OpenAPI document
// (docs/api/openapi.json, written by the backend's OpenApiExportTest) must cover every endpoint
// the website calls (src/api/contracts → endpoints.gen.ts), with the same method and path.
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { endpoints } from '@/api/contracts/endpoints.gen';

const specPath = resolve(__dirname, '../../../docs/api/openapi.json');

describe('website contracts ⊆ backend OpenAPI', () => {
  it.skipIf(!existsSync(specPath))('every contract endpoint exists in the backend OpenAPI', () => {
    const spec = JSON.parse(readFileSync(specPath, 'utf8')) as { paths: Record<string, Record<string, unknown>> };
    const norm = (p: string) => p.replace(/^\/api\/v1/, '').replace(/\{[^}]+\}/g, ':_').replace(/:[A-Za-z_]+/g, ':_');
    const have = new Set<string>();
    for (const [p, ops] of Object.entries(spec.paths)) for (const m of Object.keys(ops)) have.add(`${m.toUpperCase()} ${norm(p)}`);
    const missing: string[] = [];
    for (const [group, eps] of Object.entries(endpoints as Record<string, Record<string, { method: string; path: string }>>))
      for (const [name, e] of Object.entries(eps)) if (!have.has(`${e.method} ${norm(e.path)}`)) missing.push(`${group}.${name}: ${e.method} ${e.path}`);
    expect(missing).toEqual([]);
  });
});
