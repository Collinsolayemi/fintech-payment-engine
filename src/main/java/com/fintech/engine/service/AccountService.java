package com.fintech.engine.service;

import com.fintech.engine.dto.AccountBalanceResponse;
import com.fintech.engine.dto.CreateAccountRequest;

import java.util.List;
import java.util.UUID;

public interface AccountService {
    AccountBalanceResponse createAccount(CreateAccountRequest request);
    AccountBalanceResponse getAccount(UUID accountId);
    List<AccountBalanceResponse> getUserAccounts(UUID userId);
}

