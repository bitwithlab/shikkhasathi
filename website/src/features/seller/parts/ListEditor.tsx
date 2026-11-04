import { useRef } from 'react';
import { Plus, X } from './icons';
import { Input } from '@/components';
import { localDigits, useT } from '@/lib/i18n';

/** Editable bullet list ("যা শিখবেন", "কাদের জন্য নয়"). Enter adds the next row. */
export function ListEditor({ values, onChange, max, placeholder, addLabel, label, marker }: { values: string[]; onChange: (v: string[]) => void; max: number; placeholder: string; addLabel: string; label: string; marker?: (i: number) => React.ReactNode }) {
  const t = useT();
  const refs = useRef<(HTMLInputElement | null)[]>([]);
  const focus = (i: number) => setTimeout(() => refs.current[i]?.focus(), 0);
  const add = (at = values.length) => {
    if (values.length >= max) return;
    onChange([...values.slice(0, at), '', ...values.slice(at)]);
    focus(at);
  };
  return (
    <div>
      <ul className="flex flex-col gap-2" aria-label={label}>
        {values.map((v, i) => (
          <li key={i} className="flex items-center gap-2">
            <span className="latin flex w-6 shrink-0 justify-center text-sm font-semibold text-muted tnum" aria-hidden>
              {marker ? marker(i) : localDigits(i + 1, t.locale)}
            </span>
            <Input
              ref={(el) => {
                refs.current[i] = el;
              }}
              value={v}
              maxLength={200}
              placeholder={i === 0 ? placeholder : undefined}
              aria-label={`${label} ${localDigits(i + 1, t.locale)}`}
              onChange={(e) => onChange(values.map((x, j) => (j === i ? e.target.value : x)))}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  add(i + 1);
                } else if (e.key === 'Backspace' && !v && values.length > 1) {
                  e.preventDefault();
                  onChange(values.filter((_, j) => j !== i));
                  focus(Math.max(0, i - 1));
                }
              }}
            />
            <button type="button" onClick={() => onChange(values.length > 1 ? values.filter((_, j) => j !== i) : [''])} className="flex size-11 shrink-0 items-center justify-center rounded-control text-muted hover:bg-surface-2 hover:text-text" aria-label={t('seller.list.remove', { n: localDigits(i + 1, t.locale) })}>
              <X className="size-4" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
      {values.length < max && (
        <button type="button" onClick={() => add()} className="mt-2 inline-flex h-11 items-center gap-1.5 rounded-control px-2 text-sm font-semibold text-primary-fg hover:bg-primary-soft">
          <Plus className="size-4" aria-hidden /> {addLabel}
        </button>
      )}
    </div>
  );
}
