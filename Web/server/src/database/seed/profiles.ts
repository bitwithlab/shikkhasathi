import { eq } from 'drizzle-orm';
import { coreDb } from '../client';
import { studentProfiles, users } from '../core/schema';
import type { CatalogIds } from './catalog';

/** Demo students get full academic profiles and completed onboarding. */
export async function seedProfiles(u: Record<string, string>, c: CatalogIds) {
  const rows = [
    { key: 'sadman', inst: 'metropolitan-university', dept: 'CSE', prog: 0, semester: 5, batch: '56', section: 'A' },
    { key: 'nusrat', inst: 'north-south-university', dept: 'CSE', prog: 0, semester: 4, batch: '221', section: '3' },
    { key: 'tanvir', inst: 'brac-university', dept: 'CSE', prog: 0, semester: 4, batch: '22', section: '07' },
    { key: 'admin', inst: 'metropolitan-university', dept: 'CSE', prog: 0, semester: 5, batch: '56', section: 'A' },
  ];
  for (const r of rows) {
    const userId = u[r.key];
    if (!userId) continue;
    const v = { institutionId: c.inst[r.inst]!, departmentId: c.dept[`${r.inst}:${r.dept}`]!, programId: c.prog[`${r.inst}:${r.dept}:${r.prog}`]!, semester: r.semester, batch: r.batch, section: r.section };
    await coreDb.insert(studentProfiles).values({ userId, ...v }).onConflictDoUpdate({ target: studentProfiles.userId, set: v });
    await coreDb.update(users).set({ onboardingCompletedAt: new Date() }).where(eq(users.id, userId));
  }
}
