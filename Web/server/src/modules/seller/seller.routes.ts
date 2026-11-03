import type { FastifyInstance } from 'fastify';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import * as svc from './seller.service';

export async function sellerRoutes(app: FastifyInstance) {
  const a = contracts.sellerApply;
  route(app, a.profile, ({ user }) => svc.getProfile(user.id));
  route(app, a.apply, ({ user, body }) => svc.apply(user.id, body), { rateLimit: { max: 10, timeWindow: '1 hour' } });
  route(app, a.emailCode, async ({ user, body }) => {
    await svc.requestUniversityEmailCode(user.id, body.email);
    return { ok: true as const };
  });
  route(app, a.emailConfirm, ({ user, body }) => svc.confirmUniversityEmail(user.id, body.email, body.code));
  route(app, a.updateStorefront, ({ user, body }) => svc.updateStorefront(user.id, body));

  const c = contracts.sellerCourses;
  route(app, c.list, ({ user }) => svc.listMyCourses(user.id));
  route(app, c.create, ({ user, body }) => svc.createCourse(user.id, body.title));
  route(app, c.get, ({ user, params }) => svc.getMyCourse(user.id, params.id));
  route(app, c.update, ({ user, params, body }) => svc.updateCourse(user.id, params.id, body));
  route(app, c.targets, ({ user, params, body }) => svc.setTargets(user.id, params.id, body.targets));
  route(app, c.curriculum, ({ user, params, body }) => svc.setCurriculum(user.id, params.id, body));
  route(app, c.checklist, ({ user, params }) => svc.checklist(user.id, params.id));
  route(app, c.submit, ({ user, params }) => svc.submit(user.id, params.id));
  route(app, c.archive, ({ user, params, body }) => svc.setVisibility(user.id, params.id, body.status));
  route(app, c.remove, async ({ user, params }) => {
    await svc.removeDraft(user.id, params.id);
    return { ok: true as const };
  });
  route(app, c.upload, ({ user, body }) => svc.createUpload(user.id, body), { rateLimit: { max: 60, timeWindow: '1 minute' } });
  route(app, c.uploadComplete, async ({ user, params }) => {
    await svc.completeUpload(user.id, params.id);
    return { ok: true as const };
  });
  route(app, c.proposeSubject, ({ user, body }) => svc.proposeSubject(user.id, body.name, body.aliases));

  const s = contracts.adminSellers;
  route(app, s.list, ({ query }) => svc.adminListSellers(query));
  route(app, s.decide, ({ user, params, body }) => svc.adminDecideSeller(user.id, params.id, body));

  const r = contracts.adminReview;
  route(app, r.queue, ({ query }) => svc.adminQueue(query));
  route(app, r.detail, ({ params }) => svc.adminDetail(params.id));
  route(app, r.decide, ({ user, params, body }) => svc.adminDecide(user.id, params.id, body));
}
