package com.elurea.order_service.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String productVariantId;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(nullable = false)
    private float price;
}
