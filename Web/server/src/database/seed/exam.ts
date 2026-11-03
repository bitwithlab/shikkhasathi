import { and, eq } from 'drizzle-orm';
import { contentDb, coreDb } from '../client';
import { enrollments, examPlans } from '../core/schema';
import { flashcards, mockOptions, mockQuestions, mockTests } from '../content/schema';

type Q = { q: string; o: string[]; a: number; e?: string; tag: string };

const DS_MOCK: Q[] = [
  { q: 'Singly linked list-এর মাঝখান থেকে একটি node delete করতে (pointer দেওয়া থাকলে, শেষ node নয়) সময় লাগে —', o: ['O(1)', 'O(log n)', 'O(n)', 'O(n log n)'], a: 0, e: 'পরের node-এর data কপি করে পরের node-টি unlink করলে O(1)।', tag: 'Linked List' },
  { q: 'Infix "A + B * C"-এর postfix রূপ কোনটি?', o: ['A B C * +', 'A B + C *', '+ A * B C', 'A B C + *'], a: 0, e: '* এর precedence বেশি, তাই B C * আগে হয়।', tag: 'Stack & Queue' },
  { q: 'Circular queue-তে size n হলে queue full হওয়ার শর্ত (একটি ঘর খালি রাখার পদ্ধতিতে) —', o: ['(rear + 1) % n == front', 'rear == front', 'rear == n - 1', 'front == 0'], a: 0, tag: 'Stack & Queue' },
  { q: 'BST-তে inorder traversal কোন ক্রমে key দেয়?', o: ['ঊর্ধ্বক্রমে (ascending)', 'নিম্নক্রমে', 'level অনুযায়ী', 'এলোমেলো'], a: 0, tag: 'BST' },
  { q: 'BST থেকে দুই child-ওয়ালা node delete করলে সাধারণত কাকে বসানো হয়?', o: ['Inorder successor বা predecessor', 'Root', 'যেকোনো leaf', 'Left child'], a: 0, e: 'Inorder successor (right subtree-র সবচেয়ে ছোট) বসালে BST property থাকে।', tag: 'BST' },
  { q: 'AVL tree-তে balance factor-এর অনুমোদিত মান —', o: ['-1, 0, 1', '0, 1, 2', '-2 থেকে 2', 'শুধু 0'], a: 0, tag: 'AVL' },
  { q: 'AVL-এ LR case ঠিক করতে লাগে —', o: ['আগে left rotation, তারপর right rotation', 'একটি right rotation', 'একটি left rotation', 'আগে right, তারপর left rotation'], a: 0, tag: 'AVL' },
  { q: 'Min-heap-এ n টি element থাকলে insert-এর worst case —', o: ['O(log n)', 'O(1)', 'O(n)', 'O(n log n)'], a: 0, tag: 'Heap' },
  { q: 'Array-ভিত্তিক heap-এ index i (0-based) এর left child —', o: ['2i + 1', '2i', 'i / 2', '2i + 2'], a: 0, tag: 'Heap' },
  { q: 'Heap sort-এর time complexity —', o: ['O(n log n)', 'O(n²)', 'O(n)', 'O(log n)'], a: 0, tag: 'Heap' },
  { q: 'Graph-এ shortest path (unweighted) বের করতে কোন traversal ব্যবহার হয়?', o: ['BFS', 'DFS', 'Inorder', 'Preorder'], a: 0, tag: 'Graph' },
  { q: 'Adjacency list-এ V vertex ও E edge থাকলে memory —', o: ['O(V + E)', 'O(V²)', 'O(E²)', 'O(V)'], a: 0, tag: 'Graph' },
];

const DS_CARDS: [string, string][] = [
  ['Stack — কোন নীতিতে চলে?', 'LIFO (Last In, First Out)'],
  ['Queue — কোন নীতিতে চলে?', 'FIFO (First In, First Out)'],
  ['BST search-এর average ও worst case', 'Average O(log n), worst O(n) (skewed tree)'],
  ['AVL rotation — ৪টি কেস', 'LL, RR, LR, RL'],
  ['Heap property (min-heap)', 'প্রতিটি parent ≤ তার children'],
  ['Inorder traversal-এর ক্রম', 'Left → Root → Right'],
  ['Preorder traversal-এর ক্রম', 'Root → Left → Right'],
  ['Postorder traversal-এর ক্রম', 'Left → Right → Root'],
  ['BFS কোন data structure ব্যবহার করে?', 'Queue'],
  ['DFS কোন data structure ব্যবহার করে?', 'Stack (বা recursion)'],
  ['Linked list-এর সুবিধা array-র তুলনায়', 'Insert/delete O(1) (pointer জানা থাকলে), dynamic size'],
  ['Circular queue কেন লাগে?', 'Linear queue-তে সামনের খালি ঘর আবার ব্যবহার করতে'],
];

const ALGO_MOCK: Q[] = [
  { q: 'Merge sort-এর recurrence —', o: ['T(n) = 2T(n/2) + O(n)', 'T(n) = T(n-1) + O(1)', 'T(n) = 2T(n-1) + 1', 'T(n) = T(n/2) + O(1)'], a: 0, tag: 'Divide & Conquer' },
  { q: 'Master theorem-এ T(n) = 2T(n/2) + n এর সমাধান —', o: ['Θ(n log n)', 'Θ(n)', 'Θ(n²)', 'Θ(log n)'], a: 0, tag: 'Recurrence' },
  { q: 'Quick sort-এর worst case কখন হয়?', o: ['Pivot সবসময় সবচেয়ে ছোট/বড় হলে', 'Random pivot নিলে', 'Array ছোট হলে', 'কখনো হয় না'], a: 0, tag: 'Divide & Conquer' },
  { q: '0/1 Knapsack সমাধানের উপযুক্ত পদ্ধতি —', o: ['Dynamic Programming', 'Greedy', 'Binary search', 'BFS'], a: 0, tag: 'DP' },
  { q: 'Dijkstra algorithm negative edge-এ —', o: ['সঠিক ফল নাও দিতে পারে', 'সবসময় সঠিক', 'দ্রুত চলে', 'কাজ করে না, compile error'], a: 0, tag: 'Graph' },
  { q: 'Big-O তে 3n² + 5n + 2 —', o: ['O(n²)', 'O(n)', 'O(n³)', 'O(1)'], a: 0, tag: 'Asymptotic' },
];

async function seedMock(courseId: string, title: string, durationSec: number, qs: Q[]) {
  const [exists] = await contentDb.select({ id: mockTests.id }).from(mockTests).where(and(eq(mockTests.courseId, courseId), eq(mockTests.title, title)));
  if (exists) return exists.id;
  const [t] = await contentDb.insert(mockTests).values({ courseId, title, durationSec, description: 'সম্ভাব্য গুরুত্বপূর্ণ টপিক থেকে MCQ — পরীক্ষার আগে নিজেকে যাচাই করুন।' }).returning({ id: mockTests.id });
  for (const [i, q] of qs.entries()) {
    const [row] = await contentDb.insert(mockQuestions).values({ testId: t!.id, body: q.q, explanation: q.e ?? null, chapterTag: q.tag, position: i }).returning({ id: mockQuestions.id });
    await contentDb.insert(mockOptions).values(q.o.map((body, j) => ({ questionId: row!.id, body, isCorrect: j === q.a, position: j })));
  }
  return t!.id;
}

async function seedCards(courseId: string, cards: [string, string][]) {
  const [exists] = await contentDb.select({ id: flashcards.id }).from(flashcards).where(eq(flashcards.courseId, courseId)).limit(1);
  if (exists) return;
  await contentDb.insert(flashcards).values(cards.map(([front, back], i) => ({ courseId, front, back, position: i })));
}

/** Mock tests, flashcards, and a demo exam plan so Exam Mode works locally. */
export async function seedExam(users: Record<string, string>, courses: Record<string, string>) {
  const ds = courses['data-structures-midterm-crash-cse-2203'];
  const algo = courses['algorithm-analysis-design-cse-2201'];
  const nsuDs = courses['data-structures-nsu-cse225'];
  if (ds) {
    await seedMock(ds, 'Midterm Mock Test — ১', 15 * 60, DS_MOCK);
    await seedMock(ds, 'Trees দ্রুত যাচাই', 8 * 60, DS_MOCK.filter((q) => ['BST', 'AVL', 'Heap'].includes(q.tag)));
    await seedCards(ds, DS_CARDS);
  }
  if (algo) {
    await seedMock(algo, 'Algorithms Midterm Mock', 10 * 60, ALGO_MOCK);
    await seedCards(algo, [['Merge sort time', 'Θ(n log n) সব ক্ষেত্রে'], ['Quick sort average', 'Θ(n log n)'], ['DP-র দুটি শর্ত', 'Optimal substructure + overlapping subproblems'], ['Greedy কখন কাজ করে?', 'Greedy-choice property থাকলে']]);
  }
  if (nsuDs) await seedMock(nsuDs, 'CSE225 Midterm Practice', 12 * 60, DS_MOCK.slice(0, 8));

  // demo student: enrolled in DS + Algorithms with a DS midterm in 6 days
  const sadman = users.sadman;
  if (sadman && ds && algo) {
    for (const c of [ds, algo]) await coreDb.insert(enrollments).values({ studentId: sadman, courseId: c, status: 'active' }).onConflictDoNothing();
    const [plan] = await coreDb.select({ id: examPlans.id }).from(examPlans).where(eq(examPlans.studentId, sadman)).limit(1);
    if (!plan) {
      const d = new Date(Date.now() + 6 * 86_400_000);
      const ymd = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(d);
      await coreDb.insert(examPlans).values({ studentId: sadman, subjectName: 'Data Structures', courseCode: 'CSE-2203', examType: 'midterm', examDate: new Date(`${ymd}T10:00:00+06:00`), courseIds: [ds] });
    }
  }
}
