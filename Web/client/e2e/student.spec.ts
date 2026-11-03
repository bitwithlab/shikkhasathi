import { expect, test } from '@playwright/test';
import { login } from './helpers';

test.describe.configure({ mode: 'serial' });

test('student home dashboard shows Classroom, exam countdown, continue learning and recommendations', async ({ page }) => {
  await login(page, 'sadman@demo.shikkhasathi.com');
  await page.goto('/home');
  await expect(page.getByText(/আসসালামু আলাইকুম/)).toBeVisible();
  await expect(page.getByText('পড়া চালিয়ে যান').filter({ visible: true }).first()).toBeVisible();
  await expect(page.getByText(/দিন বাকি|আজই পরীক্ষা|আগামীকাল/).filter({ visible: true }).first()).toBeVisible();
  await expect(page.getByText('আপনার জন্য কোর্স').filter({ visible: true }).first()).toBeVisible();
});

test('checkout with the dev gateway → course unlocked → player opens and lesson can be completed', async ({ page }) => {
  await login(page, 'nusrat@demo.shikkhasathi.com');
  await page.goto('/explore');
  // pick a paid course nusrat doesn't own yet
  const res = await page.request.get('/api/v1/search?limit=24&sort=price_desc');
  const items = ((await res.json()) as { items: { id: string; slug: string; price: number }[] }).items.filter((c) => c.price > 0);
  let target: { id: string } | undefined;
  for (const c of items) {
    const d = await (await page.request.get(`/api/v1/courses/${c.slug}`)).json();
    if (!d.viewer.enrolled && !d.viewer.isOwner) {
      target = c;
      break;
    }
  }
  test.skip(!target, 'student already owns every paid course');
  await page.goto(`/checkout/${target!.id}`);
  await page.getByRole('radio', { name: /বিকাশ/ }).click();
  await page.getByRole('button', { name: /পরিশোধ করুন/ }).first().click();
  await page.waitForURL(/\/payments\/fake\//);
  await page.getByRole('button', { name: 'পেমেন্ট সফল করুন' }).click();
  await page.waitForURL(/\/checkout\/result/);
  await expect(page.getByText('কোর্স আনলক হয়েছে')).toBeVisible({ timeout: 20_000 });
  await page.getByRole('link', { name: 'শেখা শুরু করুন' }).click();
  await page.waitForURL(/\/learn\//);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  const done = page.getByRole('button', { name: 'সম্পন্ন হিসেবে চিহ্নিত করুন' });
  await done.click();
  await expect(page.getByRole('button', { name: 'সম্পন্ন' })).toHaveAttribute('aria-pressed', 'true');
});

test('Exam Mode + mock test: start → answer → submit → result with chapter breakdown', async ({ page }) => {
  await login(page, 'sadman@demo.shikkhasathi.com');
  await page.goto('/exam-mode');
  await page.waitForURL(/\/exam-mode\/.+/);
  await expect(page.getByText('আজকের প্ল্যান')).toBeVisible();
  await page.goto('/mock-tests');
  await page.getByRole('link', { name: /শুরু করুন|চালিয়ে যান|আবার দিন/ }).first().click();
  await page.waitForURL(/\/mock-test\/[^/]+$/);
  await page.locator('main [role=radiogroup]').getByRole('radio').first().click();
  await page.getByRole('button', { name: 'জমা দিন' }).first().click();
  await page.getByRole('dialog').getByRole('button', { name: 'জমা দিন' }).click();
  await page.waitForURL(/\/result$/);
  await expect(page.getByText('চ্যাপ্টার অনুযায়ী')).toBeVisible();
});
