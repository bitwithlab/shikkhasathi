import type { SellerCourseListItem } from '@/api';
import type { z } from 'zod';

export type CourseItem = z.infer<typeof SellerCourseListItem>;

/** Dashboard KPIs derived from GET /seller/courses (earnings arrive with Phase 10). */
export function kpis(items: CourseItem[]) {
  const live = items.filter((c) => c.status === 'published' || c.status === 'unlisted');
  const rated = items.filter((c) => c.ratingCount > 0);
  const ratingCount = rated.reduce((a, c) => a + c.ratingCount, 0);
  return {
    published: items.filter((c) => c.status === 'published').length,
    students: items.reduce((a, c) => a + c.enrollmentCount, 0),
    /** weighted by number of ratings */
    rating: ratingCount ? rated.reduce((a, c) => a + c.ratingAvg * c.ratingCount, 0) / ratingCount : 0,
    ratingCount,
    drafts: items.filter((c) => c.status === 'draft' || c.status === 'changes_requested' || c.status === 'rejected').length,
    live: live.length,
  };
}
