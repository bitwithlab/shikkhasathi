import { and, eq, inArray, sql } from 'drizzle-orm';
import { contentDb, coreDb } from '../client';
import { enrollments, reviews, users } from '../core/schema';
import { courses } from '../content/schema';

/** Demo reviewers — real rows so course ratings and the star breakdown are honest (no fake aggregates). */
const REVIEWERS = ['আরিফ হোসেন', 'মিথিলা রহমান', 'জাহিদ হাসান', 'সুমাইয়া আক্তার', 'রাফি আহমেদ', 'তাসফিয়া ইসলাম', 'নাফিস চৌধুরী', 'ফারিহা খান', 'সাকিব আল হাসান', 'মারিয়া জামান', 'ইমরান কবির', 'নুসাইবা হক', 'রিদওয়ান করিম', 'অনন্যা দাস', 'তানজিম ফেরদৌস', 'লামিয়া সুলতানা'];

const GOOD = [
  'Midterm-এর আগের রাতে শুধু ⭐ লেসনগুলো দেখেছি — অনেক কাজে লেগেছে।',
  'উদাহরণগুলো একদম আমাদের ক্লাসের মতো। আগের বছরের প্রশ্নের সমাধান সবচেয়ে ভালো লেগেছে।',
  'ছোট ছোট ভিডিও, মোবাইলে দেখতে সুবিধা। ডেটা কম খরচ হয়।',
  'স্যার খুব গুছিয়ে বোঝান। Q&A-তে প্রশ্ন করলে একদিনের মধ্যে উত্তর পেয়েছি।',
  'মক টেস্টটা পরীক্ষার আগে আত্মবিশ্বাস বাড়িয়েছে।',
  'সিলেবাসের সাথে মিল আছে, অপ্রয়োজনীয় কিছু নেই।',
];
const OK = ['ভালো, তবে কয়েকটা টপিক আরেকটু বিস্তারিত হলে ভালো হতো।', 'কোর্স কাজের, কিন্তু অডিও কিছু জায়গায় একটু কম।', 'ঠিকঠাক — নোটগুলো সবচেয়ে কাজে লেগেছে।'];

/** Deterministic ratings whose mean ≈ target (e.g. 4.8 → mostly 5s with a few 4s). */
function ratingsFor(target: number, n: number) {
  const out: number[] = [];
  let sum = 0;
  for (let i = 0; i < n; i++) {
    const want = target * (i + 1) - sum;
    const r = Math.max(3, Math.min(5, Math.round(want)));
    out.push(r);
    sum += r;
  }
  return out;
}

export async function seedReviews(courseRatings: Record<string, [number, number]>, courseIds: Record<string, string>) {
  // reviewer accounts (no contact details; can't log in)
  const existing = await coreDb.select({ id: users.id, name: users.name }).from(users).where(inArray(users.name, REVIEWERS));
  const ids: string[] = [];
  for (const name of REVIEWERS) {
    const found = existing.find((u) => u.name === name);
    if (found) ids.push(found.id);
    else ids.push((await coreDb.insert(users).values({ name, roles: ['student'], status: 'active' }).returning({ id: users.id }))[0]!.id);
  }
  for (const [slug, [avg, count]] of Object.entries(courseRatings)) {
    const courseId = courseIds[slug];
    if (!courseId) continue;
    const [c] = await contentDb.select({ sellerId: courses.sellerId, status: courses.status }).from(courses).where(eq(courses.id, courseId));
    if (!c || c.status !== 'published') continue;
    // realistic sample size for a young platform: 4–14 reviews, proportional to the old demo count
    const n = Math.max(4, Math.min(REVIEWERS.length - 2, Math.round(count / 25)));
    const offset = slug.length % REVIEWERS.length;
    const who = Array.from({ length: n }, (_, i) => ids[(offset + i) % ids.length]!);
    const rs = ratingsFor(avg, n);
    for (const [i, studentId] of who.entries()) {
      await coreDb.insert(enrollments).values({ studentId, courseId, status: 'active', progressPercent: 40 + ((i * 17) % 60), createdAt: new Date(Date.now() - (30 + i * 5) * 86_400_000) }).onConflictDoNothing();
      const r = rs[i]!;
      await coreDb
        .insert(reviews)
        .values({ courseId, sellerId: c.sellerId, studentId, rating: r, body: r >= 5 ? GOOD[(i + offset) % GOOD.length]! : r === 4 ? (i % 2 ? GOOD[(i + 2) % GOOD.length]! : OK[i % OK.length]!) : OK[i % OK.length]!, createdAt: new Date(Date.now() - (2 + i * 3) * 86_400_000) })
        .onConflictDoNothing();
    }
    const [agg] = await coreDb.select({ avg: sql<string>`coalesce(round(avg(${reviews.rating})::numeric, 2), 0)::text`, n: sql<number>`count(*)::int` }).from(reviews).where(and(eq(reviews.courseId, courseId), eq(reviews.status, 'visible')));
    await contentDb.update(courses).set({ ratingAvg: agg?.avg ?? '0', ratingCount: agg?.n ?? 0 }).where(eq(courses.id, courseId));
  }
}
