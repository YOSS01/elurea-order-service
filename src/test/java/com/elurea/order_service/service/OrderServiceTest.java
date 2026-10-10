package com.elurea.order_service.service;

import com.elurea.order_service.client.PaymentServiceClient;
import com.elurea.order_service.client.ProductServiceClient;
import com.elurea.order_service.client.ProductServiceClient.ReservedItem;
import com.elurea.order_service.client.ProductServiceClient.StockLine;
import com.elurea.order_service.client.UserServiceClient;
import com.elurea.order_service.client.UserServiceClient.UserAddress;
import com.elurea.order_service.client.UserServiceClient.UserProfile;
import com.elurea.order_service.dto.CreateOrderRequest;
import com.elurea.order_service.dto.OrderResponse;
import com.elurea.order_service.entity.Order;
import com.elurea.order_service.entity.OrderItem;
import com.elurea.order_service.entity.ShippingAddress;
import com.elurea.order_service.enums.PaymentMethod;
import com.elurea.order_service.enums.PaymentStatus;
import com.elurea.order_service.enums.Status;
import com.elurea.order_service.exception.BadRequestException;
import com.elurea.order_service.exception.ConflictException;
import com.elurea.order_service.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final UUID customerId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID dressS = UUID.randomUUID();
    private final UUID shirtM = UUID.randomUUID();

    private OrderRepository orderRepository;
    private UserServiceClient userClient;
    private ProductServiceClient productClient;
    private PaymentServiceClient paymentClient;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        userClient = mock(UserServiceClient.class);
        productClient = mock(ProductServiceClient.class);
        paymentClient = mock(PaymentServiceClient.class);
        orderService = new OrderService(orderRepository, userClient, productClient, paymentClient,
                new OrderReferenceGenerator());

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userClient.getProfile(any())).thenReturn(
                new UserProfile(customerId, "MRS", "Alice", "alice@example.com", "+212600000000"));
        when(userClient.getAddress(eq(addressId), any())).thenReturn(
                new UserAddress(addressId, customerId, "1 Rue A", null, "Rabat", "RSK", "Morocco", "10000"));
        authenticate(customerId, "USER");
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkoutPricesItemsServerSideAndSnapshotsCustomerAndAddress() {
        when(productClient.reserve(anyList())).thenReturn(List.of(
                reserved(dressS, "Linen Dress", "49.90", 3),
                reserved(shirtM, "Cotton Shirt", "19.50", 1)));

        // The same variant on two lines must be merged into one reservation of 3.
        OrderResponse order = orderService.create(new CreateOrderRequest(addressId, null, PaymentMethod.CASH_ON_DELIVERY,
                List.of(item(dressS, 2), item(shirtM, 1), item(dressS, 1))));

        verify(productClient).reserve(List.of(new StockLine(dressS, 3), new StockLine(shirtM, 1)));
        assertThat(order.totalPrice()).isEqualByComparingTo("169.20"); // 3 x 49.90 + 19.50
        assertThat(order.itemCount()).isEqualTo(4);
        assertThat(order.status()).isEqualTo(Status.PENDING);
        assertThat(order.reference()).matches("EL\\d{6}-[2-9A-HJKMNP-Z]{6}");
        assertThat(order.customer().phoneNumber()).isEqualTo("+212600000000"); // from the profile
        assertThat(order.shippingAddress().city()).isEqualTo("Rabat");
        assertThat(order.userId()).isEqualTo(customerId);
    }

    @Test
    void checkoutWithoutAnyPhoneNumberIsRejectedBeforeReservingStock() {
        when(userClient.getProfile(any())).thenReturn(new UserProfile(customerId, "MR", "Bob", "bob@example.com", null));

        assertThatThrownBy(() -> orderService.create(
                new CreateOrderRequest(addressId, null, PaymentMethod.ONLINE, List.of(item(dressS, 1)))))
                .isInstanceOf(BadRequestException.class);
        verify(productClient, never()).reserve(anyList());
    }

    @Test
    void stockIsReleasedWhenSavingTheOrderFails() {
        when(productClient.reserve(anyList())).thenReturn(List.of(reserved(dressS, "Linen Dress", "49.90", 2)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> orderService.create(
                new CreateOrderRequest(addressId, "+212611111111", PaymentMethod.ONLINE, List.of(item(dressS, 2)))))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(productClient).release(List.of(new StockLine(dressS, 2)));
    }

    @Test
    void customerCancellingPendingOrderReleasesItsStock() {
        Order order = existingOrder(customerId, Status.PENDING);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        OrderResponse cancelled = orderService.cancel(order.getId());

        assertThat(cancelled.status()).isEqualTo(Status.CANCELLED);
        verify(productClient).release(List.of(new StockLine(dressS, 2)));
    }

    @Test
    void customerCannotCancelOrderBeingPrepared() {
        Order order = existingOrder(customerId, Status.PACKAGING);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(order.getId())).isInstanceOf(ConflictException.class);
        verify(productClient, never()).release(anyList());
    }

    @Test
    void customerCannotCancelSomeoneElsesOrder() {
        Order order = existingOrder(UUID.randomUUID(), Status.PENDING);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(order.getId())).isInstanceOf(AccessDeniedException.class);
        verify(productClient, never()).release(anyList());
    }

    @Test
    void adminCannotSkipLifecycleSteps() {
        authenticate(UUID.randomUUID(), "ADMIN");
        Order order = existingOrder(customerId, Status.PENDING);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(order.getId(), Status.DELIVERED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PENDING to DELIVERED");
    }

    @Test
    void cancellingPaidOrderRefundsBeforeReleasingStock() {
        Order order = existingOrder(customerId, Status.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        OrderResponse cancelled = orderService.cancel(order.getId());

        var inOrder = org.mockito.Mockito.inOrder(paymentClient, productClient);
        inOrder.verify(paymentClient).refundOrder(order.getId());
        inOrder.verify(productClient).release(anyList());
        assertThat(cancelled.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void failedRefundKeepsTheOrderAndItsStock() {
        Order order = existingOrder(customerId, Status.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        UUID orderId = order.getId();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        org.mockito.Mockito.doThrow(new com.elurea.order_service.exception.ServiceUnavailableException("down", null))
                .when(paymentClient).refundOrder(orderId);

        assertThatThrownBy(() -> orderService.cancel(orderId))
                .isInstanceOf(com.elurea.order_service.exception.ServiceUnavailableException.class);
        verify(productClient, never()).release(anyList());
        assertThat(order.getStatus()).isEqualTo(Status.CONFIRMED);
    }

    @Test
    void paymentConfirmsPendingOnlineOrderAndIsIdempotent() {
        Order order = existingOrder(customerId, Status.PENDING);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        OrderResponse paid = orderService.recordPayment(order.getId(), PaymentStatus.PAID);
        OrderResponse again = orderService.recordPayment(order.getId(), PaymentStatus.PAID);

        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.status()).isEqualTo(Status.CONFIRMED);
        assertThat(again.status()).isEqualTo(Status.CONFIRMED);
    }

    @Test
    void paymentForCancelledOrderIsRefused() {
        Order order = existingOrder(customerId, Status.CANCELLED);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.recordPayment(order.getId(), PaymentStatus.PAID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unpaidOnlineOrderCannotBeConfirmedByHand() {
        authenticate(UUID.randomUUID(), "ADMIN");
        Order order = existingOrder(customerId, Status.PENDING);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(order.getId(), Status.CONFIRMED))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("awaiting its online payment");
    }

    @Test
    void expiryCancelsAbandonedOrderButSparesOneJustPaid() {
        Instant cutoff = Instant.now();
        Order abandoned = existingOrder(customerId, Status.PENDING);
        when(abandoned.getCreatedAt()).thenReturn(cutoff.minusSeconds(60));
        when(orderRepository.findByIdForUpdate(abandoned.getId())).thenReturn(Optional.of(abandoned));

        Order paidMeanwhile = existingOrder(customerId, Status.CONFIRMED);
        paidMeanwhile.setPaymentStatus(PaymentStatus.PAID);
        when(paidMeanwhile.getCreatedAt()).thenReturn(cutoff.minusSeconds(60));
        when(orderRepository.findByIdForUpdate(paidMeanwhile.getId())).thenReturn(Optional.of(paidMeanwhile));

        assertThat(orderService.expireIfStillUnpaid(abandoned.getId(), cutoff)).isTrue();
        assertThat(abandoned.getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(orderService.expireIfStillUnpaid(paidMeanwhile.getId(), cutoff)).isFalse();
        assertThat(paidMeanwhile.getStatus()).isEqualTo(Status.CONFIRMED);
        verify(paymentClient, never()).refundOrder(any());
    }

    private static void authenticate(UUID userId, String role) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(userId.toString()).claim("role", role).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }

    private static CreateOrderRequest.Item item(UUID variantId, int quantity) {
        return new CreateOrderRequest.Item(variantId, quantity);
    }

    private static ReservedItem reserved(UUID variantId, String title, String price, int quantity) {
        return new ReservedItem(variantId, UUID.randomUUID(), title, null, "SKU-" + quantity, "M",
                new BigDecimal(price), quantity);
    }

    private Order existingOrder(UUID ownerId, Status status) {
        Order order = mock(Order.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        UUID id = UUID.randomUUID();
        when(order.getId()).thenReturn(id);
        order.setItems(new java.util.ArrayList<>());
        order.setUserId(ownerId);
        order.setStatus(status);
        order.setReference("EL260101-ABCDEF");
        order.setCustomerTitle("MRS");
        order.setCustomerName("Alice");
        order.setCustomerEmail("alice@example.com");
        order.setPhoneNumber("+212600000000");
        order.setShippingAddress(new ShippingAddress("1 Rue A", null, "Rabat", "RSK", "Morocco", "10000"));
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setPaymentStatus(PaymentStatus.UNPAID); // mocks skip field initialisers
        order.setTotalPrice(new BigDecimal("99.80"));
        order.setItemCount(2);

        OrderItem item = new OrderItem();
        item.setProductVariantId(dressS);
        item.setProductId(UUID.randomUUID());
        item.setProductTitle("Linen Dress");
        item.setSku("DRESS-S");
        item.setSize("S");
        item.setUnitPrice(new BigDecimal("49.90"));
        item.setQuantity(2);
        order.addItem(item);
        return order;
    }
}
