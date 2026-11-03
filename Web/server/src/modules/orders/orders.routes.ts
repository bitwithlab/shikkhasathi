import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { and, eq } from 'drizzle-orm';
import { contracts } from '@shikkhasathi/shared';
import { route } from '../../common/route';
import { AppError } from '../../common/errors';
import { coreDb } from '../../database/client';
import { payments } from '../../database/core/schema';
import { isProd } from '../../config/env';
import { FakeGateway } from '../../services/payments';
import * as svc from './orders.service';
import * as money from './money.service';

const GATEWAYS = new Set(['sslcommerz', 'bkash', 'fake']);
const esc = (s: string) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

/** Gateways send params as query (GET redirect) and/or urlencoded form body (POST). Merge both. */
function params(req: FastifyRequest): Record<string, string> {
  const out: Record<string, string> = {};
  for (const src of [req.query, req.body]) {
    if (src && typeof src === 'object') for (const [k, v] of Object.entries(src as Record<string, unknown>)) if (typeof v === 'string') out[k] = v;
  }
  return out;
}

export async function orderRoutes(app: FastifyInstance) {
  // urlencoded bodies (gateway callbacks/IPN) — scoped to this plugin
  app.addContentTypeParser('application/x-www-form-urlencoded', { parseAs: 'string', bodyLimit: 64 * 1024 }, (_req, body, done) => {
    done(null, Object.fromEntries(new URLSearchParams(body as string)));
  });

  const c = contracts.commerce;
  route(app, c.createOrder, ({ user, body }) => svc.createOrder(user.id, body), { rateLimit: { max: 30, timeWindow: '1 hour' } });
  route(app, c.getOrder, ({ user, params: p }) => svc.getOrder(user.id, p.id));
  route(app, c.pay, ({ user, params: p, body }) => svc.pay(user.id, p.id, body.method), { rateLimit: { max: 10, timeWindow: '1 hour' } });
  route(app, c.myOrders, ({ user, query }) => svc.myOrders(user.id, query));
  route(app, c.invoice, ({ user, params: p }) => svc.invoiceUrl(user.id, p.id, user.roles.includes('admin')));
  route(app, c.refundRequest, ({ user, params: p, body }) => svc.requestRefund(user.id, p.id, body.reason), { rateLimit: { max: 5, timeWindow: '1 hour' } });
  route(app, c.validateCoupon, ({ user, body }) => svc.validateCoupon(user.id, body), { rateLimit: { max: 30, timeWindow: '10 minutes' } });
  route(app, c.bundle, ({ params: p }) => money.getBundle(p.slug), { cacheControl: 'public, max-age=120' });

  const s = contracts.sellerMoney;
  route(app, s.coupons, ({ user }) => money.listCoupons(user.id));
  route(app, s.createCoupon, ({ user, body }) => money.createCoupon(user.id, body));
  route(app, s.updateCoupon, ({ user, params: p, body }) => money.updateCoupon(user.id, p.id, body));
  route(app, s.ledger, ({ user, query }) => money.ledger(user.id, query));
  route(app, s.createBundle, ({ user, body }) => money.createBundle(user.id, body));

  const a = contracts.adminCommerce;
  route(app, a.orders, ({ query }) => svc.adminOrders(query));
  route(app, a.refunds, ({ query }) => svc.adminRefunds(query));
  route(app, a.decideRefund, ({ user, params: p, body }) => svc.decideRefund(user.id, p.id, body));

  // ---- gateway browser return (success/fail/cancel). Validation is always server-side; params are never trusted.
  const callback = async (req: FastifyRequest<{ Params: { gateway: string } }>, reply: FastifyReply) => {
    const { gateway } = req.params;
    if (!GATEWAYS.has(gateway)) throw new AppError('NOT_FOUND');
    const to = await svc.handleCallback(gateway, params(req));
    return reply.header('cache-control', 'no-store').redirect(to, 303);
  };
  const rl = { rateLimit: { max: 60, timeWindow: '1 minute' } };
  app.get('/payments/callback/:gateway', { config: rl }, callback);
  app.post('/payments/callback/:gateway', { config: rl }, callback);

  // ---- server-to-server IPN (SSLCommerz IPN / bKash webhook). Idempotent; always 200 once processed.
  app.post<{ Params: { gateway: string } }>('/webhooks/payments/:gateway', { config: { rateLimit: { max: 300, timeWindow: '1 minute' } } }, async (req, reply) => {
    if (!GATEWAYS.has(req.params.gateway)) throw new AppError('NOT_FOUND');
    await svc.handleIpn(req.params.gateway, params(req));
    return reply.send({ ok: true });
  });

  // ---- dev-only fake hosted payment page
  if (!isProd) {
    app.get<{ Params: { trxId: string } }>('/payments/fake/:trxId', async (req, reply) => {
      const [p] = await coreDb.select().from(payments).where(and(eq(payments.gateway, 'fake'), eq(payments.gatewayTrxId, req.params.trxId)));
      if (!p) throw new AppError('NOT_FOUND');
      const trx = esc(p.gatewayTrxId);
      const form = (outcome: 'success' | 'fail', label: string, color: string) =>
        `<form method="post" action="/api/v1/payments/callback/fake?status=${outcome === 'success' ? 'success' : 'fail'}"><input type="hidden" name="tran_id" value="${trx}"><input type="hidden" name="amount" value="${p.amount}"><input type="hidden" name="outcome" value="${outcome}"><input type="hidden" name="sig" value="${FakeGateway.sign(p.gatewayTrxId, outcome)}"><button style="background:${color}" type="submit">${label}</button></form>`;
      const html = `<!doctype html><html lang="bn"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Fake payment · ShikkhaSathi</title><style>body{font-family:system-ui,sans-serif;background:#F8FAFC;color:#0F172A;display:grid;place-items:center;min-height:100vh;margin:0;padding:16px}main{background:#fff;border:1px solid #E2E8F0;border-radius:16px;padding:28px;max-width:380px;width:100%}h1{font-size:18px;margin:0 0 4px}p{color:#475569;margin:0 0 20px;font-size:14px}.amt{font-size:32px;font-weight:700;margin:8px 0 20px;font-variant-numeric:tabular-nums}button{width:100%;border:0;color:#fff;border-radius:12px;padding:14px;font-size:15px;font-weight:600;cursor:pointer;margin-bottom:10px}small{color:#94A3B8}</style></head><body><main><h1>ডেভ পেমেন্ট গেটওয়ে</h1><p>এটি শুধু ডেভেলপমেন্টের জন্য — কোনো আসল টাকা কাটা হবে না। মাধ্যম: ${esc(p.method ?? '')}</p><div class="amt">৳${(p.amount / 100).toLocaleString('en-US')}</div>${form('success', 'পেমেন্ট সফল করুন', '#10B981')}${form('fail', 'ব্যর্থ করুন', '#EF4444')}<small>trx: ${trx}</small></main></body></html>`;
      return reply.header('content-type', 'text/html; charset=utf-8').header('cache-control', 'no-store').send(html);
    });
  }
}
