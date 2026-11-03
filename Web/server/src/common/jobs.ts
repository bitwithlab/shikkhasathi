import { Queue, type JobsOptions } from 'bullmq';
import { env } from '../config/env';
import { bullConnection } from './redis';
import { logger } from './logger';

/** Queue names = job names in spec §9. One BullMQ queue per job type. */
export const QUEUES = [
  'email.send',
  'classroom.fullSync',
  'classroom.incrementalSync',
  'classroom.pubsubConsume',
  'classroom.renewRegistrations',
  'classroom.poll',
  'reminders.deadlines',
  'notifications.dispatch',
  'search.indexCourse',
  'payments.reconcile',
  'payments.fulfil',
  'ledger.release',
  'video.processed',
  'exam.replan',
  'exam.dailyPlan',
  'invoice.generate',
  'cleanup',
] as const;
export type QueueName = (typeof QUEUES)[number];

type Handler = (data: any) => Promise<unknown>; // eslint-disable-line @typescript-eslint/no-explicit-any
const handlers = new Map<QueueName, Handler>();
const queues = new Map<QueueName, Queue>();

export function defineJob<T>(name: QueueName, handler: (data: T) => Promise<unknown>) {
  handlers.set(name, handler as Handler);
}

export function jobHandler(name: QueueName): Handler | undefined {
  return handlers.get(name);
}

export function queue(name: QueueName): Queue {
  let q = queues.get(name);
  if (!q) {
    q = new Queue(name, { connection: bullConnection(), prefix: 'ss' });
    queues.set(name, q);
  }
  return q;
}

const DEFAULTS: JobsOptions = { attempts: 5, backoff: { type: 'exponential', delay: 5000 }, removeOnComplete: 1000, removeOnFail: 5000 };

/** Inline-mode run queue (tests / INLINE_JOBS): jobs execute in-process, sequentially. */
const inlineLog: { name: QueueName; data: unknown }[] = [];
export const inlineJobs = { log: inlineLog, enabled: () => env.INLINE_JOBS || env.NODE_ENV === 'test' };

export async function enqueue<T>(name: QueueName, data: T, opts: JobsOptions = {}) {
  if (inlineJobs.enabled()) {
    inlineLog.push({ name, data });
    const h = handlers.get(name);
    if (!h) return;
    if (opts.delay && opts.delay > 0) return; // delayed jobs are not run inline
    try {
      await h(data);
    } catch (err) {
      logger.error({ err, name }, 'inline job failed');
      if (opts.attempts === 1) throw err;
    }
    return;
  }
  await queue(name).add(name, data as object, { ...DEFAULTS, ...opts });
}

export async function closeQueues() {
  await Promise.allSettled([...queues.values()].map((q) => q.close()));
  queues.clear();
}

export async function queueStats() {
  const out: { name: string; waiting: number; active: number; failed: number; delayed: number }[] = [];
  if (inlineJobs.enabled()) return out;
  for (const name of QUEUES) {
    const c = await queue(name).getJobCounts('waiting', 'active', 'failed', 'delayed');
    out.push({ name, waiting: c.waiting ?? 0, active: c.active ?? 0, failed: c.failed ?? 0, delayed: c.delayed ?? 0 });
  }
  return out;
}
