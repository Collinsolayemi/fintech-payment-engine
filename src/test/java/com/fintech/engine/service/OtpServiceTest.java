package com.fintech.engine.service;

import com.fintech.engine.dto.OtpResponse;
import com.fintech.engine.exception.InvalidOtpException;
import com.fintech.engine.exception.OtpCooldownException;
import com.fintech.engine.exception.OtpExpiredException;
import com.fintech.engine.exception.OtpMaxAttemptsExceededException;
import com.fintech.engine.model.entity.OtpToken;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.OtpPurpose;
import com.fintech.engine.repository.OtpTokenRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.impl.OtpServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpTokenRepository otpTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpServiceImpl(otpTokenRepository, userRepository, notificationService);
    }

    @Test
    @DisplayName("Should successfully generate and dispatch OTP")
    void shouldSendOtpSuccessfully() {
        String email = "customer@example.com";
        User user = new User(email, "hashedPass", "Jane", "Doe");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(otpTokenRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(Optional.empty());

        OtpResponse response = otpService.sendOtp(email, OtpPurpose.FORGOT_PASSWORD);

        assertNotNull(response);
        assertEquals(email, response.email());
        assertEquals(OtpPurpose.FORGOT_PASSWORD, response.purpose());
        assertEquals(600, response.expiresInSeconds());

        verify(otpTokenRepository).save(any(OtpToken.class));
        verify(notificationService).sendOtpNotification(eq(email), anyString(), eq(OtpPurpose.FORGOT_PASSWORD));
    }

    @Test
    @DisplayName("Should reject OTP generation if requested within 60s cooldown")
    void shouldEnforceCooldown() {
        String email = "customer@example.com";
        User user = new User(email, "hashedPass", "Jane", "Doe");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        OtpToken existingToken = new OtpToken(email, "123456", OtpPurpose.FORGOT_PASSWORD, OffsetDateTime.now().plusMinutes(9));
        // Simulate creation 20 seconds ago
        try {
            var field = OtpToken.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(existingToken, OffsetDateTime.now().minusSeconds(20));
        } catch (Exception ignored) {}

        when(otpTokenRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(Optional.of(existingToken));

        assertThrows(OtpCooldownException.class, () -> otpService.sendOtp(email, OtpPurpose.FORGOT_PASSWORD));
        verify(notificationService, never()).sendOtpNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Should verify valid OTP and return reset token")
    void shouldVerifyValidOtp() {
        String email = "customer@example.com";
        OtpToken token = new OtpToken(email, "654321", OtpPurpose.FORGOT_PASSWORD, OffsetDateTime.now().plusMinutes(5));
        UUID tokenId = UUID.randomUUID();
        token.setId(tokenId);

        when(otpTokenRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(Optional.of(token));
        when(otpTokenRepository.save(any(OtpToken.class))).thenReturn(token);

        OtpResponse response = otpService.verifyOtp(email, "654321", OtpPurpose.FORGOT_PASSWORD);

        assertNotNull(response);
        assertTrue(token.isVerified());
        assertEquals(tokenId.toString(), response.resetToken());
    }

    @Test
    @DisplayName("Should reject invalid OTP and increment attempt counter")
    void shouldRejectInvalidOtp() {
        String email = "customer@example.com";
        OtpToken token = new OtpToken(email, "654321", OtpPurpose.FORGOT_PASSWORD, OffsetDateTime.now().plusMinutes(5));

        when(otpTokenRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(Optional.of(token));

        assertThrows(InvalidOtpException.class, () -> otpService.verifyOtp(email, "000000", OtpPurpose.FORGOT_PASSWORD));
        assertEquals(1, token.getAttempts());
        assertFalse(token.isVerified());
        verify(otpTokenRepository).save(token);
    }

    @Test
    @DisplayName("Should reject verification if OTP is expired")
    void shouldRejectExpiredOtp() {
        String email = "customer@example.com";
        OtpToken token = new OtpToken(email, "654321", OtpPurpose.FORGOT_PASSWORD, OffsetDateTime.now().minusMinutes(1));

        when(otpTokenRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(Optional.of(token));

        assertThrows(OtpExpiredException.class, () -> otpService.verifyOtp(email, "654321", OtpPurpose.FORGOT_PASSWORD));
    }

    @Test
    @DisplayName("Should reject verification if max attempts are exceeded")
    void shouldRejectMaxAttempts() {
        String email = "customer@example.com";
        OtpToken token = new OtpToken(email, "654321", OtpPurpose.FORGOT_PASSWORD, OffsetDateTime.now().plusMinutes(5));
        token.setAttempts(5);

        when(otpTokenRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, OtpPurpose.FORGOT_PASSWORD))
                .thenReturn(Optional.of(token));

        assertThrows(OtpMaxAttemptsExceededException.class, () -> otpService.verifyOtp(email, "654321", OtpPurpose.FORGOT_PASSWORD));
    }
}

