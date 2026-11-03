import type { FastifyInstance } from 'fastify';
import { extname } from 'node:path';
import { LocalStorage, storage, BUCKETS, type Bucket } from '../../services/storage';
import { AppError } from '../../common/errors';

const MIME: Record<string, string> = { '.pdf': 'application/pdf', '.jpg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp', '.pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation' };

/** Local storage endpoints (dev/test only — production uses Supabase Storage signed URLs directly). */
export async function filesRoutes(app: FastifyInstance) {
  app.addContentTypeParser(['application/pdf', 'image/jpeg', 'image/png', 'image/webp', 'application/octet-stream', 'application/vnd.openxmlformats-officedocument.presentationml.presentation'], { parseAs: 'buffer', bodyLimit: 100 * 1024 * 1024 }, (_req, body, done) => done(null, body));
  app.put('/files/upload', async (req, reply) => {
    const s = storage();
    if (!(s instanceof LocalStorage)) throw new AppError('NOT_FOUND');
    const { token } = req.query as { token?: string };
    const t = await LocalStorage.verifyUpload(token ?? '');
    if (!Buffer.isBuffer(req.body)) throw new AppError('BAD_REQUEST', 'ফাইল পাওয়া যায়নি।');
    await s.put(t.b, t.k, req.body);
    return reply.status(200).send({ ok: true });
  });
  app.get('/files/download', async (req, reply) => {
    const s = storage();
    if (!(s instanceof LocalStorage)) throw new AppError('NOT_FOUND');
    const { token } = req.query as { token?: string };
    const t = await LocalStorage.verifyDownload(token ?? '');
    const buf = await s.get(t.b, t.k).catch(() => null);
    if (!buf) throw new AppError('NOT_FOUND');
    reply.header('content-type', MIME[extname(t.k)] ?? 'application/octet-stream').header('cache-control', 'private, max-age=300');
    if (t.n) reply.header('content-disposition', `attachment; filename="${t.n.replace(/[^\w.-]/g, '_')}"`);
    return reply.send(buf);
  });
  app.get('/files/public/:bucket/*', async (req, reply) => {
    const s = storage();
    const { bucket } = req.params as { bucket: string };
    const key = (req.params as Record<string, string>)['*'] ?? '';
    if (!(s instanceof LocalStorage) || !(bucket in BUCKETS) || !BUCKETS[bucket as Bucket].public) throw new AppError('NOT_FOUND');
    const buf = await s.get(bucket as Bucket, key).catch(() => null);
    if (!buf) throw new AppError('NOT_FOUND');
    return reply.header('content-type', MIME[extname(key)] ?? 'application/octet-stream').header('cache-control', 'public, max-age=86400').send(buf);
  });
}
