package com.elurea.order_service.service;

import com.elurea.order_service.dto.SaveOrderRequest;
import com.elurea.order_service.entity.Order;
import com.elurea.order_service.enums.Status;
import com.elurea.order_service.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // Get All Orders
    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    // Get All Orders By Status
    public List<Order> getAllByStatus(Status status) {
        return orderRepository.findAllByStatus(status);
    }

    // Create Order
    public Order create(SaveOrderRequest request) {
        Order order = new Order();

        if(request.userId != null) order.setUserId(request.userId);
        // Should Get the Shipping Address
        order.setShippingAddressId(request.shippingAddressId);
        order.setTitle(request.title);
        order.setName(request.name);
        order.setEmail(request.email);
        order.setPhoneNumber(request.phoneNumber);
        // Should Calculate the Total Price
        order.setTotalPrice(request.totalPrice);
        order.setPaymentMethod(request.paymentMethod);
        order.setStatus(Status.PENDING);

        return orderRepository.save(order);
    }

    // Update Order
    public Order update(UUID orderId, SaveOrderRequest request) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

        if(request.userId != null) order.setUserId(request.userId);
        if(request.shippingAddressId != null) order.setShippingAddressId(request.shippingAddressId);
        if(request.title != null) order.setTitle(request.title);
        if(request.name != null) order.setName(request.name);
        if(request.email != null) order.setEmail(request.email);
        if(request.phoneNumber != null) order.setPhoneNumber(request.phoneNumber);
        if(request.status != null) order.setStatus(request.status);

        return orderRepository.save(order);
    }

    // Update Order Status
    public Order updateStatus(UUID orderId, Status status) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(status);

        return orderRepository.save(order);
    }
}
