package com.elurea.order_service.dto;

import com.elurea.order_service.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * What the customer chooses at checkout. Everything else (prices, product names, customer details,
 * the address itself) is looked up on the server, never trusted from the client.
 */
public record CreateOrderRequest(
        /* One of the customer's saved addresses (user service). */
        @NotNull
        UUID shippingAddressId,

        /* Optional: defaults to the phone number of the customer's profile. */
        @Pattern(regexp = "^\\+?[0-9][0-9 ()-]{6,19}$", message = "must be a valid phone number")
        String phoneNumber,

        @NotNull
        PaymentMethod paymentMethod,

        @NotEmpty
        @Size(max = 50)
        List<@Valid @NotNull Item> items
) {

    public record Item(
            @NotNull
            UUID variantId,

            @NotNull
            @Min(1)
            @Max(100)
            Integer quantity
    ) {
    }
}
