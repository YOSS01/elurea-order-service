package com.elurea.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of an order. Product name, SKU and unit price are copied from the product service
 * at checkout, so the line stays correct even if the product is later renamed, repriced or deleted.
 */
@Getter
@Setter
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(nullable = false, updatable = false)
    private UUID productVariantId;

    @Column(nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false)
    private String productTitle;

    @Column(length = 512)
    private String thumbnail;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
