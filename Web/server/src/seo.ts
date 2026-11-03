import { and, desc, eq, inArray, isNull } from 'drizzle-orm';
import { contentDb, coreDb } from './database/client';
import { sellerProfiles } from './database/core/schema';
import { courses, courseTargets, institutions, subjectMasters } from './database/content/schema';

/**
 * Request-time SEO for dynamic public pages (spec §5.4): course, university, subject, teacher storefront.
 * Injects title/description/canonical/Open Graph + JSON-LD and a small crawlable body into the SPA shell.
 * React replaces the body on mount (createRoot), so users never see it twice.
 */
export const SITE = 'https://shikkhasathi.com';
const TTL = 5 * 60_000;
const cache = new Map<string, { at: number; head: Head | null }>();

export interface Head {
  title: string;
  desc: string;
  path: string;
  image?: string | null;
  ld: object[];
  body: string;
}

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const clip = (s: string, n: number) => (s.length > n ? `${s.slice(0, n - 1).trimEnd()}…` : s);
const taka = (p: number) => (p / 100).toFixed(2);
const crumbs = (items: [string, string][]) => ({ '@context': 'https://schema.org', '@type': 'BreadcrumbList', itemListElement: items.map(([name, path], i) => ({ '@type': 'ListItem', position: i + 1, name, item: `${SITE}${path}` })) });
const ORG = { '@type': 'Organization', name: 'ShikkhaSathi', url: SITE };

async function coursePage(slug: string): Promise<Head | null> {
  const [c] = await contentDb.select().from(courses).where(and(eq(courses.slug, slug), eq(courses.status, 'published'), isNull(courses.deletedAt)));
  if (!c) return null;
  const [[t], [sp]] = await Promise.all([
    contentDb.select({ code: courseTargets.courseCode, inst: courseTargets.institutionId }).from(courseTargets).where(eq(courseTargets.courseId, c.id)).limit(1),
    coreDb.select({ name: sellerProfiles.displayName, slug: sellerProfiles.slug }).from(sellerProfiles).where(eq(sellerProfiles.userId, c.sellerId)),
  ]);
  const [inst] = t?.inst ? await contentDb.select({ name: institutions.name, slug: institutions.slug, short: institutions.shortName }).from(institutions).where(eq(institutions.id, t.inst)) : [];
  const desc = clip(c.shortDescription || c.description || c.title, 155);
  const ld: object[] = [
    {
      '@context': 'https://schema.org',
      '@type': 'Course',
      name: c.title,
      description: desc,
      url: `${SITE}/c/${c.slug}`,
      inLanguage: c.language === 'en' ? 'en' : 'bn',
      ...(t?.code ? { courseCode: t.code } : {}),
      provider: ORG,
      ...(sp ? { instructor: { '@type': 'Person', name: sp.name, url: `${SITE}/t/${sp.slug}` } } : {}),
      ...(c.ratingCount > 0 ? { aggregateRating: { '@type': 'AggregateRating', ratingValue: Number(c.ratingAvg), reviewCount: c.ratingCount, bestRating: 5 } } : {}),
      offers: { '@type': 'Offer', price: taka(c.price), priceCurrency: 'BDT', availability: 'https://schema.org/InStock', category: c.price === 0 ? 'Free' : 'Paid', url: `${SITE}/c/${c.slug}` },
      hasCourseInstance: { '@type': 'CourseInstance', courseMode: 'online', courseWorkload: c.totalDurationSec ? `PT${Math.max(1, Math.round(c.totalDurationSec / 60))}M` : undefined },
    },
    crumbs([['শিক্ষাসাথী', '/'], ...(inst ? ([[inst.short, `/u/${inst.slug}`]] as [string, string][]) : []), [c.title, `/c/${c.slug}`]]),
  ];
  const price = c.price === 0 ? 'ফ্রি' : `৳${c.price / 100}`;
  return {
    title: `${c.title} — ${price} | শিক্ষাসাথী`,
    desc,
    path: `/c/${c.slug}`,
    image: c.coverUrl,
    ld,
    body: `<main><h1>${esc(c.title)}</h1>${t?.code ? `<p>${esc(t.code)}${inst ? ` · ${esc(inst.name)}` : ''}</p>` : ''}<p>${esc(desc)}</p>${sp ? `<p>শিক্ষক: <a href="/t/${esc(sp.slug)}">${esc(sp.name)}</a></p>` : ''}<p>${esc(price)}</p><p>সম্ভাব্য গুরুত্বপূর্ণ টপিক — কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়।</p></main>`,
  };
}

async function universityPage(slug: string): Promise<Head | null> {
  const [u] = await contentDb.select().from(institutions).where(and(eq(institutions.slug, slug), eq(institutions.status, 'approved')));
  if (!u) return null;
  const desc = `${u.name}${u.nameBn ? ` (${u.nameBn})` : ''}-এর শিক্ষার্থীদের জন্য Midterm ও Final ক্র্যাশ কোর্স, কোর্স কোড অনুযায়ী।`;
  return {
    title: `${u.shortName} — ${u.name} কোর্স | শিক্ষাসাথী`,
    desc: clip(desc, 155),
    path: `/u/${u.slug}`,
    image: u.logoUrl,
    ld: [{ '@context': 'https://schema.org', '@type': 'CollegeOrUniversity', name: u.name, alternateName: [u.shortName, ...(u.nameBn ? [u.nameBn] : [])], ...(u.website ? { sameAs: u.website } : {}), ...(u.city ? { address: { '@type': 'PostalAddress', addressLocality: u.city, addressCountry: 'BD' } } : {}) }, crumbs([['শিক্ষাসাথী', '/'], ['ইউনিভার্সিটি', '/universities'], [u.shortName, `/u/${u.slug}`]])],
    body: `<main><h1>${esc(u.name)}</h1><p>${esc(desc)}</p><p>কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়।</p></main>`,
  };
}

async function subjectPage(slug: string): Promise<Head | null> {
  const [s] = await contentDb.select().from(subjectMasters).where(and(eq(subjectMasters.slug, slug), eq(subjectMasters.status, 'approved')));
  if (!s) return null;
  const desc = `${s.name}${s.nameBn ? ` (${s.nameBn})` : ''} — বিভিন্ন ইউনিভার্সিটির কোর্স কোড অনুযায়ী পরীক্ষার প্রস্তুতির কোর্স।`;
  return { title: `${s.name} কোর্স | শিক্ষাসাথী`, desc: clip(desc, 155), path: `/s/${s.slug}`, ld: [crumbs([['শিক্ষাসাথী', '/'], ['সাবজেক্ট', '/subjects'], [s.name, `/s/${s.slug}`]])], body: `<main><h1>${esc(s.name)}</h1><p>${esc(desc)}</p></main>` };
}

async function teacherPage(slug: string): Promise<Head | null> {
  const [sp] = await coreDb.select().from(sellerProfiles).where(and(eq(sellerProfiles.slug, slug), eq(sellerProfiles.verificationStatus, 'verified')));
  if (!sp) return null;
  const desc = clip(sp.headline ? `${sp.headline} — ${sp.bio ?? ''}` : (sp.bio ?? sp.displayName), 155);
  return { title: `${sp.displayName} — শিক্ষক | শিক্ষাসাথী`, desc, path: `/t/${sp.slug}`, ld: [{ '@context': 'https://schema.org', '@type': 'Person', name: sp.displayName, ...(sp.headline ? { jobTitle: sp.headline } : {}), url: `${SITE}/t/${sp.slug}`, worksFor: ORG }], body: `<main><h1>${esc(sp.displayName)}</h1><p>${esc(desc)}</p></main>` };
}

export async function dynamicHead(url: string): Promise<Head | null> {
  const m = /^\/(c|u|s|t)\/([a-z0-9-]{1,120})\/?$/.exec(url);
  if (!m) return null;
  const hit = cache.get(url);
  if (hit && Date.now() - hit.at < TTL) return hit.head;
  const fn = { c: coursePage, u: universityPage, s: subjectPage, t: teacherPage }[m[1] as 'c'];
  const head = await fn(m[2]!).catch(() => null);
  cache.set(url, { at: Date.now(), head });
  if (cache.size > 2000) cache.delete(cache.keys().next().value!);
  return head;
}

export function renderHead(shell: string, h: Head) {
  const canonical = `${SITE}${h.path}`;
  return shell
    .replace(/<title>[^<]*<\/title>/, `<title>${esc(h.title)}</title>`)
    .replace(/<meta name="description" content="[^"]*"\s*\/?>/, `<meta name="description" content="${esc(h.desc)}" />`)
    .replace(/<link rel="canonical" href="[^"]*"\s*\/?>/, `<link rel="canonical" href="${canonical}" />`)
    .replace(/<meta property="og:title" content="[^"]*"\s*\/?>/, `<meta property="og:title" content="${esc(h.title)}" />`)
    .replace(/<meta property="og:description" content="[^"]*"\s*\/?>/, `<meta property="og:description" content="${esc(h.desc)}" />`)
    .replace(/<meta property="og:image" content="[^"]*"\s*\/?>/, (tag) => (h.image ? `<meta property="og:image" content="${esc(h.image)}" />` : tag))
    .replace('</head>', `<meta property="og:url" content="${canonical}" />\n${h.ld.map((j) => `<script type="application/ld+json">${JSON.stringify(j).replace(/</g, '\\u003c')}</script>`).join('\n')}\n</head>`)
    .replace('<div id="root"><!--ssr--></div>', `<div id="root">${h.body}</div>`);
}

const STATIC = ['/', '/explore', '/universities', '/subjects', '/requests', '/about', '/contact', '/privacy', '/terms', '/refund'];

export async function sitemapXml() {
  const [cs, us, ss, ts] = await Promise.all([
    contentDb.select({ slug: courses.slug, at: courses.updatedAt }).from(courses).where(and(eq(courses.status, 'published'), isNull(courses.deletedAt))).orderBy(desc(courses.updatedAt)).limit(20_000),
    contentDb.select({ slug: institutions.slug, at: institutions.updatedAt }).from(institutions).where(eq(institutions.status, 'approved')),
    contentDb.select({ slug: subjectMasters.slug, at: subjectMasters.updatedAt }).from(subjectMasters).where(eq(subjectMasters.status, 'approved')),
    coreDb.select({ slug: sellerProfiles.slug, at: sellerProfiles.updatedAt }).from(sellerProfiles).where(inArray(sellerProfiles.verificationStatus, ['verified'])),
  ]);
  const url = (path: string, at?: Date, pri = '0.6') => `  <url><loc>${SITE}${path}</loc>${at ? `<lastmod>${at.toISOString().slice(0, 10)}</lastmod>` : ''}<priority>${pri}</priority></url>`;
  const rows = [
    ...STATIC.map((p) => url(p, undefined, p === '/' ? '1.0' : '0.7')),
    ...cs.map((c) => url(`/c/${c.slug}`, c.at, '0.9')),
    ...us.map((u) => url(`/u/${u.slug}`, u.at, '0.8')),
    ...ss.map((s) => url(`/s/${s.slug}`, s.at)),
    ...ts.map((t) => url(`/t/${t.slug}`, t.at)),
  ];
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${rows.join('\n')}\n</urlset>\n`;
}
