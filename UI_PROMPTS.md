# 🎨 UI Prompt Pack — CampusCourse (Multi-University Exam-Prep Marketplace)

> Stitch, Figma AI, v0, Lovable বা ChatGPT-তে সরাসরি কপি-পেস্ট করার জন্য তৈরি।
> প্রম্পট **ইংরেজিতে** (AI টুল ইংরেজিতে সবচেয়ে ভালো ফল দেয়), কিন্তু UI-র ভেতরের লেখা **বাংলা + ইংরেজি মিশ্র** রাখা হয়েছে।

---

## 📌 কীভাবে ব্যবহার করবেন

1. **প্রথমে** [Step 0: Master Design System Prompt](#step-0--master-design-system-prompt) পেস্ট করুন। এটি পুরো প্রজেক্টের ডিজাইন নিয়ম ঠিক করে দেবে।
2. তারপর **একটি করে স্ক্রিনের প্রম্পট** দিন (একসঙ্গে সব নয়)।
3. প্রতিটি স্ক্রিনের পর [Refinement Prompts](#-refinement-prompts-পলিশ-করার-জন্য) দিয়ে ঠিক করুন।
4. একই চ্যাট/প্রজেক্টে থাকুন, যাতে স্টাইল ধারাবাহিক থাকে।

**টিপস**
- প্রথমে **Mobile (360px)** বানান, তারপর Desktop (1440px)।
- ফল ভালো না হলে নতুন প্রম্পট না লিখে *"Make it more X"* বলে ধাপে ধাপে বদলান।
- এক প্রম্পটে **একটি স্ক্রিন**, বড়জোর **একটি ফ্লো**।

---

## Step 0 — Master Design System Prompt

> ⬇️ এটি সবার আগে পেস্ট করুন।

```text
You are a senior product designer. Design a modern, premium, mobile-first web app called "CampusCourse" — a multi-university exam-preparation course marketplace for students in Bangladesh. Teachers from any university create and sell short, exam-focused courses (Midterm, Final, Quiz crash courses). Students find courses by university, subject, or course code, buy them with bKash/Nagad, and learn from one dashboard.

BRAND & MOOD
- Calm, focused, confident, friendly. Reduces exam stress. Premium but approachable. Not childish, not corporate-boring.
- Tagline: "Your University. Your Courses. Your Exam Preparation."

LANGUAGE
- UI is Bangla-first with English for technical terms (Midterm, Final, Course Code, Dashboard). Use realistic Bangla copy, not lorem ipsum.
- Fonts: Hind Siliguri (Bangla) + Inter (English & numbers). Bangla line-height 1.7.

COLOR TOKENS (support Light and Dark)
- Primary Indigo #4F46E5 (dark: #818CF8), Primary Soft #EEF2FF
- Accent Emerald #10B981 for success/progress/"Enrolled"
- Warm Amber #F59E0B for exam countdown and "Important" stars
- Danger #EF4444 for deadlines
- Background #F8FAFC, Surface #FFFFFF, Text #0F172A, Muted #64748B, Border #E2E8F0
- Dark: Background #0B1020, Surface #131A2E, Text #E2E8F0, Border #232C45
- Use soft Indigo→Violet gradients sparingly (hero, countdown card).

SHAPE & SPACING
- 8px grid. Cards radius 16px, buttons/inputs 12px, chips fully rounded.
- Very soft layered shadows + 1px border. Generous whitespace.

COMPONENTS (keep consistent everywhere)
- Course Card: 16:9 thumbnail, 2-line title, teacher name with verified ✔ badge, university chip, exam-type chip (Midterm/Final/Quiz), rating ⭐, price in ৳ with strikethrough old price, subtle hover lift.
- Compatibility badge: green "✅ আপনার ইউনিভার্সিটির সিলেবাস" or amber "⚠️ ভিন্ন সিলেবাস হতে পারে".
- Progress ring/bar in Emerald. Lesson rows with ✓ done, 🔒 locked, ▶ preview, ⭐ important.
- Icons: Lucide style, 1.75px stroke. Illustrations: friendly flat, minimal, Indigo/Emerald tones.

QUALITY RULES
- Real content, realistic data (Metropolitan University, North South, BRAC; Data Structures CSE-2203; prices ৳199–৳499).
- Clear visual hierarchy, one primary action per screen, 44px minimum tap targets, WCAG AA contrast.
- Include loading skeletons, empty states, and hover/focus states where relevant.

Acknowledge this design system briefly and wait for the screen request.
```

---

## 🏠 Public / Marketing

### 1. Landing Page (Desktop)

```text
Design the Landing Page (desktop, 1440px) for CampusCourse.

Sections in order:
1. Sticky navbar: logo, links (Explore, Universities, Subjects, Exam Prep), "লগইন" ghost button, "শুরু করুন" primary button, language toggle (বাং/EN).
2. Hero: big Bangla headline "পরীক্ষার আগে যা দরকার, শুধু সেটুকুই শিখুন", subheadline, a large smart search bar ("ইউনিভার্সিটি, সাবজেক্ট বা কোর্স কোড লিখুন…") with a Search button, below it popular chips (Metropolitan University, Data Structures, CSE-2203, Midterm). Right side: a floating collage of 3 course cards and a small "Exam in 5 days" countdown card, soft gradient background.
3. Trust strip: "১০,০০০+ শিক্ষার্থী · ৫০০+ কোর্স · ২০+ ইউনিভার্সিটি" with university logo placeholders.
4. "কীভাবে কাজ করে" — 3 steps with icons: খুঁজুন → কিনুন → শিখুন.
5. "এই সপ্তাহের Midterm Crash Courses" — horizontal course card carousel with tabs by university.
6. "Exam Mode" feature showcase: split layout with phone mockup of the study plan + checklist of benefits.
7. Verified teachers grid with avatars, rating, student count.
8. Testimonials (3 cards).
9. Teacher CTA banner: "আপনার জ্ঞান শেয়ার করুন, আয় করুন" with "শিক্ষক হিসেবে যোগ দিন".
10. FAQ accordion, then a rich footer (links, bKash/Nagad/Visa logos, social, "Not affiliated with any university" note).

Style: airy, premium, subtle gradients, large typography.
```

### 2. Landing Page (Mobile)

```text
Design the mobile (360px) version of the CampusCourse landing page. Same content and design system, but: compact navbar with hamburger, hero with stacked headline and a full-width search bar, horizontally scrollable chips, vertical stack of sections, sticky bottom bar with "কোর্স খুঁজুন" primary button. Course cards in horizontal swipe carousels. Keep thumb-friendly spacing.
```

---

## 🔍 Student — Discovery

### 3. Onboarding / Academic Profile Setup (Mobile)

```text
Design a 4-step onboarding flow (mobile 360px) that builds the student's academic profile:
Step 1: Select University (searchable list with logos + "আমার ইউনিভার্সিটি নেই? অনুরোধ করুন").
Step 2: Department & Program (chips).
Step 3: Semester, Batch, Section (pickers).
Step 4: Next exam (optional): subject + exam type + date picker, with a friendly "Exam Mode চালু করুন" toggle.
Top progress stepper, friendly illustration per step, sticky bottom "পরবর্তী" button, "পরে করব" text link. Final success screen with confetti and "আপনার জন্য কোর্স সাজানো হচ্ছে…".
```

### 4. Student Home — Personalized Feed

```text
Design the student Home screen (mobile 360px and desktop 1440px).
- Greeting "আসসালামু আলাইকুম, [নাম] 👋" and the student's chip: "Metropolitan University · CSE · 5th Semester".
- Upcoming exam card (indigo→violet gradient): "Data Structures Midterm — ৫ দিন বাকি", progress ring 40%, CTA "Exam Mode খুলুন".
- Section "আপনার সেমিস্টারের কোর্স" — course cards with green compatibility badge.
- Section "একই সাবজেক্ট, অন্য ইউনিভার্সিটি" — cards with amber badge.
- Section "Midterm Crash Courses", "জনপ্রিয় এই সপ্তাহে".
- Quick filters row (Midterm / Final / Quiz / Free).
- Bottom tab bar (mobile): Home, Explore, My Courses, Hub, Profile.
```

### 5. Explore / Search Results

```text
Design the Explore page with search results.
Desktop: left filter sidebar (University, Department, Semester, Subject, Course Code, Batch, Section, Exam Type, Course Type, Price range slider, Language, Rating, Duration) with collapsible groups; main area with a large search bar with autocomplete dropdown grouped by University / Subject / Course Code / Teacher, active filter chips with "সব মুছুন", result count "২৪টি কোর্স পাওয়া গেছে", sort dropdown (Best match, Newest, Rating, Price), grid of course cards with compatibility badges.
Mobile: search bar on top, filter button opening a bottom sheet, vertical list of cards.
Also design: a zero-results state with illustration and a "এই কোর্স চাই" (Request this course) button.
```

### 6. Browse by University / Subject

```text
Design two browse pages:
(a) Universities page: grid of university cards (logo, name, number of courses, number of teachers), search box, filter by type (Public/Private/College/Polytechnic).
(b) University detail page: hero banner with logo, stats, department tabs, semester pills, subject list with course counts, and a course grid below.
Keep consistent with the design system; clean and scannable.
```

### 7. Course Details Page

```text
Design the Course Details page for "Data Structures — Midterm Crash Course" (CSE-2203, Metropolitan University, 5th Semester, Batch 52/53).
Desktop: breadcrumb (University › CSE › 5th Semester › Data Structures). Left column: title, short description, teacher card with verified badge and rating, chips (Midterm, Crash Course, বাংলা, ২ঘ ৩০মি), compatibility badge, "এই কোর্সে যা শিখবেন" checklist, collapsible curriculum (sections → lessons with duration, preview ▶, ⭐ important marks), notes & previous questions block, "এই কোর্স কাদের জন্য নয়" honesty section, reviews with rating distribution, FAQ.
Right column: sticky buy card with thumbnail/preview video, price ৳299 (old ৳499, 40% off), coupon input, "এখনই কিনুন" primary button, "প্রিভিউ দেখুন" secondary, includes list (video, PDF notes, questions, mock test, lifetime access), refund note, share/wishlist icons.
Mobile: sticky bottom bar with price and Buy button.
Add a gentle disclaimer: "সম্ভাব্য গুরুত্বপূর্ণ টপিক — পরীক্ষায় কী আসবে তার নিশ্চয়তা নয়।"
```

### 8. Checkout & Payment

```text
Design a 2-step checkout (mobile + desktop): order summary card (course thumbnail, title, price, coupon), payment method selector with bKash, Nagad, Rocket, Card (brand-colored selectable tiles), phone number input, security note, "৳299 পরিশোধ করুন" button.
Also design: payment processing state, success screen (✅ animation, "কোর্স আনলক হয়েছে 🎉", "শেখা শুরু করুন" button, download receipt), and a failure screen with a reassuring message "পেমেন্ট সম্পন্ন হয়নি। চিন্তা নেই, আপনার টাকা কাটা হয়নি।" with retry.
```

---

## 🎒 Student — Learning

### 9. My Courses & Student Dashboard

```text
Design the Student Dashboard (desktop with left sidebar, and mobile).
Widgets: "Continue Learning" large card with progress bar and resume button; Upcoming Exam countdown; Pending Assignments & Deadlines timeline (from Google Classroom, red/amber urgency); My Courses grid with progress rings; Recently Watched; Notes & Bookmarks; University Notices list; Recommended Courses.
Sidebar: Home, Explore, My Courses, Exam Mode, Academic Hub, My Learning, Profile, Settings. Add dark mode variant.
```

### 10. Course Player (Learning Screen)

```text
Design the course learning screen. Desktop: large video player (speed control, quality, captions, chapter markers, subtle moving watermark with student phone number, resume prompt), below it tabs: Overview, Notes (PDF), Q&A, Important Topics, Previous Questions. Right: collapsible curriculum sidebar with checkmarks, locked items, ⭐ important marks, and overall progress bar.
Mobile: video on top (sticky), tabs below, curriculum as a bottom sheet. Add "পরের লেসন →" button and a "Mark as complete" toggle. Include a lesson-level doubt question input.
```

### 11. Exam Mode ⭐ (Flagship)

```text
Design "Exam Mode" — the flagship feature (mobile-first, plus desktop).
Top: big gradient countdown card "Data Structures Midterm — ৫ দিন ২০ ঘণ্টা" with a circular readiness meter (62%).
Below: "আজকের প্ল্যান" daily checklist (e.g., 2 short lectures, 1 quiz, 10 flashcards) with check animations; syllabus checklist by chapter with status chips (বাকি / চলছে / শেষ); "গুরুত্বপূর্ণ টপিক ⭐" list from the teacher; Short Lectures (5–15 min) horizontal cards; Previous Questions by year; Quick Revision flashcards (swipe card UI); Mock Test (30 min) start card; "পরীক্ষার আগের রাত" last-night revision pack card.
Tone: motivating, calm. Add confetti micro-moment when a daily goal is completed.
```

### 12. Mock Test Screen

```text
Design a timed Mock Test experience: pre-start screen (rules, duration 30:00, number of questions), question screen (progress bar, timer at top in amber, question, 4 option tiles, flag-for-review, Previous/Next, question navigator drawer), submit confirmation modal, and result screen (score ring, correct/wrong/skipped, chapter-wise performance bars, "ভুল প্রশ্নগুলো দেখুন" and "আবার চেষ্টা করুন"). Mobile-first.
```

### 13. Academic Hub (Google Classroom)

```text
Design the Academic Hub screen showing Google Classroom data (read-only): "Google Classroom সংযুক্ত ✅" status, tabs: Assignments, Announcements, Calendar. Assignments grouped by due date (Today, Tomorrow, This week) with status chips (Pending, Submitted, Graded), course name, and an "Open in Classroom" link. Announcements as clean feed cards. Calendar month view with exam and deadline dots. Include the empty/not-connected state with a "Google Classroom সংযুক্ত করুন" button and a short privacy note ("আমরা শুধু পড়ার অনুমতি চাই").
```

### 14. Course Request Board

```text
Design a "কোর্স অনুরোধ" (Course Request Board): list of requested courses with upvote buttons, subject, university, request count, and status (Open / Teacher working / Published). Top: "নতুন কোর্স চাই" form (University, Subject, Exam type, note). Teachers see a "আমি বানাব" button. Friendly, community feel.
```

---

## 👩‍🏫 Teacher

### 15. Teacher Dashboard

```text
Design the Teacher Dashboard (desktop, sidebar layout): KPI cards (Total Earnings, This Month, Students, Avg Rating), revenue line chart with 7d/30d/90d toggle, "Top Courses" table, recent enrollments, unanswered Q&A inbox, payout balance card with "টাকা তুলুন" button, verification status banner. Clean analytics look, Indigo accents.
```

### 16. Course Creator Wizard

```text
Design a 5-step Course Creator wizard (desktop) with a live preview card on the right showing how the course will look to students.
Step 1 Basics: title, short description, cover upload, language.
Step 2 Target: multi-select Institutions (or "General / Cross-University" toggle), Department, Program, Semester, Subject (autocomplete), Course Code, Batch chips, Section chips, Exam Type, Course Type.
Step 3 Content: drag-and-drop Section → Chapter → Lesson builder, video upload with progress, PDF notes, mark lesson as Preview or ⭐ Important, add quiz.
Step 4 Pricing: price, discount, coupon, access type (Lifetime / X days / Until exam), bundle option.
Step 5 Review & Publish: checklist with validation, "রিভিউয়ের জন্য পাঠান".
Top stepper, autosave indicator, helpful tooltips.
```

### 17. Teacher Public Storefront

```text
Design a public teacher profile page: cover, avatar, verified badge, bio, universities taught, stats (students, courses, rating), course grid, reviews, and "Follow" button. Shareable link chip.
```

---

## 🛠️ Admin

### 18. Admin Panel

```text
Design an Admin Panel (desktop): left nav (Dashboard, Teacher Verification, Institution Requests, Course Review Queue, Subject Master, Payments & Refunds, Payouts, Reports, Users, Settings). Show: dashboard with GMV, orders, pending approvals; a review-queue table with filters, status badges, row actions (Approve / Reject / Request changes) and a side drawer showing course details and a verification checklist. Dense but clean, data-table focused.
```

---

## 🧩 Extra Screens

### 19. Auth Screens
```text
Design Login / Sign-up (mobile + desktop): phone-number OTP as primary, email as secondary, role switch (Student / Teacher), Google sign-in, friendly illustration on the side (desktop). OTP input with 6 boxes and resend timer. Bangla microcopy.
```

### 20. Profile & Settings
```text
Design Profile & Settings: academic info card (University, Dept, Semester, Batch, Section, editable), Google Classroom connection, notification preferences (SMS/Push/Email toggles), device management list, payment history, language and dark-mode toggles, delete-account section.
```

### 21. Empty, Error & Loading States
```text
Design a sheet of reusable states: skeleton loaders for course cards and lists, empty states (no courses, no purchases, no assignments), 404 and offline pages, and toast/snackbar variants (success, error, info) — all with friendly Bangla microcopy and consistent illustrations.
```

---

## ✨ Refinement Prompts (পলিশ করার জন্য)

| লক্ষ্য | প্রম্পট |
|---|---|
| আরও প্রিমিয়াম | `Make it feel more premium: refine spacing, soften shadows, improve typography hierarchy, and reduce visual clutter.` |
| আরও Bangla-friendly | `Improve Bangla typography: use Hind Siliguri, line-height 1.7, ensure no text clipping, and balance mixed Bangla-English text.` |
| Dark Mode | `Create the dark mode version using the dark tokens. Keep contrast AA and avoid pure black.` |
| Mobile | `Adapt this screen to mobile 360px with thumb-friendly layout, bottom navigation, and bottom sheets for filters.` |
| কম ভিড় | `Simplify this screen. Keep one primary action. Remove secondary elements or move them to a collapsed section.` |
| অ্যানিমেশন | `Add subtle micro-interactions: card hover lift, progress ring animation, confetti on completion, 200ms ease-out transitions.` |
| Consistency | `Audit this screen against the design system (colors, radius, spacing, components) and fix inconsistencies.` |
| Accessibility | `Check contrast, tap target sizes, focus states and add them. Target WCAG AA.` |
| ডেটা বাস্তব | `Replace placeholder text with realistic Bangladeshi university data, Bangla copy, and ৳ prices.` |

---

## 🤖 ChatGPT-এ প্রম্পট বানানোর Meta Prompt

> আপনি যদি নিজে ChatGPT দিয়ে আরও প্রম্পট বানাতে চান, এটি ব্যবহার করুন।

```text
Act as a senior UI/UX designer and prompt engineer. I am designing "CampusCourse", a multi-university exam-prep course marketplace for Bangladesh (Bangla-first UI, Indigo/Emerald/Amber palette, Hind Siliguri + Inter fonts, mobile-first, premium calm style).

Write a detailed, copy-paste-ready prompt for an AI UI design tool (like Google Stitch) to generate the following screen: [SCREEN NAME].

The prompt must include: screen purpose, device size, layout structure top-to-bottom, exact components, realistic Bangla + English copy, states (loading/empty/error), interactions, and visual style notes. Output only the final prompt in one code block.
```

---

## ✅ ডিজাইন শেষে চেকলিস্ট

- [ ] সব স্ক্রিনে একই কালার, ফন্ট, radius
- [ ] Mobile (360px) ও Desktop (1440px) দুটোই আছে
- [ ] Light + Dark মোড
- [ ] বাংলা টেক্সট কাটা বা ভাঙা নেই
- [ ] Loading / Empty / Error স্টেট আছে
- [ ] Course Card সবখানে একই রকম
- [ ] প্রতিটি স্ক্রিনে একটাই প্রধান CTA
- [ ] ডিজাইন Figma-তে সেভ করা, কম্পোনেন্ট নামকরণ ঠিক

**সুপারিশকৃত স্ক্রিন বানানোর ক্রম:** Master Prompt → Landing → Onboarding → Home → Explore → Course Details → Checkout → Dashboard → Course Player → Exam Mode → Teacher Dashboard → Course Creator → Admin।
