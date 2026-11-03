import type { FastifyInstance } from 'fastify';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import * as svc from './learning.service';

const ok = { ok: true as const };

export async function learningRoutes(app: FastifyInstance) {
  const l = contracts.learning;
  route(app, l.myCourses, ({ user, query }) => svc.myCourses(user.id, query.filter));
  route(app, l.outline, ({ user, params }) => svc.outline(user.id, params.courseId, user.roles));
  route(app, l.lesson, ({ user, params }) => svc.lessonDetail(user.id, params.id, user.roles));
  route(app, l.playback, ({ user, params, body }) => svc.playback(user.id, params.id, body, user.roles), { rateLimit: { max: 120, timeWindow: '1 hour' } });
  route(app, l.progress, ({ user, params, body }) => svc.heartbeat(user.id, params.id, body), { rateLimit: { max: 20, timeWindow: '1 minute' } });
  route(app, l.complete, ({ user, params, body }) => svc.setComplete(user.id, params.id, body.completed));

  route(app, l.notes, ({ user, query }) => svc.listNotes(user.id, query));
  route(app, l.createNote, ({ user, body }) => svc.createNote(user.id, body), { rateLimit: { max: 120, timeWindow: '1 hour' } });
  route(app, l.updateNote, ({ user, params, body }) => svc.updateNote(user.id, params.id, body.body));
  route(app, l.deleteNote, async ({ user, params }) => (await svc.deleteNote(user.id, params.id), ok));
  route(app, l.bookmarks, ({ user, query }) => svc.listBookmarks(user.id, query));
  route(app, l.createBookmark, ({ user, body }) => svc.createBookmark(user.id, body), { rateLimit: { max: 120, timeWindow: '1 hour' } });
  route(app, l.deleteBookmark, async ({ user, params }) => (await svc.deleteBookmark(user.id, params.id), ok));

  route(app, l.questions, ({ user, params, query }) => svc.listQuestions(user.id, params.id, query, user.roles));
  route(app, l.ask, ({ user, params, body }) => svc.ask(user.id, params.id, body), { rateLimit: { max: 20, timeWindow: '1 hour' } });
  route(app, l.answer, ({ user, params, body }) => svc.answer(user.id, params.id, body.body), { rateLimit: { max: 60, timeWindow: '1 hour' } });
  route(app, l.reviews, ({ params, query }) => svc.listReviews(params.courseId, query), { cacheControl: 'public, max-age=60' });
  route(app, l.review, ({ user, params, body }) => svc.upsertReview(user.id, params.courseId, body), { rateLimit: { max: 10, timeWindow: '1 hour' } });

  const s = contracts.sellerLearning;
  route(app, s.questions, ({ user, query }) => svc.sellerQuestions(user.id, query));
  route(app, s.reviews, ({ user, query }) => svc.sellerReviews(user.id, query));
  route(app, s.replyReview, ({ user, params, body }) => svc.replyReview(user.id, params.id, body.reply));
}
