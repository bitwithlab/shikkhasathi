import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Building2, X } from './icons';
import { api, contracts } from '@/lib/api';
import { useT } from '@/lib/i18n';
import { Combobox } from './Combobox';

export interface InstitutionValue {
  id: string;
  label: string;
  name?: string;
}

/** Search-select over approved institutions. Shows the pick as a removable row. */
export function InstitutionPicker({ value, onChange, id, placeholder, ...aria }: { value: InstitutionValue | null; onChange: (v: InstitutionValue | null) => void; id?: string; placeholder?: string; 'aria-invalid'?: boolean; 'aria-describedby'?: string }) {
  const t = useT();
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  useEffect(() => {
    const h = setTimeout(() => setDebounced(q.trim()), 200);
    return () => clearTimeout(h);
  }, [q]);
  const list = useQuery({ queryKey: ['institutions', debounced], queryFn: () => api(contracts.catalog.institutions, { query: { q: debounced || undefined, limit: 20 } }), enabled: !value, placeholderData: (p) => p, staleTime: 5 * 60_000 });

  if (value)
    return (
      <div className="flex min-h-12 items-center gap-3 rounded-control border border-border bg-surface px-3 py-1.5">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-[10px] bg-primary-soft text-primary">
          <Building2 className="size-4" strokeWidth={1.75} aria-hidden />
        </span>
        <span className="min-w-0 flex-1">
          <span className="latin block truncate text-sm font-semibold">{value.label}</span>
          {value.name && <span className="block truncate text-xs text-muted">{value.name}</span>}
        </span>
        <button type="button" onClick={() => onChange(null)} className="-mr-1 flex size-10 items-center justify-center rounded-control text-muted hover:bg-surface-2 hover:text-text" aria-label={t('seller.inst.change')}>
          <X className="size-4" aria-hidden />
        </button>
      </div>
    );

  const items = list.data?.items ?? [];
  return (
    <Combobox
      id={id}
      value={q}
      onValueChange={setQ}
      items={items}
      getKey={(i) => i.id}
      loading={list.isFetching}
      placeholder={placeholder ?? t('seller.inst.search')}
      label={t('seller.inst.search')}
      empty={t('seller.inst.none')}
      onSelect={(i) => {
        onChange({ id: i.id, label: i.shortName, name: t.locale === 'bn' ? (i.nameBn ?? i.name) : i.name });
        setQ('');
      }}
      renderItem={(i) => (
        <>
          <span className="latin w-14 shrink-0 truncate text-xs font-bold">{i.shortName}</span>
          <span className="min-w-0 flex-1 truncate">{t.locale === 'bn' ? (i.nameBn ?? i.name) : i.name}</span>
        </>
      )}
      {...aria}
    />
  );
}
