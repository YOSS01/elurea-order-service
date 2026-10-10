package com.elurea.order_service.enums;

/**
 * Money side of an order, kept separate from the delivery lifecycle ({@link Status}).
 * Online orders become PAID when the payment service confirms the payment; cash-on-delivery
 * orders stay UNPAID in this system (the courier collects the money).
 */
public enum PaymentStatus {
    UNPAID,
    PAID,
    REFUNDED
}
