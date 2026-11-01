package com.fintech.engine.repository.projection;

import java.math.BigDecimal;

public interface MissingPayoutPostingView {
    String getReference();
    String getStatus();
    BigDecimal getAmount();
    String getCurrency();
    String getExpectedPosting();
}

