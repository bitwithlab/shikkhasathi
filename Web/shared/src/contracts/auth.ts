import { z } from 'zod';
import { BdPhone, Email, IdParams, Ok, Password, endpoint } from './common';
import { LOCALES, PLATFORMS, ROLES, THEMES } from '../types';

export const Device = z.object({
  deviceId: z.string().min(4).max(128).optional(),
  deviceName: z.string().max(120).optional(),
  platform: z.enum(PLATFORMS).default('web'),
});

export const AcademicSummary = z.object({
  institutionId: z.uuid().nullable(),
  institutionName: z.string().nullable(),
  institutionShort: z.string().nullable(),
  departmentId: z.uuid().nullable(),
  departmentName: z.string().nullable(),
  departmentShort: z.string().nullable(),
  programId: z.uuid().nullable(),
  programName: z.string().nullable(),
  semester: z.number().int().nullable(),
  batch: z.string().nullable(),
  section: z.string().nullable(),
});

export const Me = z.object({
  id: z.uuid(),
  name: z.string(),
  phone: z.string().nullable(),
  email: z.string().nullable(),
  emailVerified: z.boolean(),
  avatarUrl: z.string().nullable(),
  roles: z.array(z.enum(ROLES)),
  locale: z.enum(LOCALES),
  theme: z.enum(THEMES),
  lowDataMode: z.boolean(),
  onboardingCompleted: z.boolean(),
  hasPassword: z.boolean(),
  academic: AcademicSummary,
  seller: z
    .object({ slug: z.string(), verificationStatus: z.enum(['none', 'pending', 'verified', 'rejected']) })
    .nullable(),
  createdAt: z.string(),
});
export type Me = z.infer<typeof Me>;

export const AuthTokens = z.object({
  accessToken: z.string(),
  accessTokenExpiresAt: z.string(),
  /** Returned in the body only for mobile clients; web gets an httpOnly cookie. */
  refreshToken: z.string().optional(),
  user: Me,
  isNewUser: z.boolean(),
});
export type AuthTokens = z.infer<typeof AuthTokens>;

export const OtpRequestBody = z.object({ phone: BdPhone });
export const OtpRequestResponse = z.object({
  sent: z.literal(true),
  expiresInSec: z.number(),
  resendInSec: z.number(),
  /** Only present outside production with the fake SMS provider, so local login works. */
  devCode: z.string().optional(),
});

export const OtpVerifyBody = Device.extend({
  phone: BdPhone,
  code: z.string().regex(/^\d{6}$/, '৬ সংখ্যার কোড দিন'),
  name: z.string().trim().min(2).max(80).optional(),
});

export const LoginBody = Device.extend({ email: Email, password: z.string().min(1).max(128) });
export const RegisterBody = Device.extend({
  name: z.string().trim().min(2, 'নাম লিখুন').max(80),
  email: Email,
  password: Password,
  asSeller: z.boolean().default(false),
});
export const GoogleLoginBody = Device.extend({
  /** Google ID token from Google Identity Services (web) or Credential Manager (Android). */
  idToken: z.string().min(10),
});
export const RefreshBody = z.object({ refreshToken: z.string().optional() });

export const EmailOtpRequestBody = z.object({ email: Email, purpose: z.enum(['email_verify', 'password_reset']) });
export const PasswordResetBody = z.object({ email: Email, code: z.string().regex(/^\d{6}$/), password: Password });
export const EmailVerifyBody = z.object({ code: z.string().regex(/^\d{6}$/) });

export const UpdateMeBody = z.object({
  name: z.string().trim().min(2).max(80).optional(),
  locale: z.enum(LOCALES).optional(),
  theme: z.enum(THEMES).optional(),
  lowDataMode: z.boolean().optional(),
  email: Email.optional(),
  avatarUrl: z.url().nullable().optional(),
});

export const ChangePasswordBody = z.object({ currentPassword: z.string().max(128).optional(), newPassword: Password });

export const SessionItem = z.object({
  id: z.uuid(),
  deviceName: z.string().nullable(),
  platform: z.enum(PLATFORMS),
  lastSeenAt: z.string(),
  createdAt: z.string(),
  current: z.boolean(),
});

export const auth = {
  otpRequest: endpoint({ method: 'POST', path: '/auth/otp/request', summary: 'Send login OTP by SMS', tag: 'auth', auth: 'public', body: OtpRequestBody, response: OtpRequestResponse }),
  otpVerify: endpoint({ method: 'POST', path: '/auth/otp/verify', summary: 'Verify phone OTP and sign in (creates account if new)', tag: 'auth', auth: 'public', body: OtpVerifyBody, response: AuthTokens }),
  login: endpoint({ method: 'POST', path: '/auth/login', summary: 'Email + password login', tag: 'auth', auth: 'public', body: LoginBody, response: AuthTokens }),
  register: endpoint({ method: 'POST', path: '/auth/register', summary: 'Email + password sign-up', tag: 'auth', auth: 'public', body: RegisterBody, response: AuthTokens, status: 201 }),
  google: endpoint({ method: 'POST', path: '/auth/google', summary: 'Sign in with Google ID token', tag: 'auth', auth: 'public', body: GoogleLoginBody, response: AuthTokens }),
  refresh: endpoint({ method: 'POST', path: '/auth/refresh', summary: 'Rotate refresh token', tag: 'auth', auth: 'public', body: RefreshBody, response: AuthTokens }),
  logout: endpoint({ method: 'POST', path: '/auth/logout', summary: 'Revoke current session', tag: 'auth', auth: 'public', body: RefreshBody, response: Ok }),
  emailOtp: endpoint({ method: 'POST', path: '/auth/email-otp', summary: 'Email a verification / password-reset code', tag: 'auth', auth: 'public', body: EmailOtpRequestBody, response: Ok }),
  passwordReset: endpoint({ method: 'POST', path: '/auth/password-reset', summary: 'Reset password with emailed code', tag: 'auth', auth: 'public', body: PasswordResetBody, response: Ok }),
  verifyEmail: endpoint({ method: 'POST', path: '/me/verify-email', summary: 'Verify email with emailed code', tag: 'auth', auth: 'user', body: EmailVerifyBody, response: Me }),
  me: endpoint({ method: 'GET', path: '/me', summary: 'Current user', tag: 'auth', auth: 'user', response: Me }),
  updateMe: endpoint({ method: 'PATCH', path: '/me', summary: 'Update profile basics & preferences', tag: 'auth', auth: 'user', body: UpdateMeBody, response: Me }),
  changePassword: endpoint({ method: 'POST', path: '/me/password', summary: 'Set or change password', tag: 'auth', auth: 'user', body: ChangePasswordBody, response: Ok }),
  sessions: endpoint({ method: 'GET', path: '/me/sessions', summary: 'Signed-in devices', tag: 'auth', auth: 'user', response: z.object({ items: z.array(SessionItem) }) }),
  revokeSession: endpoint({ method: 'DELETE', path: '/me/sessions/:id', summary: 'Sign a device out', tag: 'auth', auth: 'user', params: IdParams, response: Ok }),
  deleteMe: endpoint({ method: 'DELETE', path: '/me', summary: 'Request account deletion (30-day grace)', tag: 'auth', auth: 'user', response: Ok }),
};
