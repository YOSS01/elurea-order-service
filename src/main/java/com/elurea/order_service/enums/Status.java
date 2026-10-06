package com.elurea.order_service.enums;

public enum Status {
    // Order created but not yet validated (COD)
    PENDING,

    // Order accepted after validation by user (COD)
    CONFIRMED,

    // Order is being prepared in warehouse
    PACKAGING,

    // Order shipped / on the way
    IN_TRANSIT,

    // Order delivered to customer
    DELIVERED,

    // Order cancelled before delivery (COD)
    CANCELLED,

    // Order returned after delivery (optional but realistic for e-commerce)
    RETURNED
}
