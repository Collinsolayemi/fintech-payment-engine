package com.fintech.engine.service;

import com.fintech.engine.dto.CreatePaymentRequestDto;
import com.fintech.engine.dto.PayPaymentRequestDto;
import com.fintech.engine.dto.PaymentRequestResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentRequestService {

    PaymentRequestResponse create(String userEmail, CreatePaymentRequestDto request);

    List<PaymentRequestResponse> listSent(String userEmail);

    List<PaymentRequestResponse> listIncoming(String userEmail);

    PaymentRequestResponse getByCode(String requestCode);

    PaymentRequestResponse pay(String userEmail, UUID id, PayPaymentRequestDto request, String idempotencyKey);

    PaymentRequestResponse decline(String userEmail, UUID id);

    PaymentRequestResponse cancel(String userEmail, UUID id);
}

