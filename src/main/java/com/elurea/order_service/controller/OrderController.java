package com.elurea.order_service.controller;

import com.elurea.order_service.dto.SaveOrderRequest;
import com.elurea.order_service.entity.Order;
import com.elurea.order_service.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<Order> getAllOrders() {
        return orderService.getAll();
    }

    @PostMapping
    public Order create(@RequestBody SaveOrderRequest req) {
        return orderService.create(req);
    }

    @PutMapping("/{id}")
    public Order update(@RequestBody SaveOrderRequest req, @PathVariable("id") UUID id) {
        return orderService.update(id, req);
    }

    @PutMapping("/{id}/status")
    public Order updateStatus(@RequestBody SaveOrderRequest req, @PathVariable("id") UUID id) {
        return orderService.updateStatus(id, req.status);
    }
}
