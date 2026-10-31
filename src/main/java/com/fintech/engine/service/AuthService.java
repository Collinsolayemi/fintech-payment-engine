package com.fintech.engine.service;

import com.fintech.engine.dto.AuthResponse;
import com.fintech.engine.dto.LoginRequest;
import com.fintech.engine.dto.RegisterRequest;

public interface AuthService {

    /**
     * Registers a new customer, hashes their password, automatically provisions their default wallet account,
     * and returns a signed JWT access token.
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates customer credentials and issues a signed JWT access token.
     */
    AuthResponse login(LoginRequest request);
}

