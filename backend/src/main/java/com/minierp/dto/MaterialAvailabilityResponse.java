package com.minierp.dto;

import java.math.BigDecimal;

/** Backend-derived material availability for a work order. Shortage is computed server-side. */
public record MaterialAvailabilityResponse(
        Long workOrderId,
        String woNumber,
        Long itemId,
        String itemSku,
        Long locationId,
        String locationCode,
        BigDecimal requiredQuantity,
        BigDecimal availableQuantity,
        BigDecimal shortageQuantity,
        boolean sufficient
) {
}
