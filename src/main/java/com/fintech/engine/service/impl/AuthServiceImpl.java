package com.fintech.engine.service.impl;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.EmailAlreadyExistsException;
import com.fintech.engine.exception.InvalidCredentialsException;
import com.fintech.engine.exception.UserNotFoundException;
import com.fintech.engine.model.entity.RefreshToken;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.OtpPurpose;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.security.JwtService;
import com.fintech.engine.service.AccountService;
import com.fintech.engine.service.AuthService;
import com.fintech.engine.service.OtpService;
import com.fintech.engine.service.RefreshTokenService;
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
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuthenticationManager authenticationManager,
        AccountService accountService,
        OtpService otpService,
        RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.accountService = accountService;
        this.otpService = otpService;
        this.refreshTokenService = refreshTokenService;
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

        // 3. Generate JWT access token & Refresh token
        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole(), user.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                "Bearer",
                accessToken,
                refreshToken.getToken(),
                jwtService.getExpirationMillis() / 1000,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                defaultAccount.accountId(),
                defaultAccount.accountNumber()
        );
    }

    @Override
    @Transactional
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

        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole(), user.getId());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                "Bearer",
                accessToken,
                refreshToken.getToken(),
                jwtService.getExpirationMillis() / 1000,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                defaultAccountId,
                defaultAccountNumber
        );
    }

    @Override
    public OtpResponse forgotPassword(ForgotPasswordRequest request) {
        log.info("Initiating forgot password flow for email: {}", request.email());
        return otpService.sendOtp(request.email(), OtpPurpose.FORGOT_PASSWORD);
    }

    @Override
    public OtpResponse verifyOtp(VerifyOtpRequest request) {
        log.info("Verifying OTP for email: {}, purpose: {}", request.email(), request.purpose());
        return otpService.verifyOtp(request.email(), request.otpCode(), request.purpose());
    }

    @Override
    @Transactional
    public ApiResponseDto resetPassword(ResetPasswordRequest request) {
        log.info("Executing password reset for email: {}", request.email());
        // Verify and consume the reset token
        otpService.consumeResetToken(request.email(), request.resetToken());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + request.email()));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Revoke all refresh tokens across devices for security
        refreshTokenService.revokeAllUserTokens(user.getId());

        log.info("Password successfully reset for user: {}", user.getEmail());
        return new ApiResponseDto(true, "Password has been reset successfully. Please log in with your new password.");
    }

    @Override
    public OtpResponse resendOtp(ResendOtpRequest request) {
        log.info("Resending OTP for email: {}, purpose: {}", request.email(), request.purpose());
        return otpService.sendOtp(request.email(), request.purpose());
    }

    @Override
    @Transactional
    public ApiResponseDto changePassword(String userEmail, ChangePasswordRequest request) {
        log.info("Processing password change for user: {}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userEmail));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Revoke other active sessions
        refreshTokenService.revokeAllUserTokens(user.getId());

        return new ApiResponseDto(true, "Password changed successfully. Existing sessions have been revoked.");
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Processing refresh token rotation");

        RefreshToken newRefreshToken = refreshTokenService.verifyAndRotate(request.refreshToken());
        User user = newRefreshToken.getUser();

        List<AccountBalanceResponse> userAccounts = accountService.getUserAccounts(user.getId());
        UUID defaultAccountId = userAccounts.isEmpty() ? null : userAccounts.get(0).accountId();
        String defaultAccountNumber = userAccounts.isEmpty() ? null : userAccounts.get(0).accountNumber();

        String newAccessToken = jwtService.generateToken(user.getEmail(), user.getRole(), user.getId());

        return new AuthResponse(
                "Bearer",
                newAccessToken,
                newRefreshToken.getToken(),
                jwtService.getExpirationMillis() / 1000,
                user.getId(),
                user.getEmail(),
                user.getRole(),
                defaultAccountId,
                defaultAccountNumber
        );
    }

    @Override
    @Transactional
    public ApiResponseDto logout(RefreshTokenRequest request) {
        log.info("Logging out and revoking refresh token");
        refreshTokenService.revokeToken(request.refreshToken());
        return new ApiResponseDto(true, "Logged out successfully");
    }
}
