import type { SellerCourse } from '@/api';
import { api, contracts } from '@/lib/api';
import { xhr } from './xhr';
import { tusUpload } from './tus';

export type DocInfo = SellerCourse['documents'][number];
export type VideoInfo = NonNullable<SellerCourse['sections'][number]['chapters'][number]['lessons'][number]['video']>;
export type DocKind = DocInfo['kind'];
type Progress = (pct: number) => void;

export class UploadError extends Error {}

const pct = (loaded: number, total: number) => (total ? Math.min(100, Math.round((loaded / total) * 100)) : 0);

/** PUT the bytes to a signed storage URL with progress. */
export async function putFile(url: string, file: Blob, headers: Record<string, string>, onProgress?: Progress, signal?: AbortSignal) {
  const res = await xhr({ method: 'PUT', url, headers, body: file, signal, onProgress: (l, t) => onProgress?.(pct(l, t)) });
  if (res.status >= 300) throw new UploadError(`upload ${res.status}`);
  onProgress?.(100);
}

export async function uploadDocument(o: { courseId: string; file: File; docKind: DocKind; title: string; year?: number | null; onProgress?: Progress; signal?: AbortSignal }): Promise<DocInfo> {
  const mime = o.file.type || 'application/pdf';
  const up = await api(contracts.sellerCourses.upload, { body: { kind: 'document', courseId: o.courseId, docKind: o.docKind, title: o.title, year: o.year ?? null, mime, size: o.file.size } });
  if (!up.id || !up.uploadUrl) throw new UploadError('no upload url');
  await putFile(up.uploadUrl, o.file, up.headers, o.onProgress, o.signal);
  await api(contracts.sellerCourses.uploadComplete, { params: { id: up.id } });
  return { id: up.id, kind: o.docKind, title: o.title, year: o.year ?? null, mime, size: o.file.size, externalUrl: null, hasFile: true };
}

export async function linkDocument(o: { courseId: string; url: string; docKind: DocKind; title: string; year?: number | null }): Promise<DocInfo> {
  const up = await api(contracts.sellerCourses.upload, { body: { kind: 'document_link', courseId: o.courseId, docKind: o.docKind, title: o.title, year: o.year ?? null, url: o.url } });
  return { id: up.id!, kind: o.docKind, title: o.title, year: o.year ?? null, mime: null, size: null, externalUrl: o.url, hasFile: false };
}

/** Cover image → public URL. */
export async function uploadCover(file: File, onProgress?: Progress): Promise<string> {
  const up = await api(contracts.sellerCourses.upload, { body: { kind: 'cover', mime: file.type as 'image/jpeg', size: file.size } });
  if (!up.uploadUrl || !up.publicUrl) throw new UploadError('no upload url');
  await putFile(up.uploadUrl, file, up.headers, onProgress);
  return up.publicUrl;
}

/** ID card (verification evidence) → storage key. */
export async function uploadIdCard(file: File, onProgress?: Progress): Promise<string> {
  const up = await api(contracts.sellerCourses.upload, { body: { kind: 'id_card', mime: file.type as 'image/jpeg', size: file.size } });
  if (!up.uploadUrl || !up.key) throw new UploadError('no upload url');
  await putFile(up.uploadUrl, file, up.headers, onProgress);
  return up.key;
}

/**
 * Video: the provider decides the transport. Dev (fake provider) has no upload URL and is ready at once;
 * Bunny returns a TUS endpoint + signed headers.
 */
export async function uploadVideo(o: { file: File; title: string; onProgress?: Progress; signal?: AbortSignal }): Promise<VideoInfo> {
  const up = await api(contracts.sellerCourses.upload, { body: { kind: 'video', title: o.title, size: o.file.size } });
  if (!up.id) throw new UploadError('no video id');
  if (up.uploadUrl && up.method === 'TUS') {
    await tusUpload({ endpoint: up.uploadUrl, file: o.file, headers: up.headers, scope: up.headers.VideoId ?? up.id, metadata: { title: o.title }, signal: o.signal, onProgress: (s, t) => o.onProgress?.(pct(s, t)) });
    await api(contracts.sellerCourses.uploadComplete, { params: { id: up.id } });
    o.onProgress?.(100);
    return { id: up.id, provider: 'bunny', status: 'processing', durationSec: 0, title: o.title, externalUrl: null };
  }
  if (up.uploadUrl) await putFile(up.uploadUrl, o.file, up.headers, o.onProgress, o.signal);
  o.onProgress?.(100);
  return { id: up.id, provider: 'fake', status: 'ready', durationSec: 0, title: o.title, externalUrl: null };
}

export async function linkVideo(o: { title: string; url: string; durationSec: number }): Promise<VideoInfo> {
  const up = await api(contracts.sellerCourses.upload, { body: { kind: 'video_link', title: o.title, url: o.url, durationSec: o.durationSec } });
  return { id: up.id!, provider: 'external_link', status: 'ready', durationSec: o.durationSec, title: o.title, externalUrl: o.url };
}

export const ACCEPT = {
  video: 'video/mp4,video/webm,video/quicktime,video/x-matroska',
  pdf: 'application/pdf',
  doc: 'application/pdf,application/vnd.openxmlformats-officedocument.presentationml.presentation,image/jpeg,image/png,image/webp',
  image: 'image/jpeg,image/png,image/webp',
  idCard: 'image/jpeg,image/png,image/webp,application/pdf',
};

export const MAX = { cover: 5 * 1024 ** 2, idCard: 10 * 1024 ** 2, doc: 100 * 1024 ** 2, video: 8 * 1024 ** 3 };

export function formatBytes(n: number) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(0)} KB`;
  if (n < 1024 ** 3) return `${(n / 1024 ** 2).toFixed(1)} MB`;
  return `${(n / 1024 ** 3).toFixed(2)} GB`;
}

/** File name without extension, as a default title. */
export const baseName = (name: string) => name.replace(/\.[^.]+$/, '').replace(/[_]+/g, ' ').trim().slice(0, 200);
