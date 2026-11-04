import '@/features/seller/courses/i18n';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MockAuthoringScreen, { draftFrom, emptyQuestion, mockBody } from '@/features/seller/courses/screens/MockAuthoringScreen';
import { json, mockApi, renderAt, uuid } from './seller-ops-helpers';

window.scrollTo = () => undefined;
afterEach(() => vi.restoreAllMocks());
const CID = uuid(1);
const courseDetail = { id: CID, title: 'Data Structures Midterm', sections: [{ id: uuid(2), title: 'Midterm', chapters: [{ id: uuid(3), title: 'Linked List', lessons: [] }] }] };

describe('mockBody', () => {
  it('needs exactly one correct among filled options and drops blank rows', () => {
    const d = draftFrom(null);
    d.title = 'Midterm Mock 1';
    const q = d.questions[0]!;
    q.body = 'Stack কোন নীতিতে চলে?';
    q.options[0]!.body = 'LIFO';
    q.options[1]!.body = 'FIFO';
    expect(mockBody(d).errors.q[q.key]).toBe('ঠিক একটি সঠিক উত্তর বাছাই করুন');
    q.options[2]!.isCorrect = true;
    expect(mockBody(d).errors.q[q.key]).toBe('সঠিক হিসেবে বাছাই করা অপশনটি ফাঁকা');
    q.options[2]!.isCorrect = false;
    q.options[0]!.isCorrect = true;
    const r = mockBody(d);
    expect(r.body).toEqual({ title: 'Midterm Mock 1', description: null, durationSec: 1800, questions: [{ body: 'Stack কোন নীতিতে চলে?', explanation: null, chapterTag: null, options: [{ body: 'LIFO', isCorrect: true }, { body: 'FIFO', isCorrect: false }] }] });
  });
  it('checks title, duration and question text', () => {
    const d = { ...draftFrom(null), durationMin: '৩', questions: [emptyQuestion()] };
    const e = mockBody(d).errors;
    expect(e.title).toBeTruthy();
    expect(e.duration).toBe('৫ থেকে ২৪০ মিনিটের মধ্যে দিন');
    expect(e.q[d.questions[0]!.key]).toBe('প্রশ্নটি লিখুন (অন্তত ৩ অক্ষর)');
  });
});

describe('Mock authoring screen', () => {
  it('creates a mock test through the editor', async () => {
    const calls = mockApi((url, method, body) => {
      if (url.endsWith(`/seller/courses/${CID}`)) return json(courseDetail);
      if (url.endsWith('/mock-tests') && method === 'POST') return json({ id: uuid(90), ...(body as object), description: null, attempts: 0, questions: [] }, 201);
      if (url.endsWith('/mock-tests')) return json({ items: [] });
      return undefined;
    });
    const u = userEvent.setup();
    renderAt(`/teach/courses/${CID}/edit?tool=exam`, '/teach/courses/:id/edit', <MockAuthoringScreen courseId={CID} />);
    await u.click(await screen.findByRole('button', { name: 'প্রথম মক টেস্ট বানান' }));
    await u.type(screen.getByLabelText('টেস্টের নাম'), 'Linked List Mock');
    await u.clear(screen.getByLabelText('সময় (মিনিট)'));
    await u.type(screen.getByLabelText('সময় (মিনিট)'), '20');
    await u.type(screen.getByLabelText('প্রশ্ন'), 'Head-এ insert-এর complexity?');
    await u.type(screen.getByLabelText('প্রশ্ন ১, অপশন A'), 'O(n)');
    await u.type(screen.getByLabelText('প্রশ্ন ১, অপশন B'), 'O(1)');
    // submit without a correct answer → inline error, nothing sent
    await u.click(screen.getByRole('button', { name: 'মক টেস্ট তৈরি করুন' }));
    expect(await screen.findByText('ঠিক একটি সঠিক উত্তর বাছাই করুন')).toBeInTheDocument();
    await u.click(screen.getByRole('radio', { name: 'প্রশ্ন ১: B সঠিক উত্তর' }));
    await u.type(screen.getByLabelText(/চ্যাপ্টার ট্যাগ/), 'Linked List');
    await u.click(screen.getByRole('button', { name: 'মক টেস্ট তৈরি করুন' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ title: 'Linked List Mock', description: null, durationSec: 1200, questions: [{ body: 'Head-এ insert-এর complexity?', explanation: null, chapterTag: 'Linked List', options: [{ body: 'O(n)', isCorrect: false }, { body: 'O(1)', isCorrect: true }] }] }));
    expect(calls.find((c) => c.method === 'POST')?.url).toContain(`/seller/courses/${CID}/mock-tests`);
  });

  it('edits flashcards and saves the whole set', async () => {
    const calls = mockApi((url, method, body) => {
      if (url.endsWith(`/seller/courses/${CID}`)) return json(courseDetail);
      if (url.endsWith('/mock-tests')) return json({ items: [] });
      if (url.endsWith('/flashcards') && method === 'PUT') return json({ items: (body as { cards: object[] }).cards.map((c, i) => ({ id: uuid(100 + i), ...c })) });
      if (url.endsWith('/flashcards')) return json({ items: [{ id: uuid(99), front: 'BST inorder', back: 'Sorted order' }] });
      return undefined;
    });
    const u = userEvent.setup();
    renderAt(`/teach/courses/${CID}/edit?tool=exam`, '/teach/courses/:id/edit', <MockAuthoringScreen courseId={CID} />);
    await u.click(await screen.findByRole('tab', { name: 'ফ্ল্যাশকার্ড' }));
    expect(await screen.findByDisplayValue('BST inorder')).toBeInTheDocument();
    const save = screen.getByRole('button', { name: 'ফ্ল্যাশকার্ড সেভ করুন' });
    expect(save).toBeDisabled();
    await u.click(screen.getByRole('button', { name: 'কার্ড যোগ করুন' }));
    await u.type(screen.getByLabelText('কার্ড ২ — সামনে'), 'Queue');
    await u.click(save);
    expect(await screen.findByText('দুই পাশই লিখুন, অথবা কার্ডটি মুছে দিন')).toBeInTheDocument();
    await u.type(screen.getByLabelText('কার্ড ২ — পেছনে'), 'FIFO');
    await u.click(save);
    await waitFor(() => expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ cards: [{ front: 'BST inorder', back: 'Sorted order' }, { front: 'Queue', back: 'FIFO' }] }));
    const list = await screen.findByRole('list');
    await waitFor(() => expect(within(list).getAllByRole('listitem')).toHaveLength(2));
  });
});
