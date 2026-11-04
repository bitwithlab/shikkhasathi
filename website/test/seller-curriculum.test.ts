import { describe, expect, it } from 'vitest';
import { applyServer, arrayMove, fromApi, matchServer, moveChapter, moveLesson, moveSection, newChapter, newLesson, newSection, stepChapter, stepLesson, toBody, totalDuration, type Tree } from '@/features/seller/lib/curriculum';

const L = (title: string) => ({ ...newLesson(title), key: title });
function tree(): Tree {
  return [
    { ...newSection('S1'), key: 'S1', id: 'sid1', chapters: [{ ...newChapter('C1'), key: 'C1', id: 'cid1', lessons: [L('a'), L('b'), L('c')] }, { ...newChapter('C2'), key: 'C2', lessons: [L('d')] }] },
    { ...newSection('S2'), key: 'S2', chapters: [{ ...newChapter('C3'), key: 'C3', lessons: [] }] },
  ];
}
const titles = (t: Tree) => t.map((s) => `${s.title}[${s.chapters.map((c) => `${c.title}(${c.lessons.map((l) => l.title).join('')})`).join(' ')}]`).join(' ');

describe('curriculum moves', () => {
  it('arrayMove removes then inserts (drop-on-item semantics)', () => {
    expect(arrayMove(['a', 'b', 'c', 'd'], 0, 2)).toEqual(['b', 'c', 'a', 'd']);
    expect(arrayMove(['a', 'b', 'c', 'd'], 3, 0)).toEqual(['d', 'a', 'b', 'c']);
    expect(arrayMove(['a', 'b'], 0, 99)).toEqual(['b', 'a']);
  });
  it('moves sections, chapters across sections, lessons across chapters', () => {
    expect(titles(moveSection(tree(), 1, 0))).toBe('S2[C3()] S1[C1(abc) C2(d)]');
    expect(titles(moveChapter(tree(), { s: 0, c: 1 }, { s: 1, c: 0 }))).toBe('S1[C1(abc)] S2[C2(d) C3()]');
    expect(titles(moveLesson(tree(), { s: 0, c: 0, l: 0 }, { s: 0, c: 0, l: 2 }))).toBe('S1[C1(bca) C2(d)] S2[C3()]');
    expect(titles(moveLesson(tree(), { s: 0, c: 0, l: 1 }, { s: 1, c: 0, l: 0 }))).toBe('S1[C1(ac) C2(d)] S2[C3(b)]');
  });
  it('does not mutate the input', () => {
    const t = tree();
    const before = JSON.stringify(t);
    moveLesson(t, { s: 0, c: 0, l: 0 }, { s: 0, c: 1, l: 0 });
    expect(JSON.stringify(t)).toBe(before);
  });
  it('keyboard steps cross chapter and section boundaries', () => {
    // last lesson of C1 moves down → first lesson of C2
    let r = stepLesson(tree(), { s: 0, c: 0, l: 2 }, 1);
    expect(titles(r.tree)).toBe('S1[C1(ab) C2(cd)] S2[C3()]');
    expect(r.path).toEqual({ s: 0, c: 1, l: 0 });
    // the only lesson of C2 moves down → into empty C3 in the next section
    r = stepLesson(tree(), { s: 0, c: 1, l: 0 }, 1);
    expect(titles(r.tree)).toBe('S1[C1(abc) C2()] S2[C3(d)]');
    // first lesson overall can't move up
    expect(stepLesson(tree(), { s: 0, c: 0, l: 0 }, -1).tree).toEqual(tree());
    // chapter at top of S2 moves up → end of S1
    const c = stepChapter(tree(), { s: 1, c: 0 }, -1);
    expect(titles(c.tree)).toBe('S1[C1(abc) C2(d) C3()] S2[]');
    expect(c.path).toEqual({ s: 0, c: 2 });
  });
});

describe('save body & merging the response', () => {
  it('sends existing ids, strips keys and read-only media, nulls fields of other types, falls back on empty titles', () => {
    const t = tree();
    t[0]!.chapters[0]!.lessons[0] = { ...t[0]!.chapters[0]!.lessons[0]!, id: 'lid', title: '  ', type: 'text', body: 'hi', videoAssetId: 'v1', video: { id: 'v1', provider: 'fake', status: 'ready', durationSec: 600, title: null, externalUrl: null } };
    const b = toBody(t, 'Untitled');
    const l = b.sections[0]!.chapters[0]!.lessons[0]!;
    expect(b.sections[0]).toHaveProperty('id', 'sid1');
    expect(b.sections[1]).not.toHaveProperty('id');
    expect(l).toEqual({ id: 'lid', title: 'Untitled', type: 'text', durationSec: 0, videoAssetId: null, documentId: null, body: 'hi', isPreview: false, isImportant: false });
    expect(l).not.toHaveProperty('video');
    expect(l).not.toHaveProperty('key');
  });
  it('assigns server ids to unsaved nodes without clobbering edits made during the save', () => {
    const sent = tree();
    sent[0]!.chapters[1]!.lessons[0]!.videoAssetId = 'v9';
    const res = [
      { id: 'sid1', title: 'S1', chapters: [{ id: 'cid1', title: 'C1', lessons: ['l1', 'l2', 'l3'].map((id) => ({ id, title: id, type: 'video' as const, durationSec: 0, videoAssetId: null, documentId: null, body: null, isPreview: false, isImportant: false, video: null, document: null })) }, { id: 'cid2', title: 'C2', lessons: [{ id: 'l4', title: 'd', type: 'video' as const, durationSec: 600, videoAssetId: 'v9', documentId: null, body: null, isPreview: false, isImportant: false, video: { id: 'v9', provider: 'fake', status: 'ready', durationSec: 600, title: null, externalUrl: null }, document: null }] }] },
      { id: 'sid2', title: 'S2', chapters: [{ id: 'cid3', title: 'C3', lessons: [] }] },
    ];
    // meanwhile the teacher renamed S2 and reordered lessons in C1
    let current = moveLesson(sent, { s: 0, c: 0, l: 0 }, { s: 0, c: 0, l: 2 });
    current = current.map((s) => (s.key === 'S2' ? { ...s, title: 'S2 renamed' } : s));
    const merged = applyServer(current, matchServer(sent, res));
    expect(merged[1]).toMatchObject({ id: 'sid2', title: 'S2 renamed' });
    expect(merged[0]!.chapters[0]!.lessons.map((l) => [l.key, l.id])).toEqual([['b', 'l2'], ['c', 'l3'], ['a', 'l1']]);
    expect(merged[0]!.chapters[1]).toMatchObject({ id: 'cid2' });
    expect(merged[0]!.chapters[1]!.lessons[0]).toMatchObject({ id: 'l4', durationSec: 600, video: { status: 'ready' } });
    expect(totalDuration(merged)).toBe(600);
  });
  it('fromApi keeps ids as keys', () => {
    const t = fromApi([{ id: 's', title: 'S', chapters: [{ id: 'c', title: 'C', lessons: [] }] }]);
    expect(t[0]).toMatchObject({ key: 's', id: 's' });
  });
});
