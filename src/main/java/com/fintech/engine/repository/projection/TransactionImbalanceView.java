package com.fintech.engine.repository.projection;

import java.math.BigDecimal;

public interface TransactionImbalanceView {
    String getReference();
    BigDecimal getDebits();
    BigDecimal getCredits();
}

