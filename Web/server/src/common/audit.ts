import { coreDb } from '../database/client';
import { auditLogs } from '../database/core/schema';

export async function audit(entry: {
  actorId: string | null | undefined;
  action: string;
  entity: string;
  entityId?: string | null;
  before?: unknown;
  after?: unknown;
  ip?: string | null;
}) {
  await coreDb.insert(auditLogs).values({
    actorId: entry.actorId ?? null,
    action: entry.action,
    entity: entry.entity,
    entityId: entry.entityId ?? null,
    beforeJson: entry.before ?? null,
    afterJson: entry.after ?? null,
    ip: entry.ip ?? null,
  });
}
