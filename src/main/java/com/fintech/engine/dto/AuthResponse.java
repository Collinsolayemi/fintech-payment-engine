package com.fintech.engine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
    String tokenType,
    String accessToken,
    String refreshToken,
    long expiresInSeconds,
    UUID userId,
    String email,
    String role,
    UUID defaultAccountId,
    String defaultAccountNumber
) {
    public AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        UUID userId,
        String email,
        String role,
        UUID defaultAccountId,
        String defaultAccountNumber
    ) {
        this(tokenType, accessToken, null, expiresInSeconds, userId, email, role, defaultAccountId, defaultAccountNumber);
    }
}
