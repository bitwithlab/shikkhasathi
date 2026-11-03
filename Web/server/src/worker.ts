import { Worker } from 'bullmq';
import { QUEUES, jobHandler, queue } from './common/jobs';
import { bullConnection } from './common/redis';
import { logger } from './common/logger';
import { closeDatabases } from './database/client';
import './jobs.registry';

/** Recurring schedules (spec §9). Upserted on boot; BullMQ dedupes by scheduler id. */
const SCHEDULES: { name: (typeof QUEUES)[number]; every?: number; pattern?: string }[] = [
  { name: 'reminders.deadlines', every: 5 * 60_000 },
  { name: 'classroom.poll', every: 15 * 60_000 },
  { name: 'classroom.renewRegistrations', pattern: '15 3 * * *' },
  { name: 'payments.reconcile', every: 60 * 60_000 },
  { name: 'ledger.release', pattern: '30 0 * * *' },
  { name: 'exam.replan', pattern: '0 1 * * *' },
  { name: 'exam.dailyPlan', pattern: '0 2 * * *' }, // 08:00 Asia/Dhaka = 02:00 UTC
  { name: 'cleanup', pattern: '45 3 * * *' },
];

const workers = QUEUES.filter((n) => jobHandler(n)).map(
  (name) =>
    new Worker(
      name,
      async (job) => {
        const h = jobHandler(name)!;
        return h(job.data);
      },
      { connection: bullConnection(), prefix: 'ss', concurrency: name === 'email.send' ? 5 : 3 },
    ).on('failed', (job, err) => logger.warn({ queue: name, jobId: job?.id, err: err.message }, 'job failed')),
);

for (const s of SCHEDULES) {
  if (!jobHandler(s.name)) continue;
  await queue(s.name).upsertJobScheduler(`${s.name}:schedule`, s.every ? { every: s.every } : { pattern: s.pattern! }, { name: s.name, data: {} });
}

logger.info({ queues: workers.length }, 'worker started');

for (const sig of ['SIGINT', 'SIGTERM'] as const) {
  process.on(sig, async () => {
    await Promise.allSettled(workers.map((w) => w.close()));
    await closeDatabases();
    process.exit(0);
  });
}
