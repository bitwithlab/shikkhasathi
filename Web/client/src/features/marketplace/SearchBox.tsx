import './landing/i18n';
import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { BookOpen, Building2, Clock, Hash, Search, UserRound, X, type LucideIcon } from 'lucide-react';
import { api, contracts } from '@/lib/api';
import { useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

const RECENT_KEY = 'ss-recent-searches';
const ICON: Record<string, LucideIcon> = { institution: Building2, subject: BookOpen, code: Hash, teacher: UserRound, course: Search };

function readRecent(): string[] {
  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]') as string[];
  } catch {
    return [];
  }
}
export function pushRecent(q: string) {
  if (!q.trim()) return;
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify([q.trim(), ...readRecent().filter((x) => x !== q.trim())].slice(0, 6)));
  } catch {
    /* ignore */
  }
}

/** Smart search box: one input for university, subject, course code or teacher; grouped autocomplete (WAI-ARIA combobox). */
export function SearchBox({ initial = '', onSearch, size = 'lg', autoFocus }: { initial?: string; onSearch?: (q: string) => void; size?: 'md' | 'lg'; autoFocus?: boolean }) {
  const t = useT();
  const nav = useNavigate();
  const id = useId();
  const [q, setQ] = useState(initial);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [debounced, setDebounced] = useState('');
  const wrap = useRef<HTMLDivElement>(null);
  useEffect(() => setQ(initial), [initial]);
  useEffect(() => {
    const h = setTimeout(() => setDebounced(q.trim()), 180);
    return () => clearTimeout(h);
  }, [q]);
  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);
  const s = useQuery({ queryKey: ['suggest', debounced], queryFn: () => api(contracts.discovery.suggest, { query: { q: debounced } }), enabled: debounced.length >= 2, staleTime: 60_000, placeholderData: (p) => p });
  const recent = useMemo(() => (open && !q ? readRecent() : []), [open, q]);
  const options = q.trim().length >= 2 ? (s.data?.items ?? []) : recent.map((r) => ({ type: 'recent' as const, id: r, label: r, sub: null, href: '' }));

  const go = (query: string) => {
    pushRecent(query);
    setOpen(false);
    if (onSearch) onSearch(query);
    else nav(query ? `/explore?q=${encodeURIComponent(query)}` : '/explore');
  };
  const pick = (o: (typeof options)[number]) => {
    if (o.type === 'recent') return go(o.label);
    pushRecent(o.label);
    setOpen(false);
    nav(o.href);
  };
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (active >= 0 && options[active]) pick(options[active]!);
    else go(q.trim());
  };
  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setOpen(true);
      setActive((a) => Math.min(options.length - 1, a + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => Math.max(-1, a - 1));
    } else if (e.key === 'Escape') setOpen(false);
  };
  const groups = ['institution', 'subject', 'code', 'teacher', 'course', 'recent'] as const;
  let idx = -1;

  return (
    <div ref={wrap} className="relative w-full">
      <form onSubmit={submit} role="search" className={cn('flex items-center gap-2 rounded-[14px] border bg-surface p-1.5 shadow-e1 focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/15', open ? 'border-primary' : 'border-border')}>
        <Search className="ml-2.5 size-5 shrink-0 text-muted" aria-hidden />
        <input
          role="combobox"
          aria-expanded={open && options.length > 0}
          aria-controls={`${id}-list`}
          aria-activedescendant={active >= 0 ? `${id}-o${active}` : undefined}
          aria-autocomplete="list"
          aria-label={t('landing.hero.searchLabel')}
          autoFocus={autoFocus}
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setOpen(true);
            setActive(-1);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKey}
          placeholder={t('landing.hero.searchPlaceholder')}
          className={cn('min-w-0 flex-1 bg-transparent placeholder:text-muted focus:outline-none', size === 'lg' ? 'h-11 text-[16px]' : 'h-9 text-[15px]')}
        />
        {q && (
          <button type="button" onClick={() => { setQ(''); setOpen(true); }} className="inline-flex size-9 items-center justify-center rounded-full text-muted hover:bg-surface-2" aria-label={t('search.clear')}>
            <X className="size-4" aria-hidden />
          </button>
        )}
        <button type="submit" className={cn('inline-flex shrink-0 items-center gap-2 rounded-control bg-primary px-4 font-semibold text-on-primary hover:bg-primary-strong', size === 'lg' ? 'h-11' : 'h-9 text-sm')}>
          <Search className="size-4 sm:hidden" aria-hidden />
          <span className="hidden sm:inline">{t('common.search')}</span>
        </button>
      </form>
      {open && options.length > 0 && (
        <div id={`${id}-list`} role="listbox" className="absolute inset-x-0 top-full z-40 mt-2 max-h-[70vh] overflow-y-auto rounded-card border border-border bg-surface p-2 shadow-e3">
          {groups.map((g) => {
            const list = options.filter((o) => o.type === g);
            if (!list.length) return null;
            return (
              <div key={g} role="group" aria-label={t(`search.group.${g}`)} className="py-1">
                <p className="px-2 pb-1 text-xs font-semibold text-muted">{t(`search.group.${g}`)}</p>
                {list.map((o) => {
                  idx++;
                  const i = idx;
                  const Icon = g === 'recent' ? Clock : (ICON[g] ?? Search);
                  return (
                    <div
                      key={`${g}-${o.id}`}
                      id={`${id}-o${i}`}
                      role="option"
                      aria-selected={active === i}
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => pick(o)}
                      onMouseEnter={() => setActive(i)}
                      className={cn('flex cursor-pointer items-center gap-3 rounded-control px-2 py-2', active === i ? 'bg-primary-soft' : 'hover:bg-surface-2')}
                    >
                      <Icon className="size-4 shrink-0 text-muted" aria-hidden />
                      <span className="latin min-w-0 flex-1 truncate text-[15px]">{o.label}</span>
                      {o.sub && <span className="latin shrink-0 truncate text-xs text-muted">{o.sub}</span>}
                    </div>
                  );
                })}
              </div>
            );
          })}
          {q.trim().length >= 2 && (
            <button type="button" onClick={() => go(q.trim())} className="mt-1 w-full rounded-control border-t border-border px-2 py-2 text-left text-sm font-semibold text-primary-fg hover:bg-surface-2">
              {t('search.seeAllFor', { q: q.trim() })}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
