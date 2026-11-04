import type { AuthTokens, Endpoint, InferBody, InferParams, InferQuery, InferResponse } from '@/api';
import { endpoints as contracts } from '@/api/contracts/endpoints.gen';
import { t } from './i18n';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details?: unknown,
  ) {
    super(message);
  }
}

let accessToken: string | null = null;
let onAuthChange: ((tokens: AuthTokens | null) => void) | null = null;

export const tokenStore = {
  get: () => accessToken,
  set: (tok: string | null) => {
    accessToken = tok;
  },
  subscribe: (fn: (tokens: AuthTokens | null) => void) => {
    onAuthChange = fn;
  },
};

function csrfToken() {
  const m = document.cookie.match(/(?:^|;\s*)ss_csrf=([^;]+)/);
  return m ? decodeURIComponent(m[1]!) : '';
}

let refreshing: Promise<AuthTokens | null> | null = null;

/** Single-flight refresh using the httpOnly cookie. Returns null when the session is gone. */
export function refreshSession(): Promise<AuthTokens | null> {
  if (!refreshing) {
    refreshing = (async () => {
      if (!csrfToken()) return null;
      const res = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'content-type': 'application/json', 'x-csrf-token': csrfToken() },
        body: '{}',
      }).catch(() => null);
      if (!res || !res.ok) {
        accessToken = null;
        onAuthChange?.(null);
        return null;
      }
      const data = (await res.json()) as AuthTokens;
      accessToken = data.accessToken;
      onAuthChange?.(data);
      return data;
    })().finally(() => {
      setTimeout(() => (refreshing = null), 0);
    });
  }
  return refreshing;
}

type Args<E extends Endpoint> = {
  params?: InferParams<E>;
  query?: InferQuery<E>;
  body?: InferBody<E>;
  signal?: AbortSignal;
};

function buildUrl(e: Endpoint, params?: Record<string, unknown>, query?: Record<string, unknown>) {
  let path = e.path.replace(/:([A-Za-z]+)/g, (_, k: string) => encodeURIComponent(String(params?.[k] ?? '')));
  if (query) {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null || v === '') continue;
      if (Array.isArray(v)) v.forEach((x) => qs.append(k, String(x)));
      else qs.set(k, String(v));
    }
    const s = qs.toString();
    if (s) path += `?${s}`;
  }
  return `/api/v1${path}`;
}

async function doFetch(e: Endpoint, args: Args<Endpoint>) {
  const headers: Record<string, string> = { accept: 'application/json' };
  if (args.body !== undefined) headers['content-type'] = 'application/json';
  if (accessToken) headers.authorization = `Bearer ${accessToken}`;
  if (e.path.startsWith('/auth/')) headers['x-csrf-token'] = csrfToken();
  return fetch(buildUrl(e, args.params as Record<string, unknown>, args.query as Record<string, unknown>), {
    method: e.method,
    headers,
    credentials: 'same-origin',
    body: args.body !== undefined ? JSON.stringify(args.body) : undefined,
    signal: args.signal,
  });
}

/** Call an endpoint from the shared contracts. Throws ApiError with the server's Bangla message. */
export async function api<E extends Endpoint>(e: E, args: Args<E> = {}): Promise<InferResponse<E>> {
  let res: Response;
  try {
    res = await doFetch(e, args as Args<Endpoint>);
    if (res.status === 401 && e.auth !== 'public' && !e.path.startsWith('/auth/')) {
      const refreshed = await refreshSession();
      if (refreshed) res = await doFetch(e, args as Args<Endpoint>);
    }
  } catch (err) {
    if ((err as Error).name === 'AbortError') throw err;
    throw new ApiError(0, 'NETWORK', t('state.errorBody'));
  }
  if (res.status === 204) return undefined as InferResponse<E>;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const env = data?.error as { code?: string; message?: string; details?: unknown } | undefined;
    throw new ApiError(res.status, env?.code ?? 'INTERNAL', env?.message ?? t('state.errorBody'), env?.details);
  }
  return data as InferResponse<E>;
}

export { contracts };
