package com.fintech.engine.dto;
import com.fintech.engine.model.entity.Beneficiary;
import com.fintech.engine.model.enums.BeneficiaryType;
import java.util.UUID;
import java.time.OffsetDateTime;
public record BeneficiaryResponse(UUID id, BeneficiaryType type, String nickname, UUID recipientAccountId,
    String bankCode, String accountNumber, String accountName, String currency, OffsetDateTime createdAt) {
    public static BeneficiaryResponse from(Beneficiary b) {
        return new BeneficiaryResponse(b.getId(), b.getType(), b.getNickname(), b.getRecipientAccountId(),
            b.getBankCode(), b.getAccountNumber(), b.getAccountName(), b.getCurrency(), b.getCreatedAt());
    }
}
