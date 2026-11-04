package com.shikkhasathi.payment.adapter;

/** Callback/IPN URLs registered with gateways (same paths as the previous API). */
public record PaymentUrls(String appUrl) {
  /** {@code <APP_URL>/api/v1/payments/callback/<gateway>?status=<success|fail|cancel>} */
  public String callbackUrl(String gateway, String kind) {
    return appUrl + "/api/v1/payments/callback/" + gateway + "?status=" + kind;
  }

  /** {@code <APP_URL>/api/v1/webhooks/payments/<gateway>} */
  public String ipnUrl(String gateway) {
    return appUrl + "/api/v1/webhooks/payments/" + gateway;
  }
}
