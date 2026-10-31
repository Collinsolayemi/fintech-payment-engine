package com.fintech.engine.controller;

import com.fintech.engine.dto.*;
import com.fintech.engine.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication & Security", description = "Endpoints for customer registration, login, OTP dispatch & verification, password reset, and session management")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register Customer", description = "Creates a new user profile, hashes password, automatically provisions their default wallet account, and returns JWT access + refresh tokens.")
    @ApiResponse(responseCode = "201", description = "User registered and wallet auto-provisioned")
    @ApiResponse(responseCode = "400", description = "Invalid registration parameters")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Authenticate (Login)", description = "Authenticates user credentials and issues a signed JWT Bearer token and refresh token.")
    @ApiResponse(responseCode = "200", description = "Successfully authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Forgot Password (Request OTP)", description = "Generates and sends a secure 6-digit OTP code to the user's email address.")
    @ApiResponse(responseCode = "200", description = "OTP generated and notification sent")
    @ApiResponse(responseCode = "404", description = "Email not found")
    @ApiResponse(responseCode = "429", description = "Rate limit / cooldown active")
    @PostMapping("/forgot-password")
    public ResponseEntity<OtpResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        OtpResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Verify OTP", description = "Validates the 6-digit OTP code against the latest dispatched token within the expiration window.")
    @ApiResponse(responseCode = "200", description = "OTP verified successfully; returns reset token")
    @ApiResponse(responseCode = "400", description = "Invalid OTP code")
    @ApiResponse(responseCode = "410", description = "OTP expired")
    @ApiResponse(responseCode = "429", description = "Maximum verification attempts exceeded")
    @PostMapping("/verify-otp")
    public ResponseEntity<OtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        OtpResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Reset Password", description = "Resets user password using the verified reset token and invalidates all existing user sessions.")
    @ApiResponse(responseCode = "200", description = "Password reset successfully")
    @ApiResponse(responseCode = "400", description = "Invalid or unverified reset token")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponseDto> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ApiResponseDto response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Resend OTP", description = "Dispatches a fresh OTP code respecting rate limiting cooldown.")
    @ApiResponse(responseCode = "200", description = "Fresh OTP dispatched")
    @ApiResponse(responseCode = "429", description = "Cooldown active")
    @PostMapping("/resend-otp")
    public ResponseEntity<OtpResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        OtpResponse response = authService.resendOtp(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Change Password", description = "Updates password for currently authenticated user and revokes other active sessions.")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponse(responseCode = "200", description = "Password changed successfully")
    @ApiResponse(responseCode = "401", description = "Current password incorrect or unauthenticated")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponseDto> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        ApiResponseDto response = authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Rotate Refresh Token", description = "Exchanges a valid refresh token for a newly issued access token and rotated refresh token.")
    @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully")
    @ApiResponse(responseCode = "401", description = "Invalid, revoked, or expired refresh token")
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout", description = "Revokes the active refresh token and terminates the session.")
    @ApiResponse(responseCode = "200", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto> logout(@Valid @RequestBody RefreshTokenRequest request) {
        ApiResponseDto response = authService.logout(request);
        return ResponseEntity.ok(response);
    }
}
