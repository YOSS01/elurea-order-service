package com.elurea.order_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Copy of the delivery address at the time of the order. Later edits of the
 * customer's saved address must not change where a past order was shipped.
 */
@Getter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddress {

    @Column(name = "shipping_address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "shipping_address_line2")
    private String addressLine2;

    @Column(name = "shipping_city", nullable = false, length = 100)
    private String city;

    @Column(name = "shipping_state", nullable = false, length = 100)
    private String state;

    @Column(name = "shipping_country", nullable = false, length = 100)
    private String country;

    @Column(name = "shipping_postal_code", nullable = false, length = 20)
    private String postalCode;
}
