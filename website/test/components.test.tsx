import { describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button, EmptyState, ErrorState, ProgressRing, Badge } from '@/components';
import { OtpInput } from '@/features/auth/OtpInput';

describe('design-system components', () => {
  it('Button shows loading state and is disabled', () => {
    render(<Button loading>সেভ করুন</Button>);
    const b = screen.getByRole('button', { name: 'সেভ করুন' });
    expect(b).toBeDisabled();
    expect(b).toHaveAttribute('aria-busy', 'true');
  });
  it('ErrorState offers retry', async () => {
    const onRetry = vi.fn();
    render(<ErrorState onRetry={onRetry} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'আবার চেষ্টা করুন' }));
    expect(onRetry).toHaveBeenCalled();
  });
  it('EmptyState renders title and action', () => {
    render(<EmptyState title="কোনো কোর্স নেই" action={<button>কোর্স খুঁজুন</button>} />);
    expect(screen.getByText('কোনো কোর্স নেই')).toBeInTheDocument();
  });
  it('ProgressRing exposes an accessible label with Bangla digits', () => {
    render(<ProgressRing value={62} label="প্রস্তুতি ৬২%" />);
    expect(screen.getByRole('img', { name: 'প্রস্তুতি ৬২%' })).toBeInTheDocument();
    expect(screen.getByText('৬২%')).toBeInTheDocument();
  });
  it('Badge tones render', () => {
    render(<Badge tone="danger">Missing</Badge>);
    expect(screen.getByText('Missing')).toHaveClass('text-danger-fg');
  });
});

describe('OtpInput', () => {
  function Harness({ onComplete }: { onComplete: (v: string) => void }) {
    const [v, setV] = useState('');
    return <OtpInput value={v} onChange={setV} onComplete={onComplete} />;
  }
  it('accepts a pasted code and completes', () => {
    const done = vi.fn();
    render(<Harness onComplete={done} />);
    const boxes = screen.getAllByRole('textbox');
    expect(boxes).toHaveLength(6);
    fireEvent.paste(boxes[0]!, { clipboardData: { getData: () => '12 34 56' } });
    expect(done).toHaveBeenCalledWith('123456');
    expect((boxes[5] as HTMLInputElement).value).toBe('6');
  });
  it('typing advances focus', async () => {
    const done = vi.fn();
    render(<Harness onComplete={done} />);
    const boxes = screen.getAllByRole('textbox');
    boxes[0]!.focus();
    await userEvent.keyboard('987654');
    expect(done).toHaveBeenCalledWith('987654');
  });
});
