import { useState, type ReactNode } from 'react';
import { COURSE_TYPES, EXAM_TYPES } from '@shikkhasathi/shared/types';
import { Chip, Field, Input, ProgressBar, Select, Textarea, toast } from '@/components';
import { ApiError } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { useWizard } from './context';
import { COVER_COLORS, type Basics } from './model';
import { ACCEPT, Check, CircleSlash, Counter, FileDrop, ImagePlus, ListEditor, MAX, Plus, MessageCircleQuestion, Trash2, uploadCover, X } from '../../parts';

export function Block({ title, body, children, aside, id }: { title: string; body?: string; children: ReactNode; aside?: ReactNode; id?: string }) {
  return (
    <section className="surface-card p-4 sm:p-6" aria-labelledby={id}>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 id={id} className="font-semibold">
            {title}
          </h3>
          {body && <p className="text-sm text-muted">{body}</p>}
        </div>
        {aside}
      </div>
      {children}
    </section>
  );
}

export function StepBasics() {
  const t = useT();
  const { basics: b, setBasics } = useWizard();
  const set = <K extends keyof Basics>(k: K, v: Basics[K]) => setBasics((x) => ({ ...x, [k]: v }));
  const [coverProgress, setCoverProgress] = useState<number | null>(null);
  const titleErr = b.title.trim().length < 5 ? t('wiz.basics.titleErr') : undefined;

  const onCover = async (f: File) => {
    if (f.size > MAX.cover) return toast.error(t('seller.apply.fileTooBig', { mb: localDigits(5, t.locale) }));
    setCoverProgress(0);
    try {
      const url = await uploadCover(f, setCoverProgress);
      set('coverUrl', url);
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : t('seller.upload.failed'));
    } finally {
      setCoverProgress(null);
    }
  };

  return (
    <div className="flex flex-col gap-5">
      <Block id="b-intro" title={t('wiz.basics.intro')} body={t('wiz.basics.introBody')}>
        <div className="flex flex-col gap-4">
          <Field label={t('wiz.basics.title')} error={titleErr} hint={titleErr ? undefined : t('wiz.basics.titleHint')} trailing={<Counter n={b.title.length} max={160} />}>
            {(p) => <Input {...p} value={b.title} maxLength={160} className="latin" placeholder="Data Structures — Midterm Crash Course (CSE-2203)" onChange={(e) => set('title', e.target.value)} />}
          </Field>
          <Field label={t('wiz.basics.short')} hint={t('wiz.basics.shortHint')} trailing={<Counter n={b.shortDescription.length} max={300} />}>
            {(p) => <Textarea {...p} rows={2} value={b.shortDescription} maxLength={300} placeholder={t('wiz.basics.shortPh')} onChange={(e) => set('shortDescription', e.target.value)} />}
          </Field>
          <Field label={t('wiz.basics.desc')} trailing={<Counter n={b.description.length} max={10_000} />}>
            {(p) => <Textarea {...p} rows={7} value={b.description} maxLength={10_000} placeholder={t('wiz.basics.descPh')} onChange={(e) => set('description', e.target.value)} />}
          </Field>
        </div>
      </Block>

      <div className="grid gap-5 xl:grid-cols-2">
        <Block id="b-learn" title={t('wiz.basics.learnings')} body={t('wiz.basics.learningsBody')}>
          <ListEditor label={t('wiz.basics.learnings')} values={b.learnings} onChange={(v) => set('learnings', v)} max={15} placeholder={t('wiz.basics.learningsPh')} addLabel={t('wiz.basics.addPoint')} marker={() => <Check className="size-4 text-success" aria-hidden />} />
        </Block>
        <Block id="b-notfor" title={t('wiz.basics.notFor')} body={t('wiz.basics.notForBody')}>
          <ListEditor label={t('wiz.basics.notFor')} values={b.notFor} onChange={(v) => set('notFor', v)} max={10} placeholder={t('wiz.basics.notForPh')} addLabel={t('wiz.basics.addPoint')} marker={() => <CircleSlash className="size-4 text-muted" aria-hidden />} />
        </Block>
      </div>

      <Block id="b-cover" title={t('wiz.basics.cover')} body={t('wiz.basics.coverBody')}>
        <div className="grid gap-4 md:grid-cols-[200px_minmax(0,1fr)]">
          <div className="relative aspect-video overflow-hidden rounded-control border border-border" style={{ background: b.coverColor ?? '#4F46E5' }}>
            {b.coverUrl ? (
              <img src={b.coverUrl} alt="" width={400} height={225} className="size-full object-cover" />
            ) : (
              <span className="latin absolute inset-x-3 bottom-2 line-clamp-2 text-xs font-bold text-white">{b.title}</span>
            )}
          </div>
          <div className="flex flex-col gap-3">
            {b.coverUrl ? (
              <div className="flex gap-2">
                <FileDrop compact accept={ACCEPT.image} onFile={(f) => void onCover(f)} disabled={coverProgress !== null} title={t('seller.upload.replace')} icon={<ImagePlus className="size-5" strokeWidth={1.75} aria-hidden />} />
                <button type="button" onClick={() => set('coverUrl', null)} className="flex size-12 shrink-0 items-center justify-center rounded-control border border-border text-muted hover:bg-danger-soft hover:text-danger-fg" aria-label={t('wiz.basics.removeCover')}>
                  <Trash2 className="size-[18px]" aria-hidden />
                </button>
              </div>
            ) : (
              <FileDrop accept={ACCEPT.image} onFile={(f) => void onCover(f)} disabled={coverProgress !== null} title={t('wiz.basics.coverUpload')} hint={t('wiz.basics.coverHint')} icon={<ImagePlus className="size-5" strokeWidth={1.75} aria-hidden />} />
            )}
            {coverProgress !== null && <ProgressBar value={coverProgress} tone="primary" label={t('seller.upload.progress')} />}
            <div>
              <p className="mb-2 text-sm font-medium">{b.coverUrl ? t('wiz.basics.colorFallback') : t('wiz.basics.color')}</p>
              <div role="radiogroup" aria-label={t('wiz.basics.color')} className="flex flex-wrap gap-2">
                {COVER_COLORS.map((c, i) => (
                  <button
                    key={c}
                    type="button"
                    role="radio"
                    aria-checked={b.coverColor === c}
                    aria-label={t('wiz.basics.colorN', { n: localDigits(i + 1, t.locale) })}
                    onClick={() => set('coverColor', c)}
                    className={cn('flex size-11 items-center justify-center rounded-full border-2 transition-transform duration-150 hover:scale-105', b.coverColor === c ? 'border-text' : 'border-transparent')}
                  >
                    <span className="flex size-8 items-center justify-center rounded-full" style={{ background: c }}>
                      {b.coverColor === c && <Check className="size-4 text-white" strokeWidth={3} aria-hidden />}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </Block>

      <Block id="b-kind" title={t('wiz.basics.kind')} body={t('wiz.basics.kindBody')}>
        <div className="flex flex-col gap-5">
          <fieldset>
            <legend className="mb-2 text-sm font-medium">{t('wiz.basics.examType')}</legend>
            <div className="flex flex-wrap gap-2">
              {EXAM_TYPES.map((x) => (
                <Chip key={x} selected={b.examType === x} onClick={() => set('examType', x)} className="h-11">
                  {t(`examType.${x}`)}
                </Chip>
              ))}
            </div>
          </fieldset>
          <fieldset>
            <legend className="mb-2 text-sm font-medium">{t('wiz.basics.courseType')}</legend>
            <div className="flex flex-wrap gap-2">
              {COURSE_TYPES.map((x) => (
                <Chip key={x} selected={b.courseType === x} onClick={() => set('courseType', x)} className="h-11">
                  {t(`wiz.courseType.${x}`)}
                </Chip>
              ))}
            </div>
          </fieldset>
          <Field label={t('common.language')} className="max-w-xs">
            {(p) => (
              <Select {...p} value={b.language} onChange={(e) => set('language', e.target.value as Basics['language'])}>
                {(['bn', 'en', 'mixed'] as const).map((l) => (
                  <option key={l} value={l}>
                    {t(`wiz.lang.${l}`)}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        </div>
      </Block>

      <FaqEditor />
    </div>
  );
}

function FaqEditor() {
  const t = useT();
  const { basics, setBasics } = useWizard();
  const faq = basics.faq;
  const setFaq = (f: Basics['faq']) => setBasics((x) => ({ ...x, faq: f }));
  return (
    <Block id="b-faq" title={t('wiz.basics.faq')} body={t('wiz.basics.faqBody')} aside={<MessageCircleQuestion className="size-5 text-muted" strokeWidth={1.75} aria-hidden />}>
      {faq.length === 0 && <p className="mb-3 text-sm text-muted">{t('wiz.basics.faqEmpty')}</p>}
      <ol className="flex flex-col gap-3">
        {faq.map((f, i) => (
          <li key={i} className="rounded-control border border-border p-3">
            <div className="flex items-start gap-2">
              <span className="latin mt-3 w-6 shrink-0 text-center text-sm font-semibold text-muted">{localDigits(i + 1, t.locale)}</span>
              <div className="flex min-w-0 flex-1 flex-col gap-2">
                <Input aria-label={t('wiz.basics.faqQ')} value={f.q} maxLength={300} placeholder={i === 0 ? t('wiz.basics.faqQPh') : t('wiz.basics.faqQ')} onChange={(e) => setFaq(faq.map((x, j) => (j === i ? { ...x, q: e.target.value } : x)))} />
                <Textarea aria-label={t('wiz.basics.faqA')} rows={2} value={f.a} maxLength={2000} placeholder={i === 0 ? t('wiz.basics.faqAPh') : t('wiz.basics.faqA')} onChange={(e) => setFaq(faq.map((x, j) => (j === i ? { ...x, a: e.target.value } : x)))} />
              </div>
              <button type="button" onClick={() => setFaq(faq.filter((_, j) => j !== i))} className="flex size-11 shrink-0 items-center justify-center rounded-control text-muted hover:bg-surface-2 hover:text-text" aria-label={t('wiz.basics.faqRemove', { n: localDigits(i + 1, t.locale) })}>
                <X className="size-4" aria-hidden />
              </button>
            </div>
          </li>
        ))}
      </ol>
      {faq.length < 15 && (
        <button type="button" onClick={() => setFaq([...faq, { q: '', a: '' }])} className="mt-3 inline-flex h-11 items-center gap-1.5 rounded-control px-2 text-sm font-semibold text-primary-fg hover:bg-primary-soft">
          <Plus className="size-4" aria-hidden /> {t('wiz.basics.faqAdd')}
        </button>
      )}
    </Block>
  );
}
