package com.minierp.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryResponse(
        Long id,
        Long itemId,
        String itemSku,
        String itemName,
        String unitOfMeasure,
        Long locationId,
        String locationCode,
        BigDecimal physicalQty,
        BigDecimal reservedQty,
        BigDecimal availableQty,
        Instant updatedAt
) {
}
