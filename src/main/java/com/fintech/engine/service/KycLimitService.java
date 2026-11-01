package com.fintech.engine.service;

import com.fintech.engine.model.entity.User;
import java.math.BigDecimal;
import java.util.UUID;

public interface KycLimitService {
    void validateTransactionLimit(User user, BigDecimal requestedAmount);
    BigDecimal getDailyUsedToday(UUID userId);
    BigDecimal getDailyRemainingToday(User user);
}

