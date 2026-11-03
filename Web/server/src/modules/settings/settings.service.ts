import { and, eq } from 'drizzle-orm';
import { INTEGRATIONS, type IntegrationDef, type IntegrationProvider, type IntegrationState } from '@shikkhasathi/shared';
import { coreDb } from '../../database/client';
import { integrationSettings, integrationTests } from '../../database/core/schema';
import { decrypt, encrypt, mask } from '../../common/crypto';
import { audit } from '../../common/audit';
import { badRequest } from '../../common/errors';
import { isProd } from '../../config/env';

const CACHE_TTL_MS = 60_000;
type Values = Record<string, string | undefined>;
const cache = new Map<IntegrationProvider, { at: number; values: Values; sources: Record<string, Source> }>();
type Source = 'admin' | 'env' | 'default' | 'unset';

export function integrationDef(provider: IntegrationProvider): IntegrationDef {
  const def = INTEGRATIONS.find((d) => d.provider === provider);
  if (!def) throw badRequest('Unknown integration');
  return def;
}

async function loadRows(provider: IntegrationProvider) {
  return coreDb.select().from(integrationSettings).where(eq(integrationSettings.provider, provider));
}

async function resolve(provider: IntegrationProvider) {
  const hit = cache.get(provider);
  if (hit && Date.now() - hit.at < CACHE_TTL_MS) return hit;
  const def = integrationDef(provider);
  const rows = await loadRows(provider);
  const values: Values = {};
  const sources: Record<string, Source> = {};
  for (const f of def.fields) {
    const row = rows.find((r) => r.key === f.key);
    if (row) {
      values[f.key] = decrypt(row.valueEncrypted);
      sources[f.key] = 'admin';
    } else if (f.env && process.env[f.env]) {
      values[f.key] = process.env[f.env];
      sources[f.key] = 'env';
    } else if (f.defaultValue !== undefined) {
      values[f.key] = f.defaultValue;
      sources[f.key] = 'default';
    } else {
      sources[f.key] = 'unset';
    }
  }
  const entry = { at: Date.now(), values, sources };
  cache.set(provider, entry);
  return entry;
}

/** Effective values for an adapter: Admin panel → env → default. */
export async function getIntegration(provider: IntegrationProvider): Promise<Values> {
  return (await resolve(provider)).values;
}

export async function getBusinessNumber(key: string, fallback: number): Promise<number> {
  const v = (await getIntegration('business'))[key];
  const n = v === undefined ? NaN : Number(v);
  return Number.isFinite(n) ? n : fallback;
}

export async function getBusinessFlag(key: string, fallback = true): Promise<boolean> {
  const v = (await getIntegration('business'))[key];
  return v === undefined ? fallback : v === 'true' || v === '1';
}

export function invalidateSettingsCache(provider?: IntegrationProvider) {
  if (provider) cache.delete(provider);
  else cache.clear();
}

/** configured = all required keys come from Admin; env = required keys satisfied (some from env); else fake/disabled. */
export async function integrationMode(provider: IntegrationProvider): Promise<IntegrationState['mode']> {
  const def = integrationDef(provider);
  const { values, sources } = await resolve(provider);
  if (def.requiredKeys.length === 0) return 'configured';
  const ok = def.requiredKeys.every((k) => !!values[k]);
  if (!ok) return isProd ? 'disabled' : 'fake';
  return def.requiredKeys.every((k) => sources[k] === 'admin') ? 'configured' : 'env';
}

export async function integrationState(provider: IntegrationProvider): Promise<IntegrationState> {
  const def = integrationDef(provider);
  const rows = await loadRows(provider);
  const { values, sources } = await resolve(provider);
  const [test] = await coreDb.select().from(integrationTests).where(eq(integrationTests.provider, provider));
  return {
    provider,
    mode: await integrationMode(provider),
    fields: def.fields.map((f) => {
      const isSecret = f.type === 'secret';
      const v = values[f.key];
      return {
        key: f.key,
        value: v === undefined ? null : isSecret ? mask(v) : v,
        source: sources[f.key] ?? 'unset',
        isSecret,
        updatedAt: rows.find((r) => r.key === f.key)?.updatedAt.toISOString() ?? null,
      };
    }),
    lastTest: test ? { ok: test.ok, message: test.message, at: test.updatedAt.toISOString() } : null,
  };
}

export async function updateIntegration(
  provider: IntegrationProvider,
  values: Record<string, string | null>,
  actorId: string,
  ip?: string,
) {
  const def = integrationDef(provider);
  const before = await integrationState(provider);
  for (const [key, value] of Object.entries(values)) {
    const field = def.fields.find((f) => f.key === key);
    if (!field) throw badRequest(`Unknown field ${key}`);
    const isSecret = field.type === 'secret';
    if (value === null) {
      await coreDb.delete(integrationSettings).where(and(eq(integrationSettings.provider, provider), eq(integrationSettings.key, key)));
      continue;
    }
    if (isSecret && value === '') continue; // write-only: empty = keep existing
    if (field.type === 'number' && value !== '' && !Number.isFinite(Number(value))) throw badRequest(`${field.label}: সংখ্যা দিন`);
    if (field.type === 'select' && field.options && !field.options.includes(value)) throw badRequest(`${field.label}: অবৈধ মান`);
    if (field.type === 'json' && value) {
      try {
        JSON.parse(value);
      } catch {
        throw badRequest(`${field.label}: বৈধ JSON দিন`);
      }
    }
    await coreDb
      .insert(integrationSettings)
      .values({ provider, key, valueEncrypted: encrypt(value), isSecret, updatedBy: actorId })
      .onConflictDoUpdate({
        target: [integrationSettings.provider, integrationSettings.key],
        set: { valueEncrypted: encrypt(value), isSecret, updatedBy: actorId, updatedAt: new Date() },
      });
  }
  invalidateSettingsCache(provider);
  const after = await integrationState(provider);
  // Audit stores masked values only.
  await audit({ actorId, action: 'integration.update', entity: 'integration_settings', entityId: provider, before: before.fields, after: after.fields, ip });
  return after;
}

export type IntegrationTester = (values: Values, opts: { to?: string; actorId: string }) => Promise<{ ok: boolean; message: string }>;
const testers = new Map<IntegrationProvider, IntegrationTester>();
export function registerIntegrationTest(provider: IntegrationProvider, fn: IntegrationTester) {
  testers.set(provider, fn);
}

export async function testIntegration(provider: IntegrationProvider, opts: { to?: string; actorId: string }) {
  const tester = testers.get(provider);
  let result: { ok: boolean; message: string };
  if (!tester) result = { ok: false, message: 'এই ইন্টিগ্রেশনের জন্য টেস্ট নেই' };
  else {
    try {
      result = await tester(await getIntegration(provider), opts);
    } catch (err) {
      result = { ok: false, message: err instanceof Error ? err.message.slice(0, 300) : 'অজানা সমস্যা' };
    }
  }
  await coreDb
    .insert(integrationTests)
    .values({ provider, ok: result.ok, message: result.message, testedBy: opts.actorId })
    .onConflictDoUpdate({ target: integrationTests.provider, set: { ok: result.ok, message: result.message, testedBy: opts.actorId, updatedAt: new Date() } });
  await audit({ actorId: opts.actorId, action: 'integration.test', entity: 'integration_settings', entityId: provider, after: result });
  return result;
}
