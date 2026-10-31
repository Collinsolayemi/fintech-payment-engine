package com.fintech.engine.service.impl;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.EmailAlreadyExistsException;
import com.fintech.engine.exception.InvalidCredentialsException;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.security.JwtService;
import com.fintech.engine.service.AccountService;
import com.fintech.engine.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AccountService accountService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthenticationManager authenticationManager,
                           AccountService accountService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.accountService = accountService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Processing user registration for email: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        // 1. Hash password and persist user
        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.email(), hashedPassword, request.firstName(), request.lastName());
        user = userRepository.save(user);

        // 2. Automatically provision user's default wallet account
        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency().toUpperCase() : "USD";

        CreateAccountRequest accountRequest = new CreateAccountRequest(
                user.getId(),
                request.firstName() + "'s Default Wallet",
                AccountType.LIABILITY,
                currency
        );
        AccountBalanceResponse defaultAccount = accountService.createAccount(accountRequest);
        log.info("Auto-provisioned default wallet {} for user {}", defaultAccount.accountNumber(), user.getId());

        // 3. Generate JWT access token
        String token = jwtService.generateToken(user.getEmail(), user.getRole(), user.getId());

        return new AuthResponse(
                "Bearer",
                token,
                jwtService.getExpirationMillis() / 1000,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                defaultAccount.accountId(),
                defaultAccount.accountNumber()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Processing login authentication for email: {}", request.email());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            log.warn("Login failed: Invalid credentials for email: {}", request.email());
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        // Fetch user's primary wallet account
        List<AccountBalanceResponse> userAccounts = accountService.getUserAccounts(user.getId());
        UUID defaultAccountId = userAccounts.isEmpty() ? null : userAccounts.get(0).accountId();
        String defaultAccountNumber = userAccounts.isEmpty() ? null : userAccounts.get(0).accountNumber();

        String token = jwtService.generateToken(user.getEmail(), user.getRole(), user.getId());

        return new AuthResponse(
                "Bearer",
                token,
                jwtService.getExpirationMillis() / 1000,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                defaultAccountId,
                defaultAccountNumber
        );
    }
}

