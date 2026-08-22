package com.minierp.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkOrderStatusUpdateRequest(
        @NotBlank(message = "status is required") String status
) {
}
