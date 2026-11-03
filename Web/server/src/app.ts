import Fastify, { type FastifyError } from 'fastify';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import cookie from '@fastify/cookie';
import helmet from '@fastify/helmet';
import cors from '@fastify/cors';
import rateLimit from '@fastify/rate-limit';
import compress from '@fastify/compress';
import { env, isProd, isTest } from './config/env';
import { loggerOptions } from './common/logger';
import { AppError } from './common/errors';
import { redis } from './common/redis';
import { apiV1 } from './api/v1';
import { serveClient } from './static';
import './services/email';
import './services/sms';

/** sha256 hashes of inline <script> blocks in the built index.html (theme bootstrap) for a strict CSP. */
function inlineScriptHashes(): string[] {
  const file = join(resolve(process.cwd(), env.CLIENT_DIST_DIR), 'index.html');
  if (!existsSync(file)) return [];
  const html = readFileSync(file, 'utf8');
  return [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => `'sha256-${createHash('sha256').update(m[1]!).digest('base64')}'`);
}

export async function buildApp() {
  const app = Fastify({
    logger: loggerOptions,
    trustProxy: true,
    bodyLimit: 2 * 1024 * 1024,
    genReqId: () => crypto.randomUUID(),
  });

  await app.register(helmet, {
    contentSecurityPolicy: {
      useDefaults: true,
      directives: {
        'default-src': ["'self'"],
        'script-src': ["'self'", 'https://accounts.google.com/gsi/client', ...inlineScriptHashes()],
        'style-src': ["'self'", "'unsafe-inline'", 'https://accounts.google.com/gsi/style'],
        'img-src': ["'self'", 'data:', 'blob:', 'https:'],
        'media-src': ["'self'", 'blob:', 'https:'],
        'connect-src': ["'self'", 'https:'],
        'frame-src': ['https://accounts.google.com', 'https://iframe.mediadelivery.net', 'https://www.youtube-nocookie.com'],
        'font-src': ["'self'", 'data:'],
        'object-src': ["'none'"],
        'base-uri': ["'self'"],
        'form-action': ["'self'", 'https://*.sslcommerz.com', 'https://*.bka.sh'],
        'frame-ancestors': ["'none'"],
        'upgrade-insecure-requests': isProd ? [] : null,
      },
    },
    crossOriginEmbedderPolicy: false,
    hsts: isProd ? { maxAge: 31536000, includeSubDomains: true, preload: true } : false,
  });
  await app.register(cors, {
    // Same-origin web; mobile apps don't need CORS. Allow APP_URL only (+ Vite dev).
    origin: isProd ? [env.APP_URL] : [env.APP_URL, 'http://localhost:5173'],
    credentials: true,
  });
  await app.register(cookie);
  await app.register(compress, { encodings: ['br', 'gzip'], threshold: 1024 });
  await app.register(rateLimit, {
    global: true,
    max: 300,
    timeWindow: '1 minute',
    ...(isTest ? {} : { redis: redis() }),
    keyGenerator: (req) => req.ip,
    // Static assets are not rate limited; only the API is.
    allowList: (req) => !req.url.startsWith('/api/'),
    errorResponseBuilder: (_req, ctx) => ({ error: { code: 'RATE_LIMITED', message: 'একটু বেশি চেষ্টা হয়ে গেছে। কিছুক্ষণ পর আবার চেষ্টা করুন।', details: { retryAfterSec: Math.ceil(ctx.ttl / 1000) } }, statusCode: 429 }),
  });

  app.setErrorHandler((err: FastifyError | AppError, req, reply) => {
    if (err instanceof AppError) return reply.status(err.status).send({ error: { code: err.code, message: err.message, ...(err.details !== undefined ? { details: err.details } : {}) } });
    const status = (err as FastifyError).statusCode ?? 500;
    if (status === 429) return reply.status(429).send(err);
    if (status < 500) {
      const code = status === 404 ? 'NOT_FOUND' : status === 401 ? 'UNAUTHORIZED' : status === 403 ? 'FORBIDDEN' : 'BAD_REQUEST';
      return reply.status(status).send({ error: { code, message: new AppError(code).message } });
    }
    req.log.error({ err }, 'unhandled error');
    return reply.status(500).send({ error: { code: 'INTERNAL', message: new AppError('INTERNAL').message } });
  });

  await app.register(apiV1, { prefix: '/api/v1' });
  app.setNotFoundHandler((req, reply) => {
    if (req.url.startsWith('/api/')) return reply.status(404).send({ error: { code: 'NOT_FOUND', message: new AppError('NOT_FOUND').message } });
    return reply.status(404).type('text/plain').send('Not found');
  });
  await serveClient(app);
  return app;
}
