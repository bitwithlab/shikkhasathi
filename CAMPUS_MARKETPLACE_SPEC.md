# 🎓 Multi-University Exam-Prep Course Marketplace
### প্রজেক্ট স্পেসিফিকেশন ও ডিজাইন গাইড (Working name: **CampusCourse**)

> **এক লাইনে:** বাংলাদেশের যেকোনো ইউনিভার্সিটি/কলেজ/ইনস্টিটিউশনের শিক্ষক এখানে পরীক্ষাকেন্দ্রিক ছোট কোর্স বানিয়ে বিক্রি করবেন; শিক্ষার্থী নিজের ইউনিভার্সিটি, সাবজেক্ট বা কোর্স কোড দিয়ে খুঁজে কিনবে এবং এক ড্যাশবোর্ডে পড়াশোনা চালাবে।

**ট্যাগলাইন:** *Your University. Your Courses. Your Exam Preparation. All in One Place.*
**বিকল্প মেসেজ:** *Learn What Matters for Your Next Exam.*

---

## 📑 সূচিপত্র

1. [প্রজেক্টের মূল ধারণা](#1-প্রজেক্টের-মূল-ধারণা)
2. [ব্যবহারকারীর ধরন (Roles)](#2-ব্যবহারকারীর-ধরন-roles)
3. [মূল ডিজাইন সিদ্ধান্ত: Create ≠ Discover](#3-মূল-ডিজাইন-সিদ্ধান্ত-create--discover)
4. [Teacher Course Creation System](#4-teacher-course-creation-system)
5. [Student Discovery & Search](#5-student-discovery--search)
6. [Course Matching (Smart Ranking)](#6-course-matching-smart-ranking)
7. [Institution Directory](#7-institution-directory)
8. [Enrollment, Payment ও Access](#8-enrollment-payment-ও-access)
9. [Student Dashboard & Exam Mode](#9-student-dashboard--exam-mode)
10. [Google Classroom Integration](#10-google-classroom-integration)
11. [অতিরিক্ত ফিচার (নতুন প্রস্তাব)](#11-অতিরিক্ত-ফিচার-নতুন-প্রস্তাব)
12. [Business Model](#12-business-model)
13. [নেভিগেশন ও পেজ স্ট্রাকচার](#13-নেভিগেশন-ও-পেজ-স্ট্রাকচার)
14. [🎨 UI/UX ডিজাইন সিস্টেম (সুন্দর করার গাইড)](#14--uiux-ডিজাইন-সিস্টেম)
15. [ডেটাবেস ডিজাইন](#15-ডেটাবেস-ডিজাইন)
16. [প্রস্তাবিত Tech Stack](#16-প্রস্তাবিত-tech-stack)
17. [Roadmap (MVP → V2)](#17-roadmap-mvp--v2)
18. [ঝুঁকি, আইন ও নিয়মনীতি](#18-ঝুঁকি-আইন-ও-নিয়মনীতি)
19. [Success Metrics](#19-success-metrics)
20. [Claude Code দিয়ে বানানোর নির্দেশনা](#20-claude-code-দিয়ে-বানানোর-নির্দেশনা)

---

## 1. প্রজেক্টের মূল ধারণা

### কী এটা?
এটি **University Management System নয়**। এটি একটি **Multi-University Course Marketplace**, যেখানে:

- শিক্ষক কোর্স বানান এবং ঠিক করেন কোর্সটি **কাদের জন্য** (ইউনিভার্সিটি, ডিপার্টমেন্ট, সেমিস্টার, ব্যাচ, সেকশন)।
- শিক্ষার্থী **যেভাবে খুশি** খুঁজে কোর্স কেনে (ইউনিভার্সিটি / সাবজেক্ট / কোর্স কোড)।
- প্ল্যাটফর্ম কমিশন নেয় এবং পেমেন্ট, ভিডিও, ভেরিফিকেশন সামলায়।

### কেন কাজ করবে?
| সমস্যা | সমাধান |
|---|---|
| পরীক্ষার আগে সময় কম, পুরো কোর্স দেখার সুযোগ নেই | ৫–১৫ মিনিটের Exam-Oriented ছোট লেকচার |
| ভালো শিক্ষক পাওয়া কঠিন | যেকোনো ইউনিভার্সিটির শিক্ষকের কোর্স কেনা যায় |
| নোট, প্রশ্ন, সাজেশন ছড়িয়ে-ছিটিয়ে আছে | এক জায়গায় কোর্সের সঙ্গে সব |
| Assignment/Deadline ভুলে যাওয়া | Google Classroom সিঙ্ক করা ড্যাশবোর্ড |

### মূল তিন স্তম্ভ
1. **University-Based Marketplace** – নিজের ইউনিভার্সিটির সাবজেক্ট অনুযায়ী কোর্স।
2. **Exam-Focused Learning** – Midterm, Final, Quiz-এর লক্ষ্যভিত্তিক কোর্স।
3. **Unified Academic Dashboard** – কেনা কোর্স + Classroom আপডেট এক জায়গায়।

> ⚠️ **নীতি:** "পরীক্ষায় এটাই আসবে" – এমন নিশ্চয়তা কোনো শিক্ষক বা প্ল্যাটফর্ম দেবে না। ভাষা হবে *"সম্ভাব্য গুরুত্বপূর্ণ টপিক"*।

---

## 2. ব্যবহারকারীর ধরন (Roles)

| Role | কী করতে পারে |
|---|---|
| **Guest** | কোর্স ব্রাউজ, প্রিভিউ লেসন দেখা, সার্চ |
| **Student** | কোর্স কেনা, শেখা, প্রগ্রেস, রিভিউ, Q&A |
| **Teacher** | কোর্স তৈরি, ভিডিও/নোট আপলোড, প্রাইসিং, অ্যানালিটিক্স, পেআউট |
| **Institution Moderator** *(ঐচ্ছিক, পরে)* | নিজের প্রতিষ্ঠানের সঠিক সিলেবাস/কোর্স কোড যাচাই |
| **Platform Admin** | শিক্ষক ও প্রতিষ্ঠান ভেরিফিকেশন, রিফান্ড, কনটেন্ট মডারেশন, রেভিনিউ |
| **Support Agent** *(পরে)* | টিকেট, রিফান্ড রিকোয়েস্ট |

---

## 3. মূল ডিজাইন সিদ্ধান্ত: Create ≠ Discover

> **Course Creation = Teacher decides the target audience.**
> **Course Discovery = Student decides how to search.**

এই দুটোকে আলাদা রাখলে প্ল্যাটফর্ম একটি ইউনিভার্সিটি দিয়ে শুরু হয়েও পুরো দেশে স্কেল করবে।

```
Teacher ──(Target ঠিক করে)──▶ Course ──(Index হয়)──▶ Search Engine ◀──(যেভাবে খুশি খোঁজে)── Student
```

---

## 4. Teacher Course Creation System

### 4.1 Course Creation Form

| Field | বিবরণ | বাধ্যতামূলক |
|---|---|---|
| Course Title | যেমন: *Data Structures Midterm Preparation* | ✅ |
| Cover Image / Intro Video | আকর্ষণীয় থাম্বনেইল | ✅ |
| Short Description | ১–২ লাইনের সারাংশ | ✅ |
| Institution(s) | এক বা একাধিক, অথবা **General (Cross-University)** | ✅ |
| Department | CSE, EEE, BBA… | ✅ |
| Program | BSc in CSE… | ঐচ্ছিক |
| Academic Level | Undergraduate / Diploma / HSC… | ✅ |
| Semester | 5th… | ✅ |
| Subject | Data Structures (Subject Master থেকে) | ✅ |
| Course Code | CSE-2203 (ইউনিভার্সিটিভেদে ভিন্ন হতে পারে) | ঐচ্ছিক |
| Target Batch | 52, 53… | ঐচ্ছিক |
| Target Section | A, B বা All | ঐচ্ছিক |
| Exam Type | Midterm / Final / Quiz / Assignment | ✅ |
| Course Type | Crash Course / Full Course / Notes Pack | ✅ |
| Language | বাংলা / English / মিশ্র | ✅ |
| Price + Discount | ৳ | ✅ |
| Access Type | Lifetime / নির্দিষ্ট দিন / পরীক্ষা পর্যন্ত | ✅ |
| Syllabus Coverage | কোন কোন চ্যাপ্টার কভার হবে | ✅ |
| Content | Videos, PDF Notes, Questions, Mock Test | ✅ |

### 4.2 গুরুত্বপূর্ণ নিয়ম
- একই কোর্স **একাধিক Academic Target**-এ প্রকাশ করা যাবে → ভিডিও একবারই আপলোড হবে।
- **Target** (কাদের জন্য) কোর্স থেকে আলাদা টেবিলে থাকবে (Course ↔ Target = এক-থেকে-অনেক)।
- শিক্ষক কোর্সটি কোনো ইউনিভার্সিটির কোর্স কোড/সিলেবাসের সঙ্গে যুক্ত করতে পারবেন।
- ইউনিভার্সিটি ও Subject-এর মাস্টার ডেটা আলাদা; শিক্ষক চাইলে নতুন Subject প্রস্তাব করতে পারবেন (Admin অনুমোদন)।
- কোর্স **Draft → Review → Published** ফ্লো মানবে (কোয়ালিটি কন্ট্রোল)।

### 4.3 Course Builder (শিক্ষকের স্টুডিও)
- Drag-and-drop দিয়ে **Section → Chapter → Lesson** সাজানো
- Lesson টাইপ: Video, PDF, Quiz, Text Note, Live Class, Assignment
- **Preview Lesson** চিহ্নিত করা (ফ্রি দেখা যাবে)
- ⭐ **"Important" ট্যাগ** – কোন টপিক পরীক্ষার জন্য বেশি গুরুত্বপূর্ণ
- Auto-save, Upload progress, Resume upload (দুর্বল নেটওয়ার্কের জন্য)
- ভিডিও Auto-transcode (360p/480p/720p)

---

## 5. Student Discovery & Search

### 5.1 চারটি সার্চ পদ্ধতি
1. **Smart Search Box** – একটি বক্সে ইউনিভার্সিটি / সাবজেক্ট / কোর্স কোড / শিক্ষকের নাম
2. **Browse by University**
3. **Browse by Subject**
4. **Browse by Semester / Exam Type**

### 5.2 Filters

| Filter | উদাহরণ |
|---|---|
| University | Metropolitan University |
| Department | CSE |
| Semester | 5th |
| Subject | Data Structures |
| Course Code | CSE-2203 |
| Batch / Section | 52 / A |
| Exam Type | Midterm |
| Course Type | Crash Course |
| Price | Free / Paid / রেঞ্জ |
| Language | বাংলা / English |
| Rating | ৪+ ⭐ |
| Duration | ≤ ২ ঘণ্টা, ২–৫ ঘণ্টা… |

> **নিয়ম:** সব ফিল্টার পূরণ করা বাধ্যতামূলক নয়। শুধু "Data Structures" লিখলেও প্রাসঙ্গিক সব কোর্স আসবে।

### 5.3 Search এর মান বাড়াতে
- **Bangla + English মিশ্র সার্চ** ("ডাটা স্ট্রাকচার", "data structure", "DS")
- **Typo tolerance** ও synonym (DBMS = Database Management)
- **Autocomplete + Recent Searches**
- **Course Compatibility Badge:** ⚠️ "এই কোর্স অন্য ইউনিভার্সিটির সিলেবাস অনুযায়ী" / ✅ "আপনার ইউনিভার্সিটির সিলেবাস অনুযায়ী"
- Empty result হলে: **"এই কোর্স চাই" (Request a Course)** বাটন → শিক্ষকদের জন্য ডিমান্ড সিগন্যাল

---

## 6. Course Matching (Smart Ranking)

শিক্ষার্থীর প্রোফাইল (University, Department, Program, Semester, Batch, Section) ধরে হোম ফিডের অগ্রাধিকার:

| Priority | Match |
|---|---|
| 1 | নিজের University + Dept + Semester + Batch |
| 2 | নিজের University + Dept + Semester |
| 3 | একই Subject, অন্য University |
| 4 | Cross-University / General কোর্স |
| 5 | Related Subject / Recommended |

**Ranking Score (প্রস্তাবিত):**
```
score = match_weight (উপরের Priority)
      + rating_weight
      + recency_weight (পরীক্ষা ঘনিয়ে এলে বাড়বে)
      + popularity_weight (সাম্প্রতিক এনরোলমেন্ট)
      + teacher_verified_bonus
```
> **Exam Proximity Boost:** শিক্ষার্থী পরীক্ষার তারিখ দিলে ওই সাবজেক্টের Crash Course ফিডের উপরে আসবে।

---

## 7. Institution Directory

- কোনো Institution তালিকায় না থাকলে শিক্ষক/স্টুডেন্ট **Add Request** দিতে পারবেন।
- Admin যাচাই করে **Approved** করবেন (নাম, লোগো, ধরন: Public/Private/College/Polytechnic)।
- Approved Institution-এ Department, Program, Subject যুক্ত হবে।
- ডুপ্লিকেট এড়াতে **নাম নরমালাইজেশন** (যেমন: "Metropolitan Univ." = "Metropolitan University")।
- **Teacher Verification:** ইউনিভার্সিটি ইমেইল / আইডি কার্ড / LinkedIn / রেফারেন্স – যেকোনো একটি।
- **Verified Teacher ব্যাজ** ✔ – ট্রাস্ট বাড়ায়।

---

## 8. Enrollment, Payment ও Access

### 8.1 কোর্স ডিটেইলস পেজে যা থাকবে
Title · Instructor + ব্যাজ · Applicable University · Dept/Semester · Subject & Code · Target Batch/Section · Exam Type · Curriculum (Collapsible) · Preview Lessons · Notes/Resources · Reviews · Price & Discount · **Enroll Now** · Compatibility Badge · "এই কোর্স যাদের জন্য নয়" সেকশন (সততা = বিশ্বাস)

### 8.2 Payment
| বিষয় | বিস্তারিত |
|---|---|
| Gateway | bKash, Nagad, Rocket, কার্ড (SSLCommerz / aamarPay ইত্যাদি) |
| Flow | Purchase → Webhook Verify → Auto Enrollment → Unlock |
| Coupon | শিক্ষক-ভিত্তিক ও প্ল্যাটফর্ম-ভিত্তিক |
| Bundle | Midterm + Final Bundle, Semester Package |
| Refund | নির্দিষ্ট নীতি (যেমন: ২৪–৪৮ ঘণ্টা + ২০% এর কম দেখা হলে) |
| Revenue Split | শিক্ষক % / প্ল্যাটফর্ম % (Ledger-based হিসাব) |
| Payout | Bkash/ব্যাংকে মাসিক বা সাপ্তাহিক পেআউট রিকোয়েস্ট |
| Invoice | প্রতিটি কেনাকাটার PDF রিসিট |

### 8.3 Access Control
- ভিডিও **Signed URL + Token** দিয়ে সুরক্ষিত
- **Device limit** (যেমন: ২–৩ ডিভাইস) ও অ্যাকাউন্ট শেয়ারিং ডিটেকশন
- **Dynamic Watermark** (স্টুডেন্টের ফোন/আইডি ভিডিওর উপরে ভাসবে)
- Lifetime / Time-limited / Exam-date পর্যন্ত অ্যাক্সেস

---

## 9. Student Dashboard & Exam Mode

### 9.1 Dashboard উইজেট
My Courses · Continue Learning · Upcoming Exams · Pending Assignments · Deadlines · University Notices · Recently Watched · Progress · Personal Notes · Bookmarks · Recommended

### 9.2 ⚡ Exam Mode (ফ্ল্যাগশিপ ফিচার)
শিক্ষার্থী পরীক্ষার তারিখ দিলে সিস্টেম **Study Plan** বানাবে।

| উপাদান | উদাহরণ |
|---|---|
| Countdown | "Midterm in 5 days" |
| Syllabus Checklist | কোন চ্যাপ্টার বাকি |
| Important Topics | শিক্ষকের চিহ্নিত ⭐ টপিক |
| Short Lectures | ৫–১৫ মিনিট |
| Previous Questions | আগের বছরের প্রশ্ন + সমাধান |
| Quick Revision | সূত্র, সংজ্ঞা, ফ্ল্যাশকার্ড |
| Mock Test | ৩০ মিনিট টাইমড টেস্ট |
| Daily Plan | "আজ ২টি চ্যাপ্টার + ১টি কুইজ" |
| Last-Night Revision | পরীক্ষার আগের রাতের সংক্ষিপ্ত প্যাক |
| Study Progress | % প্রস্তুতি |

---

## 10. Google Classroom Integration

**ধাপে ধাপে, Read-only দিয়ে শুরু।**

| ফিচার | কাজ |
|---|---|
| Connect Google Account | OAuth (minimum scope) |
| Assignment Feed | সব Classroom-এর অ্যাসাইনমেন্ট এক জায়গায় |
| Announcements | শিক্ষকের নোটিশ |
| Deadlines | সময় অনুযায়ী সাজানো |
| Submission Status | Pending / Submitted / Graded |
| Calendar Sync | পরীক্ষা ও ডেডলাইন |
| Smart Reminder | ডেডলাইনের ২৪ ঘণ্টা আগে Push/SMS |

**আর্কিটেকচার নিয়ম:** Classroom ডেটা = *External Academic Source* (আলাদা টেবিল/ক্যাশ)। কোর্স, পেমেন্ট, এনরোলমেন্ট = প্ল্যাটফর্মের নিজস্ব নিয়ন্ত্রণে।
> ⚠️ Google OAuth verification-এর সময় লাগে (sensitive scopes) – তাই V1.5-এ রাখা হয়েছে।

---

## 11. অতিরিক্ত ফিচার (নতুন প্রস্তাব)

### 🔥 উচ্চ প্রভাব (MVP/V1.5-এ বিবেচনা করুন)
| ফিচার | কেন গুরুত্বপূর্ণ |
|---|---|
| **Course Request Board** | শিক্ষার্থী চায় এমন কোর্সের ডিমান্ড দেখে শিক্ষক কোর্স বানাবে |
| **Free Preview + Free Starter Pack** | বিশ্বাস তৈরি, কনভার্সন বাড়ায় |
| **Reviews & Ratings (Verified Purchase)** | সোশ্যাল প্রুফ |
| **Lesson-level Q&A / Doubt Section** | শিক্ষক ↔ শিক্ষার্থী ইন্টারঅ্যাকশন |
| **Playback Speed, Resume, Bookmark Timestamp** | শেখার আরাম |
| **Low-data Mode + Offline Download (অ্যাপে)** | বাংলাদেশের নেটওয়ার্ক বাস্তবতা |
| **SMS/WhatsApp/Push Notification** | পরীক্ষা ও ডেডলাইন রিমাইন্ডার |
| **Wishlist / Save for Later** | পরে কেনার সুযোগ |
| **Referral System** | বন্ধুকে রেফার করলে ছাড় – ক্যাম্পাসে ভাইরাল গ্রোথ |
| **Campus Ambassadors** | প্রতিটি ইউনিভার্সিটিতে প্রচারদূত + কমিশন |

### ⭐ মধ্যম প্রভাব (V2)
| ফিচার | বিবরণ |
|---|---|
| Live Group Class | Zoom/Meet/নিজস্ব ভিডিও, রেকর্ডিং সহ |
| Flashcards + Spaced Repetition | সংজ্ঞা/সূত্র মুখস্থে |
| Leaderboard & Streaks | প্রতিদিন পড়ার অভ্যাস |
| Certificates of Completion | প্রোফাইলে দেখানো যাবে |
| Study Groups / Batch Rooms | ব্যাচভিত্তিক আলোচনা |
| Question Bank (ফিল্টারসহ) | Year, Exam Type, Chapter অনুযায়ী |
| AI Study Assistant | কোর্সের কনটেন্ট থেকে প্রশ্নোত্তর (শুধু কোর্সভিত্তিক) |
| AI Quiz Generator | নোট থেকে MCQ বানানো |
| AI Summary | লেকচারের সংক্ষিপ্ত সারাংশ |
| Teacher Storefront | শিক্ষকের নিজস্ব পাবলিক পেজ (`/t/teacher-name`) |
| Gift a Course | বন্ধুকে কোর্স উপহার |
| Scholarship / Need-based Free Access | সামাজিক প্রভাব + ব্র্যান্ড |

### 🛡️ ট্রাস্ট ও সেফটি
- Verified Teacher ব্যাজ
- কনটেন্ট কপিরাইট রিপোর্ট বাটন
- Plagiarism/Duplicate কোর্স ডিটেকশন
- স্পষ্ট Refund Policy
- "সম্ভাব্য গুরুত্বপূর্ণ" ডিসক্লেইমার

---

## 12. Business Model

| আয়ের উৎস | মডেল | কখন |
|---|---|---|
| Course Sales Commission | প্রতি বিক্রিতে ১৫–৩০% | ✅ MVP |
| Crash Course Bundle | Midterm+Final প্যাক | ✅ MVP |
| Semester Package | সম্পূর্ণ সেমিস্টার প্যাক | V1.5 |
| Teacher Premium Plan | কম কমিশন + বেশি স্টোরেজ + অ্যানালিটিক্স | V2 |
| Premium Notes / Question Bank | আলাদা বিক্রি | V1.5 |
| Live Group Classes | রেভিনিউ শেয়ার | V2 |
| Sponsored Listing | শিক্ষক/কোর্স প্রমোশন (স্পষ্ট "Sponsored" লেবেলসহ) | পরে |

> **পরামর্শ:** শুরুতে **Course Sales + Crash Course Bundle** যথেষ্ট। একসঙ্গে অনেক মডেল চালাবেন না।

**Cold-start কৌশল (Chicken-and-egg সমাধান):**
1. প্রথমে ১টি ইউনিভার্সিটির ৫–১০ জন সেরা শিক্ষক/টপ স্টুডেন্টকে অনবোর্ড করুন (প্রথম ৩ মাস কম/শূন্য কমিশন)।
2. প্রতিটি সাবজেক্টে অন্তত ১টি প্রিমিয়াম মানের কোর্স দিয়ে লঞ্চ।
3. Facebook গ্রুপ, ব্যাচ রিপ্রেজেন্টেটিভ, Campus Ambassador দিয়ে প্রচার।
4. পরীক্ষার সিজনের ২–৩ সপ্তাহ আগে লঞ্চ – সর্বোচ্চ চাহিদা।

---

## 13. নেভিগেশন ও পেজ স্ট্রাকচার

### 13.1 Student Navigation
| মেনু | কাজ |
|---|---|
| 🏠 Home | Personalized Feed |
| 🔍 Explore | সব ইউনিভার্সিটির কোর্স সার্চ |
| 🏛️ Universities | ইউনিভার্সিটিভিত্তিক ব্রাউজ |
| 📚 Subjects | Subject ও Course Code ব্রাউজ |
| ⚡ Exam Prep | Midterm/Final/Quiz Crash Course |
| 🎒 My Courses | কেনা কোর্স |
| 🗓️ Academic Hub | Google Classroom ও আপডেট |
| 📈 My Learning | Progress, Notes, Bookmarks |
| 👤 Profile | University, Dept, Semester… |

### 13.2 Teacher Panel
Dashboard · My Courses · Create Course · Students · Q&A Inbox · Reviews · Sales Analytics · Coupons · Earnings & Payout · Profile/Storefront

### 13.3 Admin Panel
Dashboard · Teacher Verification · Institution Requests · Course Review Queue · Subject Master · Payments & Refunds · Payout Approval · Reports/Moderation · Users · Settings · Analytics

### 13.4 পাবলিক পেজ
Landing · Explore · University Page · Subject Page · Course Details · Teacher Profile · Pricing/How it works · Blog/Study tips (SEO) · Policies

---

## 14. 🎨 UI/UX ডিজাইন সিস্টেম

> আপনার মূল চাওয়া: **দেখতে সুন্দর হতে হবে।** নিচের গাইড অনুসরণ করলে প্রোডাক্ট "জেনেরিক টেমপ্লেট" না লেগে ব্র্যান্ডেড ও প্রিমিয়াম লাগবে।

### 14.1 ডিজাইন ফিলোসফি
- **Calm, Focused, Confident** – পরীক্ষার চাপের মধ্যে শিক্ষার্থীকে শান্ত রাখবে।
- **Mobile-first** – বেশিরভাগ শিক্ষার্থী ফোনে ব্যবহার করবে।
- **Bangla-first** – বাংলা টেক্সট সুন্দরভাবে রেন্ডার হবে।
- **Less noise, more clarity** – প্রতিটি স্ক্রিনে একটি প্রধান কাজ।
- **Delight in details** – ছোট অ্যানিমেশন, প্রগ্রেস, সেলিব্রেশন।

### 14.2 কালার প্যালেট (প্রস্তাবিত)

| ভূমিকা | Light | Dark | ব্যবহার |
|---|---|---|---|
| **Primary** (Indigo) | `#4F46E5` | `#818CF8` | বাটন, লিংক, হাইলাইট |
| **Primary Soft** | `#EEF2FF` | `#1E1B4B` | ব্যাকগ্রাউন্ড হাইলাইট |
| **Accent** (Emerald) | `#10B981` | `#34D399` | সফলতা, প্রগ্রেস, "Enrolled" |
| **Warm** (Amber) | `#F59E0B` | `#FBBF24` | ⭐ Important, Exam Countdown |
| **Danger** | `#EF4444` | `#F87171` | ডেডলাইন, এরর |
| **Background** | `#F8FAFC` | `#0B1020` | পেজ ব্যাকগ্রাউন্ড |
| **Surface** | `#FFFFFF` | `#131A2E` | কার্ড |
| **Text** | `#0F172A` | `#E2E8F0` | প্রধান লেখা |
| **Muted** | `#64748B` | `#94A3B8` | দ্বিতীয় লেখা |
| **Border** | `#E2E8F0` | `#232C45` | বর্ডার |

> সবকিছু **CSS Variables / Design Tokens** হিসেবে রাখুন। Light + Dark দুটোই সমর্থন করুন।
> কনট্রাস্ট রেশিও কমপক্ষে **WCAG AA (4.5:1)**।

### 14.3 টাইপোগ্রাফি

| ব্যবহার | বাংলা | ইংরেজি |
|---|---|---|
| Heading | **Hind Siliguri** / **Noto Sans Bengali** (SemiBold) | **Inter** / **Plus Jakarta Sans** |
| Body | Hind Siliguri / Noto Sans Bengali | Inter |
| Numbers/Price | Inter (Tabular numerals) | Inter |

- বাংলা লাইন-হাইট **১.৭–১.৮**, ইংরেজি **১.৫**।
- Scale: `12 / 14 / 16 / 18 / 20 / 24 / 30 / 36 / 48`।
- Bangla ও English মিশ্র লেখায় ফন্ট ফলব্যাক ঠিক রাখুন (সাইজ/বেসলাইন মিলিয়ে)।

### 14.4 স্পেসিং, শেপ, ছায়া
- **৮px গ্রিড**: `4, 8, 12, 16, 24, 32, 48, 64`
- **Radius:** কার্ড `16px`, বাটন/ইনপুট `12px`, চিপ `999px`
- **Shadow:** খুব হালকা, স্তরভিত্তিক (কার্ড: `0 1px 2px rgba(15,23,42,.06), 0 8px 24px rgba(15,23,42,.06)`)
- বর্ডার + হালকা শ্যাডো একসঙ্গে, ভারী শ্যাডো এড়িয়ে চলুন।

### 14.5 মূল কম্পোনেন্ট

| কম্পোনেন্ট | ডিজাইন নোট |
|---|---|
| **Course Card** | ১৬:৯ থাম্বনেইল, টাইটেল (২ লাইন), শিক্ষক + ✔ ব্যাজ, University চিপ, Exam Type চিপ, রেটিং, দাম (স্ট্রাইকড মূল্যসহ), Hover-এ লিফট |
| **Smart Search Bar** | বড়, কেন্দ্রে, Autocomplete ড্রপডাউন, গ্রুপড সাজেশন (University / Subject / Code / Teacher) |
| **Filter Chips + Drawer** | মোবাইলে Bottom Sheet, ডেস্কটপে সাইডবার |
| **Compatibility Badge** | ✅ সবুজ "আপনার ইউনিভার্সিটির জন্য" / 🟡 হলুদ "ভিন্ন সিলেবাস হতে পারে" |
| **Exam Countdown Card** | বড় সংখ্যা, গ্র্যাডিয়েন্ট (Indigo → Violet), Progress Ring |
| **Progress Ring/Bar** | অ্যানিমেটেড, মাইলস্টোনে কনফেটি |
| **Lesson List** | কমপ্লিট ✓, লক 🔒, Preview ▶, ⭐ Important |
| **Video Player** | Speed, Quality, Resume, Chapter markers, Keyboard shortcuts, Watermark |
| **Skeleton Loaders** | স্পিনারের বদলে স্কেলিটন |
| **Empty States** | ইলাস্ট্রেশন + পরবর্তী করণীয় বাটন |
| **Toast/Snackbar** | বাংলা বার্তা, স্পষ্ট ও বন্ধুত্বপূর্ণ |

### 14.6 গুরুত্বপূর্ণ স্ক্রিনের লে-আউট আইডিয়া

**Landing Page**
1. Hero: বড় হেডলাইন + Smart Search Box + পপুলার ইউনিভার্সিটি চিপ
2. "কীভাবে কাজ করে" (৩ ধাপ: খুঁজুন → কিনুন → শিখুন)
3. Exam-season বিভাগ: "এই সপ্তাহের Midterm Crash Courses"
4. Featured Teachers (Verified)
5. University লোগো গ্রিড
6. Testimonials + সংখ্যা (শিক্ষার্থী, কোর্স, ঘণ্টা)
7. শিক্ষক হওয়ার CTA ("আপনার জ্ঞান বিক্রি করুন")
8. FAQ + Footer

**Course Details**
- বাঁ পাশে কনটেন্ট, ডান পাশে Sticky **Buy Card** (মোবাইলে নিচে Sticky Bar)
- উপরে Breadcrumb: *University › Dept › Semester › Subject*

**Student Dashboard**
- উপরে "Continue Learning" + "Upcoming Exam" কার্ড
- নিচে Deadlines টাইমলাইন, My Courses গ্রিড

**Teacher Studio**
- ওয়েজার্ড-স্টাইল কোর্স ক্রিয়েশন (৫ ধাপ: Basics → Target → Content → Pricing → Review)
- সাইডে লাইভ প্রিভিউ কার্ড (শিক্ষার্থী কেমন দেখবে)

### 14.7 মাইক্রো-ইন্টারঅ্যাকশন ও মোশন
- ট্রানজিশন: `150–250ms`, `ease-out`
- কার্ড Hover: ১–২px লিফট + শ্যাডো বৃদ্ধি
- পেমেন্ট সফল হলে: ✅ অ্যানিমেশন + "কোর্স আনলক হয়েছে 🎉"
- প্রগ্রেস ১০০% হলে: হালকা কনফেটি
- `prefers-reduced-motion` সম্মান করুন।

### 14.8 কনটেন্ট ও ভাষা (Tone of Voice)
- বন্ধুত্বপূর্ণ কিন্তু পেশাদার, **সহজ বাংলা**।
- ✅ "আপনার Midterm আর ৫ দিন পর। চলুন গুছিয়ে নিই।"
- ❌ "ইউজারের পরীক্ষা নিকটবর্তী।"
- ভুল/এরর বার্তা সহানুভূতিশীল: "পেমেন্ট সম্পন্ন হয়নি। চিন্তা নেই, আপনার টাকা কাটা হয়নি।"

### 14.9 Accessibility ও Performance
- কীবোর্ড নেভিগেশন, Focus ring, ARIA লেবেল, ছবিতে Alt text
- ক্লিকযোগ্য এলাকা কমপক্ষে `44×44px`
- ছবি WebP/AVIF, Lazy loading, ভিডিও অ্যাডাপটিভ বিটরেট
- Lighthouse লক্ষ্য: Performance ≥ 90, Accessibility ≥ 95
- **কম নেটওয়ার্কের জন্য** লাইটওয়েট বান্ডেল ও Low-data Mode

### 14.10 Design Checklist ✅
- [ ] Design Tokens ঠিক (রং/টাইপ/স্পেসিং)
- [ ] Light + Dark মোড
- [ ] ৩৬০px মোবাইল থেকে ১৪৪০px ডেস্কটপ পর্যন্ত রেসপন্সিভ
- [ ] বাংলা ফন্ট সঠিক রেন্ডার
- [ ] Skeleton/Empty/Error স্টেট সবখানে
- [ ] Consistent আইকন সেট (Lucide)
- [ ] সব বাটনে Hover/Focus/Disabled/Loading স্টেট

---

## 15. ডেটাবেস ডিজাইন

### 15.1 সম্পর্ক-চিত্র

```
Institution
 └── Department
      └── Program
           └── Semester
                └── Subject (Course Code সহ, ইউনিভার্সিটিভেদে ভিন্ন)

Subject Master (গ্লোবাল: "Data Structures")  ←──  Institution Subject (Code: CSE-2203)

Teacher ──▶ Course ──▶ CourseTarget (Institution, Dept, Semester, Batch, Section, Subject)
                  ├── Section ▶ Chapter ▶ Lesson (Video/PDF/Quiz)
                  ├── Resource (Notes, Questions)
                  ├── MockTest ▶ Question ▶ Option
                  ├── Review
                  └── Enrollment ◀── Student

Student ── Profile (Institution, Dept, Program, Semester, Batch, Section)
        ├── Order ▶ Payment ▶ Transaction
        ├── Progress (lesson-level)
        ├── ExamPlan
        └── GoogleConnection ▶ ClassroomCache (courses, announcements, assignments)
```

### 15.2 মূল টেবিল (সংক্ষেপে)

| টেবিল | মূল ফিল্ড |
|---|---|
| `institutions` | id, name, type, logo, status(pending/approved), aliases |
| `departments` | id, institution_id, name |
| `programs` | id, department_id, name, level |
| `subject_masters` | id, name, aliases, category |
| `institution_subjects` | id, institution_id, department_id, semester, subject_master_id, course_code |
| `users` | id, role, name, phone, email, password/OTP, status |
| `student_profiles` | user_id, institution_id, department_id, program_id, semester, batch, section |
| `teacher_profiles` | user_id, bio, verification_status, institution_id, payout_info |
| `courses` | id, teacher_id, title, slug, description, exam_type, course_type, language, price, access_type, status |
| `course_targets` | id, course_id, institution_id, department_id, semester, subject_id, batch[], section[], is_general |
| `chapters / lessons` | id, course_id, order, type, video_id, duration, is_preview, is_important |
| `resources` | id, course_id/lesson_id, type(pdf/question/note), file_url |
| `orders / payments` | id, student_id, course_id, amount, gateway, trx_id, status |
| `enrollments` | id, student_id, course_id, access_until, status |
| `progress` | student_id, lesson_id, watched_seconds, completed_at |
| `reviews` | id, course_id, student_id, rating, text, verified |
| `coupons` | code, scope, discount, limits, expiry |
| `payouts / ledger` | teacher_id, amount, type(sale/refund/payout), balance_after |
| `exam_plans` | student_id, subject_id, exam_type, exam_date, plan_json |
| `course_requests` | student_id, subject, institution, votes |

> **ইনডেক্স:** `course_targets(institution_id, department_id, semester, subject_id)`, কোর্স কোডে, ফুল-টেক্সট সার্চে।
> **Soft delete + Audit log** রাখুন (পেমেন্ট ও কনটেন্ট সংক্রান্ত বিষয়ে)।

---

## 16. প্রস্তাবিত Tech Stack

> আপনার দক্ষতা (Python, FastAPI/Flask, Android) অনুযায়ী ৩টি অপশন:

### Option A — Web-first (দ্রুত MVP, সুন্দর UI)
| স্তর | টেকনোলজি |
|---|---|
| Frontend | **Next.js (React) + TypeScript + Tailwind + shadcn/ui** |
| Backend | **FastAPI (Python)** + SQLAlchemy |
| DB | **PostgreSQL** + Redis |
| Search | **Meilisearch** (বাংলা + typo tolerance সহজ) বা Postgres FTS দিয়ে শুরু |
| Video | **Bunny Stream / Cloudflare Stream / Mux** (HLS + Signed URL) |
| Storage | Cloudflare R2 / S3 |
| Payment | SSLCommerz / aamarPay / bKash Tokenized |
| Queue | Celery / RQ / ARQ |
| Auth | Phone OTP + Email, JWT/Session |
| Notification | SMS Gateway + FCM Push |
| Hosting | VPS (Docker) / Railway / Render + CDN |

### Option B — Mobile-first
- **Flutter** বা **Jetpack Compose (Android)** + একই FastAPI ব্যাকএন্ড
- অফলাইন ডাউনলোড ও Push-এর জন্য ভালো

### Option C — Hybrid (প্রস্তাবিত)
**Web (Next.js) দিয়ে লঞ্চ → পরে Android অ্যাপ** (একই API)। Web SEO থেকে অর্গানিক ট্রাফিক পাবেন; অ্যাপ পরে বাড়াবেন।

**অবশ্যই রাখুন:** Docker, CI/CD, Sentry (error tracking), Daily DB backup, Rate limiting, Webhook idempotency।

---

## 17. Roadmap (MVP → V2)

### 🚀 Version 1.0 — Core Marketplace
- [ ] Auth (Phone OTP/Email), Student & Teacher Profile
- [ ] Institution/Department/Subject Master + Admin Approval
- [ ] Teacher Verification
- [ ] Course Creator (Section/Chapter/Lesson), Video Upload, PDF Notes, Previous Questions
- [ ] Course Targeting (University/Dept/Semester/Batch/Section/Code)
- [ ] Explore + Smart Search + Filters + University/Subject Browse
- [ ] Course Details + Preview Lessons
- [ ] Purchase + bKash/Nagad + Auto Enrollment + Invoice
- [ ] Student Dashboard + My Courses + Progress
- [ ] Midterm/Final Crash Course + Bundle
- [ ] Reviews & Ratings
- [ ] Admin Panel + Teacher Revenue/Payout
- [ ] Landing Page + SEO + Policy Pages

### ✨ Version 1.5 — Engagement
- [ ] Google Classroom (Read-only): Assignments, Notices, Deadlines, Calendar
- [ ] Personalized Feed + Course Matching
- [ ] Q&A, Wishlist, Coupons, Referral
- [ ] Course Request Board
- [ ] Push/SMS Reminders
- [ ] Android App (Compose/Flutter) + Offline Download
- [ ] Semester Package, Premium Notes

### 🧠 Version 2.0 — Intelligence
- [ ] Exam Mode (Study Plan, Countdown, Checklist)
- [ ] Mock Test System + AI Quiz Generator
- [ ] AI Study Assistant (কোর্স-ভিত্তিক)
- [ ] Live Group Classes
- [ ] Flashcards, Leaderboard, Certificates, Study Groups
- [ ] Teacher Premium Plan, Teacher Storefront
- [ ] Institution Moderator Role

---

## 18. ঝুঁকি, আইন ও নিয়মনীতি

| ঝুঁকি | প্রতিরোধ |
|---|---|
| **ভিডিও পাইরেসি / শেয়ারিং** | Signed URL, DRM (সম্ভব হলে), Watermark, Device Limit, DMCA/Report ফ্লো |
| **কপিরাইট (ইউনিভার্সিটির প্রশ্ন/বই)** | শিক্ষকের Declaration, Takedown Policy, রিপোর্ট বাটন |
| **ইউনিভার্সিটির নাম/লোগো ব্যবহার** | "Not affiliated" ডিসক্লেইমার; অনুমতি ছাড়া অফিসিয়াল দাবি নয় |
| **ভুয়া শিক্ষক** | ভেরিফিকেশন + Verified ব্যাজ |
| **নিম্নমানের কোর্স** | Review Queue, Rating, Refund |
| **"পরীক্ষায় কমন" দাবি** | ডিসক্লেইমার ও কনটেন্ট গাইডলাইন |
| **পেমেন্ট ফ্রড / ডাবল চার্জ** | Webhook Idempotency, Reconciliation |
| **ডেটা প্রাইভেসি** | Minimum data, Google scope সীমিত, Privacy Policy, Data deletion |
| **শিক্ষকের ইউনিভার্সিটির নীতি** | কিছু ইউনিভার্সিটিতে শিক্ষকদের প্রাইভেট কোর্স নীতিমালা থাকতে পারে – Terms-এ শিক্ষকের দায় স্পষ্ট রাখুন |
| **ট্যাক্স/ট্রেড লাইসেন্স** | পেমেন্ট গেটওয়ে ও কমপ্লায়েন্সের জন্য আগে থেকে প্রস্তুতি |

> ⚖️ আইনি বিষয়ে (Terms, Privacy, Refund, Copyright) চূড়ান্ত করার আগে একজন আইনজীবীর পরামর্শ নিন।

---

## 19. Success Metrics

| ক্যাটাগরি | মেট্রিক |
|---|---|
| Acquisition | সাইনআপ, ইউনিভার্সিটিভিত্তিক শিক্ষার্থী, রেফারাল % |
| Activation | প্রথম কোর্স দেখা/কেনা % |
| Revenue | GMV, Conversion Rate, AOV, Bundle Attach Rate |
| Retention | Repeat Purchase, পরের সেমিস্টারে ফেরা |
| Learning | Completion Rate, Avg. Watch Time, Mock Test স্কোর |
| Supply | অ্যাক্টিভ শিক্ষক, কোর্স/সাবজেক্ট কভারেজ |
| Quality | Avg. Rating, Refund Rate, Support Ticket |
| Search | Zero-result রেট, Request Board ভলিউম |

**North Star Metric:** *প্রতি সপ্তাহে "পরীক্ষার আগে কোর্স থেকে উপকৃত" শিক্ষার্থীর সংখ্যা*।

---

## 20. Claude Code দিয়ে বানানোর নির্দেশনা

নিচের ধাপে ধাপে প্রম্পট দিয়ে বানান (প্রতি ধাপ শেষে টেস্ট করুন):

1. **Foundation:** "এই স্পেক অনুযায়ী প্রজেক্ট স্ট্রাকচার, Docker, Postgres, Auth, Design Tokens (সেকশন ১৪) সেট আপ করো।"
2. **Master Data:** "Institution → Department → Program → Subject মডেল ও Admin অ্যাপ্রুভাল ফ্লো বানাও।"
3. **Course Creator:** "সেকশন ৪ অনুযায়ী Teacher Course Builder ও CourseTarget সিস্টেম বানাও।"
4. **Discovery:** "সেকশন ৫–৬ অনুযায়ী Search, Filters, Ranking বানাও।"
5. **Checkout:** "সেকশন ৮ অনুযায়ী Payment, Webhook, Enrollment, Invoice বানাও।"
6. **Learning:** "ভিডিও প্লেয়ার, Progress, Dashboard বানাও।"
7. **Polish:** "সেকশন ১৪ চেকলিস্ট ধরে UI পলিশ, মোশন, Dark Mode, Accessibility করো।"

### ডেভেলপমেন্ট নিয়ম
- প্রতিটি ফিচারে **Skeleton / Empty / Error / Loading** স্টেট বাধ্যতামূলক।
- সব টেক্সট **i18n ফাইলে** (bn/en) – হার্ডকোড নয়।
- API **OpenAPI** ডকসহ, **Pagination** সব লিস্টে।
- সব পেমেন্ট ইভেন্ট **Idempotent** ও লগড।
- **Seed Data**: ৩টি ইউনিভার্সিটি, ৫টি সাবজেক্ট, ১০টি ডেমো কোর্স – যাতে ডিজাইন বাস্তব ডেটায় দেখা যায়।
- ডিজাইন রেফারেন্স হিসেবে Figma/Stitch-এ আগে স্ক্রিন বানিয়ে তারপর কোড করলে ফল আরও সুন্দর হবে।

---

## ✅ চূড়ান্ত সারসংক্ষেপ

| বিষয় | সিদ্ধান্ত |
|---|---|
| প্রোডাক্ট | Multi-University Exam-Prep Course Marketplace |
| মূল ধারণা | Teacher targets → Student discovers |
| প্রথম লক্ষ্য | ১টি ইউনিভার্সিটি, ৫–১০ শিক্ষক, Midterm/Final Crash Course |
| আয় | Commission + Bundle |
| বড় সুবিধা | Exam Mode + Unified Academic Dashboard |
| ডিজাইন | Mobile-first, Bangla-first, Calm Indigo + Emerald, Light/Dark |
| শুরু | Web (Next.js + FastAPI) → পরে Android |

> **শেষ কথা:** পরীক্ষার সময় শিক্ষার্থীর সবচেয়ে বড় প্রয়োজন *সময় বাঁচানো ও আত্মবিশ্বাস*। প্রতিটি ফিচার ও প্রতিটি পিক্সেল সেই লক্ষ্যেই ডিজাইন করুন।
