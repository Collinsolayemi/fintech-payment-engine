package com.fintech.engine.dto;

import com.fintech.engine.model.enums.OtpPurpose;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpResponse(
    String message,
    String email,
    OtpPurpose purpose,
    long expiresInSeconds,
    String resetToken
) {
    public OtpResponse(String message, String email, OtpPurpose purpose, long expiresInSeconds) {
        this(message, email, purpose, expiresInSeconds, null);
    }
}

