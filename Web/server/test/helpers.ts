import type { FastifyInstance } from 'fastify';
import { buildApp } from '../src/app';
import { coreDb } from '../src/database/client';
import { users } from '../src/database/core/schema';
import { issueSession } from '../src/modules/auth/auth.service';
import type { Role } from '@shikkhasathi/shared';

let app: FastifyInstance | null = null;
export async function getApp() {
  if (!app) {
    app = await buildApp();
    await app.ready();
  }
  return app;
}

export async function createUser(opts: { name?: string; roles?: Role[]; email?: string; phone?: string } = {}) {
  const [u] = await coreDb
    .insert(users)
    .values({ name: opts.name ?? 'Test User', roles: opts.roles ?? ['student'], email: opts.email ?? null, phone: opts.phone ?? null })
    .returning();
  const s = await issueSession(u!, { platform: 'android' });
  return { user: u!, token: s.accessToken, refreshToken: s.refreshToken, auth: { authorization: `Bearer ${s.accessToken}` } };
}
