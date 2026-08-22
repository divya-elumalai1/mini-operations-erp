package com.minierp.dto;

public record ItemResponse(
        Long id,
        String sku,
        String name,
        Long categoryId,
        String categoryName,
        String unitOfMeasure,
        boolean active
) {
}
