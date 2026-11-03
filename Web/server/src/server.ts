import { buildApp } from './app';
import { env } from './config/env';
import { closeDatabases } from './database/client';
import { closeRedis } from './common/redis';
import { closeQueues } from './common/jobs';

const app = await buildApp();
await app.listen({ port: env.PORT, host: env.HOST });

for (const sig of ['SIGINT', 'SIGTERM'] as const) {
  process.on(sig, async () => {
    app.log.info({ sig }, 'shutting down');
    await app.close();
    await Promise.allSettled([closeQueues(), closeDatabases(), closeRedis()]);
    process.exit(0);
  });
}
