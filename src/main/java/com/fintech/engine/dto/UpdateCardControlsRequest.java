package com.fintech.engine.dto;

import java.math.BigDecimal;

public record UpdateCardControlsRequest(
        BigDecimal monthlyLimit,
        Boolean onlinePaymentsEnabled,
        Boolean internationalEnabled
) {}

