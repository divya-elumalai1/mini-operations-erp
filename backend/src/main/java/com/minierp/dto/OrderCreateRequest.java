package com.minierp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreateRequest(
        @NotBlank(message = "customerName is required") String customerName,
        @NotEmpty(message = "at least one order line is required")
        @Valid List<OrderLineRequest> lines
) {

    public record OrderLineRequest(
            @NotNull(message = "itemId is required") Long itemId,
            @NotNull(message = "locationId is required") Long locationId,
            @NotNull(message = "quantity is required")
            @Positive(message = "quantity must be greater than 0") BigDecimal quantity
    ) {
    }
}
