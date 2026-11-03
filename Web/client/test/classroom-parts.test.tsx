import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { Assignment } from '@shikkhasathi/shared';
import { AssignmentRow, WeekStrip, dueLabel, urgency } from '@/features/student/classroom/parts';

const base: Assignment = {
  id: 'cw-2', courseId: 'gc', courseName: 'CSE-2204 Data Structures Lab', courseColor: '#0EA5E9', title: 'Lab 2: BST Deletion Code', description: null, workType: 'ASSIGNMENT',
  dueAt: new Date(Date.now() + 5 * 3_600_000).toISOString(), maxPoints: 10, status: 'pending', late: false, grade: null, materials: [], alternateLink: null, postedAt: null,
};
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient()}><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>);

describe('Classroom parts', () => {
  it('urgency: <24h danger, <72h warning, missing danger, turned in success', () => {
    expect(urgency(base)).toBe('danger');
    expect(urgency({ ...base, dueAt: new Date(Date.now() + 48 * 3_600_000).toISOString() })).toBe('warning');
    expect(urgency({ ...base, dueAt: new Date(Date.now() + 100 * 3_600_000).toISOString() })).toBe('neutral');
    expect(urgency({ ...base, status: 'missing' })).toBe('danger');
    expect(urgency({ ...base, status: 'turned_in' })).toBe('success');
  });
  it('dueLabel says আজ / আগামীকাল in Bangla', () => {
    const now = new Date();
    expect(dueLabel(now.toISOString(), 'bn')).toMatch(/^আজ /);
    expect(dueLabel(new Date(now.getTime() + 86_400_000).toISOString(), 'bn')).toMatch(/^আগামীকাল /);
    expect(dueLabel(null, 'bn')).toBeNull();
  });
  it('AssignmentRow shows status chip, time left, opens on click', async () => {
    const onOpen = vi.fn();
    wrap(<AssignmentRow a={base} onOpen={onOpen} />);
    expect(screen.getByText('Pending')).toBeInTheDocument();
    expect(screen.getByText(/আর . ঘণ্টা|আর .+ ঘণ্টা/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button'));
    expect(onOpen).toHaveBeenCalledWith(base);
  });
  it('WeekStrip exposes accessible day summaries', () => {
    const d = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Dhaka' }).format(new Date());
    wrap(<WeekStrip days={[{ date: d, deadlines: 2, exams: 1 }]} />);
    expect(screen.getByRole('button', { name: /২টি ডেডলাইন, ১টি পরীক্ষা/ })).toBeInTheDocument();
  });
});
