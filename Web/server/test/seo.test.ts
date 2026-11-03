import { describe, expect, it } from 'vitest';
import { seedAll } from '../src/database/seed';
import { getApp } from './helpers';
import { dynamicHead, renderHead, sitemapXml } from '../src/seo';

const SHELL = '<html><head><title>x</title><meta name="description" content="x" /><link rel="canonical" href="https://shikkhasathi.com/" /><meta property="og:title" content="x" /><meta property="og:description" content="x" /><meta property="og:image" content="https://shikkhasathi.com/og-image.jpg" /></head><body><div id="root"><!--ssr--></div></body></html>';
const ld = (html: string) => [...html.matchAll(/<script type="application\/ld\+json">(.*?)<\/script>/g)].map((m) => JSON.parse(m[1]!));

describe('request-time SEO', () => {
  it('course page: title with price, canonical, Course + BreadcrumbList JSON-LD (BDT offer, rating from real reviews), crawlable body with disclaimer', async () => {
    await getApp();
    await seedAll();
    const h = await dynamicHead('/c/data-structures-midterm-crash-cse-2203');
    expect(h).not.toBeNull();
    const html = renderHead(SHELL, h!);
    expect(html).toContain('<link rel="canonical" href="https://shikkhasathi.com/c/data-structures-midterm-crash-cse-2203" />');
    expect(html).toMatch(/<title>Data Structures[^<]*৳\d+[^<]*শিক্ষাসাথী<\/title>/);
    const [course, crumbs] = ld(html);
    expect(course['@type']).toBe('Course');
    expect(course.courseCode).toBe('CSE-2203');
    expect(course.offers).toMatchObject({ priceCurrency: 'BDT' });
    expect(course.aggregateRating.reviewCount).toBeGreaterThan(0);
    expect(crumbs['@type']).toBe('BreadcrumbList');
    expect(html).toContain('<h1>Data Structures');
    expect(html).toContain('অফিসিয়ালি যুক্ত নয়');
    expect(html).not.toContain('<!--ssr-->');
  });

  it('university, subject, teacher pages; unknown/draft slugs and private routes get no head', async () => {
    await getApp();
    await seedAll();
    expect(ld(renderHead(SHELL, (await dynamicHead('/u/metropolitan-university'))!))[0]['@type']).toBe('CollegeOrUniversity');
    expect((await dynamicHead('/s/data-structures'))!.title).toContain('Data Structures');
    expect(ld(renderHead(SHELL, (await dynamicHead('/t/rakibul-hasan'))!))[0]['@type']).toBe('Person');
    expect(await dynamicHead('/t/maruf-rashid')).toBeNull(); // pending teacher
    expect(await dynamicHead('/c/does-not-exist')).toBeNull();
    expect(await dynamicHead('/home')).toBeNull();
    expect(await dynamicHead('/c/<script>')).toBeNull();
  });

  it('JSON-LD cannot break out of the script tag', () => {
    const html = renderHead(SHELL, { title: 'a', desc: 'b', path: '/c/x', ld: [{ name: '</script><script>alert(1)</script>' }], body: '' });
    expect(html).not.toContain('</script><script>alert(1)');
  });

  it('sitemap lists published courses, universities, subjects and verified teachers only', async () => {
    await getApp();
    await seedAll();
    const xml = await sitemapXml();
    expect(xml).toContain('<loc>https://shikkhasathi.com/c/data-structures-midterm-crash-cse-2203</loc>');
    expect(xml).toContain('/u/metropolitan-university');
    expect(xml).toContain('/t/rakibul-hasan');
    expect(xml).not.toContain('/t/maruf-rashid');
    expect(xml).not.toContain('/home');
  });
});
