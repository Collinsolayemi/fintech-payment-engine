package com.fintech.engine.dto;

import com.fintech.engine.model.entity.PaymentRequest;
import com.fintech.engine.model.enums.PaymentRequestStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentRequestResponse(
        UUID id,
        String requestCode,
        UUID requesterUserId,
        String requesterName,
        UUID requesterAccountId,
        String requesterAccountNumber,
        UUID payerUserId,
        String payerEmail,
        BigDecimal amount,
        String currency,
        String description,
        PaymentRequestStatus status,
        String transactionReference,
        OffsetDateTime expiresAt,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PaymentRequestResponse from(PaymentRequest pr) {
        String requesterName = (pr.getRequesterUser().getFirstName() + " " + pr.getRequesterUser().getLastName()).trim();
        return new PaymentRequestResponse(
                pr.getId(),
                pr.getRequestCode(),
                pr.getRequesterUser().getId(),
                requesterName,
                pr.getRequesterAccount().getId(),
                pr.getRequesterAccount().getAccountNumber(),
                pr.getPayerUser() != null ? pr.getPayerUser().getId() : null,
                pr.getPayerEmail(),
                pr.getAmount(),
                pr.getCurrency(),
                pr.getDescription(),
                pr.getStatus(),
                pr.getTransactionReference(),
                pr.getExpiresAt(),
                pr.getPaidAt(),
                pr.getCreatedAt(),
                pr.getUpdatedAt()
        );
    }
}

