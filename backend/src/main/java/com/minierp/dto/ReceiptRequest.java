package com.minierp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Goods receipt: adds physical stock to an (item, location) position. */
public record ReceiptRequest(
        @NotNull(message = "itemId is required") Long itemId,
        @NotNull(message = "locationId is required") Long locationId,
        @Size(max = 40, message = "batchCode must be at most 40 characters") String batchCode,
        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than 0") BigDecimal quantity,
        @Size(max = 255, message = "notes must be at most 255 characters") String notes,
        @Size(max = 64, message = "idempotencyKey must be at most 64 characters") String idempotencyKey
) {
}
