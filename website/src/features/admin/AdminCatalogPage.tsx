import './i18n';
import '@/features/auth/i18n';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Search, Trash2 } from 'lucide-react';
import { INSTITUTION_TYPES } from '@/types';
import { Badge, Button, Card, Chip, Dialog, EmptyState, ErrorState, Field, Input, Select, Sheet, Skeleton, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { formatNumber, useT } from '@/lib/i18n';

const onErr = (t: ReturnType<typeof useT>) => (e: unknown) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody'));

export default function AdminCatalogPage() {
  const t = useT();
  const [tab, setTab] = useState<'institutions' | 'subjects'>('institutions');
  return (
    <div className="mx-auto max-w-[1200px] px-4 py-6 lg:px-8">
      <h1 className="text-2xl font-bold">{t('admin.nav.catalog')}</h1>
      <div className="mt-5 flex gap-2">
        <Chip selected={tab === 'institutions'} onClick={() => setTab('institutions')}>{t('admin.cat.institutions')}</Chip>
        <Chip selected={tab === 'subjects'} onClick={() => setTab('subjects')}>{t('admin.cat.subjects')}</Chip>
      </div>
      <div className="mt-5">{tab === 'institutions' ? <Institutions /> : <Subjects />}</div>
    </div>
  );
}

function Institutions() {
  const t = useT();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [openSlug, setOpenSlug] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const list = useQuery({ queryKey: ['admin', 'catalog', 'inst', q], queryFn: () => api(contracts.adminCatalog.institutions, { query: { q: q || undefined } }), placeholderData: (p) => p });
  return (
    <>
      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-5 -translate-y-1/2 text-muted" aria-hidden />
          <label htmlFor="inst-q" className="sr-only">{t('common.search')}</label>
          <Input id="inst-q" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('common.search')} className="pl-11" />
        </div>
        <Button icon={<Plus className="size-4" aria-hidden />} onClick={() => setCreating(true)}>{t('admin.cat.addInstitution')}</Button>
      </div>
      <Card className="mt-4 overflow-hidden">
        {list.isLoading ? (
          <div className="flex flex-col gap-2 p-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-10" />)}</div>
        ) : list.isError ? (
          <ErrorState onRetry={() => void list.refetch()} />
        ) : !list.data?.items.length ? (
          <EmptyState title={t('admin.cat.empty')} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="bg-surface-2 text-left text-xs text-muted">
                <tr>
                  <th className="px-4 py-2.5 font-medium">{t('admin.inst.name')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('onb.uni.type')}</th>
                  <th className="px-3 py-2.5 font-medium">{t('admin.cat.status')}</th>
                  <th className="px-3 py-2.5 text-right font-medium">{t('admin.cat.courses')}</th>
                  <th className="px-4 py-2.5 text-right font-medium">{t('admin.cat.teachers')}</th>
                </tr>
              </thead>
              <tbody>
                {list.data.items.map((i) => (
                  <tr key={i.id} className="cursor-pointer border-t border-border hover:bg-surface-2" onClick={() => i.status === 'approved' && setOpenSlug(i.slug)}>
                    <td className="px-4 py-2.5">
                      <button type="button" className="text-left font-medium hover:text-primary-fg" onClick={() => setOpenSlug(i.slug)} disabled={i.status !== 'approved'}>
                        <span className="latin">{i.name}</span> <span className="latin text-muted">({i.shortName})</span>
                      </button>
                    </td>
                    <td className="px-3 py-2.5 text-muted">{t(`instType.${i.type}`)}</td>
                    <td className="px-3 py-2.5"><Badge tone={i.status === 'approved' ? 'success' : i.status === 'merged' ? 'neutral' : 'warning'}>{t(`admin.inst.status.${i.status}`)}</Badge></td>
                    <td className="latin tnum px-3 py-2.5 text-right">{formatNumber(i.courseCount, t.locale)}</td>
                    <td className="latin tnum px-4 py-2.5 text-right">{formatNumber(i.teacherCount, t.locale)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {openSlug && <InstitutionDrawer slug={openSlug} onClose={() => setOpenSlug(null)} />}
      {creating && <CreateInstitution onClose={() => { setCreating(false); void qc.invalidateQueries({ queryKey: ['admin', 'catalog'] }); }} />}
    </>
  );
}

function CreateInstitution({ onClose }: { onClose: () => void }) {
  const t = useT();
  const [v, setV] = useState({ name: '', shortName: '', slug: '', type: 'private' as (typeof INSTITUTION_TYPES)[number], city: '' });
  const m = useMutation({ mutationFn: () => api(contracts.adminCatalog.createInstitution, { body: { ...v, city: v.city || null, aliases: [] } }), onSuccess: onClose, onError: onErr(t) });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={t('admin.cat.addInstitution')} footer={<Button loading={m.isPending} onClick={() => m.mutate()}>{t('common.save')}</Button>}>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t('admin.inst.name')} className="sm:col-span-2">{(p) => <Input {...p} value={v.name} onChange={(e) => setV({ ...v, name: e.target.value, slug: v.slug || '' })} />}</Field>
        <Field label={t('admin.inst.short')}>{(p) => <Input {...p} value={v.shortName} onChange={(e) => setV({ ...v, shortName: e.target.value })} className="latin" />}</Field>
        <Field label="Slug">{(p) => <Input {...p} value={v.slug} onChange={(e) => setV({ ...v, slug: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '') })} className="latin" />}</Field>
        <Field label={t('onb.uni.type')}>{(p) => <Select {...p} value={v.type} onChange={(e) => setV({ ...v, type: e.target.value as typeof v.type })}>{INSTITUTION_TYPES.map((x) => <option key={x} value={x}>{t(`instType.${x}`)}</option>)}</Select>}</Field>
        <Field label={t('onb.uni.city')}>{(p) => <Input {...p} value={v.city} onChange={(e) => setV({ ...v, city: e.target.value })} />}</Field>
      </div>
    </Dialog>
  );
}

function InstitutionDrawer({ slug, onClose }: { slug: string; onClose: () => void }) {
  const t = useT();
  const qc = useQueryClient();
  const d = useQuery({ queryKey: ['institution', slug], queryFn: () => api(contracts.catalog.institution, { params: { slug } }) });
  const subjects = useQuery({ queryKey: ['admin', 'catalog', 'subjects', ''], queryFn: () => api(contracts.adminCatalog.subjects, { query: {} }) });
  const refresh = () => void qc.invalidateQueries({ queryKey: ['institution', slug] });
  const [dept, setDept] = useState({ name: '', shortName: '' });
  const [map, setMap] = useState({ departmentId: '', semester: '', subjectMasterId: '', courseCode: '' });
  const addDept = useMutation({ mutationFn: () => api(contracts.adminCatalog.createDepartment, { body: { institutionId: d.data!.id, ...dept } }), onSuccess: () => { setDept({ name: '', shortName: '' }); refresh(); }, onError: onErr(t) });
  const delDept = useMutation({ mutationFn: (id: string) => api(contracts.adminCatalog.deleteDepartment, { params: { id } }), onSuccess: refresh, onError: onErr(t) });
  const addMap = useMutation({
    mutationFn: () => api(contracts.adminCatalog.createInstitutionSubject, { body: { institutionId: d.data!.id, departmentId: map.departmentId || null, semester: map.semester ? Number(map.semester) : null, subjectMasterId: map.subjectMasterId, courseCode: map.courseCode || null } }),
    onSuccess: () => { setMap({ ...map, subjectMasterId: '', courseCode: '' }); refresh(); },
    onError: onErr(t),
  });
  const delMap = useMutation({ mutationFn: (id: string) => api(contracts.adminCatalog.deleteInstitutionSubject, { params: { id } }), onSuccess: refresh, onError: onErr(t) });
  return (
    <Sheet open onOpenChange={(o) => !o && onClose()} side="right" title={d.data?.name ?? '…'} description={d.data ? `${d.data.shortName} · ${d.data.city ?? ''}` : undefined}>
      {d.isLoading ? <Skeleton className="h-64" /> : d.isError || !d.data ? <ErrorState onRetry={() => void d.refetch()} /> : (
        <div className="flex flex-col gap-6">
          <section>
            <h3 className="mb-2 font-semibold">{t('onb.dept.label')}</h3>
            <ul className="flex flex-col divide-y divide-border rounded-control border border-border">
              {d.data.departments.map((x) => (
                <li key={x.id} className="flex items-center gap-3 px-3 py-2">
                  <span className="latin w-14 shrink-0 font-semibold">{x.shortName}</span>
                  <span className="min-w-0 flex-1 truncate text-sm">{x.name} <span className="text-muted">· {x.programs.map((p) => p.name).join(', ')}</span></span>
                  <Button variant="ghost" size="sm" aria-label={t('common.delete')} onClick={() => delDept.mutate(x.id)} icon={<Trash2 className="size-4" aria-hidden />} />
                </li>
              ))}
            </ul>
            <div className="mt-2 flex gap-2">
              <label className="sr-only" htmlFor="d-short">{t('admin.inst.short')}</label>
              <Input id="d-short" value={dept.shortName} onChange={(e) => setDept({ ...dept, shortName: e.target.value })} placeholder="CSE" className="latin h-11 w-24" />
              <label className="sr-only" htmlFor="d-name">{t('admin.inst.name')}</label>
              <Input id="d-name" value={dept.name} onChange={(e) => setDept({ ...dept, name: e.target.value })} placeholder="Computer Science and Engineering" className="h-11" />
              <Button onClick={() => addDept.mutate()} disabled={dept.name.length < 2 || dept.shortName.length < 2} loading={addDept.isPending}>{t('admin.cat.add')}</Button>
            </div>
          </section>
          <section>
            <h3 className="mb-2 font-semibold">{t('admin.cat.codes')}</h3>
            <ul className="flex max-h-72 flex-col divide-y divide-border overflow-y-auto rounded-control border border-border">
              {d.data.subjects.map((s) => (
                <li key={s.id} className="flex items-center gap-3 px-3 py-2 text-sm">
                  <span className="latin w-20 shrink-0 font-semibold">{s.courseCode ?? '—'}</span>
                  <span className="latin min-w-0 flex-1 truncate">{s.subjectName}</span>
                  <span className="text-xs text-muted">{s.semester ? t('admin.cat.sem', { n: s.semester }) : ''}</span>
                  <Button variant="ghost" size="sm" aria-label={t('common.delete')} onClick={() => delMap.mutate(s.id)} icon={<Trash2 className="size-4" aria-hidden />} />
                </li>
              ))}
            </ul>
            <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
              <Select aria-label={t('onb.dept.label')} value={map.departmentId} onChange={(e) => setMap({ ...map, departmentId: e.target.value })} className="h-11">
                <option value="">{t('onb.dept.label')}</option>
                {d.data.departments.map((x) => <option key={x.id} value={x.id}>{x.shortName}</option>)}
              </Select>
              <Input aria-label={t('onb.sem.current')} inputMode="numeric" value={map.semester} onChange={(e) => setMap({ ...map, semester: e.target.value.replace(/\D/g, '') })} placeholder={t('onb.s3')} className="h-11" />
              <Select aria-label={t('onb.exam.subject')} value={map.subjectMasterId} onChange={(e) => setMap({ ...map, subjectMasterId: e.target.value })} className="h-11">
                <option value="">{t('onb.exam.subject')}</option>
                {subjects.data?.items.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </Select>
              <Input aria-label={t('admin.cat.code')} value={map.courseCode} onChange={(e) => setMap({ ...map, courseCode: e.target.value })} placeholder="CSE-2203" className="latin h-11" />
            </div>
            <Button className="mt-2" onClick={() => addMap.mutate()} disabled={!map.subjectMasterId} loading={addMap.isPending}>{t('admin.cat.add')}</Button>
          </section>
        </div>
      )}
    </Sheet>
  );
}

function Subjects() {
  const t = useT();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [v, setV] = useState({ name: '', slug: '', aliases: '' });
  const list = useQuery({ queryKey: ['admin', 'catalog', 'subjects', q], queryFn: () => api(contracts.adminCatalog.subjects, { query: { q: q || undefined } }), placeholderData: (p) => p });
  const create = useMutation({
    mutationFn: () => api(contracts.adminCatalog.createSubject, { body: { name: v.name, slug: v.slug, aliases: v.aliases.split(',').map((s) => s.trim()).filter(Boolean), category: 'general' } }),
    onSuccess: () => { setV({ name: '', slug: '', aliases: '' }); void qc.invalidateQueries({ queryKey: ['admin', 'catalog', 'subjects'] }); },
    onError: onErr(t),
  });
  const approve = useMutation({ mutationFn: (id: string) => api(contracts.adminCatalog.updateSubject, { params: { id }, body: { status: 'approved' } }), onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin', 'catalog', 'subjects'] }), onError: onErr(t) });
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <Card className="overflow-hidden">
        <div className="border-b border-border p-3">
          <label htmlFor="s-q" className="sr-only">{t('common.search')}</label>
          <Input id="s-q" value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('common.search')} className="h-11" />
        </div>
        {list.isLoading ? <div className="p-4"><Skeleton className="h-40" /></div> : list.isError ? <ErrorState onRetry={() => void list.refetch()} /> : (
          <ul className="divide-y divide-border">
            {list.data?.items.map((s) => (
              <li key={s.id} className="flex items-center gap-3 px-4 py-3">
                <span className="min-w-0 flex-1">
                  <span className="latin block font-medium">{s.name}</span>
                  <span className="latin block truncate text-xs text-muted">{s.aliases.join(' · ')}</span>
                </span>
                <span className="latin tnum text-sm text-muted">{formatNumber(s.courseCount, t.locale)}</span>
                {s.status !== 'approved' ? <Button size="sm" variant="soft" onClick={() => approve.mutate(s.id)}>{t('admin.inst.approve')}</Button> : <Badge tone="success">{t('admin.inst.status.approved')}</Badge>}
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card className="h-fit p-4">
        <h3 className="mb-3 font-semibold">{t('admin.cat.addSubject')}</h3>
        <div className="flex flex-col gap-3">
          <Field label={t('admin.inst.name')}>{(p) => <Input {...p} value={v.name} onChange={(e) => setV({ ...v, name: e.target.value, slug: e.target.value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') })} />}</Field>
          <Field label="Slug">{(p) => <Input {...p} value={v.slug} onChange={(e) => setV({ ...v, slug: e.target.value })} className="latin" />}</Field>
          <Field label={t('admin.cat.aliases')} hint={t('admin.cat.aliasesHint')}>{(p) => <Input {...p} value={v.aliases} onChange={(e) => setV({ ...v, aliases: e.target.value })} />}</Field>
          <Button onClick={() => create.mutate()} disabled={v.name.length < 2 || !v.slug} loading={create.isPending}>{t('admin.cat.add')}</Button>
        </div>
      </Card>
    </div>
  );
}
