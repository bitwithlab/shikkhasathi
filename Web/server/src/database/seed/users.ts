import argon2 from 'argon2';
import { eq } from 'drizzle-orm';
import { coreDb } from '../client';
import { users } from '../core/schema';

export const DEMO_PASSWORD = process.env.SEED_PASSWORD ?? 'shikkhasathi123';

export interface SeedUser {
  key: string;
  name: string;
  email: string;
  phone?: string;
  roles: ('student' | 'seller' | 'admin')[];
}

export const SEED_USERS: SeedUser[] = [
  { key: 'admin', name: 'মাহদী হাসান', email: 'admin@shikkhasathi.com', roles: ['student', 'admin'] },
  { key: 'sadman', name: 'সাদমান সাকিব', email: 'sadman@demo.shikkhasathi.com', phone: '+8801711000001', roles: ['student'] },
  { key: 'nusrat', name: 'নুসরাত জাহান', email: 'nusrat@demo.shikkhasathi.com', phone: '+8801711000002', roles: ['student'] },
  { key: 'tanvir', name: 'তানভীর আহমেদ', email: 'tanvir@demo.shikkhasathi.com', phone: '+8801711000003', roles: ['student'] },
  { key: 'rakib', name: 'মো. রাকিবুল হাসান', email: 'rakib@demo.shikkhasathi.com', phone: '+8801811000001', roles: ['student', 'seller'] },
  { key: 'tasnim', name: 'ড. তাসনিম রহমান', email: 'tasnim@demo.shikkhasathi.com', phone: '+8801811000002', roles: ['student', 'seller'] },
  { key: 'shakil', name: 'শাকিল মাহমুদ', email: 'shakil@demo.shikkhasathi.com', phone: '+8801811000003', roles: ['student', 'seller'] },
  { key: 'farhana', name: 'ফারহানা হক', email: 'farhana@demo.shikkhasathi.com', phone: '+8801811000004', roles: ['student', 'seller'] },
  { key: 'saikat', name: 'সৈকত চৌধুরী', email: 'saikat@demo.shikkhasathi.com', phone: '+8801811000005', roles: ['student', 'seller'] },
  { key: 'maruf', name: 'মারুফ রশীদ', email: 'maruf@demo.shikkhasathi.com', phone: '+8801811000006', roles: ['student', 'seller'] },
];

/** Upsert demo users (idempotent). Returns key → user id. */
export async function seedUsers(): Promise<Record<string, string>> {
  const hash = await argon2.hash(DEMO_PASSWORD, { type: argon2.argon2id });
  const out: Record<string, string> = {};
  for (const u of SEED_USERS) {
    const [existing] = await coreDb.select({ id: users.id }).from(users).where(eq(users.email, u.email));
    if (existing) {
      out[u.key] = existing.id;
      continue;
    }
    const [row] = await coreDb
      .insert(users)
      .values({ name: u.name, email: u.email, phone: u.phone ?? null, roles: u.roles, passwordHash: hash, emailVerifiedAt: new Date(), phoneVerifiedAt: u.phone ? new Date() : null, onboardingCompletedAt: u.key === 'admin' ? new Date() : null })
      .returning({ id: users.id });
    out[u.key] = row!.id;
  }
  return out;
}
