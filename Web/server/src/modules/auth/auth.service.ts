import argon2 from 'argon2';
import { uuidv7 } from 'uuidv7';
import type { Me, Role } from '@shikkhasathi/shared';
import { authRepo, type UserRow } from './auth.repository';
import { lookupAcademicNames } from '../catalog/catalog.lookup';
import { hmac, randomDigits, randomToken, safeEqual } from '../../common/crypto';
import { AppError, badRequest, conflict, rateLimited, unauthorized } from '../../common/errors';
import { REFRESH_TTL_SEC, signAccessToken } from '../../common/jwt';
import { hitLimit } from '../../common/redis';
import { isProd } from '../../config/env';
import { smsProvider } from '../../services/sms';
import { sendEmail } from '../../services/email';
import { verifyGoogleIdToken } from '../../services/google/identity';
import { coreDb } from '../../database/client';
import { studentProfiles } from '../../database/core/schema';

const OTP_TTL_SEC = 120;
const OTP_RESEND_SEC = 60;
const OTP_MAX_PER_HOUR = 5;
const OTP_MAX_ATTEMPTS = 5;
const EMAIL_CODE_TTL_SEC = 600;
/** A refresh token presented again within this window after rotation is treated as a race, not theft. */
const ROTATION_GRACE_MS = 30_000;

const ARGON = { type: argon2.argon2id, memoryCost: 19456, timeCost: 2, parallelism: 1 } as const;

export interface DeviceInfo {
  deviceId?: string;
  deviceName?: string;
  platform: 'web' | 'android' | 'ios';
  ip?: string;
  userAgent?: string;
}

let dummy: Promise<string> | null = null;
const dummyHash = () => (dummy ??= argon2.hash('not-a-real-password', ARGON));

const otpHash = (target: string, code: string) => hmac(`${target}:${code}`);

// ---------------------------------------------------------------- Me

export async function buildMe(user: UserRow): Promise<Me> {
  const [profile, seller] = await Promise.all([authRepo.studentProfile(user.id), authRepo.sellerProfile(user.id)]);
  const names = await lookupAcademicNames({
    institutionIds: profile?.institutionId ? [profile.institutionId] : [],
    departmentIds: profile?.departmentId ? [profile.departmentId] : [],
    programIds: profile?.programId ? [profile.programId] : [],
  });
  const inst = profile?.institutionId ? names.institution.get(profile.institutionId) : undefined;
  const dept = profile?.departmentId ? names.department.get(profile.departmentId) : undefined;
  const prog = profile?.programId ? names.program.get(profile.programId) : undefined;
  return {
    id: user.id,
    name: user.name,
    phone: user.phone,
    email: user.email,
    emailVerified: !!user.emailVerifiedAt,
    avatarUrl: user.avatarUrl,
    roles: user.roles as Role[],
    locale: user.locale as Me['locale'],
    theme: user.theme as Me['theme'],
    lowDataMode: profile?.lowDataMode ?? false,
    onboardingCompleted: !!user.onboardingCompletedAt,
    hasPassword: !!user.passwordHash,
    academic: {
      institutionId: profile?.institutionId ?? null,
      institutionName: inst?.name ?? null,
      institutionShort: inst?.shortName ?? null,
      departmentId: profile?.departmentId ?? null,
      departmentName: dept?.name ?? null,
      departmentShort: dept?.shortName ?? null,
      programId: profile?.programId ?? null,
      programName: prog?.name ?? null,
      semester: profile?.semester ?? null,
      batch: profile?.batch ?? null,
      section: profile?.section ?? null,
    },
    seller: seller ? { slug: seller.slug, verificationStatus: seller.verificationStatus as 'none' | 'pending' | 'verified' | 'rejected' } : null,
    createdAt: user.createdAt.toISOString(),
  };
}

export async function getMe(userId: string) {
  const u = await authRepo.byId(userId);
  if (!u || u.status === 'deleted') throw unauthorized();
  return buildMe(u);
}

// ---------------------------------------------------------------- sessions

export async function issueSession(user: UserRow, device: DeviceInfo, familyId: string = uuidv7()) {
  if (user.status === 'suspended') throw new AppError('FORBIDDEN', 'আপনার অ্যাকাউন্ট সাময়িকভাবে বন্ধ আছে। সাপোর্টে যোগাযোগ করুন।');
  if (user.status === 'deleted') throw unauthorized();
  const refreshToken = randomToken(48);
  const session = await authRepo.createSession({
    userId: user.id,
    familyId,
    refreshTokenHash: hmac(refreshToken),
    deviceId: device.deviceId ?? null,
    deviceName: device.deviceName ?? null,
    platform: device.platform,
    ip: device.ip ?? null,
    userAgent: device.userAgent?.slice(0, 300) ?? null,
    expiresAt: new Date(Date.now() + REFRESH_TTL_SEC * 1000),
  });
  const { token, expiresAt } = await signAccessToken({ id: user.id, roles: user.roles as Role[], sessionId: session.id });
  await authRepo.touch(user.id);
  return { accessToken: token, accessTokenExpiresAt: expiresAt.toISOString(), refreshToken, sessionId: session.id };
}

export async function refreshSession(refreshToken: string, device: DeviceInfo) {
  const s = await authRepo.sessionByHash(hmac(refreshToken));
  if (!s || s.revokedAt || s.expiresAt < new Date()) throw unauthorized();
  if (s.rotatedAt) {
    // Reuse of an already-rotated token. Outside the grace window → assume theft and kill the family.
    if (Date.now() - s.rotatedAt.getTime() > ROTATION_GRACE_MS) await authRepo.revokeFamily(s.familyId);
    throw unauthorized();
  }
  if (!(await authRepo.markRotated(s.id))) throw unauthorized();
  const user = await authRepo.byId(s.userId);
  if (!user) throw unauthorized();
  const tokens = await issueSession(user, { ...device, deviceId: s.deviceId ?? device.deviceId, deviceName: s.deviceName ?? device.deviceName, platform: s.platform as DeviceInfo['platform'] }, s.familyId);
  return { user, ...tokens };
}

export async function logout(refreshToken: string | undefined) {
  if (!refreshToken) return;
  const s = await authRepo.sessionByHash(hmac(refreshToken));
  if (s) await authRepo.revokeFamily(s.familyId);
}

export async function listSessions(userId: string, currentSessionId: string) {
  const rows = await authRepo.activeSessions(userId);
  return rows.map((r) => ({
    id: r.id,
    deviceName: r.deviceName,
    platform: r.platform as DeviceInfo['platform'],
    lastSeenAt: r.lastSeenAt.toISOString(),
    createdAt: r.createdAt.toISOString(),
    current: r.id === currentSessionId,
  }));
}

export async function revokeSession(userId: string, sessionId: string) {
  const [row] = await authRepo.revokeSession(sessionId, userId);
  if (row) await authRepo.revokeFamily(row.familyId);
}

// ---------------------------------------------------------------- phone OTP

export async function requestPhoneOtp(phone: string, ip?: string) {
  const perPhone = await hitLimit(`otp:phone:${phone}`, OTP_MAX_PER_HOUR, 3600);
  if (!perPhone.allowed) throw rateLimited(`এক ঘণ্টায় সর্বোচ্চ ${OTP_MAX_PER_HOUR} বার কোড পাঠানো যায়।`, { retryAfterSec: perPhone.retryAfterSec });
  if (ip) {
    const perIp = await hitLimit(`otp:ip:${ip}`, 20, 3600);
    if (!perIp.allowed) throw rateLimited(undefined, { retryAfterSec: perIp.retryAfterSec });
  }
  const last = await authRepo.latestOtp(phone, 'login');
  if (last && !last.consumedAt && Date.now() - last.createdAt.getTime() < OTP_RESEND_SEC * 1000) {
    const wait = Math.ceil((OTP_RESEND_SEC * 1000 - (Date.now() - last.createdAt.getTime())) / 1000);
    throw rateLimited(`${wait} সেকেন্ড পর আবার কোড চাইতে পারবেন।`, { retryAfterSec: wait });
  }
  const sms = await smsProvider();
  if (!sms) throw new AppError('INTEGRATION_DISABLED', 'SMS সার্ভিস এখন চালু নেই। ইমেইল দিয়ে লগইন করুন।');
  const code = randomDigits(6);
  await authRepo.createOtp({ target: phone, codeHash: otpHash(phone, code), purpose: 'login', expiresAt: new Date(Date.now() + OTP_TTL_SEC * 1000) });
  await sms.send(phone, `ShikkhaSathi কোড: ${code} (২ মিনিট বৈধ)। কাউকে জানাবেন না।`);
  return { sent: true as const, expiresInSec: OTP_TTL_SEC, resendInSec: OTP_RESEND_SEC, ...(sms.name === 'fake' && !isProd ? { devCode: code } : {}) };
}

async function checkCode(target: string, purpose: string, code: string) {
  const otp = await authRepo.latestOtp(target, purpose);
  if (!otp || otp.consumedAt || otp.expiresAt < new Date()) throw badRequest('কোডের মেয়াদ শেষ। নতুন কোড নিন।');
  if (otp.attempts >= OTP_MAX_ATTEMPTS) throw rateLimited('অনেকবার ভুল কোড দেওয়া হয়েছে। নতুন কোড নিন।');
  if (!safeEqual(otp.codeHash, otpHash(target, code))) {
    await authRepo.bumpOtpAttempts(otp.id);
    throw badRequest('কোডটি সঠিক নয়। আবার চেষ্টা করুন।');
  }
  if (!(await authRepo.consumeOtp(otp.id))) throw badRequest('কোডটি আগেই ব্যবহার হয়েছে।');
}

export async function verifyPhoneOtp(input: { phone: string; code: string; name?: string }, device: DeviceInfo) {
  await checkCode(input.phone, 'login', input.code);
  let user = await authRepo.byPhone(input.phone);
  const isNewUser = !user;
  if (!user) user = await authRepo.create({ phone: input.phone, phoneVerifiedAt: new Date(), name: input.name ?? 'শিক্ষার্থী' });
  else if (!user.phoneVerifiedAt) user = await authRepo.update(user.id, { phoneVerifiedAt: new Date() });
  const tokens = await issueSession(user, device);
  return { user, isNewUser, ...tokens };
}

// ---------------------------------------------------------------- email + password

export async function register(input: { name: string; email: string; password: string; asSeller: boolean }, device: DeviceInfo) {
  if (await authRepo.byEmail(input.email)) throw conflict('এই ইমেইলে আগেই অ্যাকাউন্ট আছে। লগইন করুন।');
  const passwordHash = await argon2.hash(input.password, ARGON);
  const user = await authRepo.create({ name: input.name, email: input.email, passwordHash, roles: ['student'] });
  const code = await createEmailCode(user.email!, 'email_verify');
  await sendEmail({ to: user.email!, toName: user.name, template: 'welcome', data: { name: user.name, code }, relatedEntity: `user:${user.id}` });
  const tokens = await issueSession(user, device);
  return { user, isNewUser: true, ...tokens };
}

export async function loginWithPassword(input: { email: string; password: string }, device: DeviceInfo, ip?: string) {
  const lim = await hitLimit(`login:${ip ?? 'na'}:${input.email}`, 10, 15 * 60);
  if (!lim.allowed) throw rateLimited('অনেকবার চেষ্টা হয়েছে। ১৫ মিনিট পর আবার চেষ্টা করুন।', { retryAfterSec: lim.retryAfterSec });
  const user = await authRepo.byEmail(input.email);
  // Constant-ish work even when user missing, to avoid user enumeration by timing.
  const hash = user?.passwordHash ?? (await dummyHash());
  const ok = await argon2.verify(hash, input.password).catch(() => false);
  if (!user || !user.passwordHash || !ok) throw unauthorized('ইমেইল বা পাসওয়ার্ড সঠিক নয়।');
  const tokens = await issueSession(user, device);
  return { user, isNewUser: false, ...tokens };
}

export async function changePassword(userId: string, current: string | undefined, next: string) {
  const user = await authRepo.byId(userId);
  if (!user) throw unauthorized();
  if (user.passwordHash) {
    if (!current || !(await argon2.verify(user.passwordHash, current).catch(() => false))) throw badRequest('বর্তমান পাসওয়ার্ড সঠিক নয়।');
  }
  await authRepo.update(userId, { passwordHash: await argon2.hash(next, ARGON) });
}

async function createEmailCode(email: string, purpose: 'email_verify' | 'password_reset') {
  const code = randomDigits(6);
  await authRepo.createOtp({ target: email, codeHash: otpHash(email, code), purpose, expiresAt: new Date(Date.now() + EMAIL_CODE_TTL_SEC * 1000) });
  return code;
}

export async function requestEmailCode(email: string, purpose: 'email_verify' | 'password_reset') {
  const lim = await hitLimit(`emailcode:${email}`, 5, 3600);
  if (!lim.allowed) throw rateLimited(undefined, { retryAfterSec: lim.retryAfterSec });
  const user = await authRepo.byEmail(email);
  // Do not reveal whether the account exists.
  if (!user) return;
  const code = await createEmailCode(email, purpose);
  await sendEmail({ to: email, toName: user.name, template: purpose === 'password_reset' ? 'password_reset' : 'email_otp', data: { code }, locale: user.locale as 'bn' | 'en', relatedEntity: `user:${user.id}` });
}

export async function resetPassword(email: string, code: string, password: string) {
  await checkCode(email, 'password_reset', code);
  const user = await authRepo.byEmail(email);
  if (!user) throw badRequest('কোডটি সঠিক নয়।');
  await authRepo.update(user.id, { passwordHash: await argon2.hash(password, ARGON), emailVerifiedAt: user.emailVerifiedAt ?? new Date() });
}

export async function verifyEmail(userId: string, code: string) {
  const user = await authRepo.byId(userId);
  if (!user?.email) throw badRequest('কোনো ইমেইল যুক্ত নেই।');
  await checkCode(user.email, 'email_verify', code);
  return authRepo.update(userId, { emailVerifiedAt: new Date() });
}

// ---------------------------------------------------------------- Google sign-in

export async function loginWithGoogle(idToken: string, device: DeviceInfo) {
  const g = await verifyGoogleIdToken(idToken);
  let user = await authRepo.byGoogleSub(g.sub);
  let isNewUser = false;
  if (!user && g.emailVerified) {
    const byEmail = await authRepo.byEmail(g.email);
    if (byEmail) user = await authRepo.update(byEmail.id, { googleSub: g.sub, emailVerifiedAt: byEmail.emailVerifiedAt ?? new Date(), avatarUrl: byEmail.avatarUrl ?? g.picture });
  }
  if (!user) {
    isNewUser = true;
    user = await authRepo.create({ name: g.name, email: g.email, googleSub: g.sub, emailVerifiedAt: g.emailVerified ? new Date() : null, avatarUrl: g.picture });
    await sendEmail({ to: g.email, toName: g.name, template: 'welcome', data: { name: g.name }, relatedEntity: `user:${user.id}` });
  }
  const tokens = await issueSession(user, device);
  return { user, isNewUser, ...tokens };
}

// ---------------------------------------------------------------- account

export async function updateMe(userId: string, patch: { name?: string; locale?: string; theme?: string; lowDataMode?: boolean; email?: string; avatarUrl?: string | null }) {
  const { lowDataMode, email, ...rest } = patch;
  const user = await authRepo.byId(userId);
  if (!user) throw unauthorized();
  const set: Record<string, unknown> = { ...rest };
  if (email && email !== user.email) {
    const other = await authRepo.byEmail(email);
    if (other && other.id !== userId) throw conflict('এই ইমেইল অন্য অ্যাকাউন্টে ব্যবহার হচ্ছে।');
    set.email = email;
    set.emailVerifiedAt = null;
  }
  const updated = Object.keys(set).length ? await authRepo.update(userId, set) : user;
  if (lowDataMode !== undefined) {
    await coreDb
      .insert(studentProfiles)
      .values({ userId, lowDataMode })
      .onConflictDoUpdate({ target: studentProfiles.userId, set: { lowDataMode, updatedAt: new Date() } });
  }
  if (set.email) {
    const code = await createEmailCode(email!, 'email_verify');
    await sendEmail({ to: email!, template: 'email_otp', data: { code }, relatedEntity: `user:${userId}` });
  }
  return buildMe(updated);
}

export async function requestDeletion(userId: string) {
  await authRepo.update(userId, { status: 'pending_deletion', deletionRequestedAt: new Date() });
  const sessions = await authRepo.activeSessions(userId);
  await Promise.all(sessions.map((s) => authRepo.revokeFamily(s.familyId)));
}
