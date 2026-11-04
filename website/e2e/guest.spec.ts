import { expect, test } from '@playwright/test';

test('guest: landing → smart search "DS" → course details → buy asks to log in', async ({ page }) => {
  await page.goto('/');
  await page.waitForFunction(() => !document.getElementById('ssr')); // prerendered copy swapped for the live app
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByText('কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়')).toBeVisible();
  const search = page.getByRole('combobox').first();
  await search.fill('DS');
  await search.press('Enter');
  await page.waitForURL(/\/explore\?/);
  await expect(page.getByText('Data Structures').first()).toBeVisible();
  await page.getByRole('link', { name: /Data Structures/ }).first().click();
  await page.waitForURL(/\/c\//);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Data Structures');
  await page.getByRole('button', { name: /কিনুন|এনরোল/ }).first().click();
  await page.waitForURL(/\/auth\?next=/);
});

test('SEO: course page ships title, canonical and Course JSON-LD before JS', async ({ request }) => {
  const html = await (await request.get('/c/data-structures-midterm-crash-cse-2203')).text();
  expect(html).toMatch(/<title>Data Structures[^<]*শিক্ষাসাথী<\/title>/);
  expect(html).toContain('rel="canonical" href="https://shikkhasathi.com/c/data-structures-midterm-crash-cse-2203"');
  expect(html).toContain('"@type":"Course"');
  const sm = await (await request.get('/sitemap.xml')).text();
  expect(sm).toContain('/c/data-structures-midterm-crash-cse-2203');
});
