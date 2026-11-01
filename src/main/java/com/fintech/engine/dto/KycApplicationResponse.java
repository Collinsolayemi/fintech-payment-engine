package com.fintech.engine.dto;

import com.fintech.engine.model.enums.IdType;
import com.fintech.engine.model.enums.KycApplicationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record KycApplicationResponse(
    UUID id,
    UUID userId,
    String userEmail,
    int targetTier,
    IdType idType,
    String idNumber,
    String documentUrl,
    KycApplicationStatus status,
    String rejectionReason,
    String reviewedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}

