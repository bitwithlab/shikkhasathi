# Design System — ShikkhaSathi

Source: `CLAUDE.md` §7, spec §13, `Reference/stitch_campuscourse_design_system/academic_calm/DESIGN.md`.
Tokens live in `website/src/styles/tokens/tokens.json` and are generated into CSS variables, a Tailwind preset, and a Compose theme (`scripts/gen-tokens.mjs`). **Never hardcode a color, radius or font in components** — use the token.

## Tokens

### Color
| Token | Light | Dark | Use |
|---|---|---|---|
| `primary` | `#4F46E5` | `#818CF8` | brand, primary CTA, active nav |
| `primary-strong` | `#4338CA` | `#A5B4FC` | hover / pressed |
| `primary-soft` | `#EEF2FF` | `rgba(129,140,248,.14)` | selected chips, tints |
| `on-primary` | `#FFFFFF` | `#0B1020` | text on primary |
| `success` | `#10B981` (text `#047857`) | `#34D399` | progress, enrolled, verified, turned in |
| `success-soft` | `#ECFDF5` | `rgba(16,185,129,.14)` | compatibility badge |
| `warning` | `#F59E0B` (text `#B45309`) | `#FBBF24` | countdown, ⭐ important, due soon |
| `warning-soft` | `#FFFBEB` | `rgba(245,158,11,.14)` | |
| `danger` | `#EF4444` (text `#B91C1C`) | `#F87171` | overdue, missing, destructive |
| `danger-soft` | `#FEF2F2` | `rgba(239,68,68,.14)` | |
| `bg` | `#F8FAFC` | `#0B1020` | canvas |
| `surface` | `#FFFFFF` | `#131A2E` | cards |
| `surface-2` | `#F1F5F9` | `#1A233D` | sunken areas, modals in dark |
| `text` | `#0F172A` | `#E2E8F0` | |
| `muted` | `#64748B` | `#94A3B8` | secondary text (AA on both bg & surface) |
| `border` | `#E2E8F0` | `#232C45` | 1px borders |

Text-on-tint variants (`*-fg`) exist so emerald/amber/red copy passes WCAG AA on soft backgrounds.

Gradient `linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%)` — **only** landing hero and exam countdown card.

### Type
- Bangla: **Hind Siliguri** (400/500/600/700), line-height 1.7 for body.
- English & numerals: **Inter** with `font-variant-numeric: tabular-nums` for prices, codes, timers.
- Stack: `"Hind Siliguri", Inter, system-ui, sans-serif` (Bangla-first; Latin glyphs fall through to Inter via `unicode-range` subsets).
- Scale: 12 / 14 / 16 / 18 / 20 / 24 / 30 / 36 / 48.
- Self-hosted, subset (Bengali + Latin), `font-display: swap`.

### Shape, space, elevation
- 8px grid (4px half-steps for icon gaps).
- Radius: card 16, control 12, thumbnail 12, chip full.
- Elevation 1 (cards): `0 1px 3px rgba(15,23,42,.05), 0 1px 2px -1px rgba(15,23,42,.05)` + 1px border. Dark: border only.
- Elevation 2 (hover): `0 10px 15px -3px rgba(79,70,229,.08), 0 4px 6px -4px rgba(15,23,42,.05)`, 1–2px lift.
- Elevation 3 (modals/sheets): `0 20px 25px -5px rgba(15,23,42,.12), 0 8px 10px -6px rgba(15,23,42,.08)`.
- Motion: 150–250 ms `cubic-bezier(.2,.8,.2,1)`; `prefers-reduced-motion` disables lifts, confetti and ring animation.

## Components (identical anatomy on web + mobile)

| Component | Web (`website/src/components`) | Mobile (`app/shared/src/commonMain/kotlin/com/shikkhasathi/shared`, `core/designsystem`) | Notes |
|---|---|---|---|
| Button (primary/secondary/ghost/danger, sm/md/lg) | `Button` | `SsButton` | 44px min (48 on mobile), pressed scale .98 |
| Input / Select / Textarea / OTP boxes | `Input`, `Select`, `OtpInput` | `SsTextField`, `OtpField` | 48px, 2px focus ring |
| Chip / Badge / StatusChip | `Chip`, `Badge`, `StatusChip` | `SsChip`, `StatusChip` | Pending · Turned in · Graded · Missing |
| Card | `Card` | `SsCard` | radius 16, 1px border |
| Course Card | `CourseCard` | `CourseCard` | 16:9 thumb, uni pill, exam pill, 2-line title, verified teacher, ⭐, ৳ + strikethrough, compatibility badge |
| Compatibility Badge | `CompatibilityBadge` | same | ✅ আপনার ইউনিভার্সিটির সিলেবাস / ⚠️ ভিন্ন সিলেবাস হতে পারে (Lucide icons, not emoji) |
| Assignment Row | `AssignmentRow` | same | course color stripe, title, due time, status chip, urgency color |
| Feed Item | `FeedItem` | same | kind icon, class name, time, unread dot |
| Countdown Card | `CountdownCard` | same | gradient, days big, readiness ring |
| Progress Ring / Bar | `ProgressRing`, `ProgressBar` | same | emerald |
| Lesson Row | `LessonRow` | same | ✓ done · lock · ▶ preview · ⭐ important |
| Week Strip | `WeekStrip` | same | 7 days, dots for deadlines (red) / exams (amber) |
| Sidebar / Bottom bar / Drawer | `Sidebar` | `BottomBar`, `Drawer` | |
| Sheet / Dialog | `Sheet`, `Dialog` (Radix) | `ModalBottomSheet` | |
| Data Table | `DataTable` | — | seller/admin only |
| Skeleton / EmptyState / ErrorState / Toast | `Skeleton`, `EmptyState`, `ErrorState`, `Toaster` | same | skeletons, never spinners for content |

## Do / Don't
- ✅ One dominant element + one primary CTA per screen. Vary section rhythm (split, list, band, asymmetric grid).
- ✅ Real data: Metropolitan University, CSE-2203, ৳২৯৯ with ~~৳৪৯৯~~, Batch 56, Section A.
- ✅ Urgency colours on deadlines; ⭐ for important lessons; honest "এই কোর্স কাদের জন্য নয়".
- ❌ Gradients outside hero/countdown · emoji as icons · glassmorphism/glows/blobs · uniform 3-col card grids everywhere · generic copy · heavy shadows · centered paragraphs · fake stock avatars (use initials).
- Every screen: skeleton, empty (illustration + next action), error (sympathetic Bangla + retry), hover/focus/pressed/disabled, light + dark, 360–1440px, WCAG AA, 44px/48dp targets.
