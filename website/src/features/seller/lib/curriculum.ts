import type { SellerCourse } from '@/api';

/**
 * Client-side curriculum tree (Section → Chapter → Lesson).
 * Every node has a stable `key` (the server id, or a temporary key for unsaved nodes) so React
 * state, uploads and drag & drop survive reorders and saves.
 */
type ApiSection = SellerCourse['sections'][number];
type ApiChapter = ApiSection['chapters'][number];
export type ApiLesson = ApiChapter['lessons'][number];
export type LessonType = ApiLesson['type'];

export interface LessonDraft {
  key: string;
  id?: string;
  title: string;
  type: LessonType;
  durationSec: number;
  videoAssetId: string | null;
  documentId: string | null;
  body: string | null;
  isPreview: boolean;
  isImportant: boolean;
  /** read-only info for display (never sent) */
  video: ApiLesson['video'];
  document: ApiLesson['document'];
}
export interface ChapterDraft {
  key: string;
  id?: string;
  title: string;
  lessons: LessonDraft[];
}
export interface SectionDraft {
  key: string;
  id?: string;
  title: string;
  chapters: ChapterDraft[];
}
export type Tree = SectionDraft[];
export interface ChapterPath {
  s: number;
  c: number;
}
export interface LessonPath extends ChapterPath {
  l: number;
}

let seq = 0;
export const newKey = () => `tmp-${(++seq).toString(36)}-${Math.random().toString(36).slice(2, 7)}`;

export function newLesson(title: string, type: LessonType = 'video'): LessonDraft {
  return { key: newKey(), title, type, durationSec: 0, videoAssetId: null, documentId: null, body: null, isPreview: false, isImportant: false, video: null, document: null };
}
export const newChapter = (title: string, lessons: LessonDraft[] = []): ChapterDraft => ({ key: newKey(), title, lessons });
export const newSection = (title: string, chapters: ChapterDraft[] = []): SectionDraft => ({ key: newKey(), title, chapters });

export function fromApi(sections: ApiSection[]): Tree {
  return sections.map((s) => ({
    key: s.id,
    id: s.id,
    title: s.title,
    chapters: s.chapters.map((ch) => ({
      key: ch.id,
      id: ch.id,
      title: ch.title,
      lessons: ch.lessons.map((l) => ({
        key: l.id,
        id: l.id,
        title: l.title,
        type: l.type,
        durationSec: l.durationSec ?? 0,
        videoAssetId: l.videoAssetId ?? null,
        documentId: l.documentId ?? null,
        body: l.body ?? null,
        isPreview: l.isPreview ?? false,
        isImportant: l.isImportant ?? false,
        video: l.video,
        document: l.document,
      })),
    })),
  }));
}

const title = (s: string, fallback: string) => s.trim().slice(0, 200) || fallback;

/** PUT body: full tree with existing ids; read-only `video`/`document` and client keys stripped. */
export function toBody(tree: Tree, untitled = 'Untitled') {
  return {
    sections: tree.map((s) => ({
      ...(s.id ? { id: s.id } : {}),
      title: title(s.title, untitled),
      chapters: s.chapters.map((ch) => ({
        ...(ch.id ? { id: ch.id } : {}),
        title: title(ch.title, untitled),
        lessons: ch.lessons.map((l) => ({
          ...(l.id ? { id: l.id } : {}),
          title: title(l.title, untitled),
          type: l.type,
          durationSec: Math.max(0, Math.min(36_000, Math.round(l.durationSec || 0))),
          videoAssetId: l.type === 'video' ? l.videoAssetId : null,
          documentId: l.type === 'pdf' ? l.documentId : null,
          body: l.type === 'text' ? l.body : null,
          isPreview: l.isPreview,
          isImportant: l.isImportant,
        })),
      })),
    })),
  };
}

// ---------------------------------------------------------------- moves (immutable)

/** Remove at `from`, insert at `to` (index in the array after removal, clamped). */
export function arrayMove<T>(arr: readonly T[], from: number, to: number): T[] {
  if (from < 0 || from >= arr.length) return [...arr];
  const next = [...arr];
  const [item] = next.splice(from, 1);
  next.splice(Math.max(0, Math.min(to, next.length)), 0, item!);
  return next;
}

export const moveSection = (tree: Tree, from: number, to: number) => arrayMove(tree, from, to);

export function moveChapter(tree: Tree, from: ChapterPath, to: ChapterPath): Tree {
  const ch = tree[from.s]?.chapters[from.c];
  if (!ch || !tree[to.s]) return tree;
  if (from.s === to.s) return tree.map((s, i) => (i === from.s ? { ...s, chapters: arrayMove(s.chapters, from.c, to.c) } : s));
  return tree.map((s, i) => {
    if (i === from.s) return { ...s, chapters: s.chapters.filter((_, j) => j !== from.c) };
    if (i === to.s) {
      const chapters = [...s.chapters];
      chapters.splice(Math.max(0, Math.min(to.c, chapters.length)), 0, ch);
      return { ...s, chapters };
    }
    return s;
  });
}

export function moveLesson(tree: Tree, from: LessonPath, to: LessonPath): Tree {
  const lesson = tree[from.s]?.chapters[from.c]?.lessons[from.l];
  if (!lesson || !tree[to.s]?.chapters[to.c]) return tree;
  const same = from.s === to.s && from.c === to.c;
  return tree.map((s, si) => ({
    ...s,
    chapters: s.chapters.map((ch, ci) => {
      const isFrom = si === from.s && ci === from.c;
      const isTo = si === to.s && ci === to.c;
      if (same && isFrom) return { ...ch, lessons: arrayMove(ch.lessons, from.l, to.l) };
      if (isFrom) return { ...ch, lessons: ch.lessons.filter((_, j) => j !== from.l) };
      if (isTo) {
        const lessons = [...ch.lessons];
        lessons.splice(Math.max(0, Math.min(to.l, lessons.length)), 0, lesson);
        return { ...ch, lessons };
      }
      return ch;
    }),
  }));
}

/** Flat list of chapter paths in reading order. */
function chapterPaths(tree: Tree): ChapterPath[] {
  return tree.flatMap((s, si) => s.chapters.map((_, ci) => ({ s: si, c: ci })));
}

/** Keyboard "move up/down" for a lesson: within its chapter, then across chapter boundaries. */
export function stepLesson(tree: Tree, p: LessonPath, dir: -1 | 1): { tree: Tree; path: LessonPath } {
  const ch = tree[p.s]?.chapters[p.c];
  if (!ch) return { tree, path: p };
  const target = p.l + dir;
  if (target >= 0 && target < ch.lessons.length) {
    const path = { ...p, l: target };
    return { tree: moveLesson(tree, p, path), path };
  }
  const all = chapterPaths(tree);
  const idx = all.findIndex((x) => x.s === p.s && x.c === p.c);
  const neighbour = all[idx + dir];
  if (!neighbour) return { tree, path: p };
  const len = tree[neighbour.s]!.chapters[neighbour.c]!.lessons.length;
  const path = { ...neighbour, l: dir === -1 ? len : 0 };
  return { tree: moveLesson(tree, p, path), path };
}

/** Keyboard move for a chapter: within its section, then into the previous/next section. */
export function stepChapter(tree: Tree, p: ChapterPath, dir: -1 | 1): { tree: Tree; path: ChapterPath } {
  const sec = tree[p.s];
  if (!sec) return { tree, path: p };
  const target = p.c + dir;
  if (target >= 0 && target < sec.chapters.length) {
    const path = { s: p.s, c: target };
    return { tree: moveChapter(tree, p, path), path };
  }
  const ns = p.s + dir;
  if (ns < 0 || ns >= tree.length) return { tree, path: p };
  const path = { s: ns, c: dir === -1 ? tree[ns]!.chapters.length : 0 };
  return { tree: moveChapter(tree, p, path), path };
}

// ---------------------------------------------------------------- edits

export function updateLesson(tree: Tree, key: string, patch: Partial<LessonDraft>): Tree {
  return tree.map((s) => ({ ...s, chapters: s.chapters.map((ch) => (ch.lessons.some((l) => l.key === key) ? { ...ch, lessons: ch.lessons.map((l) => (l.key === key ? { ...l, ...patch } : l)) } : ch)) }));
}

export function findLesson(tree: Tree, key: string): LessonPath | null {
  for (const [s, sec] of tree.entries())
    for (const [c, ch] of sec.chapters.entries()) {
      const l = ch.lessons.findIndex((x) => x.key === key);
      if (l >= 0) return { s, c, l };
    }
  return null;
}

export const countLessons = (tree: Tree) => tree.reduce((a, s) => a + s.chapters.reduce((b, ch) => b + ch.lessons.length, 0), 0);
export const totalDuration = (tree: Tree) => tree.reduce((a, s) => a + s.chapters.reduce((b, ch) => b + ch.lessons.reduce((c, l) => c + (l.durationSec || l.video?.durationSec || 0), 0), 0), 0);

// ---------------------------------------------------------------- merging a save response

export type ServerMatch = { id: string; lesson?: ApiLesson };

/** Positional match between the tree that was sent and the tree the server returned → key ⇒ server node. */
export function matchServer(sent: Tree, res: ApiSection[]): Map<string, ServerMatch> {
  const map = new Map<string, ServerMatch>();
  sent.forEach((s, si) => {
    const rs = res[si];
    if (!rs) return;
    map.set(s.key, { id: rs.id });
    s.chapters.forEach((ch, ci) => {
      const rc = rs.chapters[ci];
      if (!rc) return;
      map.set(ch.key, { id: rc.id });
      ch.lessons.forEach((l, li) => {
        const rl = rc.lessons[li];
        if (rl) map.set(l.key, { id: rl.id, lesson: rl });
      });
    });
  });
  return map;
}

/** Give unsaved nodes their new ids and refresh read-only media info, without touching edits made meanwhile. */
export function applyServer(tree: Tree, map: Map<string, ServerMatch>): Tree {
  return tree.map((s) => ({
    ...s,
    id: s.id ?? map.get(s.key)?.id,
    chapters: s.chapters.map((ch) => ({
      ...ch,
      id: ch.id ?? map.get(ch.key)?.id,
      lessons: ch.lessons.map((l) => {
        const m = map.get(l.key);
        if (!m) return l;
        const r = m.lesson;
        return {
          ...l,
          id: l.id ?? m.id,
          video: r && r.videoAssetId === l.videoAssetId ? r.video : l.video,
          document: r && r.documentId === l.documentId ? r.document : l.document,
          durationSec: !l.durationSec && r && r.videoAssetId === l.videoAssetId ? r.durationSec : l.durationSec,
        };
      }),
    })),
  }));
}
