package com.fintech.engine.service.impl;

import com.fintech.engine.exception.InvalidRefreshTokenException;
import com.fintech.engine.model.entity.RefreshToken;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.repository.RefreshTokenRepository;
import com.fintech.engine.service.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int REFRESH_TOKEN_EXPIRY_DAYS = 7;
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        String token = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(REFRESH_TOKEN_EXPIRY_DAYS);

        RefreshToken refreshToken = new RefreshToken(user, token, expiresAt);
        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional
    public RefreshToken verifyAndRotate(String tokenString) {
        RefreshToken existingToken = refreshTokenRepository.findByTokenAndRevokedFalse(tokenString)
            .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid or has already been used"));

        if (existingToken.isExpired()) {
            existingToken.revoke();
            refreshTokenRepository.save(existingToken);
            throw new InvalidRefreshTokenException("Refresh token has expired. Please log in again.");
        }

        // Revoke the old token (Token Rotation pattern)
        existingToken.revoke();
        refreshTokenRepository.save(existingToken);

        // Issue new refresh token
        return createRefreshToken(existingToken.getUser());
    }

    @Override
    @Transactional
    public void revokeToken(String tokenString) {
        refreshTokenRepository.findByTokenAndRevokedFalse(tokenString)
            .ifPresent(token -> {
                token.revoke();
                refreshTokenRepository.save(token);
            });
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.revokeAllUserTokens(userId);
    }
}

