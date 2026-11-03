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
