import { useRef, useState, type DragEvent, type ReactNode } from 'react';
import { Button, Dialog, EmptyState, Illustration, Input } from '@/components';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { countLessons, moveChapter, moveLesson, moveSection, newChapter, newLesson, newSection, stepChapter, stepLesson, totalDuration, type LessonDraft, type Tree } from '../../lib/curriculum';
import { useWizard } from './context';
import { LessonEditor } from './LessonEditor';
import { Resources } from './Resources';
import { useUploads } from './uploads';
import { LESSON_ICON } from './icons';
import { AlertTriangle, ChevronUp, formatDuration, GripVertical, ListTree, Plus, Star, Trash2 } from '../../parts';

type DragItem = { kind: 'section'; s: number } | { kind: 'chapter'; s: number; c: number } | { kind: 'lesson'; s: number; c: number; l: number };
type Over = { key: string; after: boolean } | null;


export function StepContent() {
  const t = useT();
  const { tree, setTree, targets } = useWizard();
  const [open, setOpen] = useState<string | null>(null);
  const [fresh, setFresh] = useState<string | null>(null);
  const [armed, setArmed] = useState<string | null>(null);
  const [over, setOver] = useState<Over>(null);
  const [announce, setAnnounce] = useState('');
  const [confirm, setConfirm] = useState<{ title: string; body: string; run: () => void } | null>(null);
  const drag = useRef<DragItem | null>(null);

  const subjects = targets.map((x) => [x.subjectName, x.courseCode.trim().toUpperCase()].filter(Boolean).join(' · ')).filter(Boolean);

  const update = (fn: (t: Tree) => Tree) => setTree(fn);
  const say = (msg: string) => setAnnounce(msg);

  // ------------------------------------------------ drag & drop (native HTML5; keyboard buttons below)
  const dragProps = (key: string, item: DragItem) => ({
    draggable: armed === key,
    onDragStart: (e: DragEvent) => {
      drag.current = item;
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', key);
      e.stopPropagation();
    },
    onDragEnd: () => {
      drag.current = null;
      setOver(null);
      setArmed(null);
    },
  });
  const sameList = (a: DragItem, b: DragItem) => a.kind === b.kind && (a.kind === 'section' || (a.s === b.s && (a.kind === 'chapter' || (a.kind === 'lesson' && b.kind === 'lesson' && a.c === b.c))));
  const indexOf = (d: DragItem) => (d.kind === 'section' ? d.s : d.kind === 'chapter' ? d.c : d.l);
  const dropProps = (key: string, target: DragItem) => ({
    onDragOver: (e: DragEvent) => {
      const d = drag.current;
      if (!d || d.kind !== target.kind) return;
      e.preventDefault();
      e.stopPropagation();
      e.dataTransfer.dropEffect = 'move';
      const after = sameList(d, target) && indexOf(d) < indexOf(target);
      if (over?.key !== key || over.after !== after) setOver({ key, after });
    },
    onDragLeave: (e: DragEvent) => {
      if (!(e.currentTarget as HTMLElement).contains(e.relatedTarget as Node)) setOver((o) => (o?.key === key ? null : o));
    },
    onDrop: (e: DragEvent) => {
      const d = drag.current;
      if (!d || d.kind !== target.kind) return;
      e.preventDefault();
      e.stopPropagation();
      setOver(null);
      drag.current = null;
      if (d.kind === 'section' && target.kind === 'section') update((tr) => moveSection(tr, d.s, target.s));
      if (d.kind === 'chapter' && target.kind === 'chapter') update((tr) => moveChapter(tr, d, target));
      if (d.kind === 'lesson' && target.kind === 'lesson') update((tr) => moveLesson(tr, d, target));
      say(t('wiz.content.moved'));
    },
  });
  const dropLine = (key: string) => over?.key === key && (over.after ? 'shadow-[inset_0_-3px_0_var(--ss-primary)]' : 'shadow-[inset_0_3px_0_var(--ss-primary)]');
  const handle = (key: string, label: string) => (
    <span
      role="presentation"
      title={t('wiz.content.dragHint')}
      onPointerDown={() => setArmed(key)}
      onPointerUp={() => setArmed(null)}
      className="hidden size-8 shrink-0 cursor-grab items-center justify-center rounded-[8px] text-muted hover:bg-surface-2 active:cursor-grabbing sm:flex"
      aria-label={label}
    >
      <GripVertical className="size-4" aria-hidden />
    </span>
  );

  // ------------------------------------------------ add / delete
  const addSection = () => {
    const n = tree.length + 1;
    const l = newLesson(t('wiz.content.newLesson'));
    update((tr) => [...tr, newSection(t('wiz.content.sectionN', { n: localDigits(n, t.locale) }), [newChapter(t('wiz.content.chapterN', { n: localDigits(1, t.locale) }), [l])])]);
    setOpen(l.key);
    setFresh(l.key);
  };
  const addChapter = (s: number) => {
    const n = (tree[s]?.chapters.length ?? 0) + 1;
    update((tr) => tr.map((sec, i) => (i === s ? { ...sec, chapters: [...sec.chapters, newChapter(t('wiz.content.chapterN', { n: localDigits(n, t.locale) }))] } : sec)));
  };
  const addLesson = (s: number, c: number) => {
    const l = newLesson(t('wiz.content.newLesson'));
    update((tr) => tr.map((sec, i) => (i === s ? { ...sec, chapters: sec.chapters.map((ch, j) => (j === c ? { ...ch, lessons: [...ch.lessons, l] } : ch)) } : sec)));
    setOpen(l.key);
    setFresh(l.key);
  };
  const ask = (title: string, body: string, run: () => void, needed: boolean) => (needed ? setConfirm({ title, body, run }) : run());

  const lessons = countLessons(tree);

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3 rounded-card border border-border bg-surface-2 p-4 text-sm sm:flex-row sm:items-center">
        <ListTree className="size-5 shrink-0 text-primary" strokeWidth={1.75} aria-hidden />
        <p className="min-w-0 flex-1 text-muted">
          {subjects.length ? t('wiz.content.syllabus', { subjects: subjects.join(', ') }) : t('wiz.content.syllabusNone')}
        </p>
        <p className="latin shrink-0 font-semibold tnum">{t('wiz.content.summary', { n: localDigits(lessons, t.locale), d: localDigits(formatDuration(totalDuration(tree)), t.locale) })}</p>
      </div>

      {tree.length === 0 ? (
        <section className="surface-card">
          <EmptyState
            illustration={<Illustration kind="courses" />}
            title={t('wiz.content.emptyTitle')}
            body={t('wiz.content.emptyBody')}
            action={
              <Button variant="soft" onClick={addSection} icon={<Plus className="size-4" aria-hidden />}>
                {t('wiz.content.addSection')}
              </Button>
            }
          />
        </section>
      ) : (
        <ol className="flex flex-col gap-4" aria-label={t('wiz.content.outline')}>
          {tree.map((sec, s) => (
            <li key={sec.key} {...dropProps(sec.key, { kind: 'section', s })} {...dragProps(sec.key, { kind: 'section', s })} className={cn('surface-card overflow-hidden rounded-card', dropLine(sec.key))}>
              <div className="flex items-center gap-2 border-b border-border bg-surface-2/60 px-3 py-2 sm:px-4">
                {handle(sec.key, t('wiz.content.dragSection'))}
                <span className="latin shrink-0 text-xs font-bold uppercase tracking-wide text-muted">{t('wiz.content.section', { n: localDigits(s + 1, t.locale) })}</span>
                <Input
                  aria-label={t('wiz.content.sectionTitle', { n: localDigits(s + 1, t.locale) })}
                  value={sec.title}
                  maxLength={200}
                  onChange={(e) => update((tr) => tr.map((x, i) => (i === s ? { ...x, title: e.target.value } : x)))}
                  className="h-11 min-w-0 flex-1 border-transparent bg-transparent font-semibold hover:border-border focus:bg-surface"
                />
                <MoveButtons
                  label={t('wiz.content.section', { n: localDigits(s + 1, t.locale) })}
                  canUp={s > 0}
                  canDown={s < tree.length - 1}
                  onMove={(dir) => {
                    update((tr) => moveSection(tr, s, s + dir));
                    say(t('wiz.content.movedTo', { n: localDigits(s + dir + 1, t.locale) }));
                  }}
                />
                <IconBtn
                  danger
                  label={t('wiz.content.deleteSection', { n: localDigits(s + 1, t.locale) })}
                  onClick={() => ask(t('wiz.content.confirmSection'), t('wiz.content.confirmBody', { title: sec.title }), () => update((tr) => tr.filter((_, i) => i !== s)), sec.chapters.length > 0)}
                >
                  <Trash2 className="size-[18px]" aria-hidden />
                </IconBtn>
              </div>

              <ol className="flex flex-col gap-3 p-3 sm:p-4">
                {sec.chapters.map((ch, c) => (
                  <li key={ch.key} {...dropProps(ch.key, { kind: 'chapter', s, c })} {...dragProps(ch.key, { kind: 'chapter', s, c })} className={cn('rounded-control border border-border bg-surface', dropLine(ch.key))}>
                    <div className="flex items-center gap-2 px-2 py-1.5 sm:px-3">
                      {handle(ch.key, t('wiz.content.dragChapter'))}
                      <span className="latin shrink-0 text-xs font-semibold text-muted tnum">
                        {localDigits(s + 1, t.locale)}.{localDigits(c + 1, t.locale)}
                      </span>
                      <Input
                        aria-label={t('wiz.content.chapterTitle', { n: localDigits(c + 1, t.locale) })}
                        value={ch.title}
                        maxLength={200}
                        onChange={(e) => update((tr) => tr.map((x, i) => (i === s ? { ...x, chapters: x.chapters.map((y, j) => (j === c ? { ...y, title: e.target.value } : y)) } : x)))}
                        className="h-11 min-w-0 flex-1 border-transparent bg-transparent text-[15px] font-medium hover:border-border focus:bg-surface"
                      />
                      <MoveButtons
                        label={t('wiz.content.chapter', { n: localDigits(c + 1, t.locale) })}
                        canUp={s > 0 || c > 0}
                        canDown={s < tree.length - 1 || c < sec.chapters.length - 1}
                        onMove={(dir) => {
                          update((tr) => stepChapter(tr, { s, c }, dir).tree);
                          say(t('wiz.content.moved'));
                        }}
                      />
                      <IconBtn
                        danger
                        label={t('wiz.content.deleteChapter', { n: localDigits(c + 1, t.locale) })}
                        onClick={() => ask(t('wiz.content.confirmChapter'), t('wiz.content.confirmBody', { title: ch.title }), () => update((tr) => tr.map((x, i) => (i === s ? { ...x, chapters: x.chapters.filter((_, j) => j !== c) } : x))), ch.lessons.length > 0)}
                      >
                        <Trash2 className="size-[18px]" aria-hidden />
                      </IconBtn>
                    </div>
                    <ol className="flex flex-col border-t border-border">
                      {ch.lessons.map((l, i) => (
                        <li key={l.key} {...dropProps(l.key, { kind: 'lesson', s, c, l: i })} {...dragProps(l.key, { kind: 'lesson', s, c, l: i })} className={cn('border-b border-border last:border-b-0', dropLine(l.key))}>
                          <LessonRow
                            lesson={l}
                            n={`${localDigits(s + 1, t.locale)}.${localDigits(c + 1, t.locale)}.${localDigits(i + 1, t.locale)}`}
                            open={open === l.key}
                            onToggle={() => setOpen(open === l.key ? null : l.key)}
                            handle={handle(l.key, t('wiz.content.dragLesson'))}
                            canUp={i > 0 || c > 0 || s > 0}
                            canDown={i < ch.lessons.length - 1 || c < sec.chapters.length - 1 || s < tree.length - 1}
                            onMove={(dir) => {
                              update((tr) => stepLesson(tr, { s, c, l: i }, dir).tree);
                              say(t('wiz.content.moved'));
                            }}
                            onDelete={() => ask(t('wiz.content.confirmLesson'), t('wiz.content.confirmBody', { title: l.title }), () => update((tr) => tr.map((x, a) => (a === s ? { ...x, chapters: x.chapters.map((y, b) => (b === c ? { ...y, lessons: y.lessons.filter((_, k) => k !== i) } : y)) } : x))), !!(l.videoAssetId || l.documentId || l.body))}
                          >
                            {open === l.key && <LessonEditor lesson={l} autoFocus={fresh === l.key} />}
                          </LessonRow>
                        </li>
                      ))}
                    </ol>
                    <div {...dropProps(`${ch.key}-end`, { kind: 'lesson', s, c, l: ch.lessons.length })} className={cn('px-2 py-1.5 sm:px-3', ch.lessons.length > 0 && 'border-t border-border', dropLine(`${ch.key}-end`))}>
                      <button type="button" onClick={() => addLesson(s, c)} className="inline-flex h-11 items-center gap-1.5 rounded-control px-2 text-sm font-semibold text-primary-fg hover:bg-primary-soft">
                        <Plus className="size-4" aria-hidden /> {t('wiz.content.addLesson')}
                      </button>
                    </div>
                  </li>
                ))}
                <li {...dropProps(`${sec.key}-end`, { kind: 'chapter', s, c: sec.chapters.length })} className={cn('rounded-control', dropLine(`${sec.key}-end`))}>
                  <button type="button" onClick={() => addChapter(s)} className="flex h-11 w-full items-center justify-center gap-1.5 rounded-control border border-dashed border-border-strong text-sm font-semibold text-muted hover:border-primary hover:text-primary-fg">
                    <Plus className="size-4" aria-hidden /> {t('wiz.content.addChapter')}
                  </button>
                </li>
              </ol>
            </li>
          ))}
        </ol>
      )}

      {tree.length > 0 && tree.length < 30 && (
        <Button variant="secondary" onClick={addSection} icon={<Plus className="size-4" aria-hidden />} className="self-start">
          {t('wiz.content.addSection')}
        </Button>
      )}

      <Resources />

      <p className="sr-only" aria-live="polite">
        {announce}
      </p>
      <Dialog
        open={!!confirm}
        onOpenChange={(o) => !o && setConfirm(null)}
        title={confirm?.title ?? ''}
        description={confirm?.body}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirm(null)}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                confirm?.run();
                setConfirm(null);
              }}
            >
              {t('common.delete')}
            </Button>
          </>
        }
      >
        {null}
      </Dialog>
    </div>
  );
}

function LessonRow({ lesson: l, n, open, onToggle, handle, canUp, canDown, onMove, onDelete, children }: { lesson: LessonDraft; n: string; open: boolean; onToggle: () => void; handle: ReactNode; canUp: boolean; canDown: boolean; onMove: (d: -1 | 1) => void; onDelete: () => void; children: ReactNode }) {
  const t = useT();
  const up = useUploads((s) => s.items[l.key]);
  const Icon = LESSON_ICON[l.type];
  const missing = (l.type === 'video' && !l.videoAssetId) || (l.type === 'pdf' && !l.documentId);
  const dur = l.durationSec || l.video?.durationSec || 0;
  return (
    <div className={cn(open && 'bg-surface-2/50')}>
      <div className="flex items-center gap-1.5 px-2 py-1 sm:gap-2 sm:px-3">
        {handle}
        <button type="button" onClick={onToggle} aria-expanded={open} className="flex min-h-12 min-w-0 flex-1 items-center gap-2.5 rounded-control px-1 text-left hover:text-primary-fg">
          <Icon className={cn('size-[18px] shrink-0', l.type === 'video' ? 'text-primary' : 'text-muted')} strokeWidth={1.75} aria-hidden />
          <span className="latin hidden shrink-0 text-xs text-muted tnum sm:inline">{n}</span>
          <span className="min-w-0 flex-1">
            <span className="latin block truncate text-sm font-medium">{l.title || t('wiz.untitled')}</span>
            <span className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted">
              <span>{t(`wiz.lesson.type.${l.type}`)}</span>
              {dur > 0 && <span className="latin tnum">{localDigits(formatDuration(dur), t.locale)}</span>}
              {up && !up.error && <span className="font-semibold text-primary-fg tnum">{t('wiz.lesson.uploadingPct', { p: localDigits(up.progress, t.locale) })}</span>}
              {up?.error && <span className="font-semibold text-danger-fg">{t('seller.upload.failed')}</span>}
              {!up && missing && (
                <span className="inline-flex items-center gap-1 font-semibold text-warning-fg">
                  <AlertTriangle className="size-3" aria-hidden /> {t(l.type === 'video' ? 'wiz.lesson.needVideo' : 'wiz.lesson.needPdf')}
                </span>
              )}
            </span>
          </span>
          {l.isPreview && <span className="shrink-0 rounded-full bg-success-soft px-2 py-0.5 text-[11px] font-semibold text-success-fg">{t('wiz.lesson.preview')}</span>}
          {l.isImportant && <Star className="size-4 shrink-0 fill-warning text-warning" aria-label={t('wiz.lesson.important')} />}
          <ChevronUp className={cn('hidden size-4 shrink-0 rotate-180 text-muted transition-transform duration-150 sm:block', open && 'rotate-0')} aria-hidden />
        </button>
        <MoveButtons label={t('wiz.content.lesson', { n })} canUp={canUp} canDown={canDown} onMove={onMove} />
        <span className="hidden sm:contents">
          <IconBtn danger label={t('wiz.content.deleteLesson', { n })} onClick={onDelete}>
            <Trash2 className="size-4" aria-hidden />
          </IconBtn>
        </span>
      </div>
      {children}
      {open && (
        <div className="flex justify-end px-3 pb-3 sm:hidden">
          <button type="button" onClick={onDelete} className="inline-flex h-11 items-center gap-1.5 rounded-control px-3 text-sm font-semibold text-danger-fg hover:bg-danger-soft">
            <Trash2 className="size-4" aria-hidden /> {t('wiz.content.deleteThisLesson')}
          </button>
        </div>
      )}
    </div>
  );
}

function MoveButtons({ label, canUp, canDown, onMove }: { label: string; canUp: boolean; canDown: boolean; onMove: (d: -1 | 1) => void }) {
  const t = useT();
  return (
    <span className="flex shrink-0">
      <IconBtn label={t('wiz.content.moveUp', { what: label })} disabled={!canUp} onClick={() => onMove(-1)}>
        <ChevronUp className="size-4" aria-hidden />
      </IconBtn>
      <IconBtn label={t('wiz.content.moveDown', { what: label })} disabled={!canDown} onClick={() => onMove(1)}>
        <ChevronUp className="rotate-180 size-4" aria-hidden />
      </IconBtn>
    </span>
  );
}

function IconBtn({ label, onClick, disabled, danger, children }: { label: string; onClick: () => void; disabled?: boolean; danger?: boolean; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className={cn('flex size-11 shrink-0 items-center justify-center rounded-control text-muted transition-colors disabled:opacity-30', danger ? 'hover:bg-danger-soft hover:text-danger-fg' : 'hover:bg-surface-2 hover:text-text')}
    >
      {children}
    </button>
  );
}
