import { xhr, type XhrResult } from './xhr';

/**
 * Small dependency-free TUS 1.0 client (creation + core PATCH), used for Bunny Stream uploads.
 * Resumable: the upload URL is kept in localStorage per file fingerprint, and on restart we ask
 * the server for the current offset (HEAD) and continue from there.
 */
export const TUS_CHUNK = 8 * 1024 * 1024;
const STORE = 'ss-tus:';

/** [start, end) byte ranges still to send. */
export function chunkRanges(size: number, chunk: number, from = 0): [number, number][] {
  const out: [number, number][] = [];
  for (let o = Math.max(0, from); o < size; o += chunk) out.push([o, Math.min(size, o + chunk)]);
  return out;
}

/** Base64 for Upload-Metadata (UTF-8 safe, so Bangla titles survive). */
export function b64(s: string) {
  const bytes = new TextEncoder().encode(s);
  let bin = '';
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin);
}

export const fingerprint = (f: { name: string; size: number; lastModified: number }, scope: string) => `${STORE}${scope}:${f.name}:${f.size}:${f.lastModified}`;

function store() {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

/** Parse Upload-Offset; null when missing or invalid. */
export function parseOffset(v: string | null): number | null {
  if (v == null || !/^\d+$/.test(v.trim())) return null;
  return Number(v.trim());
}

export interface TusOptions {
  endpoint: string;
  file: File;
  headers: Record<string, string>;
  metadata?: Record<string, string>;
  /** key that identifies the target (e.g. provider video id) so resumes never cross videos */
  scope: string;
  chunkSize?: number;
  onProgress?: (sent: number, total: number) => void;
  signal?: AbortSignal;
  /** test seam */
  request?: typeof xhr;
}

export async function tusUpload(o: TusOptions): Promise<void> {
  const req = o.request ?? xhr;
  const base = { 'Tus-Resumable': '1.0.0', ...o.headers };
  const fp = fingerprint(o.file, o.scope);
  const ls = store();
  let url = ls?.getItem(fp) ?? null;
  let offset = 0;

  if (url) {
    const head = await req({ method: 'HEAD', url, headers: base, signal: o.signal }).catch(() => null);
    const off = head && head.status < 300 ? parseOffset(head.header('Upload-Offset')) : null;
    if (off == null) url = null;
    else offset = off;
  }
  if (!url) {
    const meta = Object.entries({ filename: o.file.name, filetype: o.file.type || 'application/octet-stream', ...o.metadata })
      .map(([k, v]) => `${k} ${b64(v)}`)
      .join(',');
    const res = await req({ method: 'POST', url: o.endpoint, headers: { ...base, 'Upload-Length': String(o.file.size), 'Upload-Metadata': meta }, signal: o.signal });
    const loc = res.header('Location');
    if (res.status >= 300 || !loc) throw new Error(`tus create ${res.status}`);
    url = new URL(loc, o.endpoint).toString();
    ls?.setItem(fp, url);
    offset = 0;
  }

  const size = o.chunkSize ?? TUS_CHUNK;
  o.onProgress?.(offset, o.file.size);
  while (offset < o.file.size) {
    const end = Math.min(o.file.size, offset + size);
    let res: XhrResult | null = null;
    for (let attempt = 0; attempt < 3 && !res; attempt++) {
      try {
        const start = offset;
        const r = await req({
          method: 'PATCH',
          url,
          headers: { ...base, 'Upload-Offset': String(start), 'Content-Type': 'application/offset+octet-stream' },
          body: o.file.slice(start, end),
          signal: o.signal,
          onProgress: (loaded) => o.onProgress?.(start + loaded, o.file.size),
        });
        if (r.status === 409) {
          // offset mismatch: ask the server where we are
          const head = await req({ method: 'HEAD', url, headers: base, signal: o.signal });
          const off = parseOffset(head.header('Upload-Offset'));
          if (off == null) throw new Error('tus offset');
          offset = off;
          break;
        }
        if (r.status >= 300) throw new Error(`tus patch ${r.status}`);
        res = r;
      } catch (e) {
        if ((e as Error).name === 'AbortError' || attempt === 2) throw e;
        await new Promise((r) => setTimeout(r, 800 * (attempt + 1)));
      }
    }
    if (!res) continue;
    offset = parseOffset(res.header('Upload-Offset')) ?? end;
    o.onProgress?.(offset, o.file.size);
  }
  ls?.removeItem(fp);
}
