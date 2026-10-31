package com.fintech.engine.security;

import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public interface JwtService {
    String generateToken(String email, String role, UUID userId);
    String extractEmail(String token);
    UUID extractUserId(String token);
    boolean isTokenValid(String token, UserDetails userDetails);
    long getExpirationMillis();
}
