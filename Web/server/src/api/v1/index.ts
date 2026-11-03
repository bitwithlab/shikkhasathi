import type { FastifyInstance } from 'fastify';
import { authRoutes } from '../../modules/auth/auth.routes';
import { healthRoutes, openapiRoute } from '../../modules/health/health.routes';
import { settingsRoutes } from '../../modules/settings/settings.routes';

/** All /api/v1 route modules. Handlers stay thin; logic lives in modules/<m>/<m>.service.ts. */
export async function apiV1(app: FastifyInstance) {
  await app.register(healthRoutes);
  await app.register(openapiRoute);
  await app.register(authRoutes);
  await app.register(settingsRoutes);
}
