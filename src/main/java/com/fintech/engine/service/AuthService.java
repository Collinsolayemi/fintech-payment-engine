package com.fintech.engine.service;

import com.fintech.engine.dto.*;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    OtpResponse forgotPassword(ForgotPasswordRequest request);
    OtpResponse verifyOtp(VerifyOtpRequest request);
    ApiResponseDto resetPassword(ResetPasswordRequest request);
    OtpResponse resendOtp(ResendOtpRequest request);
    ApiResponseDto changePassword(String userEmail, ChangePasswordRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    ApiResponseDto logout(RefreshTokenRequest request);
}
