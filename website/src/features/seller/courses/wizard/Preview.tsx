import { CourseCard, type CourseCardData } from '@/components/CourseCard';
import { useAuth } from '@/lib/auth';
import { localDigits, useT } from '@/lib/i18n';
import { countLessons, totalDuration } from '../../lib/curriculum';
import { useWizard } from './context';
import { Eye, PlayCircle, Star, takaToPaisa } from '../../parts';

/** Live student-facing preview: the real Course Card + a mini outline. */
export function Preview() {
  const t = useT();
  const w = useWizard();
  const user = useAuth((s) => s.user);
  const first = w.targets[0];
  const price = w.pricing.free ? 0 : takaToPaisa(w.pricing.price);
  const compare = w.pricing.free ? null : takaToPaisa(w.pricing.compare);
  const card: CourseCardData = {
    id: w.id,
    slug: w.server.slug,
    title: w.basics.title || t('wiz.untitled'),
    courseCode: first?.courseCode.trim() ? first.courseCode.trim().toUpperCase() : null,
    institutionShort: first && !first.isGeneral ? first.institutionShort : first?.isGeneral ? t('wiz.target.generalShort') : null,
    sellerName: user?.name ?? '',
    sellerVerified: w.verified,
    examType: w.basics.examType,
    price: price != null && !Number.isNaN(price) ? price : 0,
    compareAtPrice: compare != null && !Number.isNaN(compare) ? compare : null,
    ratingAvg: 0,
    ratingCount: 0,
    coverUrl: w.basics.coverUrl,
    coverColor: w.basics.coverColor,
    compatibility: 'general',
  };
  const lessons = countLessons(w.tree);
  const minutes = Math.round(totalDuration(w.tree) / 60);
  return (
    <div>
      <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted">
        <Eye className="size-3.5" aria-hidden /> {t('wiz.preview.title')}
      </p>
      {/* inert: the card is a link to the public page, which does not exist yet */}
      <div inert className="select-none">
        <CourseCard c={card} />
      </div>
      {w.basics.shortDescription && <p className="mt-3 line-clamp-3 text-sm text-muted">{w.basics.shortDescription}</p>}
      <div className="mt-4 border-t border-border pt-3">
        <p className="flex items-baseline justify-between text-sm font-semibold">
          {t('wiz.preview.outline')}
          <span className="text-xs font-normal text-muted">
            {t('wiz.preview.stats', { n: localDigits(lessons, t.locale), m: localDigits(minutes, t.locale) })}
          </span>
        </p>
        {w.tree.length === 0 ? (
          <p className="mt-2 text-sm text-muted">{t('wiz.preview.noContent')}</p>
        ) : (
          <ol className="mt-2 flex flex-col gap-2">
            {w.tree.slice(0, 6).map((s, i) => (
              <li key={s.key} className="text-sm">
                <p className="flex gap-2 font-medium">
                  <span className="latin w-4 shrink-0 text-muted tnum">{localDigits(i + 1, t.locale)}</span>
                  <span className="min-w-0 flex-1 truncate">{s.title || t('wiz.untitled')}</span>
                </p>
                <ul className="ml-6 mt-1 flex flex-col gap-0.5">
                  {s.chapters.flatMap((ch) => ch.lessons).slice(0, 4).map((l) => (
                    <li key={l.key} className="flex items-center gap-1.5 text-xs text-muted">
                      <PlayCircle className="size-3.5 shrink-0" aria-hidden />
                      <span className="min-w-0 flex-1 truncate">{l.title || t('wiz.untitled')}</span>
                      {l.isImportant && <Star className="size-3.5 shrink-0 fill-warning text-warning" aria-label={t('wiz.lesson.important')} />}
                      {l.isPreview && <span className="shrink-0 rounded-full bg-success-soft px-1.5 text-[10px] font-semibold text-success-fg">{t('wiz.lesson.preview')}</span>}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}
