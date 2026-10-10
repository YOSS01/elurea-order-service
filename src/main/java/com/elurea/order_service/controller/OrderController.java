package com.elurea.order_service.controller;

import com.elurea.order_service.common.PageResponse;
import com.elurea.order_service.common.SortWhitelist;
import com.elurea.order_service.dto.CreateOrderRequest;
import com.elurea.order_service.dto.OrderResponse;
import com.elurea.order_service.dto.OrderSummaryResponse;
import com.elurea.order_service.dto.UpdateStatusRequest;
import com.elurea.order_service.enums.Status;
import com.elurea.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * All endpoints require a token. Orders are never edited or deleted after checkout:
 * they only move through their lifecycle (see {@link Status}).
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "totalPrice", "status");

    private final OrderService orderService;

    /** Checkout: reserves stock, prices the order server-side and saves it as PENDING. */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(request);
        return ResponseEntity.created(Locations.of(created.id())).body(created);
    }

    /** Customers get their own orders; administrators get all, filterable by userId and status. */
    @GetMapping
    public PageResponse<OrderSummaryResponse> getAll(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Status status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.getAll(userId, status, SortWhitelist.validate(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable UUID id) {
        return orderService.getById(id);
    }

    /** Cancels the order and returns its stock. */
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return orderService.cancel(id);
    }

    /** Back-office: moves the order to its next status (409 if the transition is not allowed). */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}
