import './i18n';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BroadcastBody } from '@/api';
import { Bell, Mail, Megaphone, Send, Users } from 'lucide-react';
import { Badge, Button, Card, Chip, Dialog, EmptyState, ErrorState, Field, Input, Select, Skeleton, Switch, Textarea, toast } from '@/components';
import { api, contracts } from '@/lib/api';
import { formatDate, formatNumber, formatTime, useT } from '@/lib/i18n';
import { AdminHeader, errMsg } from './shared';

type Audience = 'all' | 'students' | 'sellers' | 'institution';
const AUDIENCES: Audience[] = ['all', 'students', 'sellers', 'institution'];

export default function AdminBroadcastsPage() {
  const t = useT();
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <AdminHeader title={t('admin.nav.broadcasts')} subtitle={t('admin.bc.subtitle')} />
      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_400px]">
        <Compose />
        <History />
      </div>
    </div>
  );
}

function Compose() {
  const t = useT();
  const qc = useQueryClient();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [link, setLink] = useState('');
  const [audience, setAudience] = useState<Audience>('all');
  const [institutionId, setInstitutionId] = useState('');
  const [email, setEmail] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [touched, setTouched] = useState(false);

  const insts = useQuery({ queryKey: ['catalog', 'institutions', 'all'], queryFn: () => api(contracts.catalog.institutions, { query: { limit: 100 } }), enabled: audience === 'institution', staleTime: 5 * 60_000 });
  const target = { audience, institutionId: audience === 'institution' ? institutionId || null : null };
  const preview = useQuery({
    queryKey: ['admin', 'broadcast-preview', target.audience, target.institutionId],
    queryFn: () => api(contracts.adminOps.broadcastPreview, { body: target }),
    enabled: target.audience !== 'institution' || !!target.institutionId,
  });

  const draft = { title, body, link: link.trim() || null, audience, institutionId: audience === 'institution' ? institutionId || null : null, email };
  const parsed = BroadcastBody.safeParse(draft);
  const issues = parsed.success ? {} : Object.fromEntries(parsed.error.issues.map((i) => [String(i.path[0]), i.message]));
  const instMissing = audience === 'institution' && !institutionId;
  const recipients = preview.data?.recipients ?? 0;
  const canSend = parsed.success && !instMissing && recipients > 0;

  const send = useMutation({
    mutationFn: () => api(contracts.adminOps.broadcast, { body: parsed.data! }),
    onSuccess: (r) => {
      toast.success(t('admin.bc.sent', { n: formatNumber(r.recipients, t.locale) }));
      setConfirming(false);
      setTitle(''); setBody(''); setLink(''); setEmail(false); setTouched(false);
      void qc.invalidateQueries({ queryKey: ['admin', 'broadcasts'] });
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  const err = (k: string) => (touched ? (k === 'link' && issues[k] ? t('admin.bc.linkErr') : issues[k] ? t(`admin.bc.err.${k}`) : undefined) : undefined);

  return (
    <Card className="p-5">
      <form
        className="flex flex-col gap-5"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          setTouched(true);
          if (canSend) setConfirming(true);
        }}
      >
        <h2 className="font-semibold">{t('admin.bc.compose')}</h2>
        <Field label={t('admin.bc.title')} error={err('title')} trailing={<span className="text-xs text-muted tnum">{formatNumber(title.length, t.locale)}/120</span>}>
          {(p) => <Input {...p} value={title} maxLength={120} onChange={(e) => setTitle(e.target.value)} placeholder={t('admin.bc.titlePh')} />}
        </Field>
        <Field label={t('admin.bc.body')} error={err('body')} trailing={<span className="text-xs text-muted tnum">{formatNumber(body.length, t.locale)}/1000</span>}>
          {(p) => <Textarea {...p} value={body} maxLength={1000} rows={5} onChange={(e) => setBody(e.target.value)} placeholder={t('admin.bc.bodyPh')} />}
        </Field>
        <Field label={`${t('admin.bc.link')} (${t('common.optional')})`} hint={t('admin.bc.linkHint')} error={err('link')}>
          {(p) => <Input {...p} value={link} maxLength={300} onChange={(e) => setLink(e.target.value)} placeholder="/explore" className="latin" />}
        </Field>
        <fieldset>
          <legend className="text-sm font-medium">{t('admin.bc.audience')}</legend>
          <div className="mt-2 flex flex-wrap gap-2">
            {AUDIENCES.map((a) => <Chip key={a} selected={audience === a} onClick={() => setAudience(a)}>{t(`admin.bc.aud.${a}`)}</Chip>)}
          </div>
          {audience === 'institution' && (
            <div className="mt-3">
              <Select value={institutionId} onChange={(e) => setInstitutionId(e.target.value)} aria-label={t('admin.bc.pickInst')} disabled={insts.isLoading} aria-invalid={touched && instMissing ? true : undefined}>
                <option value="">{insts.isLoading ? t('common.loading') : t('admin.bc.pickInst')}</option>
                {insts.data?.items.map((i) => <option key={i.id} value={i.id}>{i.nameBn ?? i.name} ({i.shortName})</option>)}
              </Select>
              {insts.isError && <p className="mt-1 text-[13px] text-danger-fg" role="alert">{t('state.errorBody')}</p>}
            </div>
          )}
        </fieldset>
        <div className="flex items-center justify-between gap-4 rounded-control border border-border p-3">
          <div className="flex items-start gap-3">
            <Mail className="mt-0.5 size-4 shrink-0 text-muted" aria-hidden />
            <div>
              <p className="text-sm font-medium">{t('admin.bc.email')}</p>
              <p className="text-xs text-muted">{t('admin.bc.emailHint')}</p>
            </div>
          </div>
          <Switch label={t('admin.bc.email')} checked={email} onCheckedChange={setEmail} />
        </div>

        <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2 text-sm" aria-live="polite">
            <Users className="size-4 text-muted" aria-hidden />
            {instMissing ? (
              <span className="text-muted">{t('admin.bc.pickInstFirst')}</span>
            ) : preview.isLoading ? (
              <Skeleton className="h-4 w-28" />
            ) : preview.isError ? (
              <span className="text-danger-fg">{t('state.errorBody')}</span>
            ) : (
              <span>{t('admin.bc.recipients', { n: formatNumber(recipients, t.locale) })}</span>
            )}
          </div>
          <Button type="submit" icon={<Send className="size-4" aria-hidden />} disabled={touched && !canSend}>{t('admin.bc.review')}</Button>
        </div>
      </form>

      {confirming && parsed.success && (
        <Dialog
          open
          onOpenChange={(o) => !o && setConfirming(false)}
          title={t('admin.bc.confirmTitle', { n: formatNumber(recipients, t.locale) })}
          description={t(`admin.bc.aud.${audience}`) + (audience === 'institution' ? ` · ${insts.data?.items.find((i) => i.id === institutionId)?.shortName ?? ''}` : '')}
          footer={
            <>
              <Button variant="ghost" onClick={() => setConfirming(false)}>{t('common.cancel')}</Button>
              <Button loading={send.isPending} icon={<Send className="size-4" aria-hidden />} onClick={() => send.mutate()}>{t('admin.bc.send')}</Button>
            </>
          }
        >
          <div className="rounded-card border border-border p-4">
            <div className="flex items-start gap-3">
              <span className="grid size-9 shrink-0 place-items-center rounded-full bg-primary-soft text-primary-fg"><Bell className="size-4" aria-hidden /></span>
              <div className="min-w-0">
                <p className="font-semibold">{parsed.data.title}</p>
                <p className="mt-1 whitespace-pre-line text-sm">{parsed.data.body}</p>
                {parsed.data.link && <p className="latin mt-1.5 text-xs text-primary-fg">shikkhasathi.com{parsed.data.link}</p>}
              </div>
            </div>
          </div>
          <p className="mt-3 text-sm text-muted">{email ? t('admin.bc.channelsEmail') : t('admin.bc.channels')}</p>
        </Dialog>
      )}
    </Card>
  );
}

function History() {
  const t = useT();
  const q = useQuery({ queryKey: ['admin', 'broadcasts'], queryFn: () => api(contracts.adminOps.broadcasts) });
  return (
    <section aria-labelledby="bc-hist">
      <h2 id="bc-hist" className="mb-3 font-semibold">{t('admin.bc.history')}</h2>
      {q.isLoading ? (
        <div className="flex flex-col gap-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-24 rounded-card" />)}</div>
      ) : q.isError ? (
        <Card><ErrorState compact onRetry={() => void q.refetch()} /></Card>
      ) : !q.data?.items.length ? (
        <Card><EmptyState compact illustration={<Megaphone className="size-9 text-muted" strokeWidth={1.5} aria-hidden />} title={t('admin.bc.empty')} body={t('admin.bc.emptyBody')} /></Card>
      ) : (
        <ol className="relative flex flex-col gap-3 border-l border-border pl-4">
          {q.data.items.map((b) => (
            <li key={b.id} className="relative">
              <span className="absolute -left-[21px] top-4 size-2.5 rounded-full border-2 border-surface bg-primary" aria-hidden />
              <Card className="p-4">
                <div className="flex items-start justify-between gap-3">
                  <p className="font-medium">{b.title}</p>
                  <span className="shrink-0 text-xs text-muted">{formatDate(b.createdAt, t.locale)} · {formatTime(b.createdAt, t.locale)}</span>
                </div>
                <p className="mt-1 line-clamp-2 text-sm text-muted">{b.body}</p>
                <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
                  <Badge>{AUDIENCES.includes(b.audience as Audience) ? t(`admin.bc.aud.${b.audience}`) : b.audience}</Badge>
                  <Badge tone="primary" icon={<Users className="size-3" aria-hidden />}>{formatNumber(b.recipients, t.locale)}</Badge>
                  {b.email && <Badge icon={<Mail className="size-3" aria-hidden />}>{t('admin.bc.emailShort')}</Badge>}
                  {b.actorName && <span className="text-muted">· {b.actorName}</span>}
                </div>
              </Card>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
