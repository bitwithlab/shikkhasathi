import '../i18n';
import '@/features/auth/i18n';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, GraduationCap, KeyRound, Laptop, LogOut, Monitor, Palette, Presentation, ReceiptText, Smartphone, Trash2, UserRound } from 'lucide-react';
import type { NotificationChannel, NotificationType } from '@shikkhasathi/shared';
import { Avatar, Badge, Button, Dialog, ErrorState, Field, Input, Select, Skeleton, Switch, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { ordinal, relativeTime, useT } from '@/lib/i18n';
import { LangToggle, ThemeSwitcher } from '@/app/shells/Prefs';
import { ConnectClassroomButton } from '../classroom/ConnectClassroomButton';
import { SyncStatus } from '../classroom/parts';
import { OrdersList } from './OrdersPanel';

const onErr = (t: ReturnType<typeof useT>) => (e: unknown) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody'));

function Panel({ id, icon, title, children, desc }: { id: string; icon: ReactNode; title: string; desc?: string; children: ReactNode }) {
  return (
    <section id={id} aria-labelledby={`${id}-h`} className="surface-card scroll-mt-20 p-5">
      <h2 id={`${id}-h`} className="flex items-center gap-2 text-[17px] font-semibold">
        {icon}
        {title}
      </h2>
      {desc && <p className="mt-0.5 text-sm text-muted">{desc}</p>}
      <div className="mt-4">{children}</div>
    </section>
  );
}

export default function ProfilePage() {
  const t = useT();
  const user = useAuth((s) => s.user);
  useEffect(() => {
    if (location.hash) document.getElementById(location.hash.slice(1))?.scrollIntoView();
  }, []);
  if (!user) return null;
  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <header className="mb-6 flex items-center gap-4">
        <Avatar name={user.name} src={user.avatarUrl} size={64} />
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-bold">{user.name}</h1>
          <p className="latin truncate text-sm text-muted">{[user.phone?.replace('+88', ''), user.email].filter(Boolean).join(' · ')}</p>
        </div>
      </header>
      <nav aria-label={t('nav.profile')} className="-mx-4 mb-5 flex gap-2 overflow-x-auto px-4 text-sm scrollbar-none">
        {[
          ['academic', 'prof.academic'],
          ['classroom', 'nav.classroom'],
          ['orders', 'prof.orders'],
          ['notifications', 'nav.notifications'],
          ['devices', 'prof.devices'],
          ['prefs', 'prof.prefs'],
          ['account', 'prof.account'],
        ].map(([id, k]) => (
          <a key={id} href={`#${id}`} className="shrink-0 rounded-full border border-border bg-surface px-3 py-1.5 font-medium hover:border-primary">
            {t(k!)}
          </a>
        ))}
      </nav>
      <div className="flex flex-col gap-5">
        <AcademicPanel />
        <ClassroomPanel />
        <Panel id="orders" icon={<ReceiptText className="size-5 text-primary" strokeWidth={1.75} aria-hidden />} title={t('prof.orders')} desc={t('prof.ordersDesc')}>
          <OrdersList />
        </Panel>
        <NotificationPanel />
        <DevicesPanel />
        <PrefsPanel />
        <AccountPanel />
      </div>
    </div>
  );
}

function AcademicPanel() {
  const t = useT();
  const { user, setUser } = useAuth();
  const a = user!.academic;
  const [edit, setEdit] = useState(false);
  const [v, setV] = useState({ institutionId: a.institutionId ?? '', departmentId: a.departmentId ?? '', programId: a.programId ?? '', semester: a.semester ? String(a.semester) : '', batch: a.batch ?? '', section: a.section ?? '' });
  const insts = useQuery({ queryKey: ['institutions', ''], queryFn: () => api(contracts.catalog.institutions, { query: { limit: 100 } }), enabled: edit });
  const depts = useQuery({ queryKey: ['departments', v.institutionId], queryFn: () => api(contracts.catalog.departments, { query: { institutionId: v.institutionId } }), enabled: edit && !!v.institutionId });
  const progs = useQuery({ queryKey: ['programs', v.departmentId], queryFn: () => api(contracts.catalog.programs, { query: { departmentId: v.departmentId } }), enabled: edit && !!v.departmentId });
  const qc = useQueryClient();
  const save = useMutation({
    mutationFn: () => api(contracts.catalog.academicProfile, { body: { institutionId: v.institutionId || null, departmentId: v.departmentId || null, programId: v.programId || null, semester: v.semester ? Number(v.semester) : null, batch: v.batch || null, section: v.section || null } }),
    onSuccess: (me) => {
      setUser(me);
      setEdit(false);
      toast.success(t('common.saved'));
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: onErr(t),
  });
  const rows: [string, string | null][] = [
    [t('onb.s1'), a.institutionName],
    [t('onb.dept.label'), a.departmentName ? `${a.departmentName} (${a.departmentShort})` : null],
    [t('onb.prog.label'), a.programName],
    [t('onb.sem.current'), a.semester ? ordinal(a.semester, t.locale) : null],
    [t('onb.batch.label'), a.batch],
    [t('onb.section.label'), a.section],
  ];
  return (
    <Panel id="academic" icon={<GraduationCap className="size-5 text-primary" strokeWidth={1.75} aria-hidden />} title={t('prof.academic')} desc={t('prof.academicDesc')}>
      {!edit ? (
        <>
          <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
            {rows.map(([k, val]) => (
              <div key={k}>
                <dt className="text-xs text-muted">{k}</dt>
                <dd className="latin font-medium">{val ?? '—'}</dd>
              </div>
            ))}
          </dl>
          <Button variant="secondary" className="mt-4" onClick={() => setEdit(true)}>{t('common.edit')}</Button>
        </>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t('onb.s1')} className="sm:col-span-2">
            {(p) => (
              <Select {...p} value={v.institutionId} onChange={(e) => setV({ ...v, institutionId: e.target.value, departmentId: '', programId: '' })}>
                <option value="">—</option>
                {insts.data?.items.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
              </Select>
            )}
          </Field>
          <Field label={t('onb.dept.label')}>
            {(p) => (
              <Select {...p} value={v.departmentId} onChange={(e) => setV({ ...v, departmentId: e.target.value, programId: '' })} disabled={!v.institutionId}>
                <option value="">—</option>
                {depts.data?.items.map((d) => <option key={d.id} value={d.id}>{d.shortName} · {d.name}</option>)}
              </Select>
            )}
          </Field>
          <Field label={t('onb.prog.label')}>
            {(p) => (
              <Select {...p} value={v.programId} onChange={(e) => setV({ ...v, programId: e.target.value })} disabled={!v.departmentId}>
                <option value="">—</option>
                {progs.data?.items.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
              </Select>
            )}
          </Field>
          <Field label={t('onb.sem.current')}>
            {(p) => (
              <Select {...p} value={v.semester} onChange={(e) => setV({ ...v, semester: e.target.value })}>
                <option value="">—</option>
                {Array.from({ length: 12 }, (_, i) => i + 1).map((n) => <option key={n} value={n}>{ordinal(n, t.locale)}</option>)}
              </Select>
            )}
          </Field>
          <Field label={t('onb.batch.label')}>{(p) => <Input {...p} value={v.batch} onChange={(e) => setV({ ...v, batch: e.target.value.slice(0, 8) })} className="latin" />}</Field>
          <Field label={t('onb.section.label')}>{(p) => <Input {...p} value={v.section} onChange={(e) => setV({ ...v, section: e.target.value.slice(0, 10) })} className="latin" />}</Field>
          <div className="flex gap-2 sm:col-span-2">
            <Button onClick={() => save.mutate()} loading={save.isPending}>{t('common.save')}</Button>
            <Button variant="ghost" onClick={() => setEdit(false)}>{t('common.cancel')}</Button>
          </div>
        </div>
      )}
    </Panel>
  );
}

function ClassroomPanel() {
  const t = useT();
  const qc = useQueryClient();
  const [confirm, setConfirm] = useState(false);
  const s = useQuery({ queryKey: ['classroom', 'status'], queryFn: () => api(contracts.classroom.status) });
  const disconnect = useMutation({
    mutationFn: () => api(contracts.classroom.disconnect),
    onSuccess: () => {
      setConfirm(false);
      toast.success(t('prof.gcDisconnected'));
      void qc.invalidateQueries({ queryKey: ['classroom'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      void qc.invalidateQueries({ queryKey: ['notifications'] });
    },
    onError: onErr(t),
  });
  return (
    <Panel id="classroom" icon={<Presentation className="size-5 text-success" strokeWidth={1.75} aria-hidden />} title="Google Classroom" desc={t('brand.disclaimerGoogle')}>
      {s.isLoading ? (
        <Skeleton className="h-16" />
      ) : s.isError || !s.data ? (
        <ErrorState compact onRetry={() => void s.refetch()} />
      ) : !s.data.connected ? (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-[15px] text-muted">{t('gc.connectBody')}</p>
          <div className="sm:w-64"><ConnectClassroomButton returnTo="/profile#classroom" size="md" /></div>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={s.data.status === 'active' ? 'success' : s.data.status === 'syncing' ? 'primary' : 'warning'}>{t(`prof.gcStatus.${s.data.status ?? 'active'}`)}</Badge>
            <span className="latin text-sm">{s.data.email}</span>
            {s.data.isFake && <Badge tone="neutral">{t('prof.gcDemo')}</Badge>}
          </div>
          <SyncStatus status={s.data} />
          <p className="text-sm text-muted">{t('prof.gcCourses', { n: s.data.courseCount })}</p>
          <div className="flex flex-wrap gap-2">
            {s.data.status === 'needs_reauth' && <div className="w-60"><ConnectClassroomButton returnTo="/profile#classroom" size="md" label={t('gc.reconnect')} /></div>}
            <Button variant="secondary" icon={<LogOut className="size-4" aria-hidden />} onClick={() => setConfirm(true)}>{t('prof.gcDisconnect')}</Button>
          </div>
        </div>
      )}
      <Dialog open={confirm} onOpenChange={setConfirm} title={t('prof.gcDisconnectTitle')} description={t('prof.gcDisconnectBody')} footer={<><Button variant="ghost" onClick={() => setConfirm(false)}>{t('common.cancel')}</Button><Button variant="danger" onClick={() => disconnect.mutate()} loading={disconnect.isPending}>{t('prof.gcDisconnect')}</Button></>}>
        <span />
      </Dialog>
    </Panel>
  );
}

const PREF_GROUPS: { key: string; types: NotificationType[] }[] = [
  { key: 'classroom', types: ['classroom.assignment_new', 'classroom.material_new', 'classroom.announcement', 'classroom.graded', 'classroom.due_24h', 'classroom.due_2h', 'classroom.overdue'] },
  { key: 'learning', types: ['exam.daily_plan', 'course.qa_answered', 'request.published'] },
  { key: 'orders', types: ['order.paid', 'order.refunded'] },
];
const CHANNELS: NotificationChannel[] = ['in_app', 'push', 'email'];

function NotificationPanel() {
  const t = useT();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['notifications', 'prefs'], queryFn: () => api(contracts.notifications.preferences) });
  const m = useMutation({
    mutationFn: (item: { type: NotificationType; channel: NotificationChannel; enabled: boolean }) => api(contracts.notifications.updatePreferences, { body: { items: [item] } }),
    onSuccess: (d) => qc.setQueryData(['notifications', 'prefs'], d),
    onError: onErr(t),
  });
  const get = useMemo(() => (type: string, ch: string) => q.data?.items.find((i) => i.type === type && i.channel === ch)?.enabled ?? false, [q.data]);
  return (
    <Panel id="notifications" icon={<Bell className="size-5 text-warning" strokeWidth={1.75} aria-hidden />} title={t('nav.notifications')} desc={t('prof.notifDesc')}>
      {q.isLoading ? <Skeleton className="h-48" /> : q.isError ? <ErrorState compact onRetry={() => void q.refetch()} /> : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[440px] text-sm">
            <thead>
              <tr className="text-left text-xs text-muted">
                <th className="py-2 font-medium" />
                {CHANNELS.map((c) => <th key={c} className="w-20 py-2 text-center font-medium">{t(`prof.ch.${c}`)}</th>)}
              </tr>
            </thead>
            {PREF_GROUPS.map((g) => (
              <tbody key={g.key}>
                <tr><th colSpan={4} className="pb-1 pt-4 text-left text-xs font-semibold uppercase tracking-wide text-muted">{t(`prof.group.${g.key}`)}</th></tr>
                {g.types.map((type) => (
                  <tr key={type} className="border-t border-border">
                    <td className="py-2.5 pr-3">{t(`notifType.${type}`)}</td>
                    {CHANNELS.map((ch) => (
                      <td key={ch} className="py-2.5 text-center">
                        <Switch label={`${t(`notifType.${type}`)} · ${t(`prof.ch.${ch}`)}`} checked={get(type, ch)} onCheckedChange={(enabled) => m.mutate({ type, channel: ch, enabled })} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            ))}
          </table>
          <p className="mt-3 text-xs text-muted">{t('prof.quietHours')}</p>
        </div>
      )}
    </Panel>
  );
}

function DevicesPanel() {
  const t = useT();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['me', 'sessions'], queryFn: () => api(contracts.auth.sessions) });
  const revoke = useMutation({ mutationFn: (id: string) => api(contracts.auth.revokeSession, { params: { id } }), onSuccess: () => void qc.invalidateQueries({ queryKey: ['me', 'sessions'] }), onError: onErr(t) });
  return (
    <Panel id="devices" icon={<Laptop className="size-5 text-primary" strokeWidth={1.75} aria-hidden />} title={t('prof.devices')} desc={t('prof.devicesDesc')}>
      {q.isLoading ? <Skeleton className="h-24" /> : q.isError ? <ErrorState compact onRetry={() => void q.refetch()} /> : (
        <ul className="flex flex-col divide-y divide-border">
          {q.data?.items.map((s) => {
            const Icon = s.platform === 'web' ? Monitor : Smartphone;
            return (
              <li key={s.id} className="flex items-center gap-3 py-3">
                <Icon className="size-5 shrink-0 text-muted" strokeWidth={1.75} aria-hidden />
                <span className="min-w-0 flex-1">
                  <span className="latin block truncate font-medium">{s.deviceName ?? s.platform}</span>
                  <span className="block text-xs text-muted">{t('prof.lastSeen', { when: relativeTime(s.lastSeenAt, t.locale) })}</span>
                </span>
                {s.current ? <Badge tone="success">{t('prof.thisDevice')}</Badge> : <Button variant="ghost" size="sm" onClick={() => revoke.mutate(s.id)}>{t('prof.signOut')}</Button>}
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}

function PrefsPanel() {
  const t = useT();
  const { user, setUser } = useAuth();
  const m = useMutation({ mutationFn: (lowDataMode: boolean) => api(contracts.auth.updateMe, { body: { lowDataMode } }), onSuccess: setUser, onError: onErr(t) });
  return (
    <Panel id="prefs" icon={<Palette className="size-5 text-primary" strokeWidth={1.75} aria-hidden />} title={t('prof.prefs')}>
      <ul className="flex flex-col divide-y divide-border">
        <li className="flex items-center justify-between gap-4 py-3"><span>{t('common.language')}</span><LangToggle /></li>
        <li className="flex items-center justify-between gap-4 py-3"><span>{t('common.theme')}</span><ThemeSwitcher /></li>
        <li className="flex items-center justify-between gap-4 py-3">
          <span>
            <span className="block">{t('prof.lowData')}</span>
            <span className="block text-sm text-muted">{t('prof.lowDataDesc')}</span>
          </span>
          <Switch label={t('prof.lowData')} checked={user!.lowDataMode} onCheckedChange={(v) => m.mutate(v)} />
        </li>
      </ul>
    </Panel>
  );
}

function AccountPanel() {
  const t = useT();
  const { user, setUser, logout } = useAuth();
  const [name, setName] = useState(user!.name);
  const [email, setEmail] = useState(user!.email ?? '');
  const [pw, setPw] = useState({ current: '', next: '' });
  const [del, setDel] = useState(false);
  const save = useMutation({ mutationFn: () => api(contracts.auth.updateMe, { body: { name, ...(email && email !== user!.email ? { email } : {}) } }), onSuccess: (me) => { setUser(me); toast.success(t('common.saved')); }, onError: onErr(t) });
  const pwm = useMutation({ mutationFn: () => api(contracts.auth.changePassword, { body: { currentPassword: pw.current || undefined, newPassword: pw.next } }), onSuccess: () => { setPw({ current: '', next: '' }); toast.success(t('common.saved')); }, onError: onErr(t) });
  const delm = useMutation({ mutationFn: () => api(contracts.auth.deleteMe), onSuccess: () => void logout(), onError: onErr(t) });
  return (
    <Panel id="account" icon={<UserRound className="size-5 text-primary" strokeWidth={1.75} aria-hidden />} title={t('prof.account')}>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t('auth.name')}>{(p) => <Input {...p} value={name} onChange={(e) => setName(e.target.value)} />}</Field>
        <Field label={t('auth.email')} hint={user!.email && !user!.emailVerified ? t('prof.emailUnverified') : undefined}>{(p) => <Input {...p} type="email" value={email} onChange={(e) => setEmail(e.target.value)} />}</Field>
      </div>
      <Button className="mt-3" onClick={() => save.mutate()} loading={save.isPending} disabled={name.trim().length < 2}>{t('common.save')}</Button>

      <h3 className="mt-6 flex items-center gap-2 font-semibold"><KeyRound className="size-4" aria-hidden /> {user!.hasPassword ? t('prof.changePw') : t('prof.setPw')}</h3>
      <div className="mt-3 grid gap-4 sm:grid-cols-2">
        {user!.hasPassword && <Field label={t('prof.currentPw')}>{(p) => <Input {...p} type="password" autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} />}</Field>}
        <Field label={t('auth.newPassword')} hint={t('auth.passwordHint')}>{(p) => <Input {...p} type="password" autoComplete="new-password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} />}</Field>
      </div>
      <Button variant="secondary" className="mt-3" onClick={() => pwm.mutate()} loading={pwm.isPending} disabled={pw.next.length < 8}>{t('common.save')}</Button>

      <div className="mt-8 rounded-card border border-danger/30 bg-danger-soft/50 p-4">
        <h3 className="flex items-center gap-2 font-semibold text-danger-fg"><Trash2 className="size-4" aria-hidden /> {t('prof.delete')}</h3>
        <p className="mt-1 text-sm text-muted">{t('prof.deleteDesc')}</p>
        <Button variant="danger" size="sm" className="mt-3" onClick={() => setDel(true)}>{t('prof.delete')}</Button>
      </div>
      <Dialog open={del} onOpenChange={setDel} title={t('prof.deleteConfirm')} description={t('prof.deleteDesc')} footer={<><Button variant="ghost" onClick={() => setDel(false)}>{t('common.cancel')}</Button><Button variant="danger" onClick={() => delm.mutate()} loading={delm.isPending}>{t('prof.delete')}</Button></>}>
        <span />
      </Dialog>
    </Panel>
  );
}
