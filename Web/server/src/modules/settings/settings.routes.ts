import type { FastifyInstance } from 'fastify';
import { INTEGRATIONS, contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import { getBusinessFlag, getIntegration, integrationState, testIntegration, updateIntegration } from './settings.service';

export async function settingsRoutes(app: FastifyInstance) {
  const c = contracts.settings;
  route(app, c.list, async () => ({ items: await Promise.all(INTEGRATIONS.map((d) => integrationState(d.provider))) }));
  route(app, c.update, async ({ params, body, user, req }) => updateIntegration(params.provider, body.values, user.id, req.ip));
  route(app, c.test, async ({ params, body, user }) => testIntegration(params.provider, { to: body.to, actorId: user.id }), {
    rateLimit: { max: 10, timeWindow: '1 minute' },
  });
  route(app, c.publicConfig, async () => {
    const [google, push, payment] = await Promise.all([getIntegration('google'), getIntegration('push'), getIntegration('payment')]);
    return {
      googleClientId: google.client_id ?? null,
      webPushPublicKey: push.web_push_public_key ?? null,
      features: {
        bundles: await getBusinessFlag('feature_bundles'),
        courseRequests: await getBusinessFlag('feature_course_requests'),
        classroom: await getBusinessFlag('feature_classroom'),
      },
      paymentGateway: payment.gateway ?? 'fake',
    };
  });
}
