import { fileURLToPath } from 'node:url';
import { closeDatabases } from '../client';
import { closeRedis } from '../../common/redis';
import { seedUsers, DEMO_PASSWORD } from './users';

export async function seedAll() {
  if (process.env.NODE_ENV === 'production' && process.env.ALLOW_PROD_SEED !== 'true') throw new Error('Refusing to seed production');
  const users = await seedUsers();
  return { users };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  seedAll()
    .then((r) => {
      console.info(`seeded: ${Object.keys(r.users).length} users (password: ${DEMO_PASSWORD})`);
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
