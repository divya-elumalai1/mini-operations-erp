package com.minierp.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WorkOrderResponse(
        Long id,
        String woNumber,
        Long itemId,
        String itemSku,
        String itemName,
        Long locationId,
        String locationCode,
        BigDecimal requiredQuantity,
        Long assignedUserId,
        String assignedUsername,
        String status,
        Instant completedAt,
        String createdByUsername,
        Instant createdAt
) {
}
