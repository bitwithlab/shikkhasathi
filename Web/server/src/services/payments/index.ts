import { createHash } from 'node:crypto';
import { env, isProd } from '../../config/env';
import { getIntegration, registerIntegrationTest } from '../../modules/settings/settings.service';

export interface InitInput {
  orderId: string;
  /** our transaction id (unique per attempt) */
  trxId: string;
  amount: number; // paisa
  method: 'bkash' | 'nagad' | 'rocket' | 'card';
  customer: { name: string; email: string | null; phone: string | null };
  productName: string;
}
export interface InitResult {
  redirectUrl: string;
  sessionId: string | null;
}
export interface ValidateResult {
  ok: boolean;
  status: 'succeeded' | 'failed' | 'pending';
  trxId: string;
  amount: number; // paisa as reported by the gateway
  gatewayRef: string | null; // gateway's own transaction id (for refunds)
  raw: unknown;
}

/** One interface for every gateway. Client-side "success" is never trusted: we always call validate()/query(). */
export interface PaymentGateway {
  readonly name: 'sslcommerz' | 'bkash' | 'fake';
  init(i: InitInput): Promise<InitResult>;
  /** Validate a callback / IPN payload against the gateway's server API. */
  validate(params: Record<string, string>): Promise<ValidateResult>;
  /** Re-check by our trx id (reconciliation). */
  query(trxId: string, sessionId: string | null): Promise<ValidateResult>;
  refund(p: { trxId: string; gatewayRef: string | null; amount: number; reason: string }): Promise<{ supported: boolean; refundId: string | null }>;
}

export const callbackUrl = (gateway: string, kind: 'success' | 'fail' | 'cancel') => `${env.APP_URL}/api/v1/payments/callback/${gateway}?status=${kind}`;
export const ipnUrl = (gateway: string) => `${env.APP_URL}/api/v1/webhooks/payments/${gateway}`;
const taka = (paisa: number) => (paisa / 100).toFixed(2);
const toPaisa = (v: unknown) => Math.round(Number(v ?? 0) * 100);

// ---------------------------------------------------------------- SSLCommerz (bKash, Nagad, Rocket, cards via one hosted page)

export class SslcommerzGateway implements PaymentGateway {
  readonly name = 'sslcommerz' as const;
  private readonly base: string;
  constructor(private readonly cfg: { storeId: string; storePassword: string; sandbox: boolean }, private readonly fetchImpl: typeof fetch = fetch) {
    this.base = cfg.sandbox ? 'https://sandbox.sslcommerz.com' : 'https://securepay.sslcommerz.com';
  }
  async init(i: InitInput) {
    const body = new URLSearchParams({
      store_id: this.cfg.storeId,
      store_passwd: this.cfg.storePassword,
      total_amount: taka(i.amount),
      currency: 'BDT',
      tran_id: i.trxId,
      success_url: callbackUrl('sslcommerz', 'success'),
      fail_url: callbackUrl('sslcommerz', 'fail'),
      cancel_url: callbackUrl('sslcommerz', 'cancel'),
      ipn_url: ipnUrl('sslcommerz'),
      cus_name: i.customer.name,
      cus_email: i.customer.email ?? 'no-reply@shikkhasathi.com',
      cus_phone: i.customer.phone ?? '01700000000',
      cus_add1: 'Bangladesh',
      cus_city: 'Dhaka',
      cus_country: 'Bangladesh',
      shipping_method: 'NO',
      product_name: i.productName.slice(0, 250),
      product_category: 'Education',
      product_profile: 'non-physical-goods',
      value_a: i.orderId,
      // pre-select the wallet the student chose
      multi_card_name: i.method === 'card' ? 'visacard,mastercard,amexcard' : i.method === 'rocket' ? 'dbbl_mobile' : i.method,
    });
    const res = await this.fetchImpl(`${this.base}/gwprocess/v4/api.php`, { method: 'POST', body, signal: AbortSignal.timeout(20_000) });
    const data = (await res.json()) as { status: string; GatewayPageURL?: string; sessionkey?: string; failedreason?: string };
    if (data.status !== 'SUCCESS' || !data.GatewayPageURL) throw new Error(`SSLCommerz init: ${data.failedreason ?? data.status}`);
    return { redirectUrl: data.GatewayPageURL, sessionId: data.sessionkey ?? null };
  }
  private map(d: Record<string, unknown>): ValidateResult {
    const st = String(d.status ?? '').toUpperCase();
    const ok = st === 'VALID' || st === 'VALIDATED';
    return { ok, status: ok ? 'succeeded' : st === 'PENDING' ? 'pending' : 'failed', trxId: String(d.tran_id ?? ''), amount: toPaisa(d.amount), gatewayRef: d.bank_tran_id ? String(d.bank_tran_id) : null, raw: d };
  }
  async validate(params: Record<string, string>) {
    if (!params.val_id) return { ok: false, status: 'failed' as const, trxId: params.tran_id ?? '', amount: 0, gatewayRef: null, raw: params };
    const u = new URL(`${this.base}/validator/api/validationserverAPI.php`);
    u.search = new URLSearchParams({ val_id: params.val_id, store_id: this.cfg.storeId, store_passwd: this.cfg.storePassword, format: 'json' }).toString();
    const res = await this.fetchImpl(u, { signal: AbortSignal.timeout(20_000) });
    return this.map((await res.json()) as Record<string, unknown>);
  }
  async query(trxId: string) {
    const u = new URL(`${this.base}/validator/api/merchantTransIDvalidationAPI.php`);
    u.search = new URLSearchParams({ tran_id: trxId, store_id: this.cfg.storeId, store_passwd: this.cfg.storePassword, format: 'json' }).toString();
    const res = await this.fetchImpl(u, { signal: AbortSignal.timeout(20_000) });
    const data = (await res.json()) as { element?: Record<string, unknown>[] };
    const el = data.element?.find((e) => ['VALID', 'VALIDATED'].includes(String(e.status))) ?? data.element?.[0];
    return el ? this.map(el) : { ok: false, status: 'pending' as const, trxId, amount: 0, gatewayRef: null, raw: data };
  }
  async refund(p: { gatewayRef: string | null; amount: number; reason: string }) {
    if (!p.gatewayRef) return { supported: false, refundId: null };
    const u = new URL(`${this.base}/validator/api/merchantTransIDvalidationAPI.php`);
    u.search = new URLSearchParams({ bank_tran_id: p.gatewayRef, refund_amount: taka(p.amount), refund_remarks: p.reason.slice(0, 200), store_id: this.cfg.storeId, store_passwd: this.cfg.storePassword, format: 'json' }).toString();
    const res = await this.fetchImpl(u, { signal: AbortSignal.timeout(20_000) });
    const d = (await res.json()) as { APIConnect?: string; status?: string; refund_ref_id?: string };
    if (d.APIConnect !== 'DONE' || !['success', 'processing'].includes(String(d.status))) throw new Error(`SSLCommerz refund: ${d.status ?? d.APIConnect}`);
    return { supported: true, refundId: d.refund_ref_id ?? null };
  }
}

// ---------------------------------------------------------------- bKash tokenized checkout

export class BkashGateway implements PaymentGateway {
  readonly name = 'bkash' as const;
  private readonly base: string;
  private token: { id: string; exp: number } | null = null;
  constructor(private readonly cfg: { appKey: string; appSecret: string; username: string; password: string; sandbox: boolean }, private readonly fetchImpl: typeof fetch = fetch) {
    this.base = cfg.sandbox ? 'https://tokenized.sandbox.bka.sh/v1.2.0-beta/tokenized/checkout' : 'https://tokenized.pay.bka.sh/v1.2.0-beta/tokenized/checkout';
  }
  private async idToken() {
    if (this.token && this.token.exp > Date.now() + 60_000) return this.token.id;
    const res = await this.fetchImpl(`${this.base}/token/grant`, { method: 'POST', headers: { 'content-type': 'application/json', accept: 'application/json', username: this.cfg.username, password: this.cfg.password }, body: JSON.stringify({ app_key: this.cfg.appKey, app_secret: this.cfg.appSecret }) });
    const d = (await res.json()) as { id_token?: string; expires_in?: number; statusMessage?: string };
    if (!d.id_token) throw new Error(`bKash token: ${d.statusMessage ?? res.status}`);
    this.token = { id: d.id_token, exp: Date.now() + (d.expires_in ?? 3600) * 1000 };
    return d.id_token;
  }
  private async call(path: string, body: Record<string, unknown>) {
    const res = await this.fetchImpl(`${this.base}${path}`, { method: 'POST', headers: { 'content-type': 'application/json', accept: 'application/json', authorization: await this.idToken(), 'x-app-key': this.cfg.appKey }, body: JSON.stringify(body), signal: AbortSignal.timeout(20_000) });
    return (await res.json()) as Record<string, string>;
  }
  async init(i: InitInput) {
    const d = await this.call('/create', { mode: '0011', payerReference: i.customer.phone ?? i.orderId, callbackURL: callbackUrl('bkash', 'success'), amount: taka(i.amount), currency: 'BDT', intent: 'sale', merchantInvoiceNumber: i.trxId });
    if (!d.bkashURL) throw new Error(`bKash create: ${d.statusMessage ?? 'failed'}`);
    return { redirectUrl: d.bkashURL, sessionId: d.paymentID ?? null };
  }
  private map(d: Record<string, string>, trxId: string): ValidateResult {
    const ok = d.transactionStatus === 'Completed';
    return { ok, status: ok ? 'succeeded' : d.transactionStatus === 'Initiated' ? 'pending' : 'failed', trxId: d.merchantInvoiceNumber ?? trxId, amount: toPaisa(d.amount), gatewayRef: d.trxID ?? null, raw: d };
  }
  async validate(params: Record<string, string>) {
    if (params.status !== 'success' || !params.paymentID) return { ok: false, status: 'failed' as const, trxId: '', amount: 0, gatewayRef: null, raw: params };
    const d = await this.call('/execute', { paymentID: params.paymentID });
    if (d.transactionStatus) return this.map(d, '');
    const q = await this.call('/payment/status', { paymentID: params.paymentID });
    return this.map(q, '');
  }
  async query(trxId: string, sessionId: string | null) {
    if (!sessionId) return { ok: false, status: 'pending' as const, trxId, amount: 0, gatewayRef: null, raw: null };
    return this.map(await this.call('/payment/status', { paymentID: sessionId }), trxId);
  }
  async refund(p: { trxId: string; gatewayRef: string | null; amount: number; reason: string }) {
    if (!p.gatewayRef) return { supported: false, refundId: null };
    const d = await this.call('/payment/refund', { paymentID: p.trxId, trxID: p.gatewayRef, amount: taka(p.amount), reason: p.reason.slice(0, 200), sku: 'course' });
    if (!d.refundTrxID) throw new Error(`bKash refund: ${d.statusMessage ?? 'failed'}`);
    return { supported: true, refundId: d.refundTrxID };
  }
}

// ---------------------------------------------------------------- Fake (dev/tests)

/** Dev/test gateway: a local hosted page (GET /api/v1/payments/fake/:trxId) where you choose success or failure. */
export class FakeGateway implements PaymentGateway {
  readonly name = 'fake' as const;
  static sign(trxId: string, outcome: string) {
    return createHash('sha256').update(`${env.JWT_ACCESS_SECRET}:${trxId}:${outcome}`).digest('hex').slice(0, 24);
  }
  /** results by trx id (set when the fake page is submitted) */
  static results = new Map<string, { outcome: 'success' | 'fail'; amount: number }>();
  async init(i: InitInput) {
    return { redirectUrl: `${env.APP_URL}/api/v1/payments/fake/${i.trxId}?amount=${i.amount}&method=${i.method}`, sessionId: `fake-session-${i.trxId}` };
  }
  async validate(params: Record<string, string>) {
    const trxId = params.tran_id ?? '';
    const outcome = params.outcome === 'success' ? 'success' : 'fail';
    const sigOk = params.sig === FakeGateway.sign(trxId, outcome);
    const ok = sigOk && outcome === 'success';
    if (sigOk) FakeGateway.results.set(trxId, { outcome, amount: Number(params.amount ?? 0) });
    return { ok, status: ok ? ('succeeded' as const) : ('failed' as const), trxId, amount: Number(params.amount ?? 0), gatewayRef: ok ? `FAKE-${trxId.slice(-8)}` : null, raw: { ...params, sig: undefined } };
  }
  async query(trxId: string) {
    const r = FakeGateway.results.get(trxId);
    if (!r) return { ok: false, status: 'pending' as const, trxId, amount: 0, gatewayRef: null, raw: null };
    return { ok: r.outcome === 'success', status: r.outcome === 'success' ? ('succeeded' as const) : ('failed' as const), trxId, amount: r.amount, gatewayRef: `FAKE-${trxId.slice(-8)}`, raw: r };
  }
  async refund(p: { trxId: string }) {
    return { supported: true, refundId: `FAKE-REFUND-${p.trxId.slice(-6)}` };
  }
}

export async function gatewayByName(name: string): Promise<PaymentGateway> {
  const c = await getIntegration('payment');
  if (name === 'sslcommerz' && c.sslcommerz_store_id && c.sslcommerz_store_password) return new SslcommerzGateway({ storeId: c.sslcommerz_store_id, storePassword: c.sslcommerz_store_password, sandbox: c.sslcommerz_sandbox !== 'false' });
  if (name === 'bkash' && c.bkash_app_key && c.bkash_app_secret && c.bkash_username && c.bkash_password) return new BkashGateway({ appKey: c.bkash_app_key, appSecret: c.bkash_app_secret, username: c.bkash_username, password: c.bkash_password, sandbox: c.bkash_sandbox !== 'false' });
  if (name === 'fake' && !isProd) return new FakeGateway();
  throw new Error(`payment gateway "${name}" not configured`);
}

/** The active gateway for a method (bKash tokenized is used for bKash when it's the selected gateway). */
export async function activeGateway(): Promise<PaymentGateway> {
  const c = await getIntegration('payment');
  return gatewayByName(c.gateway ?? 'fake').catch(async (e) => {
    if (!isProd) return new FakeGateway();
    throw e;
  });
}

registerIntegrationTest('payment', async (c) => {
  if (c.gateway === 'sslcommerz') {
    if (!c.sslcommerz_store_id || !c.sslcommerz_store_password) return { ok: false, message: 'SSLCommerz store id ও password দিন' };
    const g = new SslcommerzGateway({ storeId: c.sslcommerz_store_id, storePassword: c.sslcommerz_store_password, sandbox: c.sslcommerz_sandbox !== 'false' });
    const r = await g.query('connectivity-test');
    return { ok: true, message: `SSLCommerz API পৌঁছানো গেছে (${c.sslcommerz_sandbox !== 'false' ? 'sandbox' : 'live'}) — ${(r.raw as { APIConnect?: string })?.APIConnect ?? 'OK'}` };
  }
  if (c.gateway === 'bkash') {
    const g = new BkashGateway({ appKey: c.bkash_app_key ?? '', appSecret: c.bkash_app_secret ?? '', username: c.bkash_username ?? '', password: c.bkash_password ?? '', sandbox: c.bkash_sandbox !== 'false' });
    await (g as unknown as { idToken: () => Promise<string> }).idToken();
    return { ok: true, message: 'bKash টোকেন পাওয়া গেছে — সংযোগ ঠিক আছে' };
  }
  return { ok: true, message: 'Fake গেটওয়ে (ডেভ মোড) — আসল টাকা কাটা হবে না' };
});
