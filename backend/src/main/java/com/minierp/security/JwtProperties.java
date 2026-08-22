package com.minierp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration. The dev default secret exists only so the sample app boots;
 * production deployments MUST override JWT_SECRET (see .env.example / README).
 */
@ConfigurationProperties(prefix = "minierp.jwt")
public record JwtProperties(String secret, long expirationMinutes) {

    public JwtProperties {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalStateException("JWT expiration must be positive");
        }
    }
}
