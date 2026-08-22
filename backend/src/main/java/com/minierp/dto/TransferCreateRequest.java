package com.minierp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferCreateRequest(
        @NotNull(message = "sourceLocationId is required") Long sourceLocationId,
        @NotNull(message = "destinationLocationId is required") Long destinationLocationId,
        @NotNull(message = "itemId is required") Long itemId,
        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than 0") BigDecimal quantity
) {
}
