package com.elurea.order_service.controller;

import com.elurea.order_service.dto.OrderResponse;
import com.elurea.order_service.dto.PaymentStatusUpdateRequest;
import com.elurea.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service endpoints, authenticated with the internal API key (role SERVICE).
 * The /internal prefix must never be routed by the public gateway.
 */
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    /** Payment service: an online payment succeeded (PAID) or was refunded (REFUNDED). Idempotent. */
    @PutMapping("/{id}/payment-status")
    public OrderResponse recordPayment(@PathVariable UUID id, @Valid @RequestBody PaymentStatusUpdateRequest request) {
        return orderService.recordPayment(id, request.status());
    }
}
