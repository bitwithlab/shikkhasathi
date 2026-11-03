import type { FastifyInstance } from 'fastify';
import fastifyStatic from '@fastify/static';
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { env } from './config/env';
import { dynamicHead, renderHead, sitemapXml } from './seo';

/**
 * Serves the built client (Web/client/dist): hashed assets cached immutably, prerendered public HTML
 * (dist/<route>/index.html) when present, otherwise the SPA shell for non-API browser routes.
 */
export async function serveClient(app: FastifyInstance) {
  const dist = resolve(process.cwd(), env.CLIENT_DIST_DIR);
  if (!existsSync(join(dist, 'index.html'))) return;
  const shell = readFileSync(join(dist, 'index.html'), 'utf8');

  await app.register(fastifyStatic, {
    root: dist,
    wildcard: false,
    index: false,
    setHeaders(res, path) {
      if (path.includes('/assets/')) res.setHeader('cache-control', 'public, max-age=31536000, immutable');
      else res.setHeader('cache-control', 'public, max-age=300');
    },
  });

  // live sitemap (courses, universities, subjects, teachers) — overrides the build-time one
  app.get('/sitemap.xml', async (_req, reply) => reply.header('cache-control', 'public, max-age=3600').type('application/xml; charset=utf-8').send(await sitemapXml()));

  app.get('/*', async (req, reply) => {
    const url = req.url.split('?')[0] ?? '/';
    if (url.startsWith('/api/')) return reply.callNotFound();
    const file = join(dist, url);
    if (file.startsWith(dist) && existsSync(file) && !file.endsWith('/') && /\.[a-z0-9]+$/i.test(url)) return reply.sendFile(url.slice(1));
    const pre = join(dist, url, 'index.html');
    reply.header('cache-control', 'no-cache').type('text/html; charset=utf-8');
    if (url !== '/' && pre.startsWith(dist) && existsSync(pre)) return reply.send(readFileSync(pre, 'utf8'));
    const head = await dynamicHead(url);
    if (head) return reply.header('cache-control', 'public, max-age=60').send(renderHead(shell, head));
    const root = join(dist, 'index.prerendered.html');
    if (url === '/' && existsSync(root)) return reply.send(readFileSync(root, 'utf8'));
    return reply.send(shell);
  });
}
