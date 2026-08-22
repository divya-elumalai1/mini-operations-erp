package com.minierp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WorkOrderCreateRequest(
        @NotNull(message = "itemId is required") Long itemId,
        @NotNull(message = "locationId is required") Long locationId,
        @NotNull(message = "assignedUserId is required") Long assignedUserId,
        @NotNull(message = "requiredQuantity is required")
        @Positive(message = "requiredQuantity must be greater than 0") BigDecimal requiredQuantity
) {
}
