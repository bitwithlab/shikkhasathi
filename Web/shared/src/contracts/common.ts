import { z } from 'zod';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
export type AuthLevel = 'public' | 'optional' | 'user' | 'seller' | 'verified_seller' | 'admin';

/**
 * An endpoint contract. The server validates with it, the web client calls through it,
 * and `scripts/gen-openapi` turns the whole registry into OpenAPI for the mobile app.
 */
export interface Endpoint<
  P extends z.ZodType = z.ZodType,
  Q extends z.ZodType = z.ZodType,
  B extends z.ZodType = z.ZodType,
  R extends z.ZodType = z.ZodType,
> {
  method: HttpMethod;
  /** Path relative to /api/v1, fastify style params (`/courses/:id`). */
  path: string;
  summary: string;
  tag: string;
  auth: AuthLevel;
  params?: P;
  query?: Q;
  body?: B;
  response: R;
  /** HTTP status on success (default 200). */
  status?: number;
}

export function endpoint<
  P extends z.ZodType = z.ZodUndefined,
  Q extends z.ZodType = z.ZodUndefined,
  B extends z.ZodType = z.ZodUndefined,
  R extends z.ZodType = z.ZodType,
>(e: Endpoint<P, Q, B, R>): Endpoint<P, Q, B, R> {
  return e;
}

export type InferParams<E> = E extends Endpoint<infer P, z.ZodType, z.ZodType, z.ZodType> ? z.infer<P> : never;
export type InferQuery<E> = E extends Endpoint<z.ZodType, infer Q, z.ZodType, z.ZodType> ? z.input<Q> : never;
export type InferBody<E> = E extends Endpoint<z.ZodType, z.ZodType, infer B, z.ZodType> ? z.input<B> : never;
export type InferResponse<E> = E extends Endpoint<z.ZodType, z.ZodType, z.ZodType, infer R> ? z.infer<R> : never;

export const ErrorEnvelope = z.object({
  error: z.object({
    code: z.string(),
    message: z.string(),
    details: z.unknown().optional(),
  }),
});
export type ErrorEnvelope = z.infer<typeof ErrorEnvelope>;

export const ERROR_CODES = [
  'BAD_REQUEST',
  'VALIDATION_FAILED',
  'UNAUTHORIZED',
  'FORBIDDEN',
  'NOT_FOUND',
  'CONFLICT',
  'RATE_LIMITED',
  'PAYMENT_FAILED',
  'INTEGRATION_DISABLED',
  'CLASSROOM_REAUTH',
  'CLASSROOM_WORKSPACE_BLOCKED',
  'CLASSROOM_QUOTA',
  'INTERNAL',
] as const;
export type ErrorCode = (typeof ERROR_CODES)[number];

export const uuid = z.uuid();
export const IdParams = z.object({ id: z.uuid() });
export const SlugParams = z.object({ slug: z.string().min(1).max(200) });
export const isoDate = z.iso.datetime({ offset: true });

export const CursorQuery = z.object({
  cursor: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(100).default(20),
});

export function page<T extends z.ZodType>(item: T) {
  return z.object({ items: z.array(item), nextCursor: z.string().nullable() });
}

export const Ok = z.object({ ok: z.literal(true) });

/** Bangladeshi mobile number, normalised to +8801XXXXXXXXX. */
export const BdPhone = z
  .string()
  .trim()
  .transform((v) => v.replace(/[\s-]/g, ''))
  .transform((v) => (v.startsWith('+880') ? v : v.startsWith('880') ? `+${v}` : v.startsWith('0') ? `+88${v}` : v))
  .pipe(z.string().regex(/^\+8801[3-9]\d{8}$/, 'সঠিক মোবাইল নম্বর দিন (01XXXXXXXXX)'));

export const Email = z.string().trim().toLowerCase().pipe(z.email('সঠিক ইমেইল দিন'));
export const Password = z.string().min(8, 'পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে').max(128);

/**
 * PATCH body from an upsert schema: every field optional and **defaults removed**.
 * (zod 4 keeps `.default()` inside `.partial()`, which would silently reset omitted fields.)
 */
export function patchOf<S extends z.ZodRawShape>(schema: z.ZodObject<S>) {
  const shape: Record<string, z.ZodType> = {};
  for (const [k, v] of Object.entries(schema.shape)) {
    let f = v as z.ZodType;
    while (f instanceof z.ZodDefault) f = f.unwrap() as z.ZodType;
    shape[k] = f.optional();
  }
  return z.object(shape) as unknown as z.ZodObject<{ [K in keyof S]: z.ZodOptional<S[K] extends z.ZodDefault<infer I> ? I : S[K]> }>;
}
