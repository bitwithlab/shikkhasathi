import './i18n';
import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ROLES, type AdminUser, type Role } from '@shikkhasathi/shared';
import { Ban, RotateCcw, Search, ShieldCheck, UserCog, Users } from 'lucide-react';
import { Avatar, Badge, Button, Card, Dialog, Field, Input, Select, Switch, Textarea, toast } from '@/components';
import { api, contracts } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { formatNumber, relativeTime, useT } from '@/lib/i18n';
import { AdminHeader, ListBody, LoadMore, errMsg, useDebounced } from './shared';

type UStatus = AdminUser['status'];
const ROLE_TONE = { student: 'neutral', seller: 'primary', admin: 'warning' } as const;

export default function AdminUsersPage() {
  const t = useT();
  const me = useAuth((s) => s.user);
  const [search, setSearch] = useState('');
  const [role, setRole] = useState<Role | ''>('');
  const [status, setStatus] = useState<UStatus | ''>('');
  const [editing, setEditing] = useState<AdminUser | null>(null);
  const q = useDebounced(search.trim());
  const list = useInfiniteQuery({
    queryKey: ['admin', 'users', q, role, status],
    queryFn: ({ pageParam }) => api(contracts.adminOps.users, { query: { q: q || undefined, role: role || undefined, status: status || undefined, cursor: pageParam, limit: 30 } }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (p) => p.nextCursor ?? undefined,
  });
  const items = list.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <AdminHeader title={t('admin.nav.users')} subtitle={t('admin.users.subtitle')} />
      <div className="mt-5 grid gap-3 sm:grid-cols-[minmax(0,1fr)_180px_180px]">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted" aria-hidden />
          <Input type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t('admin.users.searchPh')} aria-label={t('common.search')} className="pl-10" />
        </div>
        <Select value={role} onChange={(e) => setRole(e.target.value as Role | '')} aria-label={t('admin.users.role')}>
          <option value="">{t('admin.users.allRoles')}</option>
          {ROLES.map((r) => <option key={r} value={r}>{t(`admin.role.${r}`)}</option>)}
        </Select>
        <Select value={status} onChange={(e) => setStatus(e.target.value as UStatus | '')} aria-label={t('admin.common.status')}>
          <option value="">{t('admin.common.allStatuses')}</option>
          <option value="active">{t('admin.userStatus.active')}</option>
          <option value="suspended">{t('admin.userStatus.suspended')}</option>
        </Select>
      </div>
      <Card className="mt-4 overflow-hidden">
        <ListBody loading={list.isLoading} error={list.isError} empty={!items.length} onRetry={() => void list.refetch()} emptyIcon={<Users className="size-10 text-muted" strokeWidth={1.5} aria-hidden />} emptyTitle={t('admin.common.noMatch')} rows={6}>
          <div className="relative overflow-x-auto">
            <table className="w-full min-w-[820px] text-sm">
              <thead className="bg-surface-2 text-left text-xs text-muted">
                <tr>
                  <th className="px-4 py-2.5 font-medium">{t('admin.users.user')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.users.roles')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.common.status')}</th>
                  <th className="px-3 py-2.5 text-right font-medium">{t('admin.users.enrollments')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.users.joined')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.users.lastActive')}</th>
                  <th className="px-4 py-2.5"><span className="sr-only">{t('admin.users.manage')}</span></th>
                </tr>
              </thead>
              <tbody>
                {items.map((u) => (
                  <tr key={u.id} className="border-t border-border hover:bg-surface-2">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <Avatar name={u.name} size={36} />
                        <div className="min-w-0">
                          <p className="font-medium">{u.name}{u.id === me?.id && <span className="ml-1.5 text-xs font-normal text-muted">({t('admin.users.you')})</span>}</p>
                          <p className="latin truncate text-xs text-muted">{[u.email, u.phone].filter(Boolean).join(' · ') || '—'}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex flex-wrap gap-1">
                        {u.roles.map((r) => <Badge key={r} tone={ROLE_TONE[r]}>{t(`admin.role.${r}`)}</Badge>)}
                        {(u.sellerStatus === 'pending' || u.sellerStatus === 'rejected') && <Badge>{t(`admin.sellers.status.${u.sellerStatus}`)}</Badge>}
                      </div>
                    </td>
                    <td className="px-3 py-3"><Badge tone={u.status === 'active' ? 'success' : 'danger'}>{t(`admin.userStatus.${u.status}`)}</Badge></td>
                    <td className="px-3 py-3 text-right tnum">{formatNumber(u.enrollments, t.locale)}</td>
                    <td className="px-3 py-3 text-muted">{relativeTime(u.createdAt, t.locale)}</td>
                    <td className="px-3 py-3 text-muted">{u.lastActiveAt ? relativeTime(u.lastActiveAt, t.locale) : '—'}</td>
                    <td className="px-4 py-3 text-right">
                      <Button size="sm" variant="ghost" icon={<UserCog className="size-4" aria-hidden />} onClick={() => setEditing(u)} aria-label={t('admin.users.manageX', { name: u.name })}>{t('admin.users.manage')}</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <LoadMore shown={items.length} hasMore={!!list.hasNextPage} loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()} />
        </ListBody>
      </Card>
      {editing && <ManageDialog u={editing} self={editing.id === me?.id} onClose={() => setEditing(null)} />}
    </div>
  );
}

function ManageDialog({ u, self, onClose }: { u: AdminUser; self: boolean; onClose: () => void }) {
  const t = useT();
  const qc = useQueryClient();
  const [roles, setRoles] = useState<Role[]>(u.roles);
  const [reason, setReason] = useState('');
  const m = useMutation({
    mutationFn: (body: { status?: UStatus; roles?: Role[] }) => api(contracts.adminOps.updateUser, { params: { id: u.id }, body: { ...body, reason: reason.trim() || undefined } }),
    onSuccess: () => {
      toast.success(t('common.saved'));
      void qc.invalidateQueries({ queryKey: ['admin', 'users'] });
      onClose();
    },
    onError: (e) => toast.error(errMsg(e, t('state.errorBody'))),
  });
  const toggle = (r: Role, on: boolean) => setRoles((s) => (on ? [...new Set([...s, r])] : s.filter((x) => x !== r)));
  const rolesChanged = roles.slice().sort().join() !== u.roles.slice().sort().join();
  const suspended = u.status === 'suspended';
  const reasonOk = reason.trim().length >= 5;
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={u.name} description={<span className="latin">{[u.email, u.phone].filter(Boolean).join(' · ')}</span>}>
      <div className="flex flex-col gap-5">
        {self && <p className="flex items-center gap-2 rounded-control bg-warning-soft px-3 py-2 text-sm text-warning-fg"><ShieldCheck className="size-4 shrink-0" aria-hidden />{t('admin.users.selfNote')}</p>}
        <section aria-labelledby="roles-h">
          <h3 id="roles-h" className="text-sm font-semibold">{t('admin.users.roles')}</h3>
          <ul className="mt-2 divide-y divide-border rounded-control border border-border">
            {(['seller', 'admin'] as const).map((r) => (
              <li key={r} className="flex items-center justify-between gap-3 px-3 py-2.5">
                <div>
                  <p className="text-sm font-medium">{t(`admin.role.${r}`)}</p>
                  <p className="text-xs text-muted">{t(`admin.users.roleHint.${r}`)}</p>
                </div>
                <Switch label={t(`admin.role.${r}`)} checked={roles.includes(r)} disabled={self && r === 'admin'} onCheckedChange={(v) => toggle(r, v)} />
              </li>
            ))}
          </ul>
        </section>
        <Field label={t('admin.users.reason')} hint={t('admin.users.reasonHint')}>{(p) => <Textarea {...p} value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} />}</Field>
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
          {suspended ? (
            <Button variant="secondary" icon={<RotateCcw className="size-4" aria-hidden />} loading={m.isPending && m.variables?.status === 'active'} disabled={m.isPending} onClick={() => m.mutate({ status: 'active' })}>{t('admin.users.reactivate')}</Button>
          ) : (
            <Button variant="danger" icon={<Ban className="size-4" aria-hidden />} disabled={self || !reasonOk || m.isPending} loading={m.isPending && m.variables?.status === 'suspended'} onClick={() => m.mutate({ status: 'suspended' })}>{t('admin.users.suspend')}</Button>
          )}
          <Button disabled={!rolesChanged || roles.length === 0 || m.isPending} loading={m.isPending && !!m.variables?.roles} onClick={() => m.mutate({ roles })}>{t('admin.users.saveRoles')}</Button>
        </div>
        {!suspended && !self && !reasonOk && <p className="-mt-3 text-xs text-muted">{t('admin.users.suspendNeedsReason')}</p>}
      </div>
    </Dialog>
  );
}
