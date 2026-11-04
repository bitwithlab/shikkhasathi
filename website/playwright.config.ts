import { existsSync } from 'node:fs';
import { defineConfig, devices } from '@playwright/test';

/**
 * E2E happy paths against a running server with seeded data (see docs/TESTING.md):
 *   cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   (seeds an empty database)
 *   cd website && pnpm build && pnpm preview &   then: E2E_BASE_URL=http://localhost:4173 pnpm e2e
 */
const chromium = process.env.PW_CHROMIUM ?? (existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined);
const launchOptions = chromium ? { executablePath: chromium } : {};

export default defineConfig({
  testDir: './e2e',
  timeout: 45_000,
  expect: { timeout: 8_000 },
  fullyParallel: false, // specs share one seeded database
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4173', locale: 'bn-BD', timezoneId: 'Asia/Dhaka', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, launchOptions } },
    { name: 'mobile', use: { ...devices['Pixel 7'], launchOptions }, testMatch: /responsive\.spec\.ts/ },
  ],
});
