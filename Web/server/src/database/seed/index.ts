import { fileURLToPath } from 'node:url';
import { closeDatabases } from '../client';
import { closeRedis } from '../../common/redis';
import { seedUsers, DEMO_PASSWORD } from './users';
import { seedCatalog } from './catalog';
import { seedProfiles } from './profiles';
import { seedCourses } from './courses';
import { seedExam } from './exam';

export async function seedAll() {
  if (process.env.NODE_ENV === 'production' && process.env.ALLOW_PROD_SEED !== 'true') throw new Error('Refusing to seed production');
  const users = await seedUsers();
  const catalog = await seedCatalog();
  await seedProfiles(users, catalog);
  const courses = await seedCourses(users, catalog);
  await seedExam(users, courses);
  return { users, catalog, courses };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  seedAll()
    .then((r) => {
      console.info(`seeded: ${Object.keys(r.users).length} users (password: ${DEMO_PASSWORD}), ${Object.keys(r.catalog.inst).length} institutions, ${Object.keys(r.catalog.subj).length} subjects, ${Object.keys(r.catalog.instSubj).length} course codes, ${Object.keys(r.courses).length} courses`);
    })
    .catch((e) => {
      console.error(e);
      process.exitCode = 1;
    })
    .finally(async () => {
      await closeDatabases();
      await closeRedis();
    });
}
