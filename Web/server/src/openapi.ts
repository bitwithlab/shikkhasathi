import { z } from 'zod';
import { ErrorEnvelope, allEndpoints, type Endpoint } from '@shikkhasathi/shared';

const toSchema = (s: z.ZodType, io: 'input' | 'output') => z.toJSONSchema(s, { target: 'openapi-3.0', io, unrepresentable: 'any' }) as Record<string, unknown>;

export function buildOpenApi() {
  const paths: Record<string, Record<string, unknown>> = {};
  for (const e of allEndpoints() as Endpoint[]) {
    const path = e.path.replace(/:([A-Za-z]+)/g, '{$1}');
    const parameters: unknown[] = [];
    for (const [where, schema] of [['path', e.params], ['query', e.query]] as const) {
      if (!schema) continue;
      const js = toSchema(schema, 'input') as { properties?: Record<string, unknown>; required?: string[] };
      for (const [name, prop] of Object.entries(js.properties ?? {})) {
        parameters.push({ name, in: where, required: where === 'path' || (js.required ?? []).includes(name), schema: prop });
      }
    }
    const security = e.auth === 'public' ? [] : e.auth === 'optional' ? [{}, { bearer: [] }] : [{ bearer: [] }];
    (paths[`/api/v1${path}`] ??= {})[e.method.toLowerCase()] = {
      tags: [e.tag],
      summary: e.summary,
      operationId: `${e.method.toLowerCase()}${path.replace(/[{}]/g, '').replace(/[/-](\w)/g, (_, c: string) => c.toUpperCase())}`,
      'x-auth': e.auth,
      security,
      ...(parameters.length ? { parameters } : {}),
      ...(e.body ? { requestBody: { required: true, content: { 'application/json': { schema: toSchema(e.body, 'input') } } } } : {}),
      responses: {
        [String(e.status ?? 200)]: { description: 'OK', content: { 'application/json': { schema: toSchema(e.response, 'output') } } },
        default: { description: 'Error', content: { 'application/json': { schema: toSchema(ErrorEnvelope, 'output') } } },
      },
    };
  }
  return {
    openapi: '3.0.3',
    info: { title: 'ShikkhaSathi API', version: '1.0.0', description: 'shikkhasathi.com/api/v1 — generated from Web/shared contracts' },
    servers: [{ url: 'https://shikkhasathi.com' }, { url: 'http://localhost:3000' }],
    components: { securitySchemes: { bearer: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' } } },
    paths,
  };
}
