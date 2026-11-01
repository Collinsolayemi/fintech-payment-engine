package com.fintech.engine.repository.projection;

import java.math.BigDecimal;

public interface CurrencyAmountView {
    String getCurrency();
    BigDecimal getAmount();
}

