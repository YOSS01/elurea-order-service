package com.elurea.order_service.service;

import com.elurea.order_service.client.PaymentServiceClient;
import com.elurea.order_service.client.ProductServiceClient;
import com.elurea.order_service.client.ProductServiceClient.ReservedItem;
import com.elurea.order_service.client.ProductServiceClient.StockLine;
import com.elurea.order_service.client.UserServiceClient;
import com.elurea.order_service.client.UserServiceClient.UserAddress;
import com.elurea.order_service.client.UserServiceClient.UserProfile;
import com.elurea.order_service.common.PageResponse;
import com.elurea.order_service.common.Strings;
import com.elurea.order_service.dto.CreateOrderRequest;
import com.elurea.order_service.dto.OrderResponse;
import com.elurea.order_service.dto.OrderSummaryResponse;
import com.elurea.order_service.entity.Order;
import com.elurea.order_service.entity.OrderItem;
import com.elurea.order_service.entity.ShippingAddress;
import com.elurea.order_service.enums.PaymentMethod;
import com.elurea.order_service.enums.PaymentStatus;
import com.elurea.order_service.enums.Status;
import com.elurea.order_service.exception.BadRequestException;
import com.elurea.order_service.exception.ConflictException;
import com.elurea.order_service.exception.ResourceNotFoundException;
import com.elurea.order_service.exception.ServiceUnavailableException;
import com.elurea.order_service.repository.OrderRepository;
import com.elurea.order_service.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checkout and order lifecycle.
 * <p>
 * Placing an order touches two other services, so it cannot be one database transaction. The sequence is:
 * read profile and address, reserve stock (the product service also gives the real prices), then save.
 * If saving fails, the reservation is released again (compensation).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userClient;
    private final ProductServiceClient productClient;
    private final PaymentServiceClient paymentClient;
    private final OrderReferenceGenerator referenceGenerator;

    /** Deliberately not @Transactional: no database connection is held during the remote calls. */
    public OrderResponse create(CreateOrderRequest request) {
        UUID userId = CurrentUser.requireId();
        String token = CurrentUser.requireToken();

        UserProfile profile = userClient.getProfile(token);
        UserAddress address = userClient.getAddress(request.shippingAddressId(), token);
        String phoneNumber = Strings.trimToNull(request.phoneNumber()) != null
                ? Strings.trimToNull(request.phoneNumber())
                : Strings.trimToNull(profile.phoneNumber());
        if (phoneNumber == null) {
            throw new BadRequestException("A phone number is required for delivery: add one to the order or your profile");
        }

        List<StockLine> lines = mergeLines(request.items());
        List<ReservedItem> reserved = productClient.reserve(lines);

        try {
            Order order = buildOrder(userId, profile, address, phoneNumber, request, lines, reserved);
            // saveAndFlush runs in its own transaction and surfaces constraint errors here, inside the try.
            return OrderResponse.from(orderRepository.saveAndFlush(order));
        } catch (RuntimeException ex) {
            releaseAfterFailedCheckout(lines, ex);
            throw ex;
        }
    }

    /** Administrators see every order (optionally filtered); customers only ever see their own. */
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getAll(UUID userId, Status status, Pageable pageable) {
        UUID ownerFilter = userId;
        if (!CurrentUser.isAdmin()) {
            ownerFilter = userId != null ? userId : CurrentUser.requireId();
            CurrentUser.requireSelfOrAdmin(ownerFilter);
        }
        return PageResponse.of(orderRepository.search(ownerFilter, status, pageable), OrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        CurrentUser.requireSelfOrAdmin(order.getUserId());
        return OrderResponse.from(order);
    }

    /** Customers may cancel their own order until it is being prepared; administrators until it ships. */
    @Transactional
    public OrderResponse cancel(UUID id) {
        Order order = lockOrder(id);
        CurrentUser.requireSelfOrAdmin(order.getUserId());
        if (order.getStatus() == Status.CANCELLED) {
            throw new ConflictException("This order is already cancelled");
        }
        if (!CurrentUser.isAdmin() && !order.getStatus().isCancellableByCustomer()) {
            throw new ConflictException("This order is already %s and can no longer be cancelled"
                    .formatted(order.getStatus()));
        }
        return changeStatus(order, Status.CANCELLED);
    }

    /** Admin only (enforced in the controller). */
    @Transactional
    public OrderResponse updateStatus(UUID id, Status next) {
        return changeStatus(lockOrder(id), next);
    }

    /**
     * Called by the payment service (internal endpoint) when an online payment succeeds or is refunded.
     * Idempotent, because payment providers may deliver the same notification several times.
     * A payment for an order that was cancelled meanwhile is refused (409); the payment service then refunds it.
     */
    @Transactional
    public OrderResponse recordPayment(UUID id, PaymentStatus reported) {
        Order order = lockOrder(id);
        PaymentStatus current = order.getPaymentStatus();
        switch (reported) {
            case PAID -> {
                if (current == PaymentStatus.PAID) {
                    return OrderResponse.from(order);
                }
                if (order.getStatus() == Status.CANCELLED || current == PaymentStatus.REFUNDED) {
                    throw new ConflictException("Order %s is %s and cannot be paid"
                            .formatted(order.getReference(), order.getStatus()));
                }
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setPaidAt(Instant.now());
                if (order.getStatus() == Status.PENDING) {
                    order.setStatus(Status.CONFIRMED); // an online order is confirmed by its payment
                }
            }
            case REFUNDED -> {
                if (current == PaymentStatus.REFUNDED) {
                    return OrderResponse.from(order);
                }
                if (current != PaymentStatus.PAID) {
                    throw new ConflictException("Order %s was not paid".formatted(order.getReference()));
                }
                order.setPaymentStatus(PaymentStatus.REFUNDED);
            }
            case UNPAID -> throw new BadRequestException("A payment status can only be reported as PAID or REFUNDED");
        }
        return OrderResponse.from(orderRepository.saveAndFlush(order));
    }

    /** Ids of online orders still unpaid after the allowed delay, oldest first. */
    @Transactional(readOnly = true)
    public List<UUID> findExpiredUnpaidOrders(Instant createdBefore, int limit) {
        return orderRepository.findExpiredUnpaid(createdBefore, PageRequest.of(0, limit));
    }

    /**
     * Cancels one abandoned online checkout and returns its stock. Conditions are checked again under
     * the row lock: a payment confirmed in the meantime wins and the order is left alone.
     */
    @Transactional
    public boolean expireIfStillUnpaid(UUID id, Instant createdBefore) {
        Order order = lockOrder(id);
        boolean stillAbandoned = order.getPaymentMethod() == PaymentMethod.ONLINE
                && order.getPaymentStatus() == PaymentStatus.UNPAID
                && order.getStatus() == Status.PENDING
                && order.getCreatedAt().isBefore(createdBefore);
        if (stillAbandoned) {
            changeStatus(order, Status.CANCELLED);
        }
        return stillAbandoned;
    }

    /**
     * Applies a lifecycle transition.
     * Cancelling refunds a paid order first, then returns the stock. Both remote calls are idempotent on
     * retry, and any failure rolls the status change back, so neither money nor stock is ever lost.
     */
    private OrderResponse changeStatus(Order order, Status next) {
        Status current = order.getStatus();
        if (!current.canTransitionTo(next)) {
            throw new ConflictException("Cannot change order status from %s to %s. Allowed: %s"
                    .formatted(current, next, current.allowedNext()));
        }
        if (next == Status.CONFIRMED && order.getPaymentMethod() == PaymentMethod.ONLINE
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ConflictException("This order is awaiting its online payment");
        }
        if (next == Status.CANCELLED) {
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                paymentClient.refundOrder(order.getId());
                order.setPaymentStatus(PaymentStatus.REFUNDED);
            }
            productClient.release(order.getItems().stream()
                    .map(item -> new StockLine(item.getProductVariantId(), item.getQuantity()))
                    .toList());
        }
        order.setStatus(next);
        return OrderResponse.from(orderRepository.saveAndFlush(order));
    }

    /** Row lock, so two concurrent status changes (e.g. double-clicked "cancel") run one after the other. */
    private Order lockOrder(UUID id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private Order buildOrder(UUID userId, UserProfile profile, UserAddress address, String phoneNumber,
                             CreateOrderRequest request, List<StockLine> lines, List<ReservedItem> reserved) {
        Map<UUID, ReservedItem> byVariant = reserved.stream()
                .collect(Collectors.toMap(ReservedItem::variantId, Function.identity()));

        Order order = new Order();
        order.setReference(referenceGenerator.next());
        order.setUserId(userId);
        order.setCustomerTitle(profile.title());
        order.setCustomerName(profile.name());
        order.setCustomerEmail(profile.email());
        order.setPhoneNumber(phoneNumber);
        order.setShippingAddress(new ShippingAddress(address.addressLine1(), address.addressLine2(), address.city(),
                address.state(), address.country(), address.postalCode()));
        order.setPaymentMethod(request.paymentMethod());
        order.setStatus(Status.PENDING);

        BigDecimal total = BigDecimal.ZERO;
        int itemCount = 0;
        for (StockLine line : lines) {
            ReservedItem product = byVariant.get(line.variantId());
            if (product == null) {
                throw new ServiceUnavailableException("Product service did not confirm variant " + line.variantId(), null);
            }
            OrderItem item = new OrderItem();
            item.setProductVariantId(product.variantId());
            item.setProductId(product.productId());
            item.setProductTitle(product.productTitle());
            item.setThumbnail(product.thumbnail());
            item.setSku(product.sku());
            item.setSize(product.size());
            item.setUnitPrice(product.unitPrice());
            item.setQuantity(line.quantity());
            order.addItem(item);

            total = total.add(item.getLineTotal());
            itemCount += line.quantity();
        }
        order.setTotalPrice(total);
        order.setItemCount(itemCount);
        return order;
    }

    /** The same variant on two lines becomes one line with the summed quantity. */
    private static List<StockLine> mergeLines(List<CreateOrderRequest.Item> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        items.forEach(item -> quantities.merge(item.variantId(), item.quantity(), Integer::sum));
        return quantities.entrySet().stream()
                .map(entry -> new StockLine(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void releaseAfterFailedCheckout(List<StockLine> lines, RuntimeException cause) {
        try {
            productClient.release(lines);
        } catch (RuntimeException releaseFailure) {
            // Needs manual correction: stock stays reserved for an order that does not exist.
            log.error("Checkout failed AND stock release failed, stock must be restored manually: {}", lines,
                    releaseFailure);
            cause.addSuppressed(releaseFailure);
        }
    }
}
