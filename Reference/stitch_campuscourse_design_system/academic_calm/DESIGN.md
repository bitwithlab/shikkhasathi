---
name: Academic Calm
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#464555'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#777587'
  outline-variant: '#c7c4d8'
  surface-tint: '#4d44e3'
  primary: '#3525cd'
  on-primary: '#ffffff'
  primary-container: '#4f46e5'
  on-primary-container: '#dad7ff'
  inverse-primary: '#c3c0ff'
  secondary: '#006c49'
  on-secondary: '#ffffff'
  secondary-container: '#6cf8bb'
  on-secondary-container: '#00714d'
  tertiary: '#684000'
  on-tertiary: '#ffffff'
  tertiary-container: '#885500'
  on-tertiary-container: '#ffd4a4'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2dfff'
  primary-fixed-dim: '#c3c0ff'
  on-primary-fixed: '#0f0069'
  on-primary-fixed-variant: '#3323cc'
  secondary-fixed: '#6ffbbe'
  secondary-fixed-dim: '#4edea3'
  on-secondary-fixed: '#002113'
  on-secondary-fixed-variant: '#005236'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb95f'
  on-tertiary-fixed: '#2a1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  display-hero:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  display-hero-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 30px
  headline-sm:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 27px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 22px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 18px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.04em
  price-currency:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 22px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-desktop: 1.5rem
  margin: 1rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system establishes an empathetic, high-trust academic environment tailored specifically for university students navigating high-stakes midterm and final exams. Rather than amplifying panic or relying on gamified distraction, the UI acts as a calm, orderly study sanctuary. It conveys structured authority, academic prestige, and approachable modernism.

### Design Movement
The aesthetic synthesizes **Modern Scandinavian Corporate** with **Subtle Neumorphic Depth and Card-based Modularity**. Clean white and deep midnight slate surfaces eliminate visual clutter, while tactile layered cards, soft indigo glow accents, and precise informational hierarchy guide students effortlessly through dense curriculums and high-pressure revision periods.

### Emotional Targets
- **Reassurance & Clarity:** De-escalating exam anxiety through organized, bite-sized syllabus breakdowns.
- **Institutional Legitimacy:** Delivering the academic rigor of premier public and private universities with crisp typography and verified accreditation marks.
- **Effortless Agency:** Seamless, high-confidence navigation optimized for one-handed mobile interactions during late-night study sessions.

## Colors

The palette balances deep focus with high-visibility situational cues designed to prioritize academic milestones.

### Core Swatches
- **Primary Indigo (`#4F46E5` | Dark Mode: `#818CF8`):** Anchors brand trust, core actions, active navigation, and primary CTAs. Primary Soft (`#EEF2FF` in light mode, `rgba(79, 70, 229, 0.15)` in dark mode) serves as tint surfaces for badges and selected states.
- **Accent Emerald (`#10B981`):** Represents syllabus completion, enrolled confirmations, passing progress, and verified instructor marks.
- **Warm Amber (`#F59E0B`):** Dedicated to high-attention triggers, upcoming midterm/final countdowns, and high-frequency exam questions.
- **Critical Red (`#EF4444`):** Strictly reserved for urgent assignment deadlines, registration cutoffs, and destructive actions.

### Surface System
- **Light Mode:** Canvas `Background` (`#F8FAFC`), Elevated `Surface` (`#FFFFFF`), `Border` (`#E2E8F0`), Primary `Text` (`#0F172A`), `Muted` Body (`#64748B`).
- **Dark Mode:** Canvas `Background` (`#0B1020`), Elevated `Surface` (`#131A2E`), Structural `Border` (`#232C45`), Primary `Text` (`#E2E8F0`), `Muted` Body (`#94A3B8`).

### Gradient Treatments
Subtle directional gradients (`linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%)`) are strictly limited to hero banner surfaces, milestone celebration overlays, and high-priority countdown banners to preserve focus across informational cards.

## Typography

The type stack is optimized for bilingual balance between Bengali content and English/alphanumeric course codes (e.g., *CSE-2203*, *MAT-101*, *Midterm*, *৳1250*).

### Script & Line-Height Protocol
- **Font Stack Hierarchy:** `Inter, 'Hind Siliguri', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`.
- **Vertical Rhythm for Indic Script:** Due to the distinct ascenders, descenders, and matras of the Bengali script, all continuous running text levels must retain a loose line-height factor between `1.65` and `1.75` (e.g., 14px font with 24px line height). This prevents diacritic clipping and eye fatigue during prolonged reading.
- **Code & Numeric Clarity:** University course codes, term abbreviations, and prices must render with tabular figures (`font-variant-numeric: tabular-nums`) through the Latin glyphs of Inter to preserve visual baseline alignment with the Bengali body copy.

## Layout & Spacing

A strictly enforced 8px geometric grid dictates all micro and macro spatial distances, ensuring mobile touch precision and desktop scannability.

### Grid & Form Factors
- **Mobile First (<640px):** Single-column stack with `margin: 1rem` (16px) canvas gutters. Component cards fill 100% width with 12px or 16px internal padding. Critical navigation adopts a fixed bottom-bar architecture.
- **Tablet (640px – 1023px):** 6-column fluid grid with `gutter: 1rem` and `margin: 1.5rem`. Course cards reflow into a 2-column layout.
- **Desktop (≥1024px):** 12-column layout max-width constrained to `1200px` centered with `margin-desktop: 2rem` and `gutter-desktop: 1.5rem`. Adapts to a 3-column course grid alongside a persistent 4-column filter/syllabus contextual sidebar.

### Vertical Rhythm
Section wrappers leverage `space-xl` (32px) on mobile and 48px to 64px on larger screens. Form and card item clusters maintain consistent spacing with `space-sm` (8px) for tightly coupled items and `space-md` (16px) for distinct content rows.

## Elevation & Depth

Visual depth utilizes fine 1px containment borders combined with diffuse, colored ambient drop shadows. This creates a multi-layered study workspace while keeping content readable under direct sunlight or in night mode.

### Layer Hierarchy
- **Level 0 (Flat / Canvas):** Used for main background screens (`#F8FAFC` light / `#0B1020` dark). No elevation.
- **Level 1 (Cards, Course Tiles, Input Containers):** `box-shadow: 0 1px 3px 0 rgba(15, 23, 42, 0.05), 0 1px 2px -1px rgba(15, 23, 42, 0.05);` combined with a crisp border `1px solid var(--border)`. In dark mode, borders shift to `rgba(255, 255, 255, 0.07)` with no direct drop shadow to prevent muddy outlines.
- **Level 2 (Hover States, Active Filter Sheets, Floating Progress):** `box-shadow: 0 10px 15px -3px rgba(79, 70, 229, 0.08), 0 4px 6px -4px rgba(15, 23, 42, 0.05);` providing a subtle indigo-tinted elevation lift.
- **Level 3 (Modals, Sticky Paywall Bars, Payment Bottom Sheets):** `box-shadow: 0 20px 25px -5px rgba(15, 23, 42, 0.12), 0 8px 10px -6px rgba(15, 23, 42, 0.08);`. Dark mode uses surface tier separation via an elevated slate container (`#1A233D`).

## Shapes

The system implements consistent corner curves to balance approachable, student-friendly interaction with structural stability.

### Boundary Radius Rules
- **Interactive Controls (Inputs, Buttons, Dropdowns):** `12px` border radius (`rounded-md`), offering large, finger-friendly tap targets that look precise.
- **Structural Containers (Course Cards, Test Prep Modules, Review Banners):** `16px` border radius (`rounded-lg`), establishing distinct compartmentalized study units.
- **Status Identifiers & Filters (Chips, Badges, University Tags):** `9999px` full-pill radius (`rounded-full`), making them immediately distinct from actionable squared buttons.
- **Image & Thumbnail Masks:** `12px` internal radius within `16px` card frames, keeping the 16:9 media area balanced with surrounding padding.

## Components

### Buttons & Interactive Tap Targets
- **Sizing & Accessibility:** Minimum hit area of `44px` height on mobile (standard button height: `48px`).
- **Primary CTA:** Indigo background (`#4F46E5`), bold text (`#FFFFFF`), `12px` border radius. Active state triggers scale down (`scale(0.98)`).
- **Secondary CTA:** Surface background with `1px solid #E2E8F0`, dark text (`#0F172A`).
- **bKash / Nagad Instant Pay Trigger:** Styled button incorporating localized payment accents with localized currency glyphs (`৳`) positioned before the amount.

### Course Card (Mobile-First Core Pattern)
- **Ratio & Thumbnail:** 16:9 aspect-ratio media container with a gentle gradient overlay for low-res preview suppression.
- **Header Badges:** Floating top-left university identification pill (e.g., "NSU", "BUET", "Metropolitan University") and top-right exam indicator ("Midterm Prep", "Final Crash Course").
- **Title & Metadata:** Strict 2-line title truncation (`line-clamp-2`), course code chip (e.g., `CSE-2203`), verified instructor row with green checkmark, followed by star rating and enrolled student counts.
- **Pricing & Checkout Row:** Clear Bengali numeral display with Latin currency fallback: `৳১,২৫০` / `৳1,250`.

### Syllabus Compatibility Badges
- **Match Confirmed:** Tinted emerald badge (`#ECFDF5` light, `rgba(16, 185, 129, 0.12)` dark) with emerald text: `✅ আপনার ইউনিভার্সিটির সিলেবাস`.
- **Syllabus Divergence Warning:** Amber-tinted warning pill (`#FFFBEB` light, `rgba(245, 158, 11, 0.12)` dark) with amber text: `⚠️ ভিন্ন সিলেবাস হতে পারে`.

### Lesson Progress Modules
- **State Indicators:**
  - `Done`: Emerald circle with white checkmark (`✓`).
  - `Locked`: Slate muted circle with lock icon (`🔒`).
  - `Free Preview`: Primary Indigo outline with play indicator (`▶`).
  - `Important / High-Yield Exam Topic`: Warm amber badge with star icon (`⭐ গুরুত্বপূর্ণ`).

### Form Inputs & Selectors
- **Text Inputs:** `48px` minimum height, `12px` radius, `1px solid #E2E8F0`. Focus state applies a `2px solid #4F46E5` outline with an indigo focus ring glow (`rgba(79, 70, 229, 0.15)`).
- **Filter Chips:** Pill-shaped horizontally scrollable row on mobile with zero scrollbar friction; unselected state uses surface tint, selected state uses Primary Indigo fill with white text.