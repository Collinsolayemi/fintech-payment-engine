package com.fintech.engine.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface AccountBalanceView {
    UUID getAccountId();
    String getAccountNumber();
    String getCurrency();
    BigDecimal getBalance();
}

