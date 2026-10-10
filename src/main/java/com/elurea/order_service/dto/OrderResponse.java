package com.elurea.order_service.dto;

import com.elurea.order_service.entity.Order;
import com.elurea.order_service.entity.OrderItem;
import com.elurea.order_service.entity.ShippingAddress;
import com.elurea.order_service.enums.PaymentMethod;
import com.elurea.order_service.enums.PaymentStatus;
import com.elurea.order_service.enums.Status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full order: customer, delivery address, lines and the statuses it can move to next.
 */
public record OrderResponse(
        UUID id,
        String reference,
        UUID userId,
        Status status,
        List<Status> nextStatuses,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        Customer customer,
        Address shippingAddress,
        List<Item> items,
        int itemCount,
        BigDecimal totalPrice,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order) {
        ShippingAddress address = order.getShippingAddress();
        return new OrderResponse(
                order.getId(),
                order.getReference(),
                order.getUserId(),
                order.getStatus(),
                List.copyOf(order.getStatus().allowedNext()),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                new Customer(order.getCustomerTitle(), order.getCustomerName(), order.getCustomerEmail(),
                        order.getPhoneNumber()),
                new Address(address.getAddressLine1(), address.getAddressLine2(), address.getCity(),
                        address.getState(), address.getCountry(), address.getPostalCode()),
                order.getItems().stream().map(Item::from).toList(),
                order.getItemCount(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public record Customer(String title, String name, String email, String phoneNumber) {
    }

    public record Address(String addressLine1, String addressLine2, String city, String state,
                          String country, String postalCode) {
    }

    public record Item(
            UUID id,
            UUID variantId,
            UUID productId,
            String productTitle,
            String thumbnail,
            String sku,
            String size,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {

        static Item from(OrderItem item) {
            return new Item(item.getId(), item.getProductVariantId(), item.getProductId(), item.getProductTitle(),
                    item.getThumbnail(), item.getSku(), item.getSize(), item.getUnitPrice(), item.getQuantity(),
                    item.getLineTotal());
        }
    }
}
