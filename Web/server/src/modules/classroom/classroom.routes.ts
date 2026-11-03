import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import * as svc from './classroom.service';

export async function classroomRoutes(app: FastifyInstance) {
  const c = contracts.classroom;
  route(app, c.connectUrl, ({ user, query }) => svc.connectUrl(user.id, query.returnTo));
  route(app, c.connect, ({ user, body }) => svc.connectWithCode(user.id, body.code, body.redirectUri));
  route(app, c.status, ({ user }) => svc.getStatus(user.id));
  route(app, c.disconnect, async ({ user }) => {
    await svc.disconnect(user.id);
    return { ok: true as const };
  });
  route(app, c.sync, ({ user }) => svc.manualSync(user.id), { rateLimit: { max: 3, timeWindow: '1 minute' } });
  route(app, c.courses, ({ user }) => svc.listCourses(user.id));
  route(app, c.stream, ({ user, params }) => svc.courseStream(user.id, params.id));
  route(app, c.assignments, ({ user, query }) => svc.listAssignments(user.id, query));
  route(app, c.assignment, ({ user, params }) => svc.getAssignment(user.id, params.id));
  route(app, c.calendar, ({ user, query }) => svc.calendar(user.id, query.from, query.to));
  route(app, c.feed, ({ user, query }) => svc.feed(user.id, query));
  route(app, c.feedRead, async ({ user, body }) => {
    await svc.markFeedRead(user.id, body);
    return { ok: true as const };
  });

  // Browser redirect target of the Google consent screen (not JSON — redirects back into the SPA).
  const CallbackQuery = z.object({ code: z.string().optional(), state: z.string().optional(), error: z.string().optional() });
  app.get(svc.REDIRECT_PATH.replace('/api/v1', ''), async (req, reply) => {
    const q = CallbackQuery.safeParse(req.query);
    const to = await svc.oauthCallback(q.success ? q.data : {});
    return reply.redirect(to, 302);
  });

  route(app, contracts.webhooks.googlePubsub, async ({ query, body }) => {
    await svc.handlePubsub(query.token, body);
    return { ok: true as const };
  });
}
