package com.fintech.engine.dto;

import jakarta.validation.constraints.NotNull;

public record ProcessPayoutCallbackRequest(
    @NotNull(message = "Success flag is required")
    Boolean success,
    String failureReason
) {}

