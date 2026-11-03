import type { FastifyInstance } from 'fastify';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import * as svc from './notifications.service';

export async function notificationRoutes(app: FastifyInstance) {
  const c = contracts.notifications;
  route(app, c.list, ({ user, query }) => svc.listNotifications(user.id, query));
  route(app, c.read, async ({ user, body }) => {
    await svc.markRead(user.id, body);
    return { ok: true as const };
  });
  route(app, c.unreadCount, async ({ user }) => ({ count: await svc.unreadCount(user.id) }));
  route(app, c.preferences, ({ user }) => svc.getPreferences(user.id));
  route(app, c.updatePreferences, ({ user, body }) => svc.setPreferences(user.id, body.items));
  route(app, c.registerPush, async ({ user, body }) => {
    await svc.registerPushToken(user.id, body.platform, body.token);
    return { ok: true as const };
  });
  route(app, c.unregisterPush, async ({ user, body }) => {
    await svc.unregisterPushToken(user.id, body.token);
    return { ok: true as const };
  });
}
