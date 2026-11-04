package com.shikkhasathi.payment.adapter;

import java.util.Map;

/**
 * One interface for every gateway (SSLCommerz hosted page, bKash tokenized, fake). Client-side "success" is never
 * trusted: callers always confirm with {@link #validate} / {@link #query}. Amounts are integer paisa.
 */
public interface PaymentGateway {
  /** method: bkash | nagad | rocket | card */
  record InitInput(String orderId, String trxId, long amount, String method, Customer customer, String productName) {}

  /** email/phone nullable */
  record Customer(String name, String email, String phone) {}

  /** Where to send the student, plus the gateway's session/payment id (nullable). */
  record InitResult(String redirectUrl, String sessionId) {}

  /**
   * @param status     succeeded | failed | pending
   * @param amount     paisa as reported by the gateway
   * @param gatewayRef the gateway's own transaction id (needed for refunds), nullable
   * @param raw        gateway payload for the payments row (never contains our signature)
   */
  record ValidateResult(boolean ok, String status, String trxId, long amount, String gatewayRef, Object raw) {}

  record RefundRequest(String trxId, String gatewayRef, long amount, String reason) {}

  /** {@code supported=false} = the gateway can't refund automatically (manual refund). */
  record RefundResult(boolean supported, String refundId) {}

  /** sslcommerz | bkash | fake */
  String name();

  InitResult init(InitInput i);

  /** Validate a callback / IPN payload against the gateway's server API. */
  ValidateResult validate(Map<String, String> params);

  /** Re-check by our trx id (reconciliation); {@code sessionId} nullable. */
  ValidateResult query(String trxId, String sessionId);

  RefundResult refund(RefundRequest r);

  /** paisa → "299.00" */
  static String taka(long paisa) {
    return String.format(java.util.Locale.ROOT, "%.2f", paisa / 100.0);
  }

  /** gateway amount ("299.00") → paisa */
  static long toPaisa(Object v) {
    if (v == null) return 0;
    try {
      return Math.round(Double.parseDouble(String.valueOf(v).trim()) * 100);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
