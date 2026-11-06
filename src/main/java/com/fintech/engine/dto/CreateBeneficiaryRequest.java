package com.fintech.engine.dto;
import com.fintech.engine.model.enums.BeneficiaryType;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record CreateBeneficiaryRequest(
    @NotNull BeneficiaryType type,
    @NotBlank @Size(max=100) String nickname,
    UUID recipientAccountId,
    @Size(max=32) String bankCode,
    @Size(max=64) String accountNumber,
    @Size(max=200) String accountName,
    @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency
) {}
