package com.fintech.engine.dto;

import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalanceResponse(
    UUID accountId,
    String accountNumber,
    String accountName,
    AccountType type,
    String currency,
    AccountStatus status,
    BigDecimal balance,
    BigDecimal totalDebits,
    BigDecimal totalCredits
) {}

