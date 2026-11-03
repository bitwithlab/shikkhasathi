import { expect, type Page } from '@playwright/test';

export const PASSWORD = 'shikkhasathi123';

export async function login(page: Page, email: string) {
  await page.goto('/auth');
  await page.getByRole('button', { name: /ইমেইল দিয়ে লগইন/ }).click();
  await page.getByLabel('ইমেইল').fill(email);
  await page.getByLabel('পাসওয়ার্ড').fill(PASSWORD);
  await page.getByRole('button', { name: 'লগইন', exact: true }).click();
  await page.waitForURL((u) => !u.pathname.startsWith('/auth'));
}

export async function noHorizontalScroll(page: Page) {
  const [sw, cw] = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
  expect(sw, 'page must not scroll horizontally').toBeLessThanOrEqual(cw + 1);
}
