package com.fintech.engine.service;

import com.fintech.engine.dto.AccountBalanceResponse;
import com.fintech.engine.dto.AuthResponse;
import com.fintech.engine.dto.LoginRequest;
import com.fintech.engine.dto.RegisterRequest;
import com.fintech.engine.exception.EmailAlreadyExistsException;
import com.fintech.engine.exception.InvalidCredentialsException;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.security.JwtService;
import com.fintech.engine.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AccountService accountService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, authenticationManager, accountService);
    }

    @Test
    @DisplayName("Should successfully register user and automatically provision their default wallet")
    void shouldRegisterUserAndAutoProvisionWallet() {
        RegisterRequest request = new RegisterRequest(
                "jane@example.com", "Password123!", "Jane", "Doe", "USD"
        );

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashedPassword");

        User savedUser = new User("jane@example.com", "hashedPassword", "Jane", "Doe");
        savedUser.setId(userId);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AccountBalanceResponse defaultAccount = new AccountBalanceResponse(
                walletId, "USD-11223344", "Jane's Default Wallet", AccountType.LIABILITY,
                "USD", AccountStatus.ACTIVE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        when(accountService.createAccount(any())).thenReturn(defaultAccount);
        when(jwtService.generateToken(eq("jane@example.com"), eq("ROLE_USER"), eq(userId))).thenReturn("jwt-token-123");
        when(jwtService.getExpirationMillis()).thenReturn(86400000L);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("Bearer", response.tokenType());
        assertEquals("jwt-token-123", response.accessToken());
        assertEquals("jane@example.com", response.email());
        assertEquals(walletId, response.defaultAccountId());
        assertEquals("USD-11223344", response.defaultAccountNumber());

        verify(accountService).createAccount(any());
    }

    @Test
    @DisplayName("Should reject registration when email already exists")
    void shouldRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "existing@example.com", "Password123!", "Jane", "Doe", "USD"
        );

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("Should authenticate and login user successfully")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("jane@example.com", "Password123!");

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        User user = new User("jane@example.com", "hashedPassword", "Jane", "Doe");
        user.setId(userId);

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        AccountBalanceResponse userAccount = new AccountBalanceResponse(
                walletId, "USD-11223344", "Jane's Default Wallet", AccountType.LIABILITY,
                "USD", AccountStatus.ACTIVE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        when(accountService.getUserAccounts(userId)).thenReturn(List.of(userAccount));
        when(jwtService.generateToken(eq("jane@example.com"), eq("ROLE_USER"), eq(userId))).thenReturn("jwt-token-456");
        when(jwtService.getExpirationMillis()).thenReturn(86400000L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-token-456", response.accessToken());
        assertEquals(walletId, response.defaultAccountId());
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException on bad credentials")
    void shouldRejectInvalidCredentials() {
        LoginRequest request = new LoginRequest("jane@example.com", "WrongPassword");

        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }
}

