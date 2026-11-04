package com.shikkhasathi.payment.service;

import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.common.jobs.EnqueueOptions;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.payment.adapter.PaymentGateway.ValidateResult;
import com.shikkhasathi.payment.adapter.PaymentGateways;
import com.shikkhasathi.payment.repository.OrderRepository;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Gateway confirmation: browser callback, IPN webhook and the hourly reconcile all go through {@link #applyValidation},
 * which is idempotent on the unique (gateway, trx id) payments row. Client-side "success" is never trusted.
 */
@Service
public class PaymentService {
  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
  /** fulfilment job payload */
  public record FulfilJob(UUID orderId) {}

  private final OrderRepository orders;
  private final PaymentGateways gateways;
  private final FulfilmentService fulfilment;
  private final AuditService audit;
  private final JobQueue jobs;
  private final JsonMapper json;
  private final TransactionTemplate tx;

  public PaymentService(OrderRepository orders, PaymentGateways gateways, FulfilmentService fulfilment, AuditService audit, JobQueue jobs, JsonMapper json,
                        @org.springframework.beans.factory.annotation.Qualifier("coreTransactionManager") PlatformTransactionManager coreTransactionManager) {
    this.orders = orders;
    this.gateways = gateways;
    this.fulfilment = fulfilment;
    this.audit = audit;
    this.jobs = jobs;
    this.json = json;
    this.tx = new TransactionTemplate(coreTransactionManager);
  }

  private String rawJson(Object raw) {
    return raw == null ? null : json.writeValueAsString(raw);
  }

  /** @return the order id, or null for an unknown trx */
  public UUID applyValidation(String gateway, ValidateResult v) {
    var p = orders.paymentByTrx(gateway, v.trxId()).orElse(null);
    if (p == null) {
      log.warn("payment for unknown trx (gateway={}, trxId={})", gateway, v.trxId());
      return null;
    }
    if ("succeeded".equals(p.status())) return p.orderId(); // already processed
    if ("pending".equals(v.status())) return p.orderId();
    if (v.ok() && v.amount() != p.amount()) {
      log.error("payment amount mismatch (trxId={}, expected={}, got={})", v.trxId(), p.amount(), v.amount());
      orders.failPayment(p.id(), rawJson(v.raw()));
      orders.failIfPending(p.orderId());
      return p.orderId();
    }
    if (!v.ok()) {
      orders.failPaymentValidated(p.id(), rawJson(v.raw()));
      orders.failIfPending(p.orderId());
      return p.orderId();
    }
    // succeeded: flip payment + order atomically, once
    ObjectNode raw = v.raw() == null ? json.createObjectNode() : json.valueToTree(v.raw()) instanceof ObjectNode n ? n : json.createObjectNode();
    raw.put("gatewayRef", v.gatewayRef());
    String rawStr = json.writeValueAsString(raw);
    Boolean flipped = tx.execute(s -> {
      if (!orders.succeedPayment(p.id(), rawStr)) return false;
      orders.markPaid(p.orderId());
      return true;
    });
    if (Boolean.TRUE.equals(flipped)) {
      Map<String, Object> after = new LinkedHashMap<>();
      after.put("orderId", p.orderId());
      after.put("gateway", gateway);
      after.put("trxId", v.trxId());
      after.put("amount", v.amount());
      audit.log(AuditService.Entry.of(null, "payment.succeeded", "payments", p.id()).after(after));
    }
    // fulfil right away so the student lands on an unlocked course; the job (and hourly reconcile) retry on failure
    try {
      fulfilment.fulfil(p.orderId());
    } catch (RuntimeException e) {
      log.error("inline fulfilment failed — queued for retry (orderId={}): {}", p.orderId(), e.getMessage());
      jobs.enqueue(JobNames.PAYMENTS_FULFIL, new FulfilJob(p.orderId()), EnqueueOptions.jobId("fulfil-" + p.orderId()));
    }
    return p.orderId();
  }

  /** Browser return from the gateway → where to send the student. */
  public String handleCallback(String gateway, Map<String, String> params) {
    try {
      var gw = gateways.gatewayByName(gateway);
      var v = gw.validate(params);
      UUID orderId = applyValidation(gateway, v);
      if (orderId == null) return "/checkout/result?status=failed";
      String status = orders.status(orderId);
      String st = "paid".equals(status) ? "paid" : "cancel".equals(params.get("status")) ? "cancelled" : "pending".equals(v.status()) ? "pending" : "failed";
      return "/checkout/result?order=" + orderId + "&status=" + st;
    } catch (RuntimeException e) {
      log.error("payment callback failed (gateway={}): {}", gateway, e.getMessage());
      return "/checkout/result?status=failed";
    }
  }

  /** Server-to-server IPN. */
  public void handleIpn(String gateway, Map<String, String> params) {
    var gw = gateways.gatewayByName(gateway);
    applyValidation(gateway, gw.validate(params));
  }

  /** {@code payments.reconcile} (hourly): re-validate payments stuck in "initiated" for > 10 minutes; re-queue unfulfilled paid orders. */
  public int reconcile() {
    int fixed = 0;
    Instant dayAgo = Instant.now().minusMillis(24 * 3_600_000L);
    for (var p : orders.stuckPayments(200)) {
      try {
        var gw = gateways.gatewayByName(p.gateway());
        var v = gw.query(p.gatewayTrxId(), p.gatewaySessionId());
        if (!"pending".equals(v.status())) {
          applyValidation(p.gateway(), new ValidateResult(v.ok(), v.status(), p.gatewayTrxId(), v.amount(), v.gatewayRef(), v.raw()));
          fixed++;
        } else if (p.createdAt().isBefore(dayAgo)) {
          orders.failPaymentPlain(p.id());
        }
      } catch (RuntimeException e) {
        log.warn("reconcile failed (trx={}): {}", p.gatewayTrxId(), e.getMessage());
      }
    }
    // fulfil any paid orders whose fulfilment didn't complete (cross-DB outbox)
    for (var o : orders.paidUnfulfilled(200)) jobs.enqueue(JobNames.PAYMENTS_FULFIL, new FulfilJob(o.id()), EnqueueOptions.jobId("fulfil-" + o.id()));
    return fixed;
  }
}
