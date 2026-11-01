package com.fintech.engine.repository.projection;

import java.math.BigDecimal;

public interface PostedDepositView {
    String getReference();
    BigDecimal getAmount();
    String getCurrency();
}

