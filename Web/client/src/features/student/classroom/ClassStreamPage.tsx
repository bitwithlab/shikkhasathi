import '../i18n';
import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, BookOpen, ExternalLink, Megaphone } from 'lucide-react';
import type { Assignment } from '@shikkhasathi/shared';
import { Button, Chip, EmptyState, ErrorState, Skeleton } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { relativeTime, useT } from '@/lib/i18n';
import { ClassroomGate } from './ClassroomGate';
import { AssignmentRow, AssignmentSheet, MaterialChips } from './parts';

type Tab = 'stream' | 'assignments' | 'materials';

export default function ClassStreamPage() {
  const { courseId = '' } = useParams();
  return (
    <div className="mx-auto max-w-3xl px-4 py-6">
      <ClassroomGate returnTo={`/classroom/${encodeURIComponent(courseId)}`}>
        <Stream courseId={courseId} />
      </ClassroomGate>
    </div>
  );
}

function Stream({ courseId }: { courseId: string }) {
  const t = useT();
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') as Tab) ?? 'stream';
  const [open, setOpen] = useState<Assignment | null>(null);
  const q = useQuery({ queryKey: ['classroom', 'stream', courseId], queryFn: () => api(contracts.classroom.stream, { params: { id: courseId } }) });
  if (q.isLoading) return <><Skeleton className="h-32 rounded-card" /><div className="mt-4 flex flex-col gap-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-20" />)}</div></>;
  if (q.isError) return <ErrorState title={q.error instanceof ApiError && q.error.status === 404 ? t('gc.classNotFound') : undefined} onRetry={() => void q.refetch()} />;
  const { course, items } = q.data!;
  const list = tab === 'assignments' ? items.filter((i) => i.kind === 'assignment') : tab === 'materials' ? items.filter((i) => i.kind === 'material') : items;
  return (
    <>
      <Link to="/classroom" className="-ml-2 mb-3 inline-flex items-center gap-1 rounded-control px-2 py-1 text-sm text-muted hover:text-text">
        <ArrowLeft className="size-4" aria-hidden /> {t('nav.classroom')}
      </Link>
      <header className="overflow-hidden rounded-card p-5 text-white shadow-e1" style={{ background: course.color }}>
        <h1 className="latin text-[22px] font-bold leading-snug">{course.name}</h1>
        <p className="latin mt-1 text-sm text-white/85">{[course.section, course.room, course.teacherNames.join(', ')].filter(Boolean).join(' · ')}</p>
        {course.alternateLink && (
          <Button asChild size="sm" variant="secondary" className="mt-4 border-transparent">
            <a href={course.alternateLink} target="_blank" rel="noreferrer noopener">
              {t('gc.openInClassroom')} <ExternalLink className="size-4" aria-hidden />
            </a>
          </Button>
        )}
      </header>
      <div role="tablist" aria-label={course.name} className="mt-5 flex gap-2 overflow-x-auto scrollbar-none">
        {(['stream', 'assignments', 'materials'] as const).map((k) => (
          <Chip key={k} role="tab" aria-selected={tab === k} selected={tab === k} onClick={() => setParams({ tab: k }, { replace: true })}>
            {t(`gc.tab.${k}`)}
          </Chip>
        ))}
      </div>
      <ul className="mt-4 flex flex-col gap-3" role="tabpanel">
        {list.length === 0 && <EmptyState compact title={t('gc.tabEmpty')} />}
        {list.map((i) =>
          i.kind === 'assignment' && i.assignment ? (
            <li key={i.id}>
              <AssignmentRow a={i.assignment} onOpen={setOpen} showCourse={false} />
            </li>
          ) : (
            <li key={i.id} className="surface-card p-4">
              <div className="flex items-center gap-2 text-[13px] text-muted">
                {i.kind === 'announcement' ? <Megaphone className="size-4" style={{ color: course.color }} aria-hidden /> : <BookOpen className="size-4" style={{ color: course.color }} aria-hidden />}
                <span className="font-semibold" style={{ color: course.color }}>{t(`gc.kind.${i.kind}`)}</span>
                {i.author && <span>· {i.author}</span>}
                {i.postedAt && <span className="ml-auto">{relativeTime(i.postedAt, t.locale)}</span>}
              </div>
              <p className="latin mt-1.5 whitespace-pre-line text-[15px]">{i.kind === 'announcement' ? i.body : i.title}</p>
              {i.kind === 'material' && i.body && <p className="mt-1 text-sm text-muted">{i.body}</p>}
              <MaterialChips materials={i.materials} />
            </li>
          ),
        )}
      </ul>
      <AssignmentSheet a={open} onClose={() => setOpen(null)} />
    </>
  );
}
