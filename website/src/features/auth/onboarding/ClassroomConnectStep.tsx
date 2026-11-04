import { BellRing, CalendarClock, ClipboardCheck, FileText, Lock } from 'lucide-react';
import { Illustration } from '@/components';
import { useT } from '@/lib/i18n';
import { ConnectClassroomButton } from '@/features/student/classroom/ConnectClassroomButton';

/** Onboarding step 4 — optional, explains benefits + privacy (CLAUDE.md §0A). */
export function ClassroomConnectStep({ onLater }: { onLater: () => void }) {
  const t = useT();
  const points = [
    [ClipboardCheck, 'onb.gc.p1'],
    [FileText, 'onb.gc.p2'],
    [BellRing, 'onb.gc.p3'],
    [CalendarClock, 'onb.gc.p4'],
  ] as const;
  return (
    <section>
      <div className="flex flex-col items-center text-center">
        <Illustration kind="classroom" className="h-24 w-28" />
        <h1 className="mt-3 text-[26px] font-bold leading-snug">{t('onb.gc.title')}</h1>
        <p className="mt-1 max-w-md text-[15px] text-muted">{t('onb.gc.body')}</p>
      </div>
      <ul className="surface-card mt-6 flex flex-col gap-4 p-5">
        {points.map(([Icon, k]) => (
          <li key={k} className="flex items-start gap-3">
            <Icon className="mt-0.5 size-5 shrink-0 text-primary" strokeWidth={1.75} aria-hidden />
            <span>{t(k)}</span>
          </li>
        ))}
      </ul>
      <div className="mt-5">
        <ConnectClassroomButton returnTo="/onboarding" onUnavailable={onLater} />
      </div>
      <p className="mt-4 flex items-start justify-center gap-2 text-center text-[13px] text-muted">
        <Lock className="mt-0.5 size-4 shrink-0" aria-hidden /> {t('onb.gc.privacy')} · {t('brand.disclaimerGoogle')}
      </p>
    </section>
  );
}
