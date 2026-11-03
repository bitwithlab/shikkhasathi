import { useId, useRef, useState, type ReactNode } from 'react';
import { Search } from './icons';
import { Input, Skeleton } from '@/components';
import { cn } from '@/lib/cn';

interface Props<T> {
  id?: string;
  value: string;
  onValueChange: (v: string) => void;
  items: T[];
  getKey: (item: T) => string;
  renderItem: (item: T) => ReactNode;
  onSelect: (item: T) => void;
  loading?: boolean;
  empty?: ReactNode;
  footer?: ReactNode;
  placeholder?: string;
  label?: string;
  'aria-invalid'?: boolean;
  'aria-describedby'?: string;
  inputClassName?: string;
}

/** Accessible search-as-you-type combobox (ARIA 1.2 pattern): arrows move, Enter picks, Esc closes. */
export function Combobox<T>({ id, value, onValueChange, items, getKey, renderItem, onSelect, loading, empty, footer, placeholder, label, inputClassName, ...aria }: Props<T>) {
  const auto = useId();
  const inputId = id ?? auto;
  const listId = `${inputId}-list`;
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const pick = (item: T) => {
    onSelect(item);
    setOpen(false);
  };

  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3.5 top-1/2 size-[18px] -translate-y-1/2 text-muted" aria-hidden />
      <Input
        id={inputId}
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-label={label}
        aria-activedescendant={open && items[active] ? `${listId}-${getKey(items[active]!)}` : undefined}
        autoComplete="off"
        value={value}
        placeholder={placeholder}
        className={cn('pl-10', inputClassName)}
        onChange={(e) => {
          onValueChange(e.target.value);
          setActive(0);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => {
          blurTimer.current = setTimeout(() => setOpen(false), 120);
        }}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') {
            e.preventDefault();
            setOpen(true);
            setActive((a) => Math.min(items.length - 1, a + 1));
          } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setActive((a) => Math.max(0, a - 1));
          } else if (e.key === 'Enter' && open && items[active]) {
            e.preventDefault();
            pick(items[active]!);
          } else if (e.key === 'Escape') setOpen(false);
        }}
        {...aria}
      />
      {open && (
        <div className="absolute inset-x-0 top-full z-30 mt-1.5 max-h-72 overflow-y-auto rounded-control border border-border bg-surface p-1 shadow-e3" onMouseDown={(e) => e.preventDefault()}>
          <ul id={listId} role="listbox" aria-label={label}>
            {loading && items.length === 0
              ? Array.from({ length: 3 }, (_, i) => (
                  <li key={i} className="p-2">
                    <Skeleton className="h-5 w-3/4" />
                  </li>
                ))
              : items.map((item, i) => (
                  <li
                    key={getKey(item)}
                    id={`${listId}-${getKey(item)}`}
                    role="option"
                    aria-selected={i === active}
                    onMouseEnter={() => setActive(i)}
                    onClick={() => pick(item)}
                    className={cn('flex min-h-11 cursor-pointer items-center gap-3 rounded-[10px] px-3 py-2 text-sm', i === active ? 'bg-primary-soft text-primary-fg' : 'text-text')}
                  >
                    {renderItem(item)}
                  </li>
                ))}
          </ul>
          {!loading && items.length === 0 && empty && <div className="px-3 py-3 text-sm text-muted">{empty}</div>}
          {footer && <div className="border-t border-border pt-1">{footer}</div>}
        </div>
      )}
    </div>
  );
}
