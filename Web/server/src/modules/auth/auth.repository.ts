import { and, desc, eq, gt, isNull, sql } from 'drizzle-orm';
import { coreDb } from '../../database/client';
import { authOtps, sellerProfiles, sessions, studentProfiles, users } from '../../database/core/schema';

export type UserRow = typeof users.$inferSelect;

export const authRepo = {
  byId: async (id: string) => (await coreDb.select().from(users).where(eq(users.id, id)))[0],
  byPhone: async (phone: string) => (await coreDb.select().from(users).where(eq(users.phone, phone)))[0],
  byEmail: async (email: string) => (await coreDb.select().from(users).where(eq(users.email, email)))[0],
  byGoogleSub: async (sub: string) => (await coreDb.select().from(users).where(eq(users.googleSub, sub)))[0],
  create: async (v: typeof users.$inferInsert) => (await coreDb.insert(users).values(v).returning())[0]!,
  update: async (id: string, v: Partial<typeof users.$inferInsert>) => (await coreDb.update(users).set(v).where(eq(users.id, id)).returning())[0]!,
  touch: (id: string) => coreDb.update(users).set({ lastActiveAt: new Date() }).where(eq(users.id, id)),

  studentProfile: async (userId: string) => (await coreDb.select().from(studentProfiles).where(eq(studentProfiles.userId, userId)))[0],
  sellerProfile: async (userId: string) => (await coreDb.select().from(sellerProfiles).where(eq(sellerProfiles.userId, userId)))[0],

  latestOtp: async (target: string, purpose: string) =>
    (await coreDb.select().from(authOtps).where(and(eq(authOtps.target, target), eq(authOtps.purpose, purpose))).orderBy(desc(authOtps.createdAt)).limit(1))[0],
  createOtp: (v: typeof authOtps.$inferInsert) => coreDb.insert(authOtps).values(v),
  bumpOtpAttempts: (id: string) => coreDb.update(authOtps).set({ attempts: sql`${authOtps.attempts} + 1` }).where(eq(authOtps.id, id)),
  consumeOtp: async (id: string) =>
    (await coreDb.update(authOtps).set({ consumedAt: new Date() }).where(and(eq(authOtps.id, id), isNull(authOtps.consumedAt))).returning({ id: authOtps.id })).length > 0,

  createSession: async (v: typeof sessions.$inferInsert) => (await coreDb.insert(sessions).values(v).returning())[0]!,
  sessionByHash: async (hash: string) => (await coreDb.select().from(sessions).where(eq(sessions.refreshTokenHash, hash)))[0],
  sessionById: async (id: string) => (await coreDb.select().from(sessions).where(eq(sessions.id, id)))[0],
  markRotated: async (id: string) =>
    (await coreDb.update(sessions).set({ rotatedAt: new Date() }).where(and(eq(sessions.id, id), isNull(sessions.rotatedAt))).returning({ id: sessions.id })).length > 0,
  revokeFamily: (familyId: string) => coreDb.update(sessions).set({ revokedAt: new Date() }).where(and(eq(sessions.familyId, familyId), isNull(sessions.revokedAt))),
  revokeSession: (id: string, userId: string) =>
    coreDb.update(sessions).set({ revokedAt: new Date() }).where(and(eq(sessions.id, id), eq(sessions.userId, userId))).returning({ familyId: sessions.familyId }),
  activeSessions: (userId: string) =>
    coreDb
      .select()
      .from(sessions)
      .where(and(eq(sessions.userId, userId), isNull(sessions.revokedAt), isNull(sessions.rotatedAt), gt(sessions.expiresAt, new Date())))
      .orderBy(desc(sessions.lastSeenAt)),
};
