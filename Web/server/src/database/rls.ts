import type { Sql } from 'postgres';

/**
 * Supabase Data API lockdown: enable RLS with NO policies on every table (deny-all for anon/authenticated).
 * Idempotent; runs after every migration on both databases.
 */
export const ENABLE_RLS_SQL = `
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT schemaname, tablename FROM pg_tables WHERE schemaname IN ('public', 'drizzle') LOOP
    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', r.schemaname, r.tablename);
  END LOOP;
END $$;`;

export async function enableRls(sql: Sql) {
  await sql.unsafe(ENABLE_RLS_SQL);
}

/** Tables (public schema) that do NOT have RLS enabled. Must be empty. */
export async function tablesWithoutRls(sql: Sql): Promise<string[]> {
  const rows = await sql<{ relname: string }[]>`
    SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relrowsecurity ORDER BY 1`;
  return rows.map((r) => r.relname);
}

/** Policies on public tables. Must be empty (we use deny-all; the server uses a privileged role). */
export async function publicPolicies(sql: Sql): Promise<string[]> {
  const rows = await sql<{ name: string }[]>`SELECT tablename || '.' || policyname AS name FROM pg_policies WHERE schemaname = 'public'`;
  return rows.map((r) => r.name);
}
