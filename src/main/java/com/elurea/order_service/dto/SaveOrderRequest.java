package com.elurea.order_service.dto;

import com.elurea.order_service.enums.PaymentMethod;
import com.elurea.order_service.enums.Status;

import java.util.UUID;

public class SaveOrderRequest {
    public UUID id;
    public String userId;
    public String shippingAddressId;
    public String title;
    public String name;
    public String email;
    public String phoneNumber;
    public PaymentMethod paymentMethod;
    public float totalPrice;
    public Status status;
}
