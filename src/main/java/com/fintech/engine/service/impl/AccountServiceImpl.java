package com.fintech.engine.service.impl;

import com.fintech.engine.dto.AccountBalanceResponse;
import com.fintech.engine.dto.CreateAccountRequest;
import com.fintech.engine.dto.CreateCurrencyWalletRequest;
import com.fintech.engine.exception.DuplicateWalletException;
import com.fintech.engine.exception.UserNotFoundException;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.AccountService;
import com.fintech.engine.service.LedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountServiceImpl(AccountRepository accountRepository,
                              UserRepository userRepository,
                              LedgerService ledgerService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
    }

    @Override
    @Transactional
    public AccountBalanceResponse createAccount(CreateAccountRequest request) {
        User user = null;
        if (request.userId() != null) {
            user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + request.userId()));
        }

        String accountNumber = generateAccountNumber(request.currency() != null ? request.currency() : "USD");
        Account account = new Account(user, accountNumber, request.accountName(), request.type(), request.currency());
        account = accountRepository.save(account);

        return ledgerService.getAccountBalance(account.getId());
    }

    @Override
    @Transactional
    public AccountBalanceResponse createCurrencyWallet(String userEmailOrId, CreateCurrencyWalletRequest request) {
        User user;
        try {
            UUID userId = UUID.fromString(userEmailOrId);
            user = userRepository.findById(userId)
                    .orElseGet(() -> userRepository.findByEmail(userEmailOrId)
                            .orElseThrow(() -> new UserNotFoundException(userEmailOrId)));
        } catch (IllegalArgumentException notUuid) {
            user = userRepository.findByEmail(userEmailOrId)
                    .orElseThrow(() -> new UserNotFoundException(userEmailOrId));
        }

        String currency = request.currency().toUpperCase().trim();
        List<Account> existingAccounts = accountRepository.findByUserId(user.getId());
        boolean alreadyExists = existingAccounts.stream()
                .anyMatch(a -> a.getCurrency().equalsIgnoreCase(currency));
        if (alreadyExists) {
            throw new DuplicateWalletException(currency);
        }

        String accountName = request.accountName() != null && !request.accountName().isBlank()
                ? request.accountName().trim()
                : user.getFirstName() + "'s " + currency + " Wallet";

        String accountNumber = generateAccountNumber(currency);
        Account account = new Account(user, accountNumber, accountName, AccountType.LIABILITY, currency);
        account = accountRepository.save(account);

        return ledgerService.getAccountBalance(account.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountBalanceResponse getAccount(UUID accountId) {
        return ledgerService.getAccountBalance(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountBalanceResponse> getUserAccounts(UUID userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(acc -> ledgerService.getAccountBalance(acc.getId()))
                .collect(Collectors.toList());
    }

    private String generateAccountNumber(String currency) {
        int randomDigits = 10000000 + secureRandom.nextInt(90000000);
        return String.format("%s-%08d", currency.toUpperCase(), randomDigits);
    }
}

