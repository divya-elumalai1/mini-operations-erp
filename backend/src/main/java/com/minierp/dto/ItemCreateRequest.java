package com.minierp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ItemCreateRequest(
        @NotBlank(message = "sku is required") @Size(max = 40) String sku,
        @NotBlank(message = "name is required") @Size(max = 120) String name,
        @NotNull(message = "categoryId is required") Long categoryId,
        @Size(max = 16, message = "unitOfMeasure must be at most 16 characters") String unitOfMeasure
) {
}
