import { SignJWT, jwtVerify } from 'jose';
import type { Role } from '@shikkhasathi/shared';
import { env } from '../config/env';
import { unauthorized } from './errors';

const accessKey = new TextEncoder().encode(env.JWT_ACCESS_SECRET);
export const ACCESS_TTL_SEC = 15 * 60;
export const REFRESH_TTL_SEC = 30 * 24 * 3600;

export interface AuthUser {
  id: string;
  roles: Role[];
  sessionId: string;
}

export async function signAccessToken(u: AuthUser): Promise<{ token: string; expiresAt: Date }> {
  const expiresAt = new Date(Date.now() + ACCESS_TTL_SEC * 1000);
  const token = await new SignJWT({ roles: u.roles, sid: u.sessionId })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(u.id)
    .setIssuer('shikkhasathi')
    .setAudience('shikkhasathi-api')
    .setIssuedAt()
    .setExpirationTime(Math.floor(expiresAt.getTime() / 1000))
    .sign(accessKey);
  return { token, expiresAt };
}

export async function verifyAccessToken(token: string): Promise<AuthUser> {
  try {
    const { payload } = await jwtVerify(token, accessKey, { issuer: 'shikkhasathi', audience: 'shikkhasathi-api' });
    if (!payload.sub || typeof payload.sid !== 'string') throw new Error('bad payload');
    return { id: payload.sub, roles: (payload.roles as Role[]) ?? ['student'], sessionId: payload.sid };
  } catch {
    throw unauthorized();
  }
}

/** Generic signed short-lived tokens (OAuth state, playback, etc.). */
export async function signPurposeToken(purpose: string, data: Record<string, unknown>, ttlSec: number) {
  return new SignJWT({ ...data, p: purpose })
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuedAt()
    .setExpirationTime(`${ttlSec}s`)
    .sign(accessKey);
}

export async function verifyPurposeToken<T extends Record<string, unknown>>(purpose: string, token: string): Promise<T> {
  try {
    const { payload } = await jwtVerify(token, accessKey);
    if (payload.p !== purpose) throw new Error('wrong purpose');
    return payload as unknown as T;
  } catch {
    throw unauthorized('লিংকটির মেয়াদ শেষ বা অবৈধ।');
  }
}
