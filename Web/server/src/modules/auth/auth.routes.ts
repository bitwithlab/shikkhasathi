import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import { badRequest, unauthorized } from '../../common/errors';
import { randomToken, safeEqual } from '../../common/crypto';
import { REFRESH_TTL_SEC } from '../../common/jwt';
import { isProd } from '../../config/env';
import * as svc from './auth.service';
import type { UserRow } from './auth.repository';

const RT_COOKIE = 'ss_rt';
const CSRF_COOKIE = 'ss_csrf';
const COOKIE_PATH = '/api/v1/auth';

function device(req: FastifyRequest, body: { deviceId?: string; deviceName?: string; platform?: 'web' | 'android' | 'ios' }): svc.DeviceInfo {
  return { deviceId: body.deviceId, deviceName: body.deviceName ?? req.headers['user-agent']?.slice(0, 80), platform: body.platform ?? 'web', ip: req.ip, userAgent: req.headers['user-agent'] };
}

function setWebCookies(reply: FastifyReply, refreshToken: string) {
  const common = { path: COOKIE_PATH, secure: isProd, sameSite: 'lax' as const, maxAge: REFRESH_TTL_SEC };
  reply.setCookie(RT_COOKIE, refreshToken, { ...common, httpOnly: true });
  // Double-submit CSRF token: readable by JS, must be echoed in X-CSRF-Token on cookie-authenticated calls.
  reply.setCookie(CSRF_COOKIE, randomToken(18), { ...common, path: '/', httpOnly: false });
}

function clearWebCookies(reply: FastifyReply) {
  reply.clearCookie(RT_COOKIE, { path: COOKIE_PATH });
  reply.clearCookie(CSRF_COOKIE, { path: '/' });
}

async function respond(
  reply: FastifyReply,
  platform: string,
  r: { user: UserRow; isNewUser: boolean; accessToken: string; accessTokenExpiresAt: string; refreshToken: string },
) {
  const user = await svc.buildMe(r.user);
  if (platform === 'web') {
    setWebCookies(reply, r.refreshToken);
    return { accessToken: r.accessToken, accessTokenExpiresAt: r.accessTokenExpiresAt, user, isNewUser: r.isNewUser };
  }
  return { accessToken: r.accessToken, accessTokenExpiresAt: r.accessTokenExpiresAt, refreshToken: r.refreshToken, user, isNewUser: r.isNewUser };
}

/** Web refresh/logout use the httpOnly cookie and must pass the CSRF double-submit check. */
function refreshTokenFrom(req: FastifyRequest, bodyToken: string | undefined): { token: string | undefined; platform: 'web' | 'mobile' } {
  if (bodyToken) return { token: bodyToken, platform: 'mobile' };
  const cookie = req.cookies[RT_COOKIE];
  if (!cookie) return { token: undefined, platform: 'web' };
  const header = req.headers['x-csrf-token'];
  const csrf = req.cookies[CSRF_COOKIE];
  if (typeof header !== 'string' || !csrf || !safeEqual(header, csrf)) throw badRequest('CSRF যাচাই ব্যর্থ হয়েছে।');
  return { token: cookie, platform: 'web' };
}

export async function authRoutes(app: FastifyInstance) {
  const c = contracts.auth;
  const strict = { rateLimit: { max: 10, timeWindow: '15 minutes' } };

  route(app, c.otpRequest, ({ body, req }) => svc.requestPhoneOtp(body.phone, req.ip), { rateLimit: { max: 10, timeWindow: '1 hour' } });
  route(app, c.otpVerify, async ({ body, req, reply }) => respond(reply, body.platform, await svc.verifyPhoneOtp(body, device(req, body))), strict);
  route(app, c.register, async ({ body, req, reply }) => respond(reply, body.platform, await svc.register(body, device(req, body))), strict);
  route(app, c.login, async ({ body, req, reply }) => respond(reply, body.platform, await svc.loginWithPassword(body, device(req, body), req.ip)), strict);
  route(app, c.google, async ({ body, req, reply }) => respond(reply, body.platform, await svc.loginWithGoogle(body.idToken, device(req, body))), strict);

  route(app, c.refresh, async ({ body, req, reply }) => {
    const { token, platform } = refreshTokenFrom(req, body.refreshToken);
    if (!token) throw unauthorized();
    try {
      const r = await svc.refreshSession(token, device(req, { platform: platform === 'web' ? 'web' : 'android' }));
      return respond(reply, platform === 'web' ? 'web' : 'mobile', { ...r, isNewUser: false });
    } catch (err) {
      if (platform === 'web') clearWebCookies(reply);
      throw err;
    }
  }, { rateLimit: { max: 60, timeWindow: '1 minute' } });

  route(app, c.logout, async ({ body, req, reply }) => {
    const { token } = refreshTokenFrom(req, body.refreshToken);
    await svc.logout(token);
    clearWebCookies(reply);
    return { ok: true as const };
  });

  route(app, c.emailOtp, async ({ body }) => {
    await svc.requestEmailCode(body.email, body.purpose);
    return { ok: true as const };
  }, { rateLimit: { max: 5, timeWindow: '1 hour' } });
  route(app, c.passwordReset, async ({ body }) => {
    await svc.resetPassword(body.email, body.code, body.password);
    return { ok: true as const };
  }, strict);

  route(app, c.me, ({ user }) => svc.getMe(user.id));
  route(app, c.updateMe, ({ user, body }) => svc.updateMe(user.id, body));
  route(app, c.verifyEmail, async ({ user, body }) => svc.buildMe(await svc.verifyEmail(user.id, body.code)));
  route(app, c.changePassword, async ({ user, body }) => {
    await svc.changePassword(user.id, body.currentPassword, body.newPassword);
    return { ok: true as const };
  }, strict);
  route(app, c.sessions, async ({ user }) => ({ items: await svc.listSessions(user.id, user.sessionId) }));
  route(app, c.revokeSession, async ({ user, params }) => {
    await svc.revokeSession(user.id, params.id);
    return { ok: true as const };
  });
  route(app, c.deleteMe, async ({ user, reply }) => {
    await svc.requestDeletion(user.id);
    clearWebCookies(reply);
    return { ok: true as const };
  });
}
