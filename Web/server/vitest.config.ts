import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    include: ['test/**/*.test.ts', 'src/**/*.test.ts'],
    globalSetup: ['test/global-setup.ts'],
    setupFiles: ['test/setup.ts'],
    fileParallelism: false,
    pool: 'forks',
    testTimeout: 20_000,
    hookTimeout: 60_000,
    env: {
      NODE_ENV: 'test',
      CORE_DATABASE_URL: process.env.TEST_CORE_DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/shikkhasathi_core_test',
      CONTENT_DATABASE_URL: process.env.TEST_CONTENT_DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/shikkhasathi_content_test',
      REDIS_URL: process.env.TEST_REDIS_URL ?? 'redis://localhost:6379/15',
      ENCRYPTION_KEY: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
      APP_URL: 'http://localhost:3000',
      LOCAL_STORAGE_DIR: '/tmp/ss-test-storage',
    },
  },
});
