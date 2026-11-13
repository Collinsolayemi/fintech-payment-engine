package com.fintech.engine.service;

import com.fintech.engine.dto.CreateRecurringTransferRequest;
import com.fintech.engine.dto.RecurringTransferResponse;

import java.util.List;
import java.util.UUID;

public interface RecurringTransferService {

    RecurringTransferResponse create(String userEmail, CreateRecurringTransferRequest request);

    List<RecurringTransferResponse> list(String userEmail);

    RecurringTransferResponse getById(String userEmail, UUID id);

    RecurringTransferResponse pause(String userEmail, UUID id);

    RecurringTransferResponse resume(String userEmail, UUID id);

    RecurringTransferResponse cancel(String userEmail, UUID id);

    RecurringTransferResponse executeNow(String userEmail, UUID id);

    void processDueTransfers();
}

