#!/usr/bin/env node
// Build-time SEO prerender for public routes: writes dist/<route>/index.html with per-page title/meta/canonical/JSON-LD.
// Full content prerender of dynamic pages (courses, universities) is added in Phase 5 by fetching the API at build time
// when PRERENDER_API is set; without it, static public routes still get correct head tags.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const dist = resolve(dirname(fileURLToPath(import.meta.url)), '../dist');
const shell = readFileSync(resolve(dist, 'index.html'), 'utf8');
const SITE = 'https://shikkhasathi.com';

const org = { '@context': 'https://schema.org', '@type': 'Organization', name: 'ShikkhaSathi', alternateName: 'শিক্ষাসাথী', url: SITE, logo: `${SITE}/icon-512.png` };

const pages = [
  { path: '/', title: 'শিক্ষাসাথী — আপনার ক্লাস, কোর্স আর পরীক্ষার প্রস্তুতি এক জায়গায়', desc: 'Google Classroom-এর সব অ্যাসাইনমেন্ট এক ড্যাশবোর্ডে, আর যেকোনো ইউনিভার্সিটির শিক্ষকের পরীক্ষাকেন্দ্রিক কোর্স।', ld: [org] },
  { path: '/explore', title: 'কোর্স খুঁজুন — শিক্ষাসাথী', desc: 'ইউনিভার্সিটি, সাবজেক্ট বা কোর্স কোড দিয়ে Midterm ও Final ক্র্যাশ কোর্স খুঁজুন।' },
  { path: '/universities', title: 'ইউনিভার্সিটি — শিক্ষাসাথী', desc: 'Metropolitan, NSU, BRAC সহ বাংলাদেশের ইউনিভার্সিটি ধরে কোর্স ব্রাউজ করুন।' },
  { path: '/subjects', title: 'সাবজেক্ট — শিক্ষাসাথী', desc: 'Data Structures, Algorithms, DBMS সহ সাবজেক্ট ধরে কোর্স।' },
  { path: '/privacy', title: 'প্রাইভেসি নীতি — শিক্ষাসাথী', desc: 'শিক্ষাসাথী কীভাবে আপনার তথ্য ও Google Classroom ডেটা ব্যবহার করে।' },
  { path: '/terms', title: 'শর্তাবলি — শিক্ষাসাথী', desc: 'শিক্ষাসাথী ব্যবহারের শর্তাবলি।' },
  { path: '/refund', title: 'রিফান্ড নীতি — শিক্ষাসাথী', desc: '৪৮ ঘণ্টার মধ্যে ও ২০%-এর কম দেখা হলে রিফান্ড।' },
  { path: '/about', title: 'আমাদের সম্পর্কে — শিক্ষাসাথী', desc: 'বাংলাদেশের বিশ্ববিদ্যালয় শিক্ষার্থীদের একাডেমিক সঙ্গী।' },
  { path: '/contact', title: 'যোগাযোগ — শিক্ষাসাথী', desc: 'সাপোর্ট: support@shikkhasathi.com' },
];

const esc = (s) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
export function renderHead(html, p) {
  const canonical = `${SITE}${p.path === '/' ? '/' : p.path}`;
  let out = html
    .replace(/<title>[^<]*<\/title>/, `<title>${esc(p.title)}</title>`)
    .replace(/<meta name="description" content="[^"]*"\s*\/?>/, `<meta name="description" content="${esc(p.desc)}" />`)
    .replace(/<link rel="canonical" href="[^"]*"\s*\/?>/, `<link rel="canonical" href="${canonical}" />`)
    .replace(/<meta property="og:title" content="[^"]*"\s*\/?>/, `<meta property="og:title" content="${esc(p.title)}" />`)
    .replace(/<meta property="og:description" content="[^"]*"\s*\/?>/, `<meta property="og:description" content="${esc(p.desc)}" />`);
  out = out.replace('</head>', `<meta property="og:url" content="${canonical}" />\n${(p.ld ?? []).map((j) => `<script type="application/ld+json">${JSON.stringify(j)}</script>`).join('\n')}\n</head>`);
  if (p.body) out = out.replace('<div id="root"><!--ssr--></div>', `<div id="root">${p.body}</div>`);
  return out;
}

for (const p of pages) {
  const html = renderHead(shell, p);
  if (p.path === '/') writeFileSync(resolve(dist, 'index.prerendered.html'), html);
  else {
    mkdirSync(resolve(dist, p.path.slice(1)), { recursive: true });
    writeFileSync(resolve(dist, p.path.slice(1), 'index.html'), html);
  }
}
const urls = pages.map((p) => `  <url><loc>${SITE}${p.path}</loc><changefreq>weekly</changefreq></url>`).join('\n');
writeFileSync(resolve(dist, 'sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`);
console.info(`prerender: ${pages.length} public routes + sitemap.xml`);
