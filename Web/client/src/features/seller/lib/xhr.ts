/** Minimal XMLHttpRequest wrapper — fetch() cannot report upload progress. */
export interface XhrResult {
  status: number;
  header: (name: string) => string | null;
}

export function xhr(opts: { method: string; url: string; headers?: Record<string, string>; body?: Blob | null; onProgress?: (loaded: number, total: number) => void; signal?: AbortSignal }): Promise<XhrResult> {
  return new Promise((resolve, reject) => {
    const req = new XMLHttpRequest();
    req.open(opts.method, opts.url, true);
    for (const [k, v] of Object.entries(opts.headers ?? {})) req.setRequestHeader(k, v);
    if (opts.onProgress) req.upload.onprogress = (e) => opts.onProgress!(e.loaded, e.lengthComputable ? e.total : (opts.body?.size ?? 0));
    req.onload = () => resolve({ status: req.status, header: (n) => req.getResponseHeader(n) });
    req.onerror = () => reject(new Error('network'));
    req.onabort = () => reject(new DOMException('aborted', 'AbortError'));
    if (opts.signal) {
      if (opts.signal.aborted) {
        reject(new DOMException('aborted', 'AbortError'));
        return;
      }
      opts.signal.addEventListener('abort', () => req.abort(), { once: true });
    }
    req.send(opts.body ?? null);
  });
}
