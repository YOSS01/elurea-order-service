package com.elurea.order_service.dto;

import com.elurea.order_service.entity.Order;
import com.elurea.order_service.enums.PaymentMethod;
import com.elurea.order_service.enums.PaymentStatus;
import com.elurea.order_service.enums.Status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order as shown in lists ("My orders", admin back-office), without items.
 */
public record OrderSummaryResponse(
        UUID id,
        String reference,
        UUID userId,
        String customerName,
        Status status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        BigDecimal totalPrice,
        int itemCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getReference(),
                order.getUserId(),
                order.getCustomerName(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getTotalPrice(),
                order.getItemCount(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
