import { useRef, type ClipboardEvent, type KeyboardEvent } from 'react';
import { cn } from '@/lib/cn';
import { useT } from '@/lib/i18n';

/** 6 boxes, paste-friendly, SMS autofill (autocomplete=one-time-code on the first box). */
export function OtpInput({ value, onChange, disabled, invalid, onComplete }: { value: string; onChange: (v: string) => void; disabled?: boolean; invalid?: boolean; onComplete?: (v: string) => void }) {
  const t = useT();
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  const digits = Array.from({ length: 6 }, (_, i) => value[i] ?? '');

  const set = (next: string) => {
    const clean = next.replace(/\D/g, '').slice(0, 6);
    onChange(clean);
    if (clean.length === 6) onComplete?.(clean);
  };

  const onKey = (i: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[i] && i > 0) refs.current[i - 1]?.focus();
    if (e.key === 'ArrowLeft' && i > 0) refs.current[i - 1]?.focus();
    if (e.key === 'ArrowRight' && i < 5) refs.current[i + 1]?.focus();
  };
  const onPaste = (e: ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const p = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    set(p);
    refs.current[Math.min(p.length, 5)]?.focus();
  };

  return (
    <div className="flex justify-between gap-2" role="group" aria-label={t('auth.otpLabel')}>
      {digits.map((d, i) => (
        <input
          key={i}
          ref={(el) => {
            refs.current[i] = el;
          }}
          value={d}
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={i === 0 ? 6 : 1}
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          aria-label={t('auth.otpDigit', { n: i + 1 })}
          aria-invalid={invalid || undefined}
          disabled={disabled}
          onPaste={onPaste}
          onKeyDown={(e) => onKey(i, e)}
          onChange={(e) => {
            const v = e.target.value.replace(/\D/g, '');
            if (v.length > 1) {
              set(v);
              refs.current[Math.min(v.length, 5)]?.focus();
              return;
            }
            const arr = digits.slice();
            arr[i] = v;
            set(arr.join(''));
            if (v && i < 5) refs.current[i + 1]?.focus();
          }}
          className={cn(
            'latin tnum h-14 w-full min-w-0 max-w-14 rounded-control border bg-surface text-center text-2xl font-semibold text-text transition-[border-color,box-shadow] focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/15',
            invalid ? 'border-danger' : d ? 'border-primary/60' : 'border-border',
          )}
        />
      ))}
    </div>
  );
}
