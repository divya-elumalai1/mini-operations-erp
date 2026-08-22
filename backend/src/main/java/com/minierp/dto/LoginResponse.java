package com.minierp.dto;

public record LoginResponse(
        String token,
        String tokenType,
        String username,
        String fullName,
        String role
) {
    public static final String BEARER = "Bearer";
}
