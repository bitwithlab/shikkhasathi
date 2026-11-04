import '@/features/seller/parts/i18n';
import '@/features/seller/courses/i18n';
import { useState } from 'react';
import { act, render, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ChipsInput } from '@/features/seller/parts/ChipsInput';
import { ListEditor } from '@/features/seller/parts/ListEditor';
import { StatusBadge } from '@/features/seller/parts/StatusBadge';
import { AutosaveIndicator } from '@/features/seller/courses/wizard/Chrome';
import { useSaver } from '@/features/seller/courses/wizard/useSaver';

function Chips() {
  const [v, setV] = useState<string[]>(['56']);
  return <ChipsInput label="ব্যাচ" values={v} onChange={setV} placeholder="56" />;
}
function List() {
  const [v, setV] = useState<string[]>(['']);
  return (
    <>
      <ListEditor label="যা শিখবেন" values={v} onChange={setV} max={3} placeholder="…" addLabel="পয়েন্ট যোগ করুন" />
      <output data-testid="out">{JSON.stringify(v)}</output>
    </>
  );
}

describe('seller parts', () => {
  it('ChipsInput adds on Enter/comma, ignores duplicates, removes via labelled button', async () => {
    const u = userEvent.setup();
    render(<Chips />);
    const input = screen.getByPlaceholderText('56');
    await u.type(input, '57{Enter}58,56{Enter}');
    const list = screen.getByRole('list', { name: 'ব্যাচ' });
    expect(list).toHaveTextContent('565758');
    await u.click(screen.getByRole('button', { name: '57 সরান' }));
    expect(list).not.toHaveTextContent('57');
  });

  it('ListEditor: Enter adds the next row, respects max, empty Backspace removes', async () => {
    const u = userEvent.setup();
    render(<List />);
    await u.type(screen.getByLabelText('যা শিখবেন ১'), 'Stack{Enter}Queue{Enter}Tree');
    expect(screen.getByTestId('out')).toHaveTextContent('["Stack","Queue","Tree"]');
    expect(screen.queryByRole('button', { name: /পয়েন্ট যোগ করুন/ })).toBeNull();
    await u.clear(screen.getByLabelText('যা শিখবেন ৩'));
    await u.type(screen.getByLabelText('যা শিখবেন ৩'), '{Backspace}');
    expect(screen.getByTestId('out')).toHaveTextContent('["Stack","Queue"]');
  });

  it('StatusBadge and autosave indicator speak Bangla', () => {
    render(
      <>
        <StatusBadge status="changes_requested" />
        <AutosaveIndicator status="saving" onRetry={() => undefined} />
      </>,
    );
    expect(screen.getByText('পরিবর্তন দরকার')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('সেভ হচ্ছে…');
  });
});

describe('useSaver', () => {
  it('debounces, never overlaps, and runs exactly one follow-up for edits made in flight', async () => {
    vi.useFakeTimers();
    let resolve: () => void = () => undefined;
    const send = vi.fn(() => new Promise<void>((r) => (resolve = r)));
    const { result } = renderHook(() => useSaver(send, 800));
    act(() => {
      result.current.schedule();
      result.current.schedule();
    });
    expect(result.current.status).toBe('pending');
    await act(async () => void vi.advanceTimersByTime(799));
    expect(send).not.toHaveBeenCalled();
    await act(async () => void vi.advanceTimersByTime(1));
    expect(send).toHaveBeenCalledTimes(1);
    expect(result.current.status).toBe('saving');
    // two edits while saving → one follow-up after the first finishes
    act(() => {
      result.current.schedule();
      result.current.schedule();
    });
    await act(async () => void vi.advanceTimersByTime(800));
    expect(send).toHaveBeenCalledTimes(1);
    await act(async () => resolve());
    expect(send).toHaveBeenCalledTimes(2);
    await act(async () => resolve());
    expect(result.current.status).toBe('saved');
    vi.useRealTimers();
  });

  it('surfaces errors and retries', async () => {
    const send = vi.fn().mockRejectedValueOnce(new Error('x')).mockResolvedValue(undefined);
    const { result } = renderHook(() => useSaver(send, 10));
    act(() => result.current.schedule());
    await act(async () => result.current.flush());
    expect(result.current.status).toBe('error');
    await act(async () => {
      result.current.retry();
      await result.current.flush();
    });
    expect(result.current.status).toBe('saved');
  });
});
