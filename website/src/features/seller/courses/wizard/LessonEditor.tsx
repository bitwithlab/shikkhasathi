import { useState } from 'react';
import { Button, Field, Input, ProgressBar, Switch, Textarea, toast } from '@/components';
import { ApiError } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import type { LessonDraft, LessonType } from '../../lib/curriculum';
import { useWizard } from './context';
import { useUploads } from './uploads';
import { LESSON_ICON } from './icons';
import { ACCEPT, baseName, CheckCircle2, ExternalLink, FileDrop, FileText, formatBytes, formatDuration, Link2, linkVideo, Lock, MAX, normalizeDigits, parseDuration, PlayCircle, RefreshCw, Star, Trash2, UploadCloud, uploadDocument, uploadVideo, X } from '../../parts';

const TYPES: LessonType[] = ['video', 'pdf', 'text', 'quiz', 'mock'];

export function LessonEditor({ lesson: l, autoFocus }: { lesson: LessonDraft; autoFocus?: boolean }) {
  const t = useT();
  const w = useWizard();
  const set = (p: Partial<LessonDraft>) => w.patchLesson(l.key, p);
  const up = useUploads((s) => s.items[l.key]);
  const { put, patch } = useUploads.getState();
  const [linkMode, setLinkMode] = useState(false);
  const [linkUrl, setLinkUrl] = useState('');
  const [linkDur, setLinkDur] = useState('');
  const [linkErr, setLinkErr] = useState<string | null>(null);
  const [linking, setLinking] = useState(false);

  const fail = (e: unknown) => {
    if ((e as Error).name === 'AbortError') return put(l.key, null);
    patch(l.key, { error: e instanceof ApiError ? e.message : t('seller.upload.failed') });
  };

  const startVideo = (f: File) => {
    if (!f.type.startsWith('video/')) return toast.error(t('wiz.lesson.notVideo'));
    if (f.size > MAX.video) return toast.error(t('seller.apply.fileTooBig', { mb: '8192' }));
    const ctrl = new AbortController();
    put(l.key, { name: f.name, progress: 0, abort: () => ctrl.abort() });
    const title = l.title.trim() || baseName(f.name);
    uploadVideo({ file: f, title, signal: ctrl.signal, onProgress: (p) => patch(l.key, { progress: p }) })
      .then((v) => {
        w.patchLesson(l.key, { videoAssetId: v.id, video: v, ...(l.title.trim() ? {} : { title }) });
        put(l.key, null);
        toast.success(t('wiz.lesson.videoDone'));
      })
      .catch(fail);
  };

  const startPdf = (f: File) => {
    if (f.type !== 'application/pdf') return toast.error(t('wiz.lesson.notPdf'));
    if (f.size > MAX.doc) return toast.error(t('seller.apply.fileTooBig', { mb: localDigits(100, t.locale) }));
    const ctrl = new AbortController();
    put(l.key, { name: f.name, progress: 0, abort: () => ctrl.abort() });
    const title = l.title.trim() || baseName(f.name);
    uploadDocument({ courseId: w.id, file: f, docKind: 'pdf', title, signal: ctrl.signal, onProgress: (p) => patch(l.key, { progress: p }) })
      .then((d) => {
        w.addDocument(d);
        w.patchLesson(l.key, { documentId: d.id, document: d, ...(l.title.trim() ? {} : { title }) });
        put(l.key, null);
        toast.success(t('wiz.lesson.pdfDone'));
      })
      .catch(fail);
  };

  const saveLink = async () => {
    const dur = parseDuration(linkDur);
    if (!/^https:\/\/\S+$/i.test(linkUrl.trim())) return setLinkErr(t('wiz.lesson.linkErr'));
    if (dur == null) return setLinkErr(t('wiz.lesson.durErr'));
    setLinkErr(null);
    setLinking(true);
    try {
      const v = await linkVideo({ title: l.title.trim() || t('wiz.content.newLesson'), url: linkUrl.trim(), durationSec: dur });
      set({ videoAssetId: v.id, video: v, durationSec: dur });
      setLinkMode(false);
    } catch (e) {
      setLinkErr(e instanceof ApiError ? e.message : t('state.errorBody'));
    } finally {
      setLinking(false);
    }
  };

  const uploading = up && !up.error && (
    <div className="rounded-control border border-border bg-surface p-3">
      <div className="mb-2 flex items-center justify-between gap-2 text-sm">
        <span className="latin min-w-0 flex-1 truncate font-medium">{up.name}</span>
        <span className="latin shrink-0 font-semibold tnum">{localDigits(up.progress, t.locale)}%</span>
        <button type="button" onClick={() => up.abort?.()} className="flex size-10 items-center justify-center rounded-control text-muted hover:bg-surface-2" aria-label={t('wiz.lesson.cancelUpload')}>
          <X className="size-4" aria-hidden />
        </button>
      </div>
      <ProgressBar value={up.progress} tone="primary" label={t('seller.upload.progress')} />
      <p className="mt-2 text-xs text-muted">{t('wiz.lesson.uploadKeepOpen')}</p>
    </div>
  );
  const failed = up?.error && (
    <div role="alert" className="flex items-center gap-2 rounded-control border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger-fg">
      <span className="min-w-0 flex-1">{up.error}</span>
      <button type="button" onClick={() => put(l.key, null)} className="inline-flex h-10 items-center gap-1 rounded-control px-2 font-semibold hover:bg-danger/10">
        <RefreshCw className="size-4" aria-hidden /> {t('common.retry')}
      </button>
    </div>
  );

  return (
    <div className="flex flex-col gap-4 border-t border-border px-3 pb-4 pt-3 sm:px-4">
      <Field label={t('wiz.lesson.title')}>
        {(p) => <Input {...p} autoFocus={autoFocus} value={l.title} maxLength={200} className="latin" placeholder="Linked List — Insertion at Head" onChange={(e) => set({ title: e.target.value })} />}
      </Field>

      <fieldset>
        <legend className="mb-2 text-sm font-medium">{t('wiz.lesson.typeLabel')}</legend>
        <div role="radiogroup" aria-label={t('wiz.lesson.typeLabel')} className="grid grid-cols-3 gap-2 sm:grid-cols-5">
          {TYPES.map((ty) => {
            const Icon = LESSON_ICON[ty];
            const soon = ty === 'quiz' || ty === 'mock';
            const sel = l.type === ty;
            return (
              <button
                key={ty}
                type="button"
                role="radio"
                aria-checked={sel}
                disabled={soon && !sel}
                onClick={() => set({ type: ty })}
                className={cn('relative flex h-14 flex-col items-center justify-center gap-0.5 rounded-control border text-xs font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60', sel ? 'border-primary bg-primary-soft text-primary-fg ring-1 ring-primary' : 'border-border bg-surface hover:border-border-strong')}
              >
                <Icon className="size-[18px]" strokeWidth={1.75} aria-hidden />
                {t(`wiz.lesson.type.${ty}`)}
                {soon && <Lock className="absolute right-1.5 top-1.5 size-3 text-muted" aria-hidden />}
              </button>
            );
          })}
        </div>
        <p className="mt-1.5 text-xs text-muted">{t('wiz.lesson.quizSoon')}</p>
      </fieldset>

      {l.type === 'video' && (
        <div className="flex flex-col gap-2">
          {uploading || failed || (l.videoAssetId ? (
            <div className="flex items-center gap-3 rounded-control border border-border bg-surface p-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-[10px] bg-success-soft text-success-fg">
                {l.video?.provider === 'external_link' ? <Link2 className="size-5" aria-hidden /> : <PlayCircle className="size-5" aria-hidden />}
              </span>
              <span className="min-w-0 flex-1 text-sm">
                <span className="latin block truncate font-semibold">{l.video?.externalUrl ?? l.video?.title ?? l.title}</span>
                <span className="flex flex-wrap gap-x-2 text-xs text-muted">
                  <span className={cn(l.video?.status === 'failed' ? 'text-danger-fg' : l.video?.status === 'ready' ? 'text-success-fg' : 'text-warning-fg')}>{t(`wiz.video.status.${l.video?.status ?? 'ready'}`)}</span>
                  {(l.durationSec || l.video?.durationSec) ? <span className="latin tnum">{localDigits(formatDuration(l.durationSec || l.video!.durationSec), t.locale)}</span> : null}
                </span>
              </span>
              <button type="button" onClick={() => set({ videoAssetId: null, video: null, durationSec: 0 })} className="flex size-11 shrink-0 items-center justify-center rounded-control text-muted hover:bg-danger-soft hover:text-danger-fg" aria-label={t('wiz.lesson.removeVideo')}>
                <Trash2 className="size-4" aria-hidden />
              </button>
            </div>
          ) : linkMode ? (
            <div className="flex flex-col gap-3 rounded-control border border-border bg-surface p-3">
              <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_140px]">
                <Field label={t('wiz.lesson.linkUrl')} error={linkErr ?? undefined}>
                  {(p) => <Input {...p} type="url" inputMode="url" className="latin" value={linkUrl} placeholder="https://youtu.be/…" onChange={(e) => setLinkUrl(e.target.value)} />}
                </Field>
                <Field label={t('wiz.lesson.linkDur')}>
                  {(p) => <Input {...p} inputMode="numeric" className="latin tnum" value={linkDur} placeholder="12:30" onChange={(e) => setLinkDur(e.target.value)} />}
                </Field>
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="ghost" onClick={() => setLinkMode(false)}>
                  {t('common.cancel')}
                </Button>
                <Button variant="secondary" loading={linking} onClick={() => void saveLink()}>
                  {t('wiz.lesson.linkSave')}
                </Button>
              </div>
            </div>
          ) : (
            <>
              <FileDrop accept={ACCEPT.video} onFile={startVideo} title={t('wiz.lesson.videoUpload')} hint={t('wiz.lesson.videoHint')} icon={<UploadCloud className="size-5" strokeWidth={1.75} aria-hidden />} />
              <button type="button" onClick={() => setLinkMode(true)} className="inline-flex h-11 items-center gap-1.5 self-start rounded-control px-2 text-sm font-semibold text-primary-fg hover:bg-primary-soft">
                <ExternalLink className="size-4" aria-hidden /> {t('wiz.lesson.giveLink')}
              </button>
            </>
          ))}
        </div>
      )}

      {l.type === 'pdf' && (
        <div className="flex flex-col gap-2">
          {uploading || failed || (l.document ? (
            <div className="flex items-center gap-3 rounded-control border border-border bg-surface p-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-[10px] bg-success-soft text-success-fg">
                <FileText className="size-5" aria-hidden />
              </span>
              <span className="min-w-0 flex-1 text-sm">
                <span className="latin block truncate font-semibold">{l.document.title}</span>
                <span className="latin text-xs text-muted">{l.document.size ? formatBytes(l.document.size) : 'PDF'}</span>
              </span>
              <button type="button" onClick={() => set({ documentId: null, document: null })} className="flex size-11 shrink-0 items-center justify-center rounded-control text-muted hover:bg-danger-soft hover:text-danger-fg" aria-label={t('wiz.lesson.removePdf')}>
                <Trash2 className="size-4" aria-hidden />
              </button>
            </div>
          ) : (
            <FileDrop accept={ACCEPT.pdf} onFile={startPdf} title={t('wiz.lesson.pdfUpload')} hint={t('wiz.lesson.pdfHint')} icon={<FileText className="size-5" strokeWidth={1.75} aria-hidden />} />
          ))}
        </div>
      )}

      {l.type === 'text' && (
        <Field label={t('wiz.lesson.body')} trailing={<span className="latin text-xs text-muted tnum">{localDigits((l.body ?? '').length, t.locale)}</span>}>
          {(p) => <Textarea {...p} rows={8} value={l.body ?? ''} maxLength={50_000} placeholder={t('wiz.lesson.bodyPh')} onChange={(e) => set({ body: e.target.value })} />}
        </Field>
      )}

      {(l.type === 'pdf' || l.type === 'text') && (
        <Field label={t('wiz.lesson.readMinutes')} className="max-w-56">
          {(p) => (
            <Input
              {...p}
              inputMode="numeric"
              className="latin tnum"
              value={l.durationSec ? String(Math.round(l.durationSec / 60)) : ''}
              placeholder="15"
              onChange={(e) => {
                const m = Number(normalizeDigits(e.target.value).replace(/\D/g, '').slice(0, 3));
                set({ durationSec: m * 60 });
              }}
            />
          )}
        </Field>
      )}

      <div className="grid gap-2 sm:grid-cols-2">
        <label className="flex min-h-14 cursor-pointer items-center gap-3 rounded-control border border-border bg-surface px-3 py-2">
          <CheckCircle2 className="size-5 shrink-0 text-success" strokeWidth={1.75} aria-hidden />
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-semibold">{t('wiz.lesson.preview')}</span>
            <span className="block text-xs text-muted">{t('wiz.lesson.previewHint')}</span>
          </span>
          <Switch checked={l.isPreview} onCheckedChange={(v) => set({ isPreview: v })} label={t('wiz.lesson.preview')} />
        </label>
        <label className="flex min-h-14 cursor-pointer items-center gap-3 rounded-control border border-border bg-surface px-3 py-2">
          <Star className="size-5 shrink-0 fill-warning text-warning" aria-hidden />
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-semibold">{t('wiz.lesson.important')}</span>
            <span className="block text-xs text-muted">{t('wiz.lesson.importantHint')}</span>
          </span>
          <Switch checked={l.isImportant} onCheckedChange={(v) => set({ isImportant: v })} label={t('wiz.lesson.important')} />
        </label>
      </div>
    </div>
  );
}
