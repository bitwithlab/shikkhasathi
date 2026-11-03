import { defineConfig } from 'drizzle-kit';

export default defineConfig({
  dialect: 'postgresql',
  schema: './src/database/core/schema/index.ts',
  out: './src/database/core/migrations',
  dbCredentials: { url: process.env.CORE_DIRECT_URL ?? process.env.CORE_DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/shikkhasathi_core' },
});
