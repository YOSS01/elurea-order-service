package com.elurea.order_service.dto;

import com.elurea.order_service.enums.Status;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull
        Status status
) {
}
