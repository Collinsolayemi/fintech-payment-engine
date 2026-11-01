package com.fintech.engine.dto;

import com.fintech.engine.model.enums.KycApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewKycRequest(
    @NotNull(message = "Status is required (APPROVED or REJECTED)")
    KycApplicationStatus status,

    @Size(max = 1000, message = "Rejection reason cannot exceed 1000 characters")
    String rejectionReason
) {}

