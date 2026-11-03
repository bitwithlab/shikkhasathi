import { createHash } from 'node:crypto';
import type { Material } from '@shikkhasathi/shared';

// ---------------------------------------------------------------- normalized shapes

export interface GcCourse {
  id: string;
  name: string;
  section: string | null;
  room: string | null;
  descriptionHeading: string | null;
  alternateLink: string | null;
  teacherNames: string[];
  state: string;
  updateTime: Date | null;
}
export interface GcCourseWork {
  id: string;
  courseId: string;
  title: string;
  description: string | null;
  workType: string;
  dueAt: Date | null;
  maxPoints: number | null;
  materials: Material[];
  alternateLink: string | null;
  state: string;
  creationTime: Date | null;
  updateTime: Date | null;
}
export interface GcSubmission {
  id: string;
  courseId: string;
  courseWorkId: string;
  state: string; // NEW | CREATED | TURNED_IN | RETURNED | RECLAIMED_BY_STUDENT
  late: boolean;
  assignedGrade: number | null;
  alternateLink: string | null;
  updateTime: Date | null;
}
export interface GcMaterialPost {
  id: string;
  courseId: string;
  title: string;
  description: string | null;
  materials: Material[];
  alternateLink: string | null;
  creationTime: Date | null;
  updateTime: Date | null;
}
export interface GcAnnouncement {
  id: string;
  courseId: string;
  text: string;
  authorName: string | null;
  materials: Material[];
  alternateLink: string | null;
  creationTime: Date | null;
  updateTime: Date | null;
}

export interface TokenSet {
  refreshToken: string | null;
  accessToken: string;
  expiresAt: Date;
  scopes: string[];
  googleUserId: string;
  email: string;
}

export type ClassroomErrorKind = 'reauth' | 'workspace' | 'quota' | 'not_found' | 'other';
export class ClassroomError extends Error {
  constructor(
    readonly kind: ClassroomErrorKind,
    message: string,
    readonly status?: number,
  ) {
    super(message);
  }
}

/** Read-only Classroom access (+ OAuth plumbing). Real = Google REST, Fake = seeded demo dataset. */
export interface GoogleClassroomClient {
  readonly fake: boolean;
  authUrl(opts: { state: string; codeChallenge: string; redirectUri: string; loginHint?: string }): string;
  exchangeCode(opts: { code: string; redirectUri?: string; codeVerifier?: string }): Promise<TokenSet>;
  refresh(refreshToken: string): Promise<{ accessToken: string; expiresAt: Date }>;
  revoke(token: string): Promise<void>;
  listCourses(accessToken: string): Promise<GcCourse[]>;
  listCourseWork(accessToken: string, courseId: string): Promise<GcCourseWork[]>;
  listSubmissions(accessToken: string, courseId: string): Promise<GcSubmission[]>;
  listMaterials(accessToken: string, courseId: string): Promise<GcMaterialPost[]>;
  listAnnouncements(accessToken: string, courseId: string): Promise<GcAnnouncement[]>;
  registerCourseWorkPush(accessToken: string, courseId: string, topicName: string): Promise<{ registrationId: string; expiresAt: Date }>;
  deleteRegistration(accessToken: string, registrationId: string): Promise<void>;
}

// ---------------------------------------------------------------- helpers

const d = (v: unknown) => (typeof v === 'string' && v ? new Date(v) : null);

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Json = any;

export function mapMaterials(list: Json[] | undefined): Material[] {
  return (list ?? [])
    .map((m: Json): Material | null => {
      if (m.driveFile?.driveFile) {
        const f = m.driveFile.driveFile;
        return { kind: 'drive', title: f.title ?? 'Drive file', url: f.alternateLink ?? null, thumbnailUrl: f.thumbnailUrl ?? null, mime: null };
      }
      if (m.link) return { kind: 'link', title: m.link.title ?? m.link.url, url: m.link.url ?? null, thumbnailUrl: m.link.thumbnailUrl ?? null, mime: null };
      if (m.youtubeVideo) return { kind: 'youtube', title: m.youtubeVideo.title ?? 'YouTube', url: m.youtubeVideo.alternateLink ?? null, thumbnailUrl: m.youtubeVideo.thumbnailUrl ?? null, mime: null };
      if (m.form) return { kind: 'form', title: m.form.title ?? 'Google Form', url: m.form.formUrl ?? null, thumbnailUrl: m.form.thumbnailUrl ?? null, mime: null };
      return null;
    })
    .filter((m): m is Material => m !== null);
}

export function mapDue(dueDate: Json, dueTime: Json): Date | null {
  if (!dueDate?.year) return null;
  // Classroom dueDate/dueTime are UTC. A missing time means end of day (23:59 UTC is how Classroom renders it).
  const h = dueTime?.hours ?? (dueTime ? 0 : 23);
  const mi = dueTime?.minutes ?? (dueTime ? 0 : 59);
  return new Date(Date.UTC(dueDate.year, dueDate.month - 1, dueDate.day, h, mi));
}

// ---------------------------------------------------------------- real client

const API = 'https://classroom.googleapis.com/v1';

export class RealClassroomClient implements GoogleClassroomClient {
  readonly fake = false;
  constructor(
    private readonly cfg: { clientId: string; clientSecret: string; scopes: readonly string[] },
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  authUrl(o: { state: string; codeChallenge: string; redirectUri: string; loginHint?: string }) {
    const u = new URL('https://accounts.google.com/o/oauth2/v2/auth');
    u.searchParams.set('client_id', this.cfg.clientId);
    u.searchParams.set('redirect_uri', o.redirectUri);
    u.searchParams.set('response_type', 'code');
    u.searchParams.set('scope', this.cfg.scopes.join(' '));
    u.searchParams.set('access_type', 'offline');
    u.searchParams.set('prompt', 'consent');
    u.searchParams.set('include_granted_scopes', 'true');
    u.searchParams.set('state', o.state);
    u.searchParams.set('code_challenge', o.codeChallenge);
    u.searchParams.set('code_challenge_method', 'S256');
    if (o.loginHint) u.searchParams.set('login_hint', o.loginHint);
    return u.toString();
  }

  private async token(body: Record<string, string>) {
    const res = await this.fetchImpl('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ client_id: this.cfg.clientId, client_secret: this.cfg.clientSecret, ...body }),
      signal: AbortSignal.timeout(15_000),
    });
    const data = (await res.json().catch(() => ({}))) as Json;
    if (!res.ok) {
      if (data.error === 'invalid_grant') throw new ClassroomError('reauth', 'invalid_grant', res.status);
      throw new ClassroomError('other', `token ${res.status} ${data.error ?? ''}`, res.status);
    }
    return data;
  }

  async exchangeCode(o: { code: string; redirectUri?: string; codeVerifier?: string }): Promise<TokenSet> {
    const data = await this.token({ grant_type: 'authorization_code', code: o.code, ...(o.redirectUri ? { redirect_uri: o.redirectUri } : { redirect_uri: '' }), ...(o.codeVerifier ? { code_verifier: o.codeVerifier } : {}) });
    const claims = data.id_token ? JSON.parse(Buffer.from(String(data.id_token).split('.')[1]!, 'base64url').toString('utf8')) : {};
    return {
      refreshToken: data.refresh_token ?? null,
      accessToken: data.access_token,
      expiresAt: new Date(Date.now() + (Number(data.expires_in ?? 3600) - 60) * 1000),
      scopes: String(data.scope ?? '').split(' ').filter(Boolean),
      googleUserId: claims.sub ?? '',
      email: claims.email ?? '',
    };
  }

  async refresh(refreshToken: string) {
    const data = await this.token({ grant_type: 'refresh_token', refresh_token: refreshToken });
    return { accessToken: data.access_token as string, expiresAt: new Date(Date.now() + (Number(data.expires_in ?? 3600) - 60) * 1000) };
  }

  async revoke(token: string) {
    await this.fetchImpl(`https://oauth2.googleapis.com/revoke?token=${encodeURIComponent(token)}`, { method: 'POST', signal: AbortSignal.timeout(10_000) }).catch(() => undefined);
  }

  private async get(at: string, path: string, params: Record<string, string> = {}): Promise<Json[]> {
    const out: Json[] = [];
    let pageToken: string | undefined;
    for (let i = 0; i < 20; i++) {
      const u = new URL(`${API}${path}`);
      for (const [k, v] of Object.entries(params)) u.searchParams.append(k, v);
      u.searchParams.set('pageSize', '100');
      if (pageToken) u.searchParams.set('pageToken', pageToken);
      const res = await this.fetchImpl(u, { headers: { authorization: `Bearer ${at}` }, signal: AbortSignal.timeout(20_000) });
      if (!res.ok) throw await this.toError(res);
      const data = (await res.json()) as Json;
      const key = Object.keys(data).find((k) => Array.isArray(data[k]));
      if (key) out.push(...data[key]);
      pageToken = data.nextPageToken;
      if (!pageToken) break;
    }
    return out;
  }

  private async toError(res: Response) {
    const body = (await res.json().catch(() => ({}))) as Json;
    const msg: string = body?.error?.message ?? `HTTP ${res.status}`;
    const reason = JSON.stringify(body?.error?.details ?? body?.error?.errors ?? '');
    if (res.status === 401) return new ClassroomError('reauth', msg, 401);
    if (res.status === 429 || /rateLimitExceeded|RESOURCE_EXHAUSTED|quota/i.test(reason + msg)) return new ClassroomError('quota', msg, res.status);
    if (res.status === 403 && /ClassroomApiDisabled|CannotAccess|admin|Workspace|disabled for your domain|PERMISSION_DENIED/i.test(reason + msg)) return new ClassroomError('workspace', msg, 403);
    if (res.status === 404) return new ClassroomError('not_found', msg, 404);
    return new ClassroomError('other', msg, res.status);
  }

  async listCourses(at: string): Promise<GcCourse[]> {
    const rows = await this.get(at, '/courses', { studentId: 'me', courseStates: 'ACTIVE' });
    // Teacher names need classroom.rosters.readonly, which we deliberately don't request (ADR-014).
    return rows.map((c) => ({ id: c.id, name: c.name, section: c.section ?? null, room: c.room ?? null, descriptionHeading: c.descriptionHeading ?? null, alternateLink: c.alternateLink ?? null, teacherNames: [], state: c.courseState ?? 'ACTIVE', updateTime: d(c.updateTime) }));
  }

  async listCourseWork(at: string, courseId: string): Promise<GcCourseWork[]> {
    const rows = await this.get(at, `/courses/${courseId}/courseWork`, { courseWorkStates: 'PUBLISHED', orderBy: 'updateTime desc' });
    return rows.map((w) => ({
      id: w.id,
      courseId,
      title: w.title ?? '',
      description: w.description ?? null,
      workType: w.workType ?? 'ASSIGNMENT',
      dueAt: mapDue(w.dueDate, w.dueTime),
      maxPoints: typeof w.maxPoints === 'number' ? w.maxPoints : null,
      materials: mapMaterials(w.materials),
      alternateLink: w.alternateLink ?? null,
      state: w.state ?? 'PUBLISHED',
      creationTime: d(w.creationTime),
      updateTime: d(w.updateTime),
    }));
  }

  async listSubmissions(at: string, courseId: string): Promise<GcSubmission[]> {
    const rows = await this.get(at, `/courses/${courseId}/courseWork/-/studentSubmissions`, { userId: 'me' });
    return rows.map((s) => ({ id: s.id, courseId, courseWorkId: s.courseWorkId, state: s.state ?? 'NEW', late: !!s.late, assignedGrade: typeof s.assignedGrade === 'number' ? s.assignedGrade : null, alternateLink: s.alternateLink ?? null, updateTime: d(s.updateTime) }));
  }

  async listMaterials(at: string, courseId: string): Promise<GcMaterialPost[]> {
    const rows = await this.get(at, `/courses/${courseId}/courseWorkMaterials`, { courseWorkMaterialStates: 'PUBLISHED' });
    return rows.map((m) => ({ id: m.id, courseId, title: m.title ?? '', description: m.description ?? null, materials: mapMaterials(m.materials), alternateLink: m.alternateLink ?? null, creationTime: d(m.creationTime), updateTime: d(m.updateTime) }));
  }

  async listAnnouncements(at: string, courseId: string): Promise<GcAnnouncement[]> {
    const rows = await this.get(at, `/courses/${courseId}/announcements`, { announcementStates: 'PUBLISHED', orderBy: 'updateTime desc' });
    return rows.map((a) => ({ id: a.id, courseId, text: a.text ?? '', authorName: null, materials: mapMaterials(a.materials), alternateLink: a.alternateLink ?? null, creationTime: d(a.creationTime), updateTime: d(a.updateTime) }));
  }

  async registerCourseWorkPush(at: string, courseId: string, topicName: string) {
    const res = await this.fetchImpl(`${API}/registrations`, {
      method: 'POST',
      headers: { authorization: `Bearer ${at}`, 'content-type': 'application/json' },
      body: JSON.stringify({ feed: { feedType: 'COURSE_WORK_CHANGES', courseWorkChangesInfo: { courseId } }, cloudPubsubTopic: { topicName } }),
      signal: AbortSignal.timeout(15_000),
    });
    if (!res.ok) throw await this.toError(res);
    const data = (await res.json()) as Json;
    return { registrationId: data.registrationId as string, expiresAt: new Date(data.expiryTime) };
  }

  async deleteRegistration(at: string, registrationId: string) {
    await this.fetchImpl(`${API}/registrations/${registrationId}`, { method: 'DELETE', headers: { authorization: `Bearer ${at}` }, signal: AbortSignal.timeout(10_000) }).catch(() => undefined);
  }
}

// ---------------------------------------------------------------- fake client (dev/demo/tests)

const HOUR = 3_600_000;
const DAY = 24 * HOUR;

interface FakeCourse {
  id: string;
  name: string;
  section: string;
  room: string;
  teacher: string;
}
const FAKE_COURSES: FakeCourse[] = [
  { id: 'gc-cse2203', name: 'CSE-2203 Data Structures', section: 'Section A · Batch 56', room: 'Room 402', teacher: 'মো. রাকিবুল হাসান' },
  { id: 'gc-cse2204', name: 'CSE-2204 Data Structures Lab', section: 'Section A1', room: 'Lab 3', teacher: 'নাফিসা তাবাসসুম' },
  { id: 'gc-cse2205', name: 'CSE-2205 OOP II (Java)', section: 'Section A', room: 'Room 305', teacher: 'ফারহান আহমেদ' },
  { id: 'gc-mat2101', name: 'MAT-2101 Linear Algebra & Differential Equations', section: 'Section A', room: 'Room 210', teacher: 'ড. মাহফুজুল আলম' },
  { id: 'gc-eng2101', name: 'ENG-2101 Technical Writing', section: 'Section B', room: 'Room 118', teacher: 'Sharmin Akter' },
];

interface FakeWork {
  id: string;
  course: string;
  title: string;
  description: string;
  workType: string;
  /** due offset from anchor (ms); null = no due date */
  due: number | null;
  posted: number;
  points: number | null;
  /** submission state; 'auto' = TURNED_IN if due already passed by > 1 day else NEW */
  sub: 'NEW' | 'TURNED_IN' | 'RETURNED';
  grade?: number;
  late?: boolean;
  materials?: Material[];
}

const pdf = (title: string): Material => ({ kind: 'drive', title, url: 'https://drive.google.com/', thumbnailUrl: null, mime: 'application/pdf' });
const slides = (title: string): Material => ({ kind: 'drive', title, url: 'https://docs.google.com/presentation/', thumbnailUrl: null, mime: 'application/vnd.google-apps.presentation' });
const yt = (title: string): Material => ({ kind: 'youtube', title, url: 'https://www.youtube.com/', thumbnailUrl: null, mime: null });

// Offsets are relative to the anchor (connection time) so the demo stays coherent and stable across syncs.
const FAKE_WORK: FakeWork[] = [
  { id: 'cw-1', course: 'gc-mat2101', title: 'Assignment 3: Eigenvalues & Eigenvectors', description: 'Chapter 4-এর ৫, ৯, ১২ নম্বর অঙ্ক। হাতে লিখে স্ক্যান করে জমা দিন।', workType: 'ASSIGNMENT', due: -20 * HOUR, posted: -6 * DAY, points: 20, sub: 'NEW', materials: [pdf('Chapter 4 – Eigenvalues.pdf')] },
  { id: 'cw-2', course: 'gc-cse2204', title: 'Lab 2: BST Deletion Code', description: 'Insert, delete (৩টি কেস) ও inorder traversal। GitHub লিংক জমা দিন।', workType: 'ASSIGNMENT', due: 9 * HOUR, posted: -3 * DAY, points: 10, sub: 'NEW', materials: [pdf('Lab 2 – BST.pdf')] },
  { id: 'cw-3', course: 'gc-cse2205', title: 'Quiz 2: Java Multithreading', description: 'Online quiz, ২০ মিনিট। Thread, Runnable, synchronized।', workType: 'MULTIPLE_CHOICE_QUESTION', due: 1 * DAY + 3 * HOUR, posted: -2 * DAY, points: 15, sub: 'NEW', materials: [{ kind: 'form', title: 'Quiz 2 – Google Form', url: 'https://forms.google.com/', thumbnailUrl: null, mime: null }] },
  { id: 'cw-4', course: 'gc-cse2203', title: 'Problem Set 4: Heaps & Priority Queue', description: 'Heapify, heap sort trace, k-th largest — ৬টি প্রশ্ন।', workType: 'ASSIGNMENT', due: 3 * DAY, posted: -1 * DAY, points: 25, sub: 'NEW' },
  { id: 'cw-5', course: 'gc-eng2101', title: 'Essay Draft: Lab Report Structure', description: '৮০০ শব্দ। APA format।', workType: 'ASSIGNMENT', due: 5 * DAY, posted: -4 * DAY, points: 30, sub: 'TURNED_IN' },
  { id: 'cw-6', course: 'gc-cse2203', title: 'Problem Set 3: AVL Rotations', description: 'LL, RR, LR, RL rotation trace।', workType: 'ASSIGNMENT', due: -4 * DAY, posted: -10 * DAY, points: 25, sub: 'RETURNED', grade: 22 },
  { id: 'cw-7', course: 'gc-cse2205', title: 'Assignment 1: Inheritance & Interfaces', description: 'UML + code।', workType: 'ASSIGNMENT', due: -7 * DAY, posted: -14 * DAY, points: 20, sub: 'RETURNED', grade: 17, late: true },
  { id: 'cw-8', course: 'gc-cse2204', title: 'Lab 1: Linked List Operations', description: '', workType: 'ASSIGNMENT', due: -9 * DAY, posted: -15 * DAY, points: 10, sub: 'TURNED_IN' },
  { id: 'cw-9', course: 'gc-mat2101', title: 'Class Test 2 Preparation Sheet', description: 'CT-2: ২৪ নভেম্বর, Chapter 3–4।', workType: 'ASSIGNMENT', due: 9 * DAY, posted: -1 * HOUR, points: null, sub: 'NEW' },
  // revealed after connect (simulates push / poll picking up new work)
  { id: 'cw-10', course: 'gc-cse2203', title: 'Midterm Practice: Graph Traversal', description: 'BFS/DFS trace + shortest path। ঐচ্ছিক, তবে Midterm-এর জন্য ভালো প্রস্তুতি।', workType: 'ASSIGNMENT', due: 6 * DAY, posted: 20 * 60_000, points: 10, sub: 'NEW', materials: [slides('Graph Traversal – slides')] },
];

const FAKE_MATERIALS: { id: string; course: string; title: string; description: string; posted: number; materials: Material[] }[] = [
  { id: 'mt-1', course: 'gc-cse2203', title: 'Lecture 9 slides: Heaps', description: 'আজকের ক্লাসের স্লাইড।', posted: -1 * DAY, materials: [slides('Lecture 9 – Heaps')] },
  { id: 'mt-2', course: 'gc-cse2203', title: 'Previous year midterm questions (2023, 2024)', description: 'রিভিশনের জন্য।', posted: -2 * DAY, materials: [pdf('DS Midterm 2023.pdf'), pdf('DS Midterm 2024.pdf')] },
  { id: 'mt-3', course: 'gc-mat2101', title: 'Chapter 4 handnote', description: '', posted: -5 * DAY, materials: [pdf('Linear Algebra Ch4 handnote.pdf')] },
  { id: 'mt-4', course: 'gc-cse2205', title: 'Thread lifecycle explained (video)', description: '', posted: -3 * DAY, materials: [yt('Java Thread Lifecycle')] },
  { id: 'mt-5', course: 'gc-cse2204', title: 'Lab 3 starter code', description: 'Lab 3-এর জন্য starter repo।', posted: 2 * HOUR, materials: [{ kind: 'link', title: 'github.com/mu-cse/ds-lab3', url: 'https://github.com/', thumbnailUrl: null, mime: null }] },
];

const FAKE_ANNOUNCEMENTS: { id: string; course: string; text: string; author: string; posted: number }[] = [
  { id: 'an-1', course: 'gc-cse2203', text: 'আগামী রবিবারের ক্লাস Room 402-এর বদলে Room 501-এ হবে। Midterm সিলেবাস: Lecture 1–10।', author: 'মো. রাকিবুল হাসান', posted: -5 * HOUR },
  { id: 'an-2', course: 'gc-cse2205', text: 'Quiz 2 অনলাইনে হবে, সময় ২০ মিনিট। ল্যাপটপ বা মোবাইল চার্জ দিয়ে রাখবেন।', author: 'ফারহান আহমেদ', posted: -1 * DAY },
  { id: 'an-3', course: 'gc-mat2101', text: 'Makeup class: বৃহস্পতিবার বিকেল ৩টা, Room 210।', author: 'ড. মাহফুজুল আলম', posted: -2 * DAY },
  { id: 'an-4', course: 'gc-eng2101', text: 'Essay draft-এ feedback দেওয়া শুরু হয়েছে, শুক্রবারের মধ্যে সবাই পেয়ে যাবেন।', author: 'Sharmin Akter', posted: 6 * HOUR },
];

export class FakeClassroomClient implements GoogleClassroomClient {
  readonly fake = true;
  /** anchor = connection time; items with posted > now-anchor are not visible yet (simulated arrivals). */
  constructor(
    private readonly anchor: Date,
    private readonly now: () => Date = () => new Date(),
  ) {}

  private visible(posted: number) {
    return this.anchor.getTime() + posted <= this.now().getTime();
  }
  private at(offset: number) {
    return new Date(this.anchor.getTime() + offset);
  }

  authUrl(o: { state: string; redirectUri: string }) {
    const u = new URL(o.redirectUri);
    u.searchParams.set('code', 'fake-auth-code');
    u.searchParams.set('state', o.state);
    return u.toString();
  }
  async exchangeCode(o: { code: string }): Promise<TokenSet> {
    if (!o.code.startsWith('fake')) throw new ClassroomError('reauth', 'invalid_grant');
    const sub = createHash('sha1').update(o.code).digest('hex').slice(0, 12);
    return { refreshToken: `fake-refresh-${sub}`, accessToken: `fake-access-${sub}`, expiresAt: new Date(Date.now() + HOUR), scopes: ['openid', 'email', 'profile', 'classroom.courses.readonly', 'classroom.coursework.me.readonly', 'classroom.courseworkmaterials.readonly', 'classroom.announcements.readonly', 'classroom.push-notifications'], googleUserId: `fake-${sub}`, email: 'student.demo@metrouni.edu.bd' };
  }
  async refresh(refreshToken: string) {
    if (refreshToken === 'fake-revoked') throw new ClassroomError('reauth', 'invalid_grant');
    return { accessToken: `fake-access-${Date.now()}`, expiresAt: new Date(Date.now() + HOUR) };
  }
  async revoke() {}
  async listCourses(): Promise<GcCourse[]> {
    return FAKE_COURSES.map((c) => ({ id: c.id, name: c.name, section: c.section, room: c.room, descriptionHeading: c.name, alternateLink: `https://classroom.google.com/c/${c.id}`, teacherNames: [c.teacher], state: 'ACTIVE', updateTime: this.at(-30 * DAY) }));
  }
  async listCourseWork(_at: string, courseId: string): Promise<GcCourseWork[]> {
    return FAKE_WORK.filter((w) => w.course === courseId && this.visible(w.posted)).map((w) => ({
      id: w.id,
      courseId,
      title: w.title,
      description: w.description || null,
      workType: w.workType,
      dueAt: w.due === null ? null : this.at(w.due),
      maxPoints: w.points,
      materials: w.materials ?? [],
      alternateLink: `https://classroom.google.com/c/${courseId}/a/${w.id}/details`,
      state: 'PUBLISHED',
      creationTime: this.at(w.posted),
      updateTime: this.at(w.posted),
    }));
  }
  async listSubmissions(_at: string, courseId: string): Promise<GcSubmission[]> {
    return FAKE_WORK.filter((w) => w.course === courseId && this.visible(w.posted)).map((w) => ({
      id: `sub-${w.id}`,
      courseId,
      courseWorkId: w.id,
      state: w.sub,
      late: !!w.late,
      assignedGrade: w.grade ?? null,
      alternateLink: `https://classroom.google.com/c/${courseId}/a/${w.id}/details`,
      updateTime: this.at(w.sub === 'RETURNED' && w.due !== null ? w.due + 2 * DAY : w.posted),
    }));
  }
  async listMaterials(_at: string, courseId: string): Promise<GcMaterialPost[]> {
    return FAKE_MATERIALS.filter((m) => m.course === courseId && this.visible(m.posted)).map((m) => ({ id: m.id, courseId, title: m.title, description: m.description || null, materials: m.materials, alternateLink: `https://classroom.google.com/c/${courseId}/m/${m.id}/details`, creationTime: this.at(m.posted), updateTime: this.at(m.posted) }));
  }
  async listAnnouncements(_at: string, courseId: string): Promise<GcAnnouncement[]> {
    return FAKE_ANNOUNCEMENTS.filter((a) => a.course === courseId && this.visible(a.posted)).map((a) => ({ id: a.id, courseId, text: a.text, authorName: a.author, materials: [], alternateLink: `https://classroom.google.com/c/${courseId}/p/${a.id}`, creationTime: this.at(a.posted), updateTime: this.at(a.posted) }));
  }
  async registerCourseWorkPush(_at: string, courseId: string) {
    return { registrationId: `fake-reg-${courseId}-${this.anchor.getTime()}`, expiresAt: new Date(this.now().getTime() + 7 * DAY) };
  }
  async deleteRegistration() {}
}
