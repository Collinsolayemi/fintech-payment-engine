package com.fintech.engine.service.impl;

import com.fintech.engine.dto.OtpResponse;
import com.fintech.engine.exception.InvalidOtpException;
import com.fintech.engine.exception.OtpCooldownException;
import com.fintech.engine.exception.OtpExpiredException;
import com.fintech.engine.exception.OtpMaxAttemptsExceededException;
import com.fintech.engine.exception.UserNotFoundException;
import com.fintech.engine.model.entity.OtpToken;
import com.fintech.engine.model.enums.OtpPurpose;
import com.fintech.engine.repository.OtpTokenRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.NotificationService;
import com.fintech.engine.service.OtpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class OtpServiceImpl implements OtpService {

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int COOLDOWN_SECONDS = 60;
    private final SecureRandom secureRandom = new SecureRandom();

    private final OtpTokenRepository otpTokenRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public OtpServiceImpl(
        OtpTokenRepository otpTokenRepository,
        UserRepository userRepository,
        NotificationService notificationService
    ) {
        this.otpTokenRepository = otpTokenRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public OtpResponse sendOtp(String email, OtpPurpose purpose) {
        if (purpose == OtpPurpose.FORGOT_PASSWORD) {
            userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account registered with email: " + email));
        }

        // Check cooldown from latest OTP request
        Optional<OtpToken> latestOtpOpt = otpTokenRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);
        if (latestOtpOpt.isPresent()) {
            OtpToken latest = latestOtpOpt.get();
            if (latest.getCreatedAt() != null) {
                long elapsedSeconds = Duration.between(latest.getCreatedAt(), OffsetDateTime.now()).getSeconds();
                if (elapsedSeconds < COOLDOWN_SECONDS) {
                    throw new OtpCooldownException(COOLDOWN_SECONDS - elapsedSeconds);
                }
            }
        }

        // Generate 6-digit numeric OTP
        int code = secureRandom.nextInt(1_000_000);
        String otpCode = String.format("%06d", code);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        OtpToken otpToken = new OtpToken(email, otpCode, purpose, expiresAt);
        otpTokenRepository.save(otpToken);

        // Dispatch notification asynchronously / through notification service
        notificationService.sendOtpNotification(email, otpCode, purpose);

        return new OtpResponse(
            "OTP verification code sent successfully to " + email,
            email,
            purpose,
            OTP_EXPIRY_MINUTES * 60L
        );
    }

    @Override
    @Transactional
    public OtpResponse verifyOtp(String email, String otpCode, OtpPurpose purpose) {
        OtpToken otpToken = otpTokenRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
            .orElseThrow(() -> new InvalidOtpException("No OTP request found for this email and purpose"));

        if (otpToken.isExpired()) {
            throw new OtpExpiredException();
        }

        if (otpToken.isMaxAttemptsReached()) {
            throw new OtpMaxAttemptsExceededException();
        }

        otpToken.incrementAttempts();

        boolean matches = MessageDigest.isEqual(
            otpToken.getOtpCode().getBytes(StandardCharsets.UTF_8),
            otpCode.getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            otpTokenRepository.save(otpToken);
            int remaining = otpToken.getMaxAttempts() - otpToken.getAttempts();
            throw new InvalidOtpException("Invalid OTP code. " + remaining + " attempts remaining.");
        }

        otpToken.markVerified();
        OtpToken saved = otpTokenRepository.save(otpToken);

        String resetToken = (purpose == OtpPurpose.FORGOT_PASSWORD) ? saved.getId().toString() : null;

        return new OtpResponse(
            "OTP verified successfully",
            email,
            purpose,
            0,
            resetToken
        );
    }

    @Override
    @Transactional(readOnly = true)
    public void validateResetToken(String email, String resetToken) {
        UUID tokenId;
        try {
            tokenId = UUID.fromString(resetToken);
        } catch (IllegalArgumentException e) {
            throw new InvalidOtpException("Invalid reset token format");
        }

        OtpToken token = otpTokenRepository.findById(tokenId)
            .orElseThrow(() -> new InvalidOtpException("Invalid or non-existent reset token"));

        if (!token.getEmail().equalsIgnoreCase(email)) {
            throw new InvalidOtpException("Reset token does not match email");
        }

        if (token.getPurpose() != OtpPurpose.FORGOT_PASSWORD) {
            throw new InvalidOtpException("Token not issued for password reset");
        }

        if (!token.isVerified()) {
            throw new InvalidOtpException("OTP has not been verified yet");
        }

        if (token.isExpired()) {
            throw new OtpExpiredException();
        }
    }

    @Override
    @Transactional
    public void consumeResetToken(String email, String resetToken) {
        validateResetToken(email, resetToken);
        UUID tokenId = UUID.fromString(resetToken);
        otpTokenRepository.deleteById(tokenId);
    }
}

