import type { FastifyInstance } from 'fastify';
import type { Sql } from 'postgres';
import { INTEGRATIONS, contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import { coreSql, contentSql } from '../../database/client';
import { tablesWithoutRls } from '../../database/rls';
import { redis } from '../../common/redis';
import { queueStats } from '../../common/jobs';
import { integrationMode } from '../settings/settings.service';

const VERSION = process.env.APP_VERSION ?? '0.1.0';

async function dbHealth(name: 'core' | 'content', sql: Sql) {
  const t = performance.now();
  try {
    await sql`select 1`;
    const latencyMs = Math.round(performance.now() - t);
    const mig = await sql<{ hash: string; created_at: string }[]>`select hash, created_at from drizzle.__drizzle_migrations order by created_at desc limit 1`.catch(() => []);
    const missing = await tablesWithoutRls(sql).catch(() => null);
    return { name, connected: true, latencyMs, migration: mig[0] ? `${String(mig[0].created_at)}:${mig[0].hash.slice(0, 8)}` : null, rlsOk: missing ? missing.length === 0 : null };
  } catch {
    return { name, connected: false, latencyMs: null, migration: null, rlsOk: null };
  }
}

export async function healthRoutes(app: FastifyInstance) {
  route(app, contracts.health.get, async () => {
    const [core, content] = await Promise.all([coreSql`select 1`.then(() => true, () => false), contentSql`select 1`.then(() => true, () => false)]);
    return { status: core && content ? ('ok' as const) : ('degraded' as const), version: VERSION, time: new Date().toISOString() };
  });

  route(app, contracts.health.system, async () => {
    const t = performance.now();
    const redisOk = await redis().ping().then(() => true, () => false);
    const redisLatency = redisOk ? Math.round(performance.now() - t) : null;
    return {
      databases: await Promise.all([dbHealth('core', coreSql), dbHealth('content', contentSql)]),
      redis: { connected: redisOk, latencyMs: redisLatency },
      queues: await queueStats().catch(() => []),
      integrations: await Promise.all(INTEGRATIONS.map(async (d) => ({ provider: d.provider, mode: await integrationMode(d.provider) }))),
    };
  });
}

export async function openapiRoute(app: FastifyInstance) {
  const { isProd } = await import('../../config/env');
  if (isProd) return;
  const { buildOpenApi } = await import('../../openapi');
  const doc = buildOpenApi();
  app.get('/openapi.json', async () => doc);
}
