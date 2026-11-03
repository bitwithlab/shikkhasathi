import { redis } from './redis';
import type { FastifyInstance, FastifyReply, FastifyRequest, RouteShorthandOptions } from 'fastify';
import type { z } from 'zod';
import type { Endpoint } from '@shikkhasathi/shared';
import { AppError, forbidden, unauthorized } from './errors';
import { verifyAccessToken, type AuthUser } from './jwt';

declare module 'fastify' {
  interface FastifyRequest {
    user?: AuthUser;
  }
}

type Part<T> = T extends z.ZodUndefined ? undefined : T extends z.ZodType ? z.infer<T> : undefined;
type PartsOf<E> = E extends Endpoint<infer P, infer Q, infer B, z.ZodType> ? { params: Part<P>; query: Part<Q>; body: Part<B> } : never;

export interface HandlerCtx<E extends Endpoint> {
  params: PartsOf<E>['params'];
  query: PartsOf<E>['query'];
  body: PartsOf<E>['body'];
  user: AuthUser | undefined;
  req: FastifyRequest;
  reply: FastifyReply;
}

export interface AuthedCtx<E extends Endpoint> extends HandlerCtx<E> {
  user: AuthUser;
}

type Ctx<E extends Endpoint> = E['auth'] extends 'public' | 'optional' ? HandlerCtx<E> : AuthedCtx<E>;
type Result<E extends Endpoint> = z.input<E['response']> | Promise<z.input<E['response']>>;

/** Hook to check "verified seller" (DB lookup) — installed by the seller module. */
let verifiedSellerCheck: (userId: string) => Promise<boolean> = async () => false;
export function setVerifiedSellerCheck(fn: (userId: string) => Promise<boolean>) {
  verifiedSellerCheck = fn;
}

export async function authenticate(req: FastifyRequest): Promise<AuthUser | undefined> {
  const h = req.headers.authorization;
  if (!h) return undefined;
  const [scheme, token] = h.split(' ');
  if (scheme?.toLowerCase() !== 'bearer' || !token) throw unauthorized();
  const user = await verifyAccessToken(token);
  // suspended accounts lose access immediately (access tokens live 15 min)
  if (await redis().exists(`susp:${user.id}`).catch(() => 0)) throw forbidden('আপনার অ্যাকাউন্ট সাময়িকভাবে বন্ধ আছে। সাপোর্টে যোগাযোগ করুন।');
  return user;
}

function parse<T extends z.ZodType>(schema: T | undefined, value: unknown, where: string): z.infer<T> | undefined {
  if (!schema) return undefined;
  const r = schema.safeParse(value ?? (where === 'body' ? {} : value));
  if (!r.success) {
    throw new AppError(
      'VALIDATION_FAILED',
      r.error.issues[0]?.message ?? undefined,
      r.error.issues.map((i) => ({ path: [where, ...i.path].join('.'), message: i.message })),
    );
  }
  return r.data;
}

/**
 * Register a route from a shared contract: validates params/query/body with Zod, enforces auth/RBAC,
 * and parses the response through the contract schema (strips unknown fields such as hashes).
 */
export function route<E extends Endpoint>(
  app: FastifyInstance,
  e: E,
  handler: (ctx: Ctx<E>) => Result<E>,
  opts: RouteShorthandOptions & { rateLimit?: { max: number; timeWindow: string | number }; cacheControl?: string } = {},
) {
  const { rateLimit, cacheControl, ...rest } = opts;
  app.route({
    method: e.method,
    url: e.path,
    ...rest,
    config: { ...(rest.config ?? {}), ...(rateLimit ? { rateLimit } : {}) },
    handler: async (req, reply) => {
      const user = await authenticate(req).catch((err) => {
        if (e.auth === 'optional') return undefined;
        throw err;
      });
      req.user = user;
      if (e.auth !== 'public' && e.auth !== 'optional') {
        if (!user) throw unauthorized();
        if (e.auth === 'admin' && !user.roles.includes('admin')) throw forbidden();
        if ((e.auth === 'seller' || e.auth === 'verified_seller') && !user.roles.includes('seller')) throw forbidden();
        if (e.auth === 'verified_seller' && !(await verifiedSellerCheck(user.id))) {
          throw forbidden('ভেরিফিকেশন সম্পন্ন হলে এই কাজটি করতে পারবেন।');
        }
      }
      const ctx = {
        params: parse(e.params, req.params, 'params'),
        query: parse(e.query, req.query, 'query'),
        body: parse(e.body, req.body, 'body'),
        user,
        req,
        reply,
      } as Ctx<E>;
      const out = await handler(ctx);
      if (reply.sent) return reply;
      const parsed = e.response.safeParse(out);
      if (!parsed.success) {
        req.log.error({ issues: parsed.error.issues, path: e.path }, 'response contract violation');
        throw new AppError('INTERNAL');
      }
      reply.status(e.status ?? 200);
      if (cacheControl) reply.header('cache-control', cacheControl);
      return parsed.data;
    },
  });
}
