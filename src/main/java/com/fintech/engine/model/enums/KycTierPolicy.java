package com.fintech.engine.model.enums;

import java.math.BigDecimal;

public enum KycTierPolicy {
    TIER_1(1, "Basic (Email Verified)", new BigDecimal("500.00"), new BigDecimal("1000.00")),
    TIER_2(2, "Standard (Identity Verified)", new BigDecimal("5000.00"), new BigDecimal("10000.00")),
    TIER_3(3, "Enhanced (Full Compliance)", new BigDecimal("50000.00"), new BigDecimal("100000.00"));

    private final int tier;
    private final String description;
    private final BigDecimal singleTransactionLimit;
    private final BigDecimal dailyLimit;

    KycTierPolicy(int tier, String description, BigDecimal singleTransactionLimit, BigDecimal dailyLimit) {
        this.tier = tier;
        this.description = description;
        this.singleTransactionLimit = singleTransactionLimit;
        this.dailyLimit = dailyLimit;
    }

    public static KycTierPolicy fromTier(int tier) {
        for (KycTierPolicy policy : values()) {
            if (policy.tier == tier) {
                return policy;
            }
        }
        return TIER_1;
    }

    public int getTier() { return tier; }
    public String getDescription() { return description; }
    public BigDecimal getSingleTransactionLimit() { return singleTransactionLimit; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
}

