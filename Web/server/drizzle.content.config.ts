import { defineConfig } from 'drizzle-kit';

export default defineConfig({
  dialect: 'postgresql',
  schema: './src/database/content/schema/index.ts',
  out: './src/database/content/migrations',
  dbCredentials: { url: process.env.CONTENT_DIRECT_URL ?? process.env.CONTENT_DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/shikkhasathi_content' },
});
