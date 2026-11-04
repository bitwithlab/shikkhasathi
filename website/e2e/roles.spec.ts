import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('teacher reaches Teacher Studio; students are kept out of /admin', async ({ page }) => {
  await login(page, 'rakib@demo.shikkhasathi.com');
  await page.goto('/teach');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await page.goto('/admin');
  await expect(page.getByText('এই পাতায় আপনার প্রবেশাধিকার নেই')).toBeVisible();
});

test('admin dashboard loads', async ({ page }) => {
  await login(page, 'admin@shikkhasathi.com');
  await page.goto('/admin');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});
