import type { FastifyInstance } from 'fastify';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import * as svc from './courses.service';
import * as search from '../search/search.service';
import * as req from '../requests/requests.service';

export async function courseRoutes(app: FastifyInstance) {
  const d = contracts.discovery;
  route(app, d.search, ({ query, user }) => search.search(query, user?.id), { rateLimit: { max: 60, timeWindow: '1 minute' } });
  route(app, d.suggest, ({ query }) => search.suggest(query.q), { rateLimit: { max: 120, timeWindow: '1 minute' }, cacheControl: 'public, max-age=60' });
  route(app, d.course, ({ params, user }) => svc.courseDetail(params.slug, user?.id, user?.roles));
  route(app, d.storefront, ({ params, user }) => svc.storefront(params.slug, user?.id));
  route(app, d.recommendations, ({ user }) => svc.recommendations(user.id));
  route(app, d.featured, ({ query }) => svc.featured(query.institutionId), { cacheControl: 'public, max-age=300' });

  const w = contracts.wishlist;
  route(app, w.add, async ({ user, params }) => {
    await svc.addWishlist(user.id, params.courseId);
    return { ok: true as const };
  });
  route(app, w.remove, async ({ user, params }) => {
    await svc.removeWishlist(user.id, params.courseId);
    return { ok: true as const };
  });
  route(app, w.list, ({ user }) => svc.listWishlist(user.id));

  const r = contracts.requests;
  route(app, r.list, ({ query, user }) => req.list(query, user?.id));
  route(app, r.create, ({ body, user }) => req.create(user.id, body), { rateLimit: { max: 10, timeWindow: '1 hour' } });
  route(app, r.vote, ({ params, user }) => req.vote(user.id, params.id));
  route(app, r.claim, ({ params, user, body }) => req.claim(user.id, params.id, body.courseId));
}
