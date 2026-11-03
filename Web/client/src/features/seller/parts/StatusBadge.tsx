import { Archive, CheckCircle2, CircleDashed, EyeOff, Hourglass, PencilLine, XCircle } from './icons';
import type { CourseStatus } from '@shikkhasathi/shared/types';
import { Badge, type Tone } from '@/components';
import { useT } from '@/lib/i18n';

const META: Record<CourseStatus, { tone: Tone; icon: typeof Archive }> = {
  draft: { tone: 'neutral', icon: CircleDashed },
  in_review: { tone: 'primary', icon: Hourglass },
  changes_requested: { tone: 'warning', icon: PencilLine },
  rejected: { tone: 'danger', icon: XCircle },
  published: { tone: 'success', icon: CheckCircle2 },
  unlisted: { tone: 'neutral', icon: EyeOff },
  archived: { tone: 'neutral', icon: Archive },
};

export function StatusBadge({ status, className }: { status: CourseStatus; className?: string }) {
  const t = useT();
  const m = META[status];
  return (
    <Badge tone={m.tone} className={className} icon={<m.icon className="size-3.5" aria-hidden />}>
      {t(`seller.status.${status}`)}
    </Badge>
  );
}
