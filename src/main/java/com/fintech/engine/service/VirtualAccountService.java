package com.fintech.engine.service;

import com.fintech.engine.dto.CreateVirtualAccountRequest;
import com.fintech.engine.dto.SimulateVirtualAccountDepositRequest;
import com.fintech.engine.dto.VirtualAccountDepositResponse;
import com.fintech.engine.dto.VirtualAccountResponse;
import com.fintech.engine.model.enums.VirtualAccountStatus;

import java.util.List;
import java.util.UUID;

public interface VirtualAccountService {

    VirtualAccountResponse createVirtualAccount(String userEmail, CreateVirtualAccountRequest request);

    List<VirtualAccountResponse> getUserVirtualAccounts(String userEmail);

    VirtualAccountResponse getVirtualAccount(String userEmail, UUID virtualAccountId);

    VirtualAccountResponse updateStatus(String userEmail, UUID virtualAccountId, VirtualAccountStatus status);

    VirtualAccountDepositResponse simulateInboundDeposit(
            String userEmail,
            UUID virtualAccountId,
            SimulateVirtualAccountDepositRequest request,
            String idempotencyKey
    );
}

