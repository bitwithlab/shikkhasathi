import { describe, expect, it } from 'vitest';
import { coreSql, contentSql } from '../src/database/client';
import { publicPolicies, tablesWithoutRls } from '../src/database/rls';

describe('Supabase lockdown: RLS deny-all', () => {
  it('every CORE table has RLS enabled and no policies', async () => {
    expect(await tablesWithoutRls(coreSql)).toEqual([]);
    expect(await publicPolicies(coreSql)).toEqual([]);
  });
  it('every CONTENT table has RLS enabled and no policies', async () => {
    expect(await tablesWithoutRls(contentSql)).toEqual([]);
    expect(await publicPolicies(contentSql)).toEqual([]);
  });
  it('CORE and CONTENT tables are placed per CLAUDE.md §0B', async () => {
    const list = async (sql: typeof coreSql) => (await sql<{ t: string }[]>`select tablename as t from pg_tables where schemaname='public'`).map((r) => r.t);
    const core = await list(coreSql);
    const content = await list(contentSql);
    for (const t of ['users', 'orders', 'payments', 'invoices', 'enrollments', 'integration_settings', 'email_logs', 'google_connections', 'classroom_coursework']) expect(core).toContain(t);
    for (const t of ['institutions', 'courses', 'lessons', 'video_assets', 'documents', 'mock_tests', 'flashcards']) expect(content).toContain(t);
    expect(core.filter((t) => content.includes(t))).toEqual([]);
  });
});
