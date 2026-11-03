import { createHash } from 'node:crypto';
import { isProd } from '../../config/env';
import { getIntegration, registerIntegrationTest } from '../../modules/settings/settings.service';

export interface VideoProvider {
  readonly name: 'bunny' | 'external_link' | 'fake';
  /** Create a video slot; returns provider id + resumable upload instructions (TUS). */
  createUpload(title: string): Promise<{ providerVideoId: string; uploadUrl: string; headers: Record<string, string> }>;
  /** Short-lived playback URL (HLS). */
  playbackUrl(providerVideoId: string, ttlSec: number): Promise<string>;
  status(providerVideoId: string): Promise<{ status: 'processing' | 'ready' | 'failed'; durationSec: number }>;
}

/** Bunny Stream: TUS upload with signed headers; token-authenticated HLS via the pull zone. */
export class BunnyStreamProvider implements VideoProvider {
  readonly name = 'bunny' as const;
  constructor(private readonly cfg: { libraryId: string; apiKey: string; tokenKey?: string; cdnHost?: string }, private readonly fetchImpl: typeof fetch = fetch) {}
  async createUpload(title: string) {
    const res = await this.fetchImpl(`https://video.bunnycdn.com/library/${this.cfg.libraryId}/videos`, { method: 'POST', headers: { AccessKey: this.cfg.apiKey, 'content-type': 'application/json', accept: 'application/json' }, body: JSON.stringify({ title }) });
    if (!res.ok) throw new Error(`Bunny create ${res.status}`);
    const v = (await res.json()) as { guid: string };
    const expires = Math.floor(Date.now() / 1000) + 24 * 3600;
    const signature = createHash('sha256').update(`${this.cfg.libraryId}${this.cfg.apiKey}${expires}${v.guid}`).digest('hex');
    return { providerVideoId: v.guid, uploadUrl: 'https://video.bunnycdn.com/tusupload', headers: { AuthorizationSignature: signature, AuthorizationExpire: String(expires), VideoId: v.guid, LibraryId: this.cfg.libraryId } };
  }
  async playbackUrl(id: string, ttlSec: number) {
    const host = this.cfg.cdnHost ?? `vz-${this.cfg.libraryId}.b-cdn.net`;
    const path = `/${id}/playlist.m3u8`;
    if (!this.cfg.tokenKey) return `https://${host}${path}`;
    // Bunny CDN token authentication (directory token so segments are covered).
    const expires = Math.floor(Date.now() / 1000) + ttlSec;
    const tokenPath = `/${id}/`;
    const hash = createHash('sha256').update(`${this.cfg.tokenKey}${tokenPath}${expires}token_path=${tokenPath}`).digest('base64').replace(/\n/g, '').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
    return `https://${host}/bcdn_token=${hash}&expires=${expires}&token_path=${encodeURIComponent(tokenPath)}${path}`;
  }
  async status(id: string) {
    const res = await this.fetchImpl(`https://video.bunnycdn.com/library/${this.cfg.libraryId}/videos/${id}`, { headers: { AccessKey: this.cfg.apiKey, accept: 'application/json' } });
    if (!res.ok) return { status: 'failed' as const, durationSec: 0 };
    const v = (await res.json()) as { status: number; length: number };
    // 0 created,1 uploaded,2 processing,3 transcoding,4 finished,5 error
    return { status: v.status === 4 ? ('ready' as const) : v.status === 5 ? ('failed' as const) : ('processing' as const), durationSec: v.length ?? 0 };
  }
}

/** Dev/demo: no real upload; plays a public sample HLS stream. */
export class FakeVideoProvider implements VideoProvider {
  readonly name = 'fake' as const;
  static SAMPLE = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8';
  async createUpload(title: string) {
    const id = `fake-${createHash('sha1').update(title + Date.now()).digest('hex').slice(0, 12)}`;
    return { providerVideoId: id, uploadUrl: '', headers: {} };
  }
  async playbackUrl() {
    return FakeVideoProvider.SAMPLE;
  }
  async status() {
    return { status: 'ready' as const, durationSec: 600 };
  }
}

export async function videoProvider(): Promise<VideoProvider> {
  const c = await getIntegration('video');
  if (c.provider === 'bunny' && c.library_id && c.api_key) return new BunnyStreamProvider({ libraryId: c.library_id, apiKey: c.api_key, tokenKey: c.token_key, cdnHost: c.cdn_host });
  if (isProd) throw new Error('Video provider not configured');
  return new FakeVideoProvider();
}

registerIntegrationTest('video', async (c) => {
  if (!(c.library_id && c.api_key)) return { ok: false, message: 'Library id ও API key দিন' };
  const res = await fetch(`https://video.bunnycdn.com/library/${c.library_id}/videos?page=1&itemsPerPage=1`, { headers: { AccessKey: c.api_key, accept: 'application/json' } });
  return res.ok ? { ok: true, message: 'Bunny Stream সংযোগ ঠিক আছে' } : { ok: false, message: `Bunny Stream: HTTP ${res.status}` };
});
