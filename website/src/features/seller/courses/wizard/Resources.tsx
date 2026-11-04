import { useMemo, useState } from 'react';
import { Button, Field, Input, ProgressBar, toast } from '@/components';
import { ApiError } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { useWizard } from './context';
import { Block } from './StepBasics';
import { ACCEPT, baseName, FileDrop, FileStack, FileText, formatBytes, Link2, linkDocument, MAX, normalizeDigits, Presentation, FileClock, type DocKind, UploadCloud, uploadDocument } from '../../parts';

const KINDS: { v: DocKind; icon: typeof FileText }[] = [
  { v: 'notes', icon: FileText },
  { v: 'previous_questions', icon: FileClock },
  { v: 'slides', icon: Presentation },
];

/** Course-level documents (not attached to a lesson): notes, previous questions with year, slides. */
export function Resources() {
  const t = useT();
  const w = useWizard();
  const used = useMemo(() => new Set(w.tree.flatMap((s) => s.chapters.flatMap((c) => c.lessons.map((l) => l.documentId))).filter(Boolean)), [w.tree]);
  const docs = w.documents.filter((d) => !used.has(d.id));
  const [kind, setKind] = useState<DocKind>('notes');
  const [title, setTitle] = useState('');
  const [year, setYear] = useState('');
  const [mode, setMode] = useState<'file' | 'link'>('file');
  const [url, setUrl] = useState('');
  const [progress, setProgress] = useState<number | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const yearNum = year ? Number(normalizeDigits(year)) : null;
  const check = () => {
    if (kind === 'previous_questions' && (!yearNum || yearNum < 1990 || yearNum > 2100)) return t('wiz.res.yearErr');
    return null;
  };
  const reset = () => {
    setTitle('');
    setYear('');
    setUrl('');
  };

  const onFile = async (f: File) => {
    const e = check();
    if (e) return setErr(e);
    if (f.size > MAX.doc) return setErr(t('seller.apply.fileTooBig', { mb: localDigits(100, t.locale) }));
    setErr(null);
    setProgress(0);
    try {
      const d = await uploadDocument({ courseId: w.id, file: f, docKind: kind, title: title.trim() || baseName(f.name), year: yearNum, onProgress: setProgress });
      w.addDocument(d);
      reset();
      toast.success(t('wiz.res.added'));
    } catch (x) {
      setErr(x instanceof ApiError ? x.message : t('seller.upload.failed'));
    } finally {
      setProgress(null);
    }
  };

  const onLink = async () => {
    const e = check() ?? (!/^https?:\/\/\S+$/i.test(url.trim()) ? t('wiz.lesson.linkErr') : null) ?? (!title.trim() ? t('wiz.res.titleErr') : null);
    if (e) return setErr(e);
    setErr(null);
    setBusy(true);
    try {
      w.addDocument(await linkDocument({ courseId: w.id, url: url.trim(), docKind: kind, title: title.trim(), year: yearNum }));
      reset();
      toast.success(t('wiz.res.added'));
    } catch (x) {
      setErr(x instanceof ApiError ? x.message : t('state.errorBody'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Block id="res" title={t('wiz.res.title')} body={t('wiz.res.body')} aside={<FileStack className="size-5 text-muted" strokeWidth={1.75} aria-hidden />}>
      {docs.length > 0 && (
        <ul className="mb-4 divide-y divide-border rounded-control border border-border">
          {docs.map((d) => {
            const K = KINDS.find((k) => k.v === d.kind)?.icon ?? FileText;
            return (
              <li key={d.id} className="flex items-center gap-3 px-3 py-2.5 text-sm">
                <K className="size-[18px] shrink-0 text-primary" strokeWidth={1.75} aria-hidden />
                <span className="min-w-0 flex-1">
                  <span className="latin block truncate font-medium">{d.title}</span>
                  <span className="text-xs text-muted">
                    {t(`wiz.doc.${d.kind}`)}
                    {d.year ? ` · ${localDigits(d.year, t.locale)}` : ''}
                    {d.size ? ` · ${formatBytes(d.size)}` : ''}
                    {d.externalUrl ? ` · ${t('wiz.res.link')}` : ''}
                  </span>
                </span>
              </li>
            );
          })}
        </ul>
      )}
      <div className="rounded-control bg-surface-2 p-3 sm:p-4">
        <div role="radiogroup" aria-label={t('wiz.res.kind')} className="flex flex-wrap gap-2">
          {KINDS.map((k) => (
            <button key={k.v} type="button" role="radio" aria-checked={kind === k.v} onClick={() => setKind(k.v)} className={cn('inline-flex h-11 items-center gap-1.5 rounded-full border px-3.5 text-sm font-medium', kind === k.v ? 'border-primary bg-primary text-on-primary' : 'border-border bg-surface hover:border-border-strong')}>
              <k.icon className="size-4" aria-hidden /> {t(`wiz.doc.${k.v}`)}
            </button>
          ))}
        </div>
        <div className={cn('mt-3 grid gap-3', kind === 'previous_questions' ? 'sm:grid-cols-[minmax(0,1fr)_120px]' : '')}>
          <Field label={t('wiz.res.docTitle')}>{(p) => <Input {...p} value={title} maxLength={200} className="latin" placeholder={kind === 'previous_questions' ? 'CSE-2203 Midterm Questions' : 'Linked List — Hand Notes'} onChange={(e) => setTitle(e.target.value)} />}</Field>
          {kind === 'previous_questions' && <Field label={t('wiz.res.year')}>{(p) => <Input {...p} inputMode="numeric" maxLength={4} className="latin tnum" value={year} placeholder="2024" onChange={(e) => setYear(e.target.value)} />}</Field>}
        </div>
        <div className="mt-3 flex gap-1 rounded-control border border-border bg-surface p-1 sm:w-fit" role="tablist" aria-label={t('wiz.res.mode')}>
          {(['file', 'link'] as const).map((m) => (
            <button key={m} type="button" role="tab" aria-selected={mode === m} onClick={() => setMode(m)} className={cn('inline-flex h-10 flex-1 items-center justify-center gap-1.5 rounded-[10px] px-3 text-sm font-medium', mode === m ? 'bg-primary-soft text-primary-fg' : 'text-muted hover:text-text')}>
              {m === 'file' ? <UploadCloud className="size-4" aria-hidden /> : <Link2 className="size-4" aria-hidden />}
              {t(`wiz.res.mode.${m}`)}
            </button>
          ))}
        </div>
        <div className="mt-3">
          {mode === 'file' ? (
            <FileDrop accept={ACCEPT.doc} onFile={(f) => void onFile(f)} disabled={progress !== null} title={t('wiz.res.upload')} hint={t('wiz.res.uploadHint')} />
          ) : (
            <div className="flex flex-col gap-2 sm:flex-row">
              <Input aria-label={t('wiz.lesson.linkUrl')} type="url" inputMode="url" className="latin" value={url} placeholder="https://drive.google.com/…" onChange={(e) => setUrl(e.target.value)} />
              <Button variant="secondary" loading={busy} onClick={() => void onLink()} className="shrink-0">
                {t('wiz.res.addLink')}
              </Button>
            </div>
          )}
          {progress !== null && <ProgressBar className="mt-2" value={progress} tone="primary" label={t('seller.upload.progress')} />}
          {err && (
            <p role="alert" className="mt-2 text-[13px] text-danger-fg">
              {err}
            </p>
          )}
        </div>
      </div>
    </Block>
  );
}
