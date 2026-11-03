import type { FastifyInstance } from 'fastify';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import { getDashboard } from './dashboard.service';

export async function dashboardRoutes(app: FastifyInstance) {
  route(app, contracts.dashboard.get, ({ user, req }) => getDashboard(user.id, { fresh: (req.query as { fresh?: string }).fresh === '1' }));
}
