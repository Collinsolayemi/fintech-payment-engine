package com.fintech.engine.repository.projection;

import java.math.BigDecimal;

public interface CurrencyTrialBalanceView {
    String getCurrency();
    BigDecimal getDebits();
    BigDecimal getCredits();
}

