import './i18n';
import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, RotateCcw, X } from 'lucide-react';
import { Button, EmptyState, ErrorState, Skeleton } from '@/components';
import { api, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

/** Swipe deck: tap/space flips; → or swipe right = known, ← or swipe left = not yet. */
export function FlashcardDeck({ planId }: { planId: string }) {
  const t = useT();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['flashcards', planId], queryFn: () => api(contracts.exam.flashcards, { params: { id: planId } }), staleTime: Infinity });
  const [i, setI] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [tally, setTally] = useState({ known: 0, again: 0 });
  const [dx, setDx] = useState(0);
  const start = useRef<number | null>(null);
  const review = useMutation({
    mutationFn: (v: { id: string; known: boolean }) => api(contracts.exam.reviewCard, { params: { id: v.id }, body: { known: v.known } }),
    onSettled: () => void qc.invalidateQueries({ queryKey: ['exam-plan', planId] }),
  });
  const cards = q.data?.items ?? [];
  const card = cards[i];

  const answer = (known: boolean) => {
    if (!card) return;
    review.mutate({ id: card.id, known });
    setTally((x) => ({ known: x.known + (known ? 1 : 0), again: x.again + (known ? 0 : 1) }));
    setFlipped(false);
    setDx(0);
    setI((n) => n + 1);
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement).closest('input,textarea')) return;
      if (e.key === ' ' || e.key === 'Enter') {
        e.preventDefault();
        setFlipped((f) => !f);
      } else if (e.key === 'ArrowRight') answer(true);
      else if (e.key === 'ArrowLeft') answer(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  if (q.isLoading) return <Skeleton className="mx-auto h-64 w-full max-w-md rounded-card" />;
  if (q.isError) return <ErrorState compact onRetry={() => void q.refetch()} />;
  if (!cards.length) return <EmptyState compact title={t('fc.empty')} />;
  if (!card)
    return (
      <div className="flex flex-col items-center py-8 text-center">
        <span className="grid size-14 place-items-center rounded-full bg-success-soft text-success-fg"><Check className="size-7" aria-hidden /></span>
        <p className="mt-4 text-lg font-semibold">{t('fc.finished')}</p>
        <p className="mt-1 text-muted">{t('fc.finishedBody', { known: localDigits(tally.known, t.locale), again: localDigits(tally.again, t.locale) })}</p>
        <Button className="mt-5" variant="secondary" icon={<RotateCcw className="size-4" aria-hidden />} onClick={() => { setI(0); setTally({ known: 0, again: 0 }); void q.refetch(); }}>{t('fc.restart')}</Button>
      </div>
    );

  return (
    <div className="mx-auto w-full max-w-md">
      <div className="mb-3 flex items-center justify-between text-sm text-muted">
        <span className="tnum">{t('fc.progress', { i: localDigits(i + 1, t.locale), n: localDigits(cards.length, t.locale) })}</span>
        <span className="hidden sm:inline">{t('fc.hint')}</span>
      </div>
      <div className="h-1 overflow-hidden rounded-full bg-surface-2"><div className="h-full bg-primary transition-[width] duration-200" style={{ width: `${(i / cards.length) * 100}%` }} /></div>
      <button
        type="button"
        onClick={() => Math.abs(dx) < 8 && setFlipped((f) => !f)}
        onPointerDown={(e) => (start.current = e.clientX)}
        onPointerMove={(e) => start.current !== null && setDx(e.clientX - start.current)}
        onPointerUp={() => {
          start.current = null;
          if (dx > 80) answer(true);
          else if (dx < -80) answer(false);
          else setDx(0);
        }}
        onPointerCancel={() => { start.current = null; setDx(0); }}
        aria-label={flipped ? card.back : `${card.front} — ${t('fc.flip')}`}
        className={cn('relative mt-4 flex min-h-64 w-full touch-pan-y select-none flex-col items-center justify-center rounded-card border p-6 text-center shadow-e1 transition-[transform,background-color] duration-200 ease-ss', flipped ? 'border-primary/30 bg-primary-soft/40' : 'border-border bg-surface')}
        style={{ transform: `translateX(${dx}px) rotate(${dx / 30}deg)` }}
      >
        <span className="absolute left-4 top-3 text-xs font-semibold uppercase tracking-wide text-muted">{flipped ? t('fc.back') : t('fc.front')}</span>
        <span className={cn('whitespace-pre-wrap text-lg leading-relaxed', !flipped && 'font-semibold')}>{flipped ? card.back : card.front}</span>
        {!flipped && <span className="mt-4 text-xs text-muted">{t('fc.flip')}</span>}
        {dx > 40 && <span className="absolute right-4 top-3 rounded-full bg-success px-2 py-0.5 text-xs font-bold text-white">{t('fc.know')}</span>}
        {dx < -40 && <span className="absolute right-4 top-3 rounded-full bg-warning px-2 py-0.5 text-xs font-bold text-white">{t('fc.again')}</span>}
      </button>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <Button variant="secondary" onClick={() => answer(false)} icon={<X className="size-4" aria-hidden />}>{t('fc.again')}</Button>
        <Button onClick={() => answer(true)} icon={<Check className="size-4" aria-hidden />}>{t('fc.know')}</Button>
      </div>
    </div>
  );
}
