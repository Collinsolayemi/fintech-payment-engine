package com.fintech.engine.service;

import com.fintech.engine.model.entity.RefreshToken;
import com.fintech.engine.model.entity.User;

import java.util.UUID;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(User user);
    RefreshToken verifyAndRotate(String tokenString);
    void revokeToken(String tokenString);
    void revokeAllUserTokens(UUID userId);
}

