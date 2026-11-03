import type { FastifyInstance } from 'fastify';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import * as svc from './ops.service';

export async function opsRoutes(app: FastifyInstance) {
  const s = contracts.sellerOps;
  route(app, s.payouts, ({ user }) => svc.myPayouts(user.id));
  route(app, s.requestPayout, ({ user, body }) => svc.requestPayout(user.id, body.amount), { rateLimit: { max: 10, timeWindow: '1 hour' } });
  route(app, s.students, ({ user, query }) => svc.sellerStudents(user.id, query));
  route(app, s.analytics, ({ user, query }) => svc.sellerAnalytics(user.id, query.days));

  route(app, contracts.reports.create, ({ user, body }) => svc.createReport(user.id, body), { rateLimit: { max: 20, timeWindow: '1 hour' } });

  const a = contracts.adminOps;
  route(app, a.payouts, ({ query }) => svc.adminPayouts(query));
  route(app, a.decidePayout, ({ user, params, body }) => svc.decidePayout(user.id, params.id, body));
  route(app, a.users, ({ query }) => svc.adminUsers(query));
  route(app, a.updateUser, ({ user, params, body }) => svc.updateUser(user.id, params.id, body));
  route(app, a.reports, ({ query }) => svc.adminReports(query));
  route(app, a.resolveReport, ({ user, params, body }) => svc.resolveReport(user.id, params.id, body));
  route(app, a.broadcasts, () => svc.listBroadcasts());
  route(app, a.broadcast, ({ user, body }) => svc.sendBroadcast(user.id, body), { rateLimit: { max: 10, timeWindow: '1 hour' } });
  route(app, a.broadcastPreview, ({ body }) => svc.previewBroadcast(body));
  route(app, a.emailLogs, ({ query }) => svc.adminEmailLogs(query));
  route(app, a.audit, ({ query }) => svc.adminAudit(query));
  route(app, a.analytics, ({ query }) => svc.adminAnalytics(query.days));
}
