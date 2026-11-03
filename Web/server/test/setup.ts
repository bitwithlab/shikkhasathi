import { afterAll, beforeEach } from 'vitest';
import { coreSql, contentSql, closeDatabases } from '../src/database/client';
import { closeRedis, redis } from '../src/common/redis';
import { invalidateSettingsCache } from '../src/modules/settings/settings.service';
import { FakeEmailProvider } from '../src/services/email/fake';
import { FakeSmsProvider } from '../src/services/sms';

async function truncateAll(sql: typeof coreSql) {
  const rows = await sql<{ tablename: string }[]>`select tablename from pg_tables where schemaname = 'public'`;
  if (rows.length) await sql.unsafe(`TRUNCATE ${rows.map((r) => `"${r.tablename}"`).join(', ')} RESTART IDENTITY CASCADE`);
}

beforeEach(async () => {
  await Promise.all([truncateAll(coreSql), truncateAll(contentSql), redis().flushdb()]);
  invalidateSettingsCache();
  FakeEmailProvider.outbox.length = 0;
  FakeSmsProvider.outbox.length = 0;
});

afterAll(async () => {
  await closeDatabases();
  await closeRedis();
});
