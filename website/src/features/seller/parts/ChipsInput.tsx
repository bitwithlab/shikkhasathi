import { useState } from 'react';
import { Plus, X } from './icons';
import { Input } from '@/components';
import { useT } from '@/lib/i18n';

/** Add/remove short tokens (batch, section). Enter or comma adds. */
export function ChipsInput({ id, values, onChange, placeholder, max = 20, maxLen = 12, label, suggestions = [] }: { id?: string; values: string[]; onChange: (v: string[]) => void; placeholder?: string; max?: number; maxLen?: number; label: string; suggestions?: string[] }) {
  const t = useT();
  const [draft, setDraft] = useState('');
  const add = (raw: string) => {
    const v = raw.trim().slice(0, maxLen);
    if (!v || values.includes(v) || values.length >= max) return;
    onChange([...values, v]);
    setDraft('');
  };
  const left = suggestions.filter((s) => !values.includes(s));
  return (
    <div>
      <div className="flex gap-2">
        <Input
          id={id}
          value={draft}
          placeholder={placeholder}
          className="latin"
          onChange={(e) => {
            const v = e.target.value;
            if (v.endsWith(',')) add(v.slice(0, -1));
            else setDraft(v);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              add(draft);
            } else if (e.key === 'Backspace' && !draft && values.length) onChange(values.slice(0, -1));
          }}
        />
        <button type="button" onClick={() => add(draft)} disabled={!draft.trim()} className="flex size-12 shrink-0 items-center justify-center rounded-control border border-border bg-surface text-text hover:border-border-strong disabled:opacity-50" aria-label={t('seller.chips.add', { label })}>
          <Plus className="size-5" aria-hidden />
        </button>
      </div>
      {(values.length > 0 || left.length > 0) && (
        <ul className="mt-2 flex flex-wrap gap-1.5" aria-label={label}>
          {values.map((v) => (
            <li key={v} className="latin inline-flex h-9 items-center gap-1 rounded-full border border-primary/30 bg-primary-soft pl-3 pr-1 text-sm font-semibold text-primary-fg">
              {v}
              <button type="button" onClick={() => onChange(values.filter((x) => x !== v))} className="flex size-7 items-center justify-center rounded-full hover:bg-primary/15" aria-label={t('seller.chips.remove', { v })}>
                <X className="size-3.5" aria-hidden />
              </button>
            </li>
          ))}
          {left.slice(0, 6).map((s) => (
            <li key={`s-${s}`}>
              <button type="button" onClick={() => add(s)} className="latin inline-flex h-9 items-center gap-1 rounded-full border border-dashed border-border-strong px-3 text-sm text-muted hover:border-primary hover:text-primary-fg">
                <Plus className="size-3.5" aria-hidden />
                {s}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
