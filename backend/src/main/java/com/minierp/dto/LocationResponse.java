package com.minierp.dto;

public record LocationResponse(
        Long id,
        String code,
        String name,
        boolean active
) {
}
