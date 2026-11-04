-- Supabase Data API lockdown (CLAUDE.md §0B): RLS enabled with NO policies on every table (deny-all).
-- Runs after every Flyway migrate; idempotent. The API connects with a privileged role.
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT schemaname, tablename FROM pg_tables WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', r.schemaname, r.tablename);
  END LOOP;
END $$;
