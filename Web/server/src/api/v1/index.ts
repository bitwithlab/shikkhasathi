import type { FastifyInstance } from 'fastify';
import { authRoutes } from '../../modules/auth/auth.routes';
import { healthRoutes, openapiRoute } from '../../modules/health/health.routes';
import { settingsRoutes } from '../../modules/settings/settings.routes';
import { catalogRoutes } from '../../modules/catalog/catalog.routes';
import { classroomRoutes } from '../../modules/classroom/classroom.routes';
import { notificationRoutes } from '../../modules/notifications/notifications.routes';
import { dashboardRoutes } from '../../modules/dashboard/dashboard.routes';
import { sellerRoutes } from '../../modules/seller/seller.routes';
import { filesRoutes } from '../../modules/files/files.routes';
import { courseRoutes } from '../../modules/courses/courses.routes';
import { orderRoutes } from '../../modules/orders/orders.routes';
import { learningRoutes } from '../../modules/learning/learning.routes';
import '../../services/search';
import '../../modules/seller/seller.jobs';

/** All /api/v1 route modules. Handlers stay thin; logic lives in modules/<m>/<m>.service.ts. */
export async function apiV1(app: FastifyInstance) {
  await app.register(healthRoutes);
  await app.register(openapiRoute);
  await app.register(authRoutes);
  await app.register(settingsRoutes);
  await app.register(catalogRoutes);
  await app.register(classroomRoutes);
  await app.register(notificationRoutes);
  await app.register(dashboardRoutes);
  await app.register(sellerRoutes);
  await app.register(filesRoutes);
  await app.register(courseRoutes);
  await app.register(orderRoutes);
  await app.register(learningRoutes);
}
