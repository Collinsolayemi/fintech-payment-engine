package com.fintech.engine.dto;

import com.fintech.engine.model.enums.RecurrenceFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateRecurringTransferRequest(
        @NotNull(message = "Sender account ID is required")
        UUID senderAccountId,

        @NotNull(message = "Recipient account ID is required")
        UUID recipientAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Frequency is required")
        RecurrenceFrequency frequency,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        OffsetDateTime startAt,

        OffsetDateTime endDate
) {
}

