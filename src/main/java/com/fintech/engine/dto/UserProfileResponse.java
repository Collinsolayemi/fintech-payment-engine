package com.fintech.engine.dto;

import com.fintech.engine.model.enums.KycStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String role,
    int kycTier,
    String kycTierDescription,
    BigDecimal singleTransactionLimit,
    BigDecimal dailyLimit,
    BigDecimal dailyUsedToday,
    BigDecimal dailyRemainingToday,
    KycStatus kycStatus,
    String phoneNumber,
    String addressLine,
    String city,
    String country,
    String postalCode,
    LocalDate dateOfBirth,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}

