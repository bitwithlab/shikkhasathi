package com.shikkhasathi.core.common

/**
 * Opens a URL outside the app UI: the payment gateway page (Custom Tab on Android, Safari on iOS),
 * a course PDF, or a web page the app has no screen for. Returns false when nothing could open it.
 *
 * The payment flow comes back through an App Link (`https://shikkhasathi.com/checkout/result?order=…`),
 * which lands on `CheckoutResultRoute`; the checkout screen also polls the order while it waits.
 */
interface ExternalBrowser {
    fun open(url: String): Boolean
}
