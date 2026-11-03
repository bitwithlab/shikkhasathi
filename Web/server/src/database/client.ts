import postgres from 'postgres';
import { drizzle, type PostgresJsDatabase } from 'drizzle-orm/postgres-js';
import { env } from '../config/env';
import * as coreSchema from './core/schema';
import * as contentSchema from './content/schema';

export type CoreDb = PostgresJsDatabase<typeof coreSchema>;
export type ContentDb = PostgresJsDatabase<typeof contentSchema>;

function connect(url: string) {
  return postgres(url, {
    max: env.DB_POOL_MAX,
    // Supabase transaction-mode pooler (6543) does not support prepared statements.
    prepare: !env.DB_POOLER && !url.includes(':6543'),
    idle_timeout: 20,
    connect_timeout: 10,
    onnotice: () => {},
    types: { bigint: postgres.BigInt },
  });
}

export const coreSql = connect(env.CORE_DATABASE_URL);
export const contentSql = connect(env.CONTENT_DATABASE_URL);

export const coreDb: CoreDb = drizzle(coreSql, { schema: coreSchema, casing: 'snake_case' });
export const contentDb: ContentDb = drizzle(contentSql, { schema: contentSchema, casing: 'snake_case' });

export { coreSchema, contentSchema };

export async function closeDatabases() {
  await Promise.allSettled([coreSql.end({ timeout: 5 }), contentSql.end({ timeout: 5 })]);
}
