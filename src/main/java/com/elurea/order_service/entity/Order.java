package com.elurea.order_service.entity;

import com.elurea.order_service.enums.PaymentMethod;
import com.elurea.order_service.enums.PaymentStatus;
import com.elurea.order_service.enums.Status;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An order is a snapshot: customer details, address and prices are copied at checkout
 * so that later changes elsewhere never alter what was bought, for how much, or where it went.
 */
@Getter
@Setter
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_unpaid_expiry", columnList = "payment_method, payment_status, status, created_at")
})
public class Order extends BaseEntity {

    /** Human-friendly number shown to customers, e.g. "EL260109-K7Q2MX". */
    @Column(nullable = false, unique = true, length = 20, updatable = false)
    private String reference;

    /** Id of the customer in the user service (no foreign key across services). */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String customerTitle;

    @Column(nullable = false, length = 100)
    private String customerName;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Embedded
    private ShippingAddress shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** When the payment service confirmed the online payment. */
    private Instant paidAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    /** Stored so that order lists do not need to load every item. */
    @Column(nullable = false)
    private int itemCount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productTitle ASC")
    private List<OrderItem> items = new ArrayList<>();

    /** Optimistic locking: concurrent status changes fail instead of overwriting each other. */
    @Version
    private long version;

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
