package com.fintech.engine.exception;

import java.math.BigDecimal;

public class KycLimitExceededException extends FintechException {
    public KycLimitExceededException(String message) {
        super(message, "KYC_LIMIT_EXCEEDED");
    }

    public static KycLimitExceededException singleLimit(int tier, BigDecimal requested, BigDecimal limit) {
        return new KycLimitExceededException(
                String.format("Transaction amount of %s exceeds Tier %d single-transaction limit of %s",
                        requested, tier, limit));
    }

    public static KycLimitExceededException dailyLimit(int tier, BigDecimal requested, BigDecimal usedToday, BigDecimal limit) {
        return new KycLimitExceededException(
                String.format("Transaction of %s exceeds Tier %d daily limit of %s (already used today: %s)",
                        requested, tier, limit, usedToday));
    }
}

