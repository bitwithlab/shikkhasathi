import { migrateDatabase } from '../src/database/migrate';

export default async function setup() {
  await migrateDatabase('core', process.env.TEST_CORE_DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/shikkhasathi_core_test');
  await migrateDatabase('content', process.env.TEST_CONTENT_DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/shikkhasathi_content_test');
}
