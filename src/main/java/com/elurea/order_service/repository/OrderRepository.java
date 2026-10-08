package com.elurea.order_service.repository;

import com.elurea.order_service.entity.Order;
import com.elurea.order_service.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findAll();

    Optional<Order> findById(UUID id);

    List<Order> findAllByStatus(Status status);

    Order save(Order order);
}
