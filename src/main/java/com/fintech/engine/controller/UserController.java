package com.fintech.engine.controller;

import com.fintech.engine.dto.*;
import com.fintech.engine.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User Profile & KYC", description = "Endpoints for managing user profiles, viewing tier limits, and submitting KYC upgrade requests")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get Current User Profile", description = "Retrieves profile details, verification status, and daily limits for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Profile details retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required")
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        return ResponseEntity.ok(userService.getProfile(extractEmail(authentication)));
    }

    @Operation(summary = "Update User Profile", description = "Updates personal contact and address details")
    @ApiResponse(responseCode = "200", description = "Profile updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid profile payload")
    @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT token required")
    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(extractEmail(authentication), request));
    }

    @Operation(summary = "Submit KYC Tier Upgrade Request", description = "Submits government ID or document verification details to request an account upgrade to Tier 2 or Tier 3")
    @ApiResponse(responseCode = "201", description = "KYC application submitted successfully")
    @ApiResponse(responseCode = "400", description = "Invalid tier progression or validation failure")
    @ApiResponse(responseCode = "409", description = "A verification application is already pending")
    @PostMapping("/kyc/upgrade")
    public ResponseEntity<KycApplicationResponse> submitKycApplication(
            Authentication authentication,
            @Valid @RequestBody SubmitKycRequest request) {
        KycApplicationResponse response = userService.submitKycApplication(extractEmail(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List My KYC Applications", description = "Retrieves the historical status of all verification requests for the authenticated user")
    @ApiResponse(responseCode = "200", description = "List of KYC applications")
    @GetMapping("/kyc/applications")
    public ResponseEntity<List<KycApplicationResponse>> getMyKycApplications(Authentication authentication) {
        return ResponseEntity.ok(userService.getMyKycApplications(extractEmail(authentication)));
    }

    private String extractEmail(Authentication authentication) {
        return authentication != null ? authentication.getName() : "user@example.com";
    }
}

