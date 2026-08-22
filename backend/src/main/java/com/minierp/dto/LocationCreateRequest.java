package com.minierp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocationCreateRequest(
        @NotBlank(message = "code is required") @Size(max = 20) String code,
        @NotBlank(message = "name is required") @Size(max = 100) String name
) {
}
