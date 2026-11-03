import postgres from 'postgres';
import { drizzle } from 'drizzle-orm/postgres-js';
import { migrate } from 'drizzle-orm/postgres-js/migrator';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { enableRls, tablesWithoutRls } from './rls';

const here = dirname(fileURLToPath(import.meta.url));

export async function migrateDatabase(name: 'core' | 'content', url: string) {
  // Migrations use the DIRECT connection (5432), never the transaction pooler.
  const sql = postgres(url, { max: 1, onnotice: () => {} });
  try {
    await migrate(drizzle(sql), { migrationsFolder: join(here, name, 'migrations') });
    await enableRls(sql);
    const missing = await tablesWithoutRls(sql);
    if (missing.length) throw new Error(`[${name}] RLS disabled on: ${missing.join(', ')}`);
  } finally {
    await sql.end();
  }
}

export async function migrateAll() {
  const core = process.env.CORE_DIRECT_URL ?? process.env.CORE_DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/shikkhasathi_core';
  const content =
    process.env.CONTENT_DIRECT_URL ?? process.env.CONTENT_DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/shikkhasathi_content';
  await migrateDatabase('core', core);
  await migrateDatabase('content', content);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  migrateAll()
    .then(() => console.info('migrations applied (core + content), RLS verified'))
    .catch((e) => {
      console.error(e);
      process.exit(1);
    });
}
