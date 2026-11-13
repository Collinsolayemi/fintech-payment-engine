package com.fintech.engine.dto;

import com.fintech.engine.model.entity.RecurringTransfer;
import com.fintech.engine.model.enums.RecurrenceFrequency;
import com.fintech.engine.model.enums.RecurringTransferStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringTransferResponse(
        UUID id,
        UUID senderAccountId,
        String senderAccountNumber,
        UUID recipientAccountId,
        String recipientAccountNumber,
        String recipientAccountName,
        BigDecimal amount,
        String currency,
        String description,
        RecurrenceFrequency frequency,
        RecurringTransferStatus status,
        OffsetDateTime nextRunAt,
        OffsetDateTime endDate,
        OffsetDateTime lastRunAt,
        String lastTransactionReference,
        String lastFailureReason,
        int executionCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static RecurringTransferResponse from(RecurringTransfer rt) {
        return new RecurringTransferResponse(
                rt.getId(),
                rt.getSenderAccount().getId(),
                rt.getSenderAccount().getAccountNumber(),
                rt.getRecipientAccount().getId(),
                rt.getRecipientAccount().getAccountNumber(),
                rt.getRecipientAccount().getAccountName(),
                rt.getAmount(),
                rt.getCurrency(),
                rt.getDescription(),
                rt.getFrequency(),
                rt.getStatus(),
                rt.getNextRunAt(),
                rt.getEndDate(),
                rt.getLastRunAt(),
                rt.getLastTransactionReference(),
                rt.getLastFailureReason(),
                rt.getExecutionCount(),
                rt.getCreatedAt(),
                rt.getUpdatedAt()
        );
    }
}

