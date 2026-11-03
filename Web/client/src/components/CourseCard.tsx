import { Link } from 'react-router';
import { AlertTriangle, BadgeCheck, CheckCircle2, Star } from 'lucide-react';
import { Badge } from './Badge';
import { formatTaka, localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';

export interface CourseCardData {
  id: string;
  slug: string;
  title: string;
  courseCode: string | null;
  institutionShort: string | null;
  sellerName: string;
  sellerVerified: boolean;
  examType: string;
  price: number;
  compareAtPrice: number | null;
  ratingAvg: number;
  ratingCount: number;
  coverUrl: string | null;
  coverColor: string | null;
  compatibility: 'match' | 'mismatch' | 'general';
}

export function CompatibilityBadge({ value }: { value: CourseCardData['compatibility'] }) {
  const t = useT();
  if (value === 'general') return null;
  return value === 'match' ? (
    <Badge tone="success" icon={<CheckCircle2 className="size-3.5" aria-hidden />}>{t('compat.match')}</Badge>
  ) : (
    <Badge tone="warning" icon={<AlertTriangle className="size-3.5" aria-hidden />}>{t('compat.mismatch')}</Badge>
  );
}

/** Course Card (design system): 16:9 cover, uni + exam pills, 2-line title, verified teacher, rating, ৳ with strikethrough. */
export function CourseCard({ c, compact, className }: { c: CourseCardData; compact?: boolean; className?: string }) {
  const t = useT();
  const off = c.compareAtPrice && c.compareAtPrice > c.price ? Math.round((1 - c.price / c.compareAtPrice) * 100) : 0;
  const color = c.coverColor ?? '#4F46E5';
  return (
    <Link
      to={`/c/${c.slug}`}
      className={cn('group surface-card flex h-full flex-col overflow-hidden transition-[transform,box-shadow,border-color] duration-200 ease-ss hover:-translate-y-0.5 hover:border-border-strong hover:shadow-e2', className)}
    >
      <div className="relative aspect-video overflow-hidden" style={{ background: `linear-gradient(135deg, ${color} 0%, ${color}cc 100%)` }}>
        {c.coverUrl ? (
          <img src={c.coverUrl} alt="" width={640} height={360} loading="lazy" decoding="async" className="size-full object-cover" />
        ) : (
          <div className="flex size-full flex-col justify-end p-3 text-white">
            {c.courseCode && <span className="latin text-xs font-semibold text-white/85">{c.courseCode}</span>}
            <span className="latin line-clamp-2 text-[15px] font-bold leading-snug">{c.title}</span>
          </div>
        )}
        <div className="absolute inset-x-2 top-2 flex items-start justify-between gap-2">
          {c.institutionShort && <span className="latin rounded-full bg-white/95 px-2 py-0.5 text-[11px] font-bold text-slate-900">{c.institutionShort}</span>}
          <span className="ml-auto rounded-full bg-slate-900/75 px-2 py-0.5 text-[11px] font-semibold text-white">{t(`examType.${c.examType}`)}</span>
        </div>
      </div>
      <div className={cn('flex flex-1 flex-col gap-1.5', compact ? 'p-3' : 'p-4')}>
        <CompatibilityBadge value={c.compatibility} />
        <h3 className={cn('latin line-clamp-2 font-semibold leading-snug text-text group-hover:text-primary-fg', compact ? 'text-[15px]' : 'text-base')}>{c.title}</h3>
        <p className="flex items-center gap-1 text-[13px] text-muted">
          <span className="truncate">{c.sellerName}</span>
          {c.sellerVerified && <BadgeCheck className="size-4 shrink-0 text-success" aria-label={t('course.verified')} />}
        </p>
        <div className="mt-auto flex items-end justify-between gap-2 pt-1">
          <span className="inline-flex items-center gap-1 text-[13px] text-muted">
            <Star className="size-4 fill-warning text-warning" aria-hidden />
            <span className="latin font-semibold text-text tnum">{c.ratingCount ? localDigits(c.ratingAvg.toFixed(1), t.locale) : '—'}</span>
            {c.ratingCount > 0 && <span className="tnum">({localDigits(c.ratingCount, t.locale)})</span>}
          </span>
          <span className="text-right">
            {off > 0 && <span className="mr-1.5 text-xs text-muted line-through tnum">{formatTaka(c.compareAtPrice!, t.locale)}</span>}
            <span className="text-[17px] font-bold text-text tnum">{c.price === 0 ? t('course.free') : formatTaka(c.price, t.locale)}</span>
          </span>
        </div>
      </div>
    </Link>
  );
}
