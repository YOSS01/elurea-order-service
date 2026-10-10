package com.elurea.order_service.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle. Only the transitions listed in {@link #allowedNext()} are accepted:
 * <pre>
 * PENDING -> CONFIRMED -> PACKAGING -> IN_TRANSIT -> DELIVERED -> RETURNED
 *    \___________\____________\--> CANCELLED (stock is returned)
 * </pre>
 */
public enum Status {
    /** Order placed, stock reserved, awaiting confirmation (e.g. phone check for cash on delivery). */
    PENDING,

    /** Order accepted. */
    CONFIRMED,

    /** Order is being prepared in the warehouse. */
    PACKAGING,

    /** Order shipped / on the way. */
    IN_TRANSIT,

    /** Order delivered to the customer. */
    DELIVERED,

    /** Order cancelled before shipping. Its stock has been released. */
    CANCELLED,

    /** Order sent back after delivery. */
    RETURNED;

    public Set<Status> allowedNext() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(PACKAGING, CANCELLED);
            case PACKAGING -> EnumSet.of(IN_TRANSIT, CANCELLED);
            case IN_TRANSIT -> EnumSet.of(DELIVERED);
            case DELIVERED -> EnumSet.of(RETURNED);
            case CANCELLED, RETURNED -> EnumSet.noneOf(Status.class);
        };
    }

    public boolean canTransitionTo(Status next) {
        return allowedNext().contains(next);
    }

    /** Customers may cancel on their own only before the order is prepared. */
    public boolean isCancellableByCustomer() {
        return this == PENDING || this == CONFIRMED;
    }
}
