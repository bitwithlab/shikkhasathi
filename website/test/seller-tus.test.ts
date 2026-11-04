import { beforeEach, describe, expect, it } from 'vitest';
import { b64, chunkRanges, fingerprint, parseOffset, tusUpload } from '@/features/seller/lib/tus';
import type { XhrResult } from '@/features/seller/lib/xhr';

type Req = { method: string; url: string; headers?: Record<string, string>; body?: Blob | null; onProgress?: (l: number, t: number) => void };

/** In-memory TUS server. */
function fakeServer(opts: { failOnce?: number; startOffset?: number } = {}) {
  let offset = opts.startOffset ?? 0;
  let fails = opts.failOnce ?? -1;
  const log: string[] = [];
  const res = (status: number, h: Record<string, string> = {}): XhrResult => ({ status, header: (n) => h[n] ?? null });
  const request = async (r: Req): Promise<XhrResult> => {
    log.push(`${r.method} ${r.headers?.['Upload-Offset'] ?? ''}`.trim());
    if (r.method === 'POST') return res(201, { Location: '/tus/abc' });
    if (r.method === 'HEAD') return res(200, { 'Upload-Offset': String(offset) });
    if (r.method === 'PATCH') {
      if (Number(r.headers!['Upload-Offset']) !== offset) return res(409);
      if (fails-- === 0) throw new Error('network');
      offset += r.body!.size;
      r.onProgress?.(r.body!.size, r.body!.size);
      return res(204, { 'Upload-Offset': String(offset) });
    }
    return res(400);
  };
  return { request, log, get offset() { return offset; } };
}

const file = (n: number, name = 'lecture.mp4') => new File([new Uint8Array(n)], name, { type: 'video/mp4', lastModified: 1 });

describe('TUS helpers', () => {
  it('chunk ranges cover the file from an offset', () => {
    expect(chunkRanges(10, 4)).toEqual([[0, 4], [4, 8], [8, 10]]);
    expect(chunkRanges(10, 4, 6)).toEqual([[6, 10]]);
    expect(chunkRanges(0, 4)).toEqual([]);
  });
  it('parses offsets and encodes UTF-8 metadata', () => {
    expect(parseOffset('1024')).toBe(1024);
    expect(parseOffset(null)).toBeNull();
    expect(parseOffset('x')).toBeNull();
    expect(atob(b64('abc'))).toBe('abc');
    expect(new TextDecoder().decode(Uint8Array.from(atob(b64('লেসন ১')), (c) => c.charCodeAt(0)))).toBe('লেসন ১');
  });
});

describe('tusUpload', () => {
  beforeEach(() => localStorage.clear());

  it('creates, PATCHes chunks with increasing Upload-Offset and reports progress', async () => {
    const s = fakeServer();
    const progress: number[] = [];
    await tusUpload({ endpoint: 'https://video.example/tusupload', file: file(10), headers: { VideoId: 'v1' }, scope: 'v1', chunkSize: 4, request: s.request, onProgress: (sent) => progress.push(sent) });
    expect(s.log).toEqual(['POST', 'PATCH 0', 'PATCH 4', 'PATCH 8']);
    expect(s.offset).toBe(10);
    expect(progress.at(-1)).toBe(10);
    expect(localStorage.length).toBe(0);
  });

  it('resumes from the server offset using the stored upload URL', async () => {
    const f = file(10);
    localStorage.setItem(fingerprint(f, 'v1'), 'https://video.example/tus/abc');
    const s = fakeServer({ startOffset: 8 });
    await tusUpload({ endpoint: 'https://video.example/tusupload', file: f, headers: {}, scope: 'v1', chunkSize: 4, request: s.request });
    expect(s.log).toEqual(['HEAD', 'PATCH 8']);
  });

  it('retries a failed chunk', async () => {
    const s = fakeServer({ failOnce: 1 });
    await tusUpload({ endpoint: 'https://video.example/tusupload', file: file(8), headers: {}, scope: 'v2', chunkSize: 4, request: s.request });
    expect(s.offset).toBe(8);
    expect(s.log.filter((l) => l === 'PATCH 4')).toHaveLength(2);
  }, 10_000);
});
