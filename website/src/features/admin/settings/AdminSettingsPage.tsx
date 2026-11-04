import '../i18n';
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, CreditCard, Database, Mail, MessageSquare, PlayCircle, Search, Send, Settings2, Smartphone, XCircle, KeyRound, Bell } from 'lucide-react';
import { INTEGRATIONS, type IntegrationDef, type IntegrationState } from '@/api';
import { Badge, Button, Card, ErrorState, Field, Input, Select, Skeleton, Switch, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { relativeTime, useT } from '@/lib/i18n';
import { useAuth } from '@/lib/auth';
import { cn } from '@/lib/cn';

const ICONS: Record<string, typeof Mail> = { brevo: Mail, payment: CreditCard, sms: MessageSquare, video: PlayCircle, google: KeyRound, push: Bell, search: Search, business: Settings2 };
const MODE_TONE = { configured: 'success', env: 'primary', fake: 'warning', disabled: 'danger' } as const;

export default function AdminSettingsPage() {
  const t = useT();
  const q = useQuery({ queryKey: ['admin', 'integrations'], queryFn: () => api(contracts.settings.list) });
  const [active, setActive] = useState<IntegrationDef['provider']>('brevo');
  const def = INTEGRATIONS.find((d) => d.provider === active)!;
  const state = q.data?.items.find((i) => i.provider === active);

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <header className="mb-6">
        <h1 className="text-2xl font-bold">{t('admin.settings.title')}</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted">{t('admin.settings.subtitle')}</p>
      </header>
      <div className="grid gap-6 lg:grid-cols-[260px_minmax(0,1fr)]">
        <nav aria-label={t('admin.settings.title')} className="flex gap-1 overflow-x-auto scrollbar-none lg:flex-col">
          {INTEGRATIONS.map((d) => {
            const Icon = ICONS[d.provider] ?? Settings2;
            const s = q.data?.items.find((i) => i.provider === d.provider);
            return (
              <button
                key={d.provider}
                type="button"
                onClick={() => setActive(d.provider)}
                aria-current={active === d.provider ? 'page' : undefined}
                className={cn('flex h-12 shrink-0 items-center gap-3 rounded-control px-3 text-left text-sm font-medium transition-colors', active === d.provider ? 'bg-primary-soft text-primary-fg' : 'text-muted hover:bg-surface-2 hover:text-text')}
              >
                <Icon className="size-[18px] shrink-0" strokeWidth={1.75} aria-hidden />
                <span className="flex-1 whitespace-nowrap">{d.title}</span>
                {s && <span className={cn('size-2 rounded-full', s.mode === 'configured' ? 'bg-success' : s.mode === 'env' ? 'bg-primary' : s.mode === 'fake' ? 'bg-warning' : 'bg-danger')} aria-label={t(`admin.settings.mode.${s.mode}`)} />}
              </button>
            );
          })}
          <div className="mt-4 hidden rounded-card border border-dashed border-border-strong p-3 text-xs text-muted lg:block">
            <Database className="mb-1.5 size-4" aria-hidden />
            {t('admin.settings.dbNote')}
          </div>
        </nav>
        <div>
          {q.isLoading ? (
            <Card className="p-6">
              <Skeleton className="h-6 w-48" />
              <div className="mt-6 grid gap-4 md:grid-cols-2">
                {Array.from({ length: 6 }, (_, i) => (
                  <Skeleton key={i} className="h-16" />
                ))}
              </div>
            </Card>
          ) : q.isError || !state ? (
            <Card>
              <ErrorState onRetry={() => void q.refetch()} />
            </Card>
          ) : (
            <IntegrationForm key={active} def={def} state={state} />
          )}
        </div>
      </div>
    </div>
  );
}

function IntegrationForm({ def, state }: { def: IntegrationDef; state: IntegrationState }) {
  const t = useT();
  const qc = useQueryClient();
  const admin = useAuth((s) => s.user);
  const initial = useMemo(() => Object.fromEntries(state.fields.map((f) => [f.key, f.isSecret ? '' : (f.value ?? '')])), [state]);
  const [values, setValues] = useState<Record<string, string>>(initial);
  const [testTo, setTestTo] = useState(admin?.email ?? '');
  const dirty = Object.keys(values).filter((k) => values[k] !== initial[k]);

  const save = useMutation({
    mutationFn: (payload: Record<string, string | null>) => api(contracts.settings.update, { params: { provider: def.provider }, body: { values: payload } }),
    onSuccess: (s) => {
      qc.setQueryData(['admin', 'integrations'], (old: { items: IntegrationState[] } | undefined) => old && { items: old.items.map((i) => (i.provider === s.provider ? s : i)) });
      toast.success(t('common.saved'));
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  const test = useMutation({
    mutationFn: () => api(contracts.settings.test, { params: { provider: def.provider }, body: { to: testTo || undefined } }),
    onSuccess: (r) => {
      void qc.invalidateQueries({ queryKey: ['admin', 'integrations'] });
      if (r.ok) toast.success(r.message);
      else toast.error(r.message);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });

  return (
    <Card className="p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold">{def.title}</h2>
          <p className="mt-0.5 max-w-xl text-sm text-muted">{def.description}</p>
        </div>
        <Badge tone={MODE_TONE[state.mode]}>{t(`admin.settings.mode.${state.mode}`)}</Badge>
      </div>

      <form
        className="mt-6 grid gap-x-5 gap-y-4 md:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate(Object.fromEntries(dirty.map((k) => [k, values[k]!])));
        }}
      >
        {def.fields.map((f) => {
          const cur = state.fields.find((x) => x.key === f.key)!;
          const sourceBadge = <span className="text-[11px] font-medium text-muted">{t(`admin.settings.source.${cur.source}`)}</span>;
          return (
            <Field key={f.key} label={f.label} hint={f.help ?? (f.type === 'secret' && cur.value ? `${t('admin.settings.current')}: ${cur.value}` : undefined)} trailing={sourceBadge} className={f.type === 'json' || f.type === 'secret' && f.key.endsWith('_json') ? 'md:col-span-2' : undefined}>
              {(p) =>
                f.type === 'boolean' ? (
                  <div className="flex h-12 items-center">
                    <Switch id={p.id} label={f.label} checked={values[f.key] === 'true'} onCheckedChange={(v) => setValues((s) => ({ ...s, [f.key]: String(v) }))} />
                  </div>
                ) : f.type === 'select' ? (
                  <Select {...p} value={values[f.key]} onChange={(e) => setValues((s) => ({ ...s, [f.key]: e.target.value }))}>
                    {f.options?.map((o) => (
                      <option key={o} value={o}>
                        {o}
                      </option>
                    ))}
                  </Select>
                ) : (
                  <Input
                    {...p}
                    type={f.type === 'secret' ? 'password' : f.type === 'number' ? 'number' : f.type === 'email' ? 'email' : f.type === 'url' ? 'url' : 'text'}
                    autoComplete={f.type === 'secret' ? 'new-password' : 'off'}
                    placeholder={f.type === 'secret' ? (cur.value ? t('admin.settings.secretPlaceholder') : f.placeholder) : f.placeholder}
                    value={values[f.key]}
                    onChange={(e) => setValues((s) => ({ ...s, [f.key]: e.target.value }))}
                    className={cn(f.type !== 'email' && 'latin')}
                  />
                )
              }
            </Field>
          );
        })}
        <div className="flex flex-wrap items-center gap-2 border-t border-border pt-4 md:col-span-2">
          <Button type="submit" loading={save.isPending} disabled={dirty.length === 0}>
            {save.isPending ? t('common.saving') : t('common.save')}
          </Button>
          {state.fields.some((f) => f.source === 'admin') && (
            <Button
              variant="ghost"
              onClick={() => save.mutate(Object.fromEntries(state.fields.filter((f) => f.source === 'admin').map((f) => [f.key, null])))}
            >
              {t('admin.settings.clearAll')}
            </Button>
          )}
        </div>
      </form>

      {def.testable && (
        <section className="mt-6 rounded-card border border-border bg-bg p-4" aria-labelledby="test-h">
          <h3 id="test-h" className="text-sm font-semibold">
            {def.provider === 'brevo' ? t('admin.settings.testEmail') : t('admin.settings.test')}
          </h3>
          <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-end">
            {(def.provider === 'brevo' || def.provider === 'sms') && (
              <Field label={t('admin.settings.testTo')} className="flex-1">
                {(p) => <Input {...p} value={testTo} onChange={(e) => setTestTo(e.target.value)} placeholder={def.provider === 'sms' ? '+8801XXXXXXXXX' : 'admin@shikkhasathi.com'} className="latin" />}
              </Field>
            )}
            <Button variant="secondary" loading={test.isPending} onClick={() => test.mutate()} icon={def.provider === 'brevo' ? <Send className="size-4" aria-hidden /> : <Smartphone className="size-4" aria-hidden />}>
              {def.provider === 'brevo' ? t('admin.settings.testEmail') : t('admin.settings.test')}
            </Button>
          </div>
          {state.lastTest && (
            <p className={cn('mt-3 flex items-start gap-2 text-sm', state.lastTest.ok ? 'text-success-fg' : 'text-danger-fg')}>
              {state.lastTest.ok ? <CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden /> : <XCircle className="mt-0.5 size-4 shrink-0" aria-hidden />}
              <span>
                {state.lastTest.message} <span className="text-muted">· {t('admin.settings.lastTest', { when: relativeTime(state.lastTest.at, t.locale) })}</span>
              </span>
            </p>
          )}
        </section>
      )}
    </Card>
  );
}
