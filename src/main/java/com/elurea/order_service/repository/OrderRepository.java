package com.elurea.order_service.repository;

import com.elurea.order_service.entity.Order;
import com.elurea.order_service.enums.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Paged listing; both filters are optional. Items are not loaded (see Order.itemCount). */
    @Query("""
            SELECT o FROM Order o
            WHERE (:userId IS NULL OR o.userId = :userId)
              AND (:status IS NULL OR o.status = :status)
            """)
    Page<Order> search(@Param("userId") UUID userId, @Param("status") Status status, Pageable pageable);

    /** Online orders still unpaid and pending, created before the cutoff (see OrderExpiryJob). */
    @Query("""
            SELECT o.id FROM Order o
            WHERE o.paymentMethod = com.elurea.order_service.enums.PaymentMethod.ONLINE
              AND o.paymentStatus = com.elurea.order_service.enums.PaymentStatus.UNPAID
              AND o.status = com.elurea.order_service.enums.Status.PENDING
              AND o.createdAt < :cutoff
            ORDER BY o.createdAt
            """)
    List<UUID> findExpiredUnpaid(@Param("cutoff") Instant cutoff, Pageable pageable);

    /** Order with its items, in one query. */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    /**
     * Locks the row (SELECT ... FOR UPDATE) for status changes. Two concurrent cancellations are
     * serialised, so stock can never be released twice for the same order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);
}
