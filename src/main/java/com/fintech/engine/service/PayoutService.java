package com.fintech.engine.service;

import com.fintech.engine.dto.InitiatePayoutRequest;
import com.fintech.engine.dto.PayoutResponse;

import java.util.List;
import java.util.UUID;

public interface PayoutService {
    PayoutResponse initiatePayout(InitiatePayoutRequest request, String idempotencyKey, String authenticatedEmail);
    PayoutResponse completePayout(String reference);
    PayoutResponse reversePayout(String reference, String failureReason);
    PayoutResponse getPayoutByReference(String reference);
    List<PayoutResponse> getUserPayouts(UUID userId);
}

