import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Dialog, Field, Input, Select, Switch, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, ordinal, useT } from '@/lib/i18n';
import { useWizard } from './context';
import { emptyTarget, targetIssue, type TargetDraft } from './model';
import { AlertCircle, BookText, ChipsInput, Combobox, Globe2, InstitutionPicker, Plus, Trash2, X } from '../../parts';

export function StepTarget() {
  const t = useT();
  const { targets, setTargets, savers } = useWizard();
  const patch = (key: string, p: Partial<TargetDraft>) => setTargets((ts) => ts.map((x) => (x.key === key ? { ...x, ...p } : x)));
  return (
    <div className="flex flex-col gap-5">
      <div className="flex gap-3 rounded-card border border-border bg-surface-2 p-4 text-sm text-muted">
        <BookText className="mt-0.5 size-5 shrink-0 text-primary" strokeWidth={1.75} aria-hidden />
        <p>{t('wiz.target.intro')}</p>
      </div>
      {savers.targets.status === 'error' && savers.targets.error && (
        <p role="alert" className="flex items-start gap-2 rounded-control border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-fg">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden /> {savers.targets.error}
        </p>
      )}
      {targets.map((x, i) => (
        <TargetCard key={x.key} n={i + 1} value={x} onChange={(p) => patch(x.key, p)} onRemove={targets.length > 1 ? () => setTargets((ts) => ts.filter((y) => y.key !== x.key)) : undefined} />
      ))}
      {targets.length < 20 && (
        <button type="button" onClick={() => setTargets((ts) => [...ts, emptyTarget()])} className="flex min-h-14 items-center justify-center gap-2 rounded-card border border-dashed border-border-strong text-sm font-semibold text-primary-fg hover:border-primary hover:bg-primary-soft">
          <Plus className="size-4" aria-hidden /> {t('wiz.target.add')}
        </button>
      )}
    </div>
  );
}

function TargetCard({ n, value: x, onChange, onRemove }: { n: number; value: TargetDraft; onChange: (p: Partial<TargetDraft>) => void; onRemove?: () => void }) {
  const t = useT();
  const issue = targetIssue(x);
  const depts = useQuery({ queryKey: ['departments', x.institutionId], queryFn: () => api(contracts.catalog.departments, { query: { institutionId: x.institutionId! } }), enabled: !!x.institutionId && !x.isGeneral, staleTime: 5 * 60_000 });
  const progs = useQuery({ queryKey: ['programs', x.departmentId], queryFn: () => api(contracts.catalog.programs, { query: { departmentId: x.departmentId! } }), enabled: !!x.departmentId && !x.isGeneral, staleTime: 5 * 60_000 });
  const semesters = progs.data?.items.find((p) => p.id === x.programId)?.semesters ?? 12;

  return (
    <section className="surface-card p-4 sm:p-6" aria-label={t('wiz.target.n', { n: localDigits(n, t.locale) })}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 font-semibold">
          <span className="latin flex size-7 items-center justify-center rounded-full bg-primary-soft text-xs font-bold text-primary-fg">{localDigits(n, t.locale)}</span>
          {t('wiz.target.n', { n: localDigits(n, t.locale) })}
          {x.institutionShort && !x.isGeneral && <span className="latin text-sm font-medium text-muted">· {x.institutionShort}</span>}
        </h3>
        <div className="flex items-center gap-1">
          <label className="flex h-11 cursor-pointer items-center gap-2.5 rounded-control px-2 text-sm font-medium">
            <Globe2 className="size-4 text-muted" aria-hidden />
            {t('wiz.target.general')}
            <Switch checked={x.isGeneral} onCheckedChange={(v) => onChange({ isGeneral: v })} label={t('wiz.target.general')} />
          </label>
          {onRemove && (
            <button type="button" onClick={onRemove} className="flex size-11 items-center justify-center rounded-control text-muted hover:bg-danger-soft hover:text-danger-fg" aria-label={t('wiz.target.remove', { n: localDigits(n, t.locale) })}>
              <Trash2 className="size-[18px]" aria-hidden />
            </button>
          )}
        </div>
      </div>

      <div className="mt-4 grid gap-4 md:grid-cols-2">
        {x.isGeneral ? (
          <p className="rounded-control bg-surface-2 px-4 py-3 text-sm text-muted md:col-span-2">{t('wiz.target.generalHint')}</p>
        ) : (
          <>
            <Field label={t('wiz.target.institution')} className="md:col-span-2" error={issue === 'institution' ? t('wiz.target.needInstitution') : undefined}>
              {(p) => (
                <InstitutionPicker
                  {...p}
                  value={x.institutionId ? { id: x.institutionId, label: x.institutionShort ?? '', name: x.institutionName ?? undefined } : null}
                  onChange={(v) => onChange({ institutionId: v?.id ?? null, institutionShort: v?.label ?? null, institutionName: v?.name ?? null, departmentId: null, programId: null })}
                />
              )}
            </Field>
            <Field label={t('wiz.target.department')}>
              {(p) => (
                <Select {...p} value={x.departmentId ?? ''} disabled={!x.institutionId || depts.isLoading} onChange={(e) => onChange({ departmentId: e.target.value || null, programId: null })}>
                  <option value="">{x.institutionId ? t('wiz.target.anyDept') : t('wiz.target.pickInstFirst')}</option>
                  {depts.data?.items.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.shortName} — {t.locale === 'bn' ? (d.nameBn ?? d.name) : d.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label={t('wiz.target.program')}>
              {(p) => (
                <Select {...p} value={x.programId ?? ''} disabled={!x.departmentId || progs.isLoading} onChange={(e) => onChange({ programId: e.target.value || null })}>
                  <option value="">{t('wiz.target.anyProgram')}</option>
                  {progs.data?.items.map((pr) => (
                    <option key={pr.id} value={pr.id}>
                      {pr.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          </>
        )}
        <Field label={t('wiz.target.semester')}>
          {(p) => (
            <Select {...p} value={x.semester ?? ''} onChange={(e) => onChange({ semester: e.target.value ? Number(e.target.value) : null })}>
              <option value="">{t('wiz.target.anySemester')}</option>
              {Array.from({ length: semesters }, (_, i) => i + 1).map((s) => (
                <option key={s} value={s}>
                  {t('common.semesterN', { ord: ordinal(s, t.locale) })}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label={t('wiz.target.code')} hint={t('wiz.target.codeHint')}>
          {(p) => <Input {...p} value={x.courseCode} maxLength={30} className="latin uppercase" placeholder="CSE-2203" onChange={(e) => onChange({ courseCode: e.target.value })} />}
        </Field>
        <Field label={t('wiz.target.subject')} className="md:col-span-2" error={issue === 'subject' ? t('wiz.target.needSubject') : undefined}>
          {(p) => <SubjectPicker {...p} target={x} onChange={onChange} />}
        </Field>
        <Field label={t('wiz.target.batches')} hint={t('wiz.target.batchesHint')}>
          {(p) => <ChipsInput id={p.id} label={t('wiz.target.batches')} values={x.batches} onChange={(v) => onChange({ batches: v })} placeholder="56" suggestions={['55', '56', '57']} />}
        </Field>
        <Field label={t('wiz.target.sections')} hint={t('wiz.target.sectionsHint')}>
          {(p) => <ChipsInput id={p.id} label={t('wiz.target.sections')} values={x.sections} onChange={(v) => onChange({ sections: v })} placeholder="A" suggestions={['A', 'B', 'C']} />}
        </Field>
      </div>
    </section>
  );
}

function SubjectPicker({ target: x, onChange, id, ...aria }: { target: TargetDraft; onChange: (p: Partial<TargetDraft>) => void; id: string; 'aria-invalid'?: boolean; 'aria-describedby'?: string }) {
  const t = useT();
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [propose, setPropose] = useState(false);
  useEffect(() => {
    const h = setTimeout(() => setDebounced(q.trim()), 200);
    return () => clearTimeout(h);
  }, [q]);
  const filters = x.isGeneral ? {} : { institutionId: x.institutionId ?? undefined, departmentId: x.departmentId ?? undefined, semester: x.semester ?? undefined };
  const list = useQuery({ queryKey: ['subjects', debounced, filters], queryFn: () => api(contracts.catalog.subjects, { query: { q: debounced || undefined, ...filters } }), enabled: !x.subjectMasterId, placeholderData: (p) => p, staleTime: 60_000 });

  if (x.subjectMasterId)
    return (
      <div className="flex min-h-12 items-center gap-3 rounded-control border border-border bg-surface px-3 py-1.5">
        <BookText className="size-[18px] shrink-0 text-primary" strokeWidth={1.75} aria-hidden />
        <span className="latin min-w-0 flex-1 truncate text-sm font-semibold">{x.subjectName ?? t('wiz.target.subjectPicked')}</span>
        <button type="button" onClick={() => onChange({ subjectMasterId: null, subjectName: null })} className="-mr-1 flex size-10 items-center justify-center rounded-control text-muted hover:bg-surface-2 hover:text-text" aria-label={t('wiz.target.subjectChange')}>
          <X className="size-4" aria-hidden />
        </button>
      </div>
    );

  return (
    <>
      <Combobox
        id={id}
        {...aria}
        value={q}
        onValueChange={setQ}
        items={list.data?.items ?? []}
        getKey={(s) => s.id}
        loading={list.isFetching}
        placeholder={t('wiz.target.subjectPh')}
        label={t('wiz.target.subject')}
        empty={t('wiz.target.subjectNone')}
        onSelect={(s) => {
          onChange({ subjectMasterId: s.id, subjectName: s.name, ...(!x.courseCode.trim() && s.courseCode ? { courseCode: s.courseCode } : {}) });
          setQ('');
        }}
        renderItem={(s) => (
          <>
            <span className="latin min-w-0 flex-1 truncate">{s.name}</span>
            {s.courseCode && <span className="latin shrink-0 text-xs font-semibold text-muted">{s.courseCode}</span>}
          </>
        )}
        footer={
          <button type="button" onClick={() => setPropose(true)} className="flex min-h-11 w-full items-center gap-2 rounded-[10px] px-3 text-left text-sm font-semibold text-primary-fg hover:bg-primary-soft">
            <Plus className="size-4" aria-hidden /> {t('wiz.target.propose')}
          </button>
        }
      />
      <ProposeDialog open={propose} onOpenChange={setPropose} initialName={q} onDone={(id, name) => onChange({ subjectMasterId: id, subjectName: name })} />
    </>
  );
}

function ProposeDialog({ open, onOpenChange, initialName, onDone }: { open: boolean; onOpenChange: (o: boolean) => void; initialName: string; onDone: (id: string, name: string) => void }) {
  const t = useT();
  const [name, setName] = useState(initialName);
  const [aliases, setAliases] = useState('');
  useEffect(() => {
    if (open) setName(initialName);
  }, [open, initialName]);
  const m = useMutation({
    mutationFn: () => api(contracts.sellerCourses.proposeSubject, { body: { name: name.trim(), aliases: aliases.split(',').map((a) => a.trim()).filter(Boolean).slice(0, 10) } }),
    onSuccess: (r) => {
      toast.success(t('wiz.target.proposed'));
      onDone(r.id, name.trim());
      onOpenChange(false);
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : t('state.errorBody')),
  });
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t('wiz.target.propose')}
      description={t('wiz.target.proposeBody')}
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={() => m.mutate()} loading={m.isPending} disabled={name.trim().length < 2}>
            {t('wiz.target.proposeSend')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <Field label={t('wiz.target.subjectName')}>{(p) => <Input {...p} value={name} maxLength={160} className="latin" placeholder="Numerical Methods" onChange={(e) => setName(e.target.value)} />}</Field>
        <Field label={t('wiz.target.aliases')} hint={t('wiz.target.aliasesHint')}>
          {(p) => <Input {...p} value={aliases} className="latin" placeholder="Numerical Analysis, NM" onChange={(e) => setAliases(e.target.value)} />}
        </Field>
      </div>
    </Dialog>
  );
}
