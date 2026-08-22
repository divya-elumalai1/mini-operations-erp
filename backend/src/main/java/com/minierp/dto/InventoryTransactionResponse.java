package com.minierp.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryTransactionResponse(
        Long id,
        Long itemId,
        String itemSku,
        Long locationId,
        String locationCode,
        String txType,
        String referenceType,
        Long referenceId,
        BigDecimal quantityDeltaPhysical,
        BigDecimal quantityDeltaReserved,
        String notes,
        String performedByUsername,
        Instant createdAt
) {
}
