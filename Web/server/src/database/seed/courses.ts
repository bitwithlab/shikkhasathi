import { and, eq } from 'drizzle-orm';
import { contentDb, coreDb } from '../client';
import { sellerProfiles } from '../core/schema';
import { courseChapters, courseSections, courseTargets, courses, documents, lessons, videoAssets } from '../content/schema';
import { encrypt } from '../../common/crypto';
import type { CatalogIds } from './catalog';

interface SellerSeed {
  key: string;
  slug: string;
  displayName: string;
  headline: string;
  bio: string;
  inst: string;
  status: 'verified' | 'pending';
  method: 'university_email' | 'linkedin' | 'id_card' | 'reference';
}

export const SELLERS: SellerSeed[] = [
  { key: 'rakib', slug: 'rakibul-hasan', displayName: 'মো. রাকিবুল হাসান', headline: 'Lecturer, CSE · Ex-MU Topper', bio: 'Metropolitan University-র CSE বিভাগে ৪ বছর ধরে Data Structures আর Algorithms পড়াচ্ছি। ছোট ছোট উদাহরণ দিয়ে কঠিন টপিক সহজ করাই আমার লক্ষ্য।', inst: 'metropolitan-university', status: 'verified', method: 'university_email' },
  { key: 'tasnim', slug: 'tasnim-rahman', displayName: 'ড. তাসনিম রহমান', headline: 'Faculty, EEE · North South University', bio: 'Electrical Circuits ও Digital Logic নিয়ে কাজ করি। পরীক্ষার আগে সূত্র মুখস্থ নয়, কেন কাজ করে সেটা বোঝানোর চেষ্টা করি।', inst: 'north-south-university', status: 'verified', method: 'university_email' },
  { key: 'shakil', slug: 'shakil-mahmud', displayName: 'শাকিল মাহমুদ', headline: 'Competitive Programmer · BRAC Alum', bio: 'ICPC Dhaka Regional-এ দুবার অংশ নিয়েছি। BRACU CSE-র Programming আর DS কোর্সের সমস্যা সমাধান আমার প্রিয় কাজ।', inst: 'brac-university', status: 'verified', method: 'linkedin' },
  { key: 'farhana', slug: 'farhana-haque', displayName: 'ফারহানা হক', headline: 'Finance Specialist · NSU SBE', bio: 'Microeconomics ও Accounting — BBA-র শুরুর সেমিস্টারের কোর্সগুলো গ্রাফ আর বাস্তব উদাহরণ দিয়ে পড়াই।', inst: 'north-south-university', status: 'verified', method: 'linkedin' },
  { key: 'saikat', slug: 'saikat-chowdhury', displayName: 'সৈকত চৌধুরী', headline: 'Lecturer, CSE · Metropolitan University', bio: 'Algorithm Analysis আর DBMS পড়াই। প্রতিটি কোর্সে আগের ৫ বছরের প্রশ্নের সমাধান থাকে।', inst: 'metropolitan-university', status: 'verified', method: 'university_email' },
  { key: 'maruf', slug: 'maruf-rashid', displayName: 'মারুফ রশীদ', headline: 'Engineer · AIUB EEE', bio: 'Digital Logic Design-এর K-Map আর sequential circuit নিয়ে শর্ট লেকচার বানাচ্ছি।', inst: 'aiub', status: 'pending', method: 'id_card' },
];

interface LessonSeed {
  t: string;
  min: number;
  type?: 'video' | 'pdf' | 'text';
  preview?: boolean;
  imp?: boolean;
}
interface CourseSeed {
  slug: string;
  seller: string;
  title: string;
  short: string;
  examType: 'midterm' | 'final' | 'quiz' | 'lab' | 'general';
  courseType: 'crash' | 'full' | 'solve' | 'suggestion' | 'lab';
  price: number; // taka
  compare?: number;
  color: string;
  learnings: string[];
  notFor: string[];
  targets: { inst?: string; dept?: string; sem?: number; subj: string; code?: string; batches?: string[] }[];
  sections: { title: string; chapters: { title: string; lessons: LessonSeed[] }[] }[];
  pq?: number[];
  rating: [number, number];
  enrolled: number;
  enrolled30d: number;
}

const L = (t: string, min: number, o: Partial<LessonSeed> = {}): LessonSeed => ({ t, min, ...o });

export const COURSES: CourseSeed[] = [
  {
    slug: 'data-structures-midterm-crash-cse-2203',
    seller: 'rakib',
    title: 'Data Structures — Midterm Crash Course (CSE-2203)',
    short: 'Linked List থেকে AVL — Midterm সিলেবাসের সম্ভাব্য গুরুত্বপূর্ণ টপিক, ছোট লেকচার আর আগের বছরের প্রশ্নের সমাধানসহ।',
    examType: 'midterm',
    courseType: 'crash',
    price: 299,
    compare: 499,
    color: '#4F46E5',
    learnings: ['Linked List-এর insert/delete ট্রেস করা', 'Stack ও Queue দিয়ে expression evaluation', 'BST insert, delete (৩টি কেস) ও traversal', 'AVL rotation (LL, RR, LR, RL)', 'আগের ৩ বছরের Midterm প্রশ্নের প্যাটার্ন'],
    notFor: ['যারা প্রথমবার প্রোগ্রামিং শিখছেন — আগে C/C++ এর বেসিক জানা দরকার', 'Final-এর Graph ও Hashing অংশ এই কোর্সে নেই'],
    targets: [{ inst: 'metropolitan-university', dept: 'CSE', sem: 4, subj: 'data-structures', code: 'CSE-2203', batches: ['55', '56'] }],
    sections: [
      { title: 'Linear structures', chapters: [{ title: 'Linked List', lessons: [L('কোর্স পরিচিতি ও Midterm সিলেবাস', 6, { preview: true }), L('Singly linked list: insert ও delete', 14, { imp: true }), L('Doubly ও circular list', 11)] }, { title: 'Stack ও Queue', lessons: [L('Stack দিয়ে infix → postfix', 13, { imp: true }), L('Queue ও circular queue', 9), L('Stack/Queue হ্যান্ডনোট', 0, { type: 'pdf' })] }] },
      { title: 'Trees', chapters: [{ title: 'Binary Search Tree', lessons: [L('BST insert ও search', 12, { preview: true }), L('BST deletion — ৩টি কেস', 16, { imp: true }), L('Traversal: inorder, preorder, postorder', 10)] }, { title: 'AVL Tree', lessons: [L('Balance factor ও কেন rotation', 9), L('LL, RR, LR, RL rotation trace', 18, { imp: true })] }] },
      { title: 'Exam prep', chapters: [{ title: 'আগের প্রশ্নের সমাধান', lessons: [L('Midterm 2024 সমাধান', 22, { imp: true }), L('Midterm 2023 সমাধান', 19), L('শেষ মুহূর্তের রিভিশন শিট', 0, { type: 'pdf' })] }] },
    ],
    pq: [2022, 2023, 2024],
    rating: [4.9, 340],
    enrolled: 1240,
    enrolled30d: 186,
  },
  {
    slug: 'algorithm-analysis-design-cse-2201',
    seller: 'saikat',
    title: 'Algorithm Analysis & Design — Midterm Prep (CSE-2201)',
    short: 'Big-O, Divide & Conquer, Greedy আর DP — প্রতিটি টপিকে ২–৩টি পরীক্ষা-ধাঁচের অঙ্ক সমাধান।',
    examType: 'midterm',
    courseType: 'crash',
    price: 249,
    compare: 499,
    color: '#0EA5E9',
    learnings: ['Recurrence relation সমাধান (Master theorem)', 'Merge sort ও Quick sort বিশ্লেষণ', 'Greedy: activity selection, Huffman', 'DP: knapsack, LCS'],
    notFor: ['Graph algorithm-এর বিস্তারিত (Final কোর্সে আছে)'],
    targets: [{ inst: 'metropolitan-university', dept: 'CSE', sem: 5, subj: 'algorithms', code: 'CSE-2201' }],
    sections: [
      { title: 'Foundations', chapters: [{ title: 'Asymptotic analysis', lessons: [L('Big-O, Θ, Ω — উদাহরণসহ', 12, { preview: true }), L('Master theorem দিয়ে recurrence', 15, { imp: true })] }] },
      { title: 'Techniques', chapters: [{ title: 'Divide & Conquer', lessons: [L('Merge sort trace', 11), L('Quick sort ও partition', 13, { imp: true })] }, { title: 'Greedy', lessons: [L('Activity selection', 10), L('Huffman coding', 14, { imp: true })] }, { title: 'Dynamic Programming', lessons: [L('0/1 Knapsack টেবিল', 17, { imp: true }), L('LCS', 13)] }] },
    ],
    pq: [2023, 2024],
    rating: [4.95, 412],
    enrolled: 980,
    enrolled30d: 142,
  },
  {
    slug: 'oop-java-quiz-midterm-cse-2205',
    seller: 'rakib',
    title: 'OOP II (Java) — Quiz ও Midterm প্রস্তুতি (CSE-2205)',
    short: 'Inheritance, Interface, Exception আর Multithreading — Quiz 2-এর আগে দ্রুত রিভিশন।',
    examType: 'quiz',
    courseType: 'crash',
    price: 199,
    compare: 349,
    color: '#10B981',
    learnings: ['Abstract class বনাম interface', 'Checked ও unchecked exception', 'Thread, Runnable ও synchronized', 'Collections framework-এর বেসিক'],
    notFor: ['Java GUI (Swing/JavaFX) এই কোর্সে নেই'],
    targets: [{ inst: 'metropolitan-university', dept: 'CSE', sem: 4, subj: 'oop', code: 'CSE-2205' }],
    sections: [{ title: 'OOP II', chapters: [{ title: 'Inheritance ও Interface', lessons: [L('কোর্স পরিচিতি', 4, { preview: true }), L('Abstract class vs interface', 12, { imp: true })] }, { title: 'Exception', lessons: [L('try-catch-finally ও custom exception', 11)] }, { title: 'Multithreading', lessons: [L('Thread lifecycle', 10, { imp: true }), L('synchronized ও deadlock', 14, { imp: true })] }] }],
    rating: [4.8, 156],
    enrolled: 640,
    enrolled30d: 98,
  },
  {
    slug: 'linear-algebra-final-mat-2101',
    seller: 'saikat',
    title: 'Linear Algebra & Differential Equations — Final Crash (MAT-2101)',
    short: 'Matrix, Eigenvalue আর ODE — প্রতিটি অধ্যায়ের সবচেয়ে বেশি আসা অঙ্কের ধরন।',
    examType: 'final',
    courseType: 'solve',
    price: 349,
    compare: 599,
    color: '#F59E0B',
    learnings: ['Gauss elimination ও rank', 'Eigenvalue/eigenvector ও diagonalization', 'First-order ODE', 'Second-order linear ODE'],
    notFor: ['যারা শুধু Midterm-এর জন্য খুঁজছেন'],
    targets: [{ inst: 'metropolitan-university', dept: 'CSE', sem: 3, subj: 'linear-algebra', code: 'MAT-2101' }],
    sections: [{ title: 'Linear algebra', chapters: [{ title: 'Matrices', lessons: [L('Gauss elimination', 15, { preview: true }), L('Rank ও consistency', 12)] }, { title: 'Eigen', lessons: [L('Eigenvalue বের করা', 16, { imp: true }), L('Diagonalization', 14, { imp: true })] }] }, { title: 'Differential equations', chapters: [{ title: 'ODE', lessons: [L('Separable ও exact', 13), L('Second-order linear', 18, { imp: true })] }] }],
    pq: [2022, 2023, 2024],
    rating: [4.7, 98],
    enrolled: 410,
    enrolled30d: 52,
  },
  {
    slug: 'dbms-midterm-cse-3101',
    seller: 'saikat',
    title: 'Database Management Systems — Midterm (CSE-3101)',
    short: 'ER diagram, Relational algebra, SQL আর Normalization — পরীক্ষার ধাঁচে।',
    examType: 'midterm',
    courseType: 'crash',
    price: 299,
    compare: 450,
    color: '#8B5CF6',
    learnings: ['ER থেকে relational schema', 'Relational algebra query', 'SQL join, group by, subquery', '1NF থেকে BCNF'],
    notFor: ['Transaction ও concurrency (Final অংশ)'],
    targets: [{ inst: 'metropolitan-university', dept: 'CSE', sem: 5, subj: 'database-management-systems', code: 'CSE-3101' }],
    sections: [{ title: 'DBMS', chapters: [{ title: 'Modeling', lessons: [L('ER diagram', 12, { preview: true }), L('ER → tables', 10)] }, { title: 'Querying', lessons: [L('Relational algebra', 14, { imp: true }), L('SQL joins', 16, { imp: true })] }, { title: 'Design', lessons: [L('Normalization', 18, { imp: true })] }] }],
    rating: [4.85, 120],
    enrolled: 380,
    enrolled30d: 61,
  },
  {
    slug: 'data-structures-nsu-cse225',
    seller: 'shakil',
    title: 'Data Structures (CSE225) — NSU Midterm Prep',
    short: 'NSU-র CSE225-এর লাইব্রেরি-স্টাইল প্রশ্ন আর code tracing — Linked list থেকে BST।',
    examType: 'midterm',
    courseType: 'crash',
    price: 349,
    compare: 549,
    color: '#EC4899',
    learnings: ['Code tracing প্রশ্ন সমাধান', 'Sorted list ও unsorted list ADT', 'Recursion ও BST'],
    notFor: ['MU বা BRACU সিলেবাস হুবহু মিলবে না'],
    targets: [{ inst: 'north-south-university', dept: 'CSE', sem: 4, subj: 'data-structures', code: 'CSE225' }],
    sections: [{ title: 'CSE225', chapters: [{ title: 'Lists', lessons: [L('Unsorted vs sorted list', 11, { preview: true }), L('Linked implementation', 14)] }, { title: 'Recursion & trees', lessons: [L('Recursion tracing', 12, { imp: true }), L('BST operations', 16, { imp: true })] }] }],
    rating: [4.88, 280],
    enrolled: 720,
    enrolled30d: 120,
  },
  {
    slug: 'structured-programming-c-bracu-cse110',
    seller: 'shakil',
    title: 'Programming Language I (CSE110) — Quiz ও Midterm',
    short: 'BRACU CSE110-এর loop, function আর array — ৫০টি সমাধান করা সমস্যা।',
    examType: 'midterm',
    courseType: 'solve',
    price: 199,
    compare: 399,
    color: '#14B8A6',
    learnings: ['Loop দিয়ে pattern ও series', 'Function ও recursion', '1D ও 2D array'],
    notFor: ['Object-oriented অংশ (CSE111-এ আছে)'],
    targets: [{ inst: 'brac-university', dept: 'CSE', sem: 1, subj: 'structured-programming', code: 'CSE110' }],
    sections: [{ title: 'CSE110', chapters: [{ title: 'Basics', lessons: [L('Loop patterns', 13, { preview: true }), L('Functions', 12)] }, { title: 'Arrays', lessons: [L('1D array problems', 15, { imp: true }), L('2D array ও matrix', 14)] }] }],
    rating: [4.88, 280],
    enrolled: 860,
    enrolled30d: 150,
  },
  {
    slug: 'data-structures-bracu-cse220',
    seller: 'shakil',
    title: 'Data Structures (CSE220) — BRACU Final Crash',
    short: 'Hashing, Heap আর Graph — BRACU CSE220 Final-এর আগে দ্রুত প্রস্তুতি।',
    examType: 'final',
    courseType: 'crash',
    price: 299,
    compare: 499,
    color: '#F97316',
    learnings: ['Hash table ও collision handling', 'Heap ও priority queue', 'BFS, DFS'],
    notFor: ['Midterm অংশ (array, linked list) এখানে সংক্ষেপে'],
    targets: [{ inst: 'brac-university', dept: 'CSE', sem: 4, subj: 'data-structures', code: 'CSE220' }],
    sections: [{ title: 'CSE220 Final', chapters: [{ title: 'Hashing', lessons: [L('Hash functions', 10, { preview: true }), L('Linear probing vs chaining', 13, { imp: true })] }, { title: 'Heap & graph', lessons: [L('Heap sort', 12), L('BFS/DFS', 15, { imp: true })] }] }],
    rating: [4.75, 90],
    enrolled: 310,
    enrolled30d: 70,
  },
  {
    slug: 'electrical-circuits-nsu-eee141',
    seller: 'tasnim',
    title: 'Electrical Circuits I (EEE141) — Midterm Crash',
    short: 'KVL, KCL, Nodal ও Mesh analysis আর Thevenin — ধাপে ধাপে অঙ্ক।',
    examType: 'midterm',
    courseType: 'crash',
    price: 349,
    compare: 599,
    color: '#0EA5E9',
    learnings: ['Nodal ও mesh analysis', 'Thevenin ও Norton', 'Superposition'],
    notFor: ['AC circuit (Final-এ)'],
    targets: [{ inst: 'north-south-university', dept: 'EEE', sem: 2, subj: 'electrical-circuits', code: 'EEE141' }, { inst: 'metropolitan-university', dept: 'EEE', sem: 2, subj: 'electrical-circuits', code: 'EEE-1201' }],
    sections: [{ title: 'DC circuits', chapters: [{ title: 'Laws', lessons: [L('KVL ও KCL', 11, { preview: true }), L('Nodal analysis', 15, { imp: true }), L('Mesh analysis', 14, { imp: true })] }, { title: 'Theorems', lessons: [L('Thevenin ও Norton', 17, { imp: true }), L('Superposition', 12)] }] }],
    rating: [4.95, 210],
    enrolled: 540,
    enrolled30d: 88,
  },
  {
    slug: 'digital-logic-design-k-map',
    seller: 'tasnim',
    title: 'Digital Logic Design — K-Map থেকে Flip-flop (সব ইউনিভার্সিটির জন্য)',
    short: 'Boolean algebra, K-Map, Multiplexer আর Flip-flop — যেকোনো বিশ্ববিদ্যালয়ের DLD কোর্সে কাজে লাগবে।',
    examType: 'midterm',
    courseType: 'full',
    price: 249,
    compare: 449,
    color: '#64748B',
    learnings: ['Boolean simplification', '৪ ও ৫ ভেরিয়েবলের K-Map', 'MUX, decoder দিয়ে function', 'SR, JK, D, T flip-flop'],
    notFor: ['নির্দিষ্ট কোনো ইউনিভার্সিটির আগের প্রশ্নের সমাধান এখানে নেই'],
    targets: [{ subj: 'digital-logic-design' }],
    sections: [{ title: 'Combinational', chapters: [{ title: 'Simplification', lessons: [L('Boolean algebra', 12, { preview: true }), L('K-Map', 16, { imp: true })] }, { title: 'Circuits', lessons: [L('Multiplexer', 11), L('Decoder', 10)] }] }, { title: 'Sequential', chapters: [{ title: 'Flip-flops', lessons: [L('SR ও JK', 14, { imp: true }), L('D ও T', 10)] }] }],
    rating: [4.92, 310],
    enrolled: 920,
    enrolled30d: 130,
  },
  {
    slug: 'microeconomics-eco101-final',
    seller: 'farhana',
    title: 'Microeconomics (ECO101) — Final Crash',
    short: 'Demand-supply, elasticity, cost curve আর market structure — গ্রাফসহ।',
    examType: 'final',
    courseType: 'crash',
    price: 299,
    compare: 550,
    color: '#10B981',
    learnings: ['Elasticity অঙ্ক', 'Cost curves', 'Perfect competition ও monopoly'],
    notFor: ['Macroeconomics অংশ'],
    targets: [{ inst: 'north-south-university', dept: 'ECO', sem: 1, subj: 'microeconomics', code: 'ECO101' }, { inst: 'brac-university', dept: 'ECO', sem: 1, subj: 'microeconomics', code: 'ECO101' }],
    sections: [{ title: 'ECO101', chapters: [{ title: 'Demand & supply', lessons: [L('Equilibrium', 11, { preview: true }), L('Elasticity', 14, { imp: true })] }, { title: 'Firms', lessons: [L('Cost curves', 13), L('Market structures', 16, { imp: true })] }] }],
    rating: [4.9, 195],
    enrolled: 470,
    enrolled30d: 64,
  },
  {
    slug: 'computer-networks-free-intro',
    seller: 'rakib',
    title: 'Computer Networks — ফ্রি পরিচিতি (OSI থেকে TCP)',
    short: 'Networks শুরু করার আগে ৪৫ মিনিটে মূল ধারণা — একদম ফ্রি।',
    examType: 'general',
    courseType: 'full',
    price: 0,
    color: '#4F46E5',
    learnings: ['OSI ও TCP/IP layer', 'IP addressing ও subnet', 'TCP বনাম UDP'],
    notFor: ['পরীক্ষার পূর্ণ প্রস্তুতি — এটা শুধু পরিচিতি'],
    targets: [{ subj: 'computer-networks' }],
    sections: [{ title: 'Intro', chapters: [{ title: 'Basics', lessons: [L('OSI model', 12, { preview: true }), L('IP ও subnet', 18, { preview: true }), L('TCP vs UDP', 14, { preview: true })] }] }],
    rating: [4.6, 72],
    enrolled: 1500,
    enrolled30d: 210,
  },
  {
    slug: 'dld-aiub-eee-2103-midterm',
    seller: 'maruf',
    title: 'Digital Logic Design (EEE-2103) — AIUB Midterm',
    short: 'AIUB-এর DLD Midterm-এর K-Map আর combinational circuit — খসড়া কোর্স।',
    examType: 'midterm',
    courseType: 'crash',
    price: 349,
    compare: 650,
    color: '#F59E0B',
    learnings: ['K-Map', 'Combinational circuits'],
    notFor: [],
    targets: [{ inst: 'aiub', dept: 'EEE', sem: 3, subj: 'digital-logic-design', code: 'EEE-2103' }],
    sections: [{ title: 'DLD', chapters: [{ title: 'K-Map', lessons: [L('K-Map basics', 12, { preview: true })] }] }],
    rating: [0, 0],
    enrolled: 0,
    enrolled30d: 0,
  },
];

/** Upserts sellers + courses (idempotent by slug). Returns course slug → id and seller key → user id. */
export async function seedCourses(users: Record<string, string>, cat: CatalogIds) {
  const courseIds: Record<string, string> = {};
  for (const s of SELLERS) {
    const userId = users[s.key];
    if (!userId) continue;
    const values = {
      slug: s.slug,
      displayName: s.displayName,
      headline: s.headline,
      bio: s.bio,
      institutionId: cat.inst[s.inst]!,
      verificationStatus: s.status,
      verificationMethod: s.method,
      verificationData: s.method === 'university_email' ? { universityEmail: `${s.key}@${s.inst === 'metropolitan-university' ? 'metrouni.edu.bd' : 'northsouth.edu'}`, universityEmailVerified: true } : s.method === 'linkedin' ? { linkedinUrl: `https://www.linkedin.com/in/${s.slug}` } : { idCardKey: null },
      verifiedAt: s.status === 'verified' ? new Date() : null,
      payoutMethod: 'bkash',
      payoutAccountEnc: encrypt('01811000000'),
      payoutAccountMasked: '••••••0000',
    };
    await coreDb.insert(sellerProfiles).values({ userId, ...values }).onConflictDoUpdate({ target: sellerProfiles.userId, set: values });
  }
  for (const c of COURSES) {
    const sellerId = users[c.seller]!;
    const seller = SELLERS.find((s) => s.key === c.seller)!;
    const published = seller.status === 'verified';
    const totalMin = c.sections.flatMap((s) => s.chapters.flatMap((ch) => ch.lessons)).reduce((a, l) => a + l.min, 0);
    const lessonCount = c.sections.flatMap((s) => s.chapters.flatMap((ch) => ch.lessons)).length;
    const values = {
      sellerId,
      title: c.title,
      slug: c.slug,
      shortDescription: c.short,
      description: `${c.short}\n\nপ্রতিটি লেকচার ৫–১৫ মিনিটের, যাতে পরীক্ষার আগের রাতেও দ্রুত দেখে নেওয়া যায়। ⭐ চিহ্নিত লেসনগুলো শিক্ষকের মতে সম্ভাব্য গুরুত্বপূর্ণ — নিশ্চয়তা নয়।`,
      learnings: c.learnings,
      notFor: c.notFor,
      faq: [
        { q: 'কোর্সটা কতদিন দেখতে পারব?', a: 'কেনার পর আজীবন অ্যাক্সেস, একসাথে সর্বোচ্চ ৩টি ডিভাইসে।' },
        { q: 'প্রশ্ন থাকলে কোথায় করব?', a: 'প্রতিটি লেসনের নিচে প্রশ্ন করার জায়গা আছে — শিক্ষক উত্তর দেন।' },
      ],
      coverColor: c.color,
      language: 'bn',
      examType: c.examType,
      courseType: c.courseType,
      price: c.price * 100,
      compareAtPrice: c.compare ? c.compare * 100 : null,
      status: published ? 'published' : 'draft',
      publishedAt: published ? new Date(Date.now() - (5 + COURSES.indexOf(c) * 3) * 86_400_000) : null,
      contentUpdatedAt: new Date(Date.now() - COURSES.indexOf(c) * 4 * 86_400_000),
      ratingAvg: String(c.rating[0]),
      ratingCount: c.rating[1],
      enrollmentCount: c.enrolled,
      enrollments30d: c.enrolled30d,
      totalDurationSec: totalMin * 60,
      lessonCount,
      sellerVerified: published,
      sellerName: seller.displayName,
    };
    const [row] = await contentDb.insert(courses).values(values).onConflictDoUpdate({ target: courses.slug, set: values }).returning({ id: courses.id });
    const courseId = row!.id;
    courseIds[c.slug] = courseId;
    // Rebuild targets + curriculum only on first insert (keeps lesson ids stable across reseeds).
    const [hasSection] = await contentDb.select({ id: courseSections.id }).from(courseSections).where(eq(courseSections.courseId, courseId)).limit(1);
    await contentDb.delete(courseTargets).where(eq(courseTargets.courseId, courseId));
    await contentDb.insert(courseTargets).values(
      c.targets.map((t) => ({
        courseId,
        isGeneral: !t.inst,
        institutionId: t.inst ? cat.inst[t.inst]! : null,
        departmentId: t.inst && t.dept ? cat.dept[`${t.inst}:${t.dept}`]! : null,
        semester: t.sem ?? null,
        subjectMasterId: cat.subj[t.subj]!,
        institutionSubjectId: t.inst && t.code ? (cat.instSubj[`${t.inst}:${t.code}`] ?? null) : null,
        courseCode: t.code ?? null,
        batches: t.batches ?? [],
        sections: [],
      })),
    );
    if (hasSection) continue;
    for (const [si, s] of c.sections.entries()) {
      const [sec] = await contentDb.insert(courseSections).values({ courseId, title: s.title, position: si }).returning({ id: courseSections.id });
      for (const [ci, ch] of s.chapters.entries()) {
        const [chap] = await contentDb.insert(courseChapters).values({ courseId, sectionId: sec!.id, title: ch.title, position: ci }).returning({ id: courseChapters.id });
        for (const [li, l] of ch.lessons.entries()) {
          const type = l.type ?? 'video';
          let videoAssetId: string | null = null;
          let documentId: string | null = null;
          if (type === 'video') {
            const [v] = await contentDb.insert(videoAssets).values({ ownerId: sellerId, provider: 'fake', providerVideoId: `seed-${c.slug}-${si}-${ci}-${li}`, title: l.t, status: 'ready', durationSec: l.min * 60 }).returning({ id: videoAssets.id });
            videoAssetId = v!.id;
          } else if (type === 'pdf') {
            const [d] = await contentDb.insert(documents).values({ courseId, ownerId: sellerId, kind: 'notes', title: l.t, externalUrl: 'https://shikkhasathi.com/sample-notes.pdf', mime: 'application/pdf', pages: 6 }).returning({ id: documents.id });
            documentId = d!.id;
          }
          await contentDb.insert(lessons).values({ courseId, chapterId: chap!.id, title: l.t, type, position: li, durationSec: l.min * 60, videoAssetId, documentId, body: type === 'text' ? l.t : null, isPreview: !!l.preview, isImportant: !!l.imp });
        }
      }
    }
    for (const y of c.pq ?? []) {
      const [exists] = await contentDb.select({ id: documents.id }).from(documents).where(and(eq(documents.courseId, courseId), eq(documents.kind, 'previous_questions'), eq(documents.year, y)));
      if (!exists) await contentDb.insert(documents).values({ courseId, ownerId: sellerId, kind: 'previous_questions', title: `${c.targets[0]?.code ?? ''} ${c.examType === 'final' ? 'Final' : 'Midterm'} ${y}`.trim(), year: y, externalUrl: 'https://shikkhasathi.com/sample-question.pdf', mime: 'application/pdf', pages: 2 });
    }
  }
  return courseIds;
}
