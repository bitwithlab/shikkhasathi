import type { FastifyInstance } from 'fastify';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import * as svc from './exam.service';

const ok = { ok: true as const };

export async function examRoutes(app: FastifyInstance) {
  const e = contracts.exam;
  route(app, e.plans, ({ user }) => svc.listPlans(user.id));
  route(app, e.create, ({ user, body }) => svc.createPlan(user.id, body), { rateLimit: { max: 30, timeWindow: '1 hour' } });
  route(app, e.get, ({ user, params }) => svc.planDetail(user.id, params.id));
  route(app, e.update, ({ user, params, body }) => svc.updatePlan(user.id, params.id, body));
  route(app, e.remove, async ({ user, params }) => (await svc.deletePlan(user.id, params.id), ok));
  route(app, e.flashcards, ({ user, params }) => svc.planFlashcards(user.id, params.id));
  route(app, e.reviewCard, async ({ user, params, body }) => (await svc.reviewFlashcard(user.id, params.id, body.known), ok));
  route(app, e.mockTests, ({ user }) => svc.studentMockTests(user.id));
  route(app, e.start, ({ user, params }) => svc.startAttempt(user.id, params.id), { rateLimit: { max: 30, timeWindow: '1 hour' } });
  route(app, e.attempt, ({ user, params }) => svc.getAttempt(user.id, params.id));
  route(app, e.answer, async ({ user, params, body }) => (await svc.saveAnswer(user.id, params.id, body), ok), { rateLimit: { max: 600, timeWindow: '1 hour' } });
  route(app, e.submit, ({ user, params }) => svc.submitAttempt(user.id, params.id));
  route(app, e.result, ({ user, params }) => svc.attemptResult(user.id, params.id));

  const s = contracts.sellerExam;
  route(app, s.mockTests, ({ user, params }) => svc.listSellerMocks(user.id, params.courseId));
  route(app, s.createMock, ({ user, params, body }) => svc.createMock(user.id, params.courseId, body));
  route(app, s.updateMock, ({ user, params, body }) => svc.updateMock(user.id, params.id, body));
  route(app, s.deleteMock, async ({ user, params }) => (await svc.deleteMock(user.id, params.id), ok));
  route(app, s.flashcards, ({ user, params }) => svc.sellerFlashcards(user.id, params.courseId));
  route(app, s.setFlashcards, ({ user, params, body }) => svc.setFlashcards(user.id, params.courseId, body.cards));
}
