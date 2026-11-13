package com.fintech.engine.dto;

import com.fintech.engine.model.entity.VirtualAccount;
import com.fintech.engine.model.enums.VirtualAccountStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VirtualAccountResponse(
        UUID id,
        UUID accountId,
        String walletAccountNumber,
        String currency,
        String accountNumber,
        String accountName,
        String bankName,
        String routingNumber,
        String sortCode,
        String iban,
        String bicSwift,
        VirtualAccountStatus status,
        String provider,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static VirtualAccountResponse from(VirtualAccount va) {
        return new VirtualAccountResponse(
                va.getId(),
                va.getAccount().getId(),
                va.getAccount().getAccountNumber(),
                va.getCurrency(),
                va.getAccountNumber(),
                va.getAccountName(),
                va.getBankName(),
                va.getRoutingNumber(),
                va.getSortCode(),
                va.getIban(),
                va.getBicSwift(),
                va.getStatus(),
                va.getProvider(),
                va.getCreatedAt(),
                va.getUpdatedAt()
        );
    }
}

