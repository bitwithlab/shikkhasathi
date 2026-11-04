import { expect, test } from '@playwright/test';
import { login, noHorizontalScroll } from './helpers';

for (const path of ['/', '/explore', '/universities', '/c/data-structures-midterm-crash-cse-2203']) {
  test(`no horizontal scroll on ${path}`, async ({ page }) => {
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    await noHorizontalScroll(page);
  });
}

test('student pages fit a phone screen', async ({ page }) => {
  await login(page, 'sadman@demo.shikkhasathi.com');
  for (const path of ['/home', '/my-courses', '/exam-mode', '/notifications', '/classroom']) {
    await page.goto(path);
    await page.waitForLoadState('networkidle');
    await noHorizontalScroll(page);
    await expect(page.getByRole('navigation').first()).toBeVisible();
  }
});
