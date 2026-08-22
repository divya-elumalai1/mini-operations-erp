package com.minierp.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        Long id,
        String transferNumber,
        Long itemId,
        String itemSku,
        BigDecimal quantity,
        Long sourceLocationId,
        String sourceLocationCode,
        Long destinationLocationId,
        String destinationLocationCode,
        String status,
        String dispatchedByUsername,
        Instant dispatchedAt,
        String receivedByUsername,
        Instant receivedAt,
        String createdByUsername,
        Instant createdAt
) {
}
