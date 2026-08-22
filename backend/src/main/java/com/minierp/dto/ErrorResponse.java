package com.minierp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Single error envelope for every non-2xx response.
 * Never exposes stack traces or internal details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> details
) {

    public record FieldViolation(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse withDetails(int status, String error, String message, String path,
                                            List<FieldViolation> details) {
        return new ErrorResponse(Instant.now(), status, error, message, path, details);
    }
}
