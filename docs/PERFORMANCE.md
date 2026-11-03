# Performance

## Budgets (CLAUDE.md §5.3, §6; spec §12)
| Target | Budget |
|---|---|
| Landing initial JS | ≤ 150 KB gzip |
| Student shell + `/home` | ≤ 200 KB gzip |
| Any route chunk | ≤ 100 KB gzip |
| Lighthouse mobile | Perf ≥ 90, A11y ≥ 95, BP ≥ 95, SEO ≥ 95 |
| Web vitals | LCP < 2.0 s, CLS < 0.05, INP < 200 ms |
| API p95 | reads < 200 ms, writes < 500 ms, `/dashboard` < 300 ms cached |
| Android cold start | < 1.5 s mid-range |

`node scripts/check-bundle.mjs` (CI) measures route import graphs from the Vite manifest and fails the build when a budget is exceeded.

## Techniques in place
- Route-level lazy chunks; Teacher Studio and Admin shells/pages never load for students; Radix dialog loads only when a sheet opens.
- Zod is not in the initial bundle: the client calls endpoints through a generated zod-free map (`endpoints.gen.ts`); forms import schemas inside their lazy chunks.
- i18n split: Bangla core dictionary in the entry; feature copy ships with feature chunks; English loads on demand.
- Self-hosted subset fonts (Hind Siliguri bengali subset, Inter latin) with `font-display: swap` and `unicode-range`.
- Hashed assets cached immutably; brotli/gzip; prerendered public HTML heads + sitemap.

## Measurements
| Date | Landing | `/home` | Largest lazy chunk | Notes |
|---|---|---|---|---|
| 2026-11-03 (Phase 1) | 135.7 KB | 145.4 KB | zod 25.7 KB | before: 181 KB (zod + all shells + both dictionaries in entry) |

## Measured — 2026-11-03 (Phase 12)
Lighthouse 12.6 mobile (simulated slow 4G: 150 ms RTT, 1.6 Mbps, 4× CPU) against the local production build:

| Page | Perf | A11y | Best pr. | SEO | FCP (sim / observed) | LCP (sim / observed) | CLS | TBT |
|---|---|---|---|---|---|---|---|---|
| `/` landing | 81 | 100 | 100 | 100 | 3.0 s / 0.32 s | 3.1 s / 0.40 s | 0 | 300 ms |
| `/c/data-structures-…` | 80 | 100 | 100 | 100 | 2.4 s | 3.8 s | 0.001 | 250 ms |
| `/explore` | 79 | 100 | 100 | 100 | 3.3 s | 3.9 s | 0.003 | 140 ms |

Bundles (`node scripts/check-bundle.mjs`): landing initial **148.4 KB** gzip (≤ 150), student shell + `/home` **170.2 KB** (≤ 200), largest lazy chunk hls.js light **87.4 KB** (≤ 100, player only).

What changed in Phase 12:
- **Landing server-rendered at build time** (`vite build --ssr src/prerender/entry.tsx` → `dist/index.prerendered.html`) with the stylesheet inlined. The prerendered copy stays on screen until the live page mounts (`app/reveal.ts`), so CLS went 0.41 → 0.
- **Fonts:** 2 files per script (400 covers 400–500, 600 covers 600–800). First load is ≈ 190 KB lighter.
- **Request-time head + JSON-LD** for `/c /u /s /t` (crawlable without JS).
- **Accessibility 87 → 100:** chips with `role=tab` no longer add `aria-pressed`, the icon-only search button has a name, and the muted token, avatar palette and footer brand colours meet AA.

Gap vs budget: **simulated** LCP is 3.1–3.9 s against the < 2.0 s target. Observed LCP locally is 0.4 s. The simulation is dominated by request latency (562 ms per request) on the HTML, fonts and about 140 KB of JS. Next levers, in order:
1. Serve from the CDN edge (`media.shikkhasathi.com` / Caddy with HTTP/3) so real RTT ≪ 150 ms.
2. Drop the `modulepreload` of the router/query chunks on the landing page, so first paint gets the bandwidth.
3. Subset Hind Siliguri to the glyphs the landing actually uses for the 600 weight.
