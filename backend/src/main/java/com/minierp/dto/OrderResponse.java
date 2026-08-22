package com.minierp.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        String customerName,
        String status,
        List<OrderLineResponse> lines,
        String createdByUsername,
        Instant createdAt
) {

    public record OrderLineResponse(
            Long id,
            Long itemId,
            String itemSku,
            Long locationId,
            String locationCode,
            BigDecimal quantity
    ) {
    }
}
