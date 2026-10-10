package com.elurea.order_service.dto;

import com.elurea.order_service.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Sent by the payment service. paymentId is kept for tracing in logs.
 */
public record PaymentStatusUpdateRequest(
        @NotNull
        PaymentStatus status,

        UUID paymentId
) {
}
