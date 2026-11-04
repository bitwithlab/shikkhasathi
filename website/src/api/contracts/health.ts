import { z } from 'zod';
import { endpoint } from './common';

export const DbHealth = z.object({
  name: z.enum(['core', 'content']),
  connected: z.boolean(),
  latencyMs: z.number().nullable(),
  migration: z.string().nullable(),
  rlsOk: z.boolean().nullable(),
});

export const HealthResponse = z.object({
  status: z.enum(['ok', 'degraded']),
  version: z.string(),
  time: z.string(),
});

export const SystemHealthResponse = z.object({
  databases: z.array(DbHealth),
  redis: z.object({ connected: z.boolean(), latencyMs: z.number().nullable() }),
  queues: z.array(
    z.object({ name: z.string(), waiting: z.number(), active: z.number(), failed: z.number(), delayed: z.number() }),
  ),
  integrations: z.array(z.object({ provider: z.string(), mode: z.enum(['configured', 'env', 'fake', 'disabled']) })),
});

export const health = {
  get: endpoint({ method: 'GET', path: '/health', summary: 'Liveness/readiness', tag: 'system', auth: 'public', response: HealthResponse }),
  system: endpoint({
    method: 'GET',
    path: '/admin/system-health',
    summary: 'Database, Redis, queue and integration health (read-only)',
    tag: 'admin',
    auth: 'admin',
    response: SystemHealthResponse,
  }),
};
