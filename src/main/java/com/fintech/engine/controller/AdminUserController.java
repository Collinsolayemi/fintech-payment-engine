package com.fintech.engine.controller;

import com.fintech.engine.dto.KycApplicationResponse;
import com.fintech.engine.dto.ReviewKycRequest;
import com.fintech.engine.dto.UserProfileResponse;
import com.fintech.engine.model.enums.KycApplicationStatus;
import com.fintech.engine.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin User & KYC Management", description = "Administrative endpoints for listing customer accounts and adjudicating KYC upgrade applications")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Search & List Users", description = "Searches users by email, first name, or last name with pagination")
    @ApiResponse(responseCode = "200", description = "Paginated list of users")
    @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_ADMIN")
    @GetMapping
    public ResponseEntity<Page<UserProfileResponse>> listUsers(
            @Parameter(description = "Search term for email or name") @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.listUsers(search, PageRequest.of(page, size)));
    }

    @Operation(summary = "List KYC Upgrade Applications", description = "Lists submitted KYC applications filtered by status")
    @ApiResponse(responseCode = "200", description = "Paginated list of KYC applications")
    @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_ADMIN")
    @GetMapping("/kyc/applications")
    public ResponseEntity<Page<KycApplicationResponse>> listKycApplications(
            @RequestParam(required = false) KycApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.listKycApplications(status, PageRequest.of(page, size)));
    }

    @Operation(summary = "Review KYC Application", description = "Approves or rejects a KYC upgrade application. Approving immediately raises the user's KYC tier.")
    @ApiResponse(responseCode = "200", description = "Application reviewed and user tier updated")
    @ApiResponse(responseCode = "400", description = "Invalid review status or notes")
    @ApiResponse(responseCode = "404", description = "Application not found")
    @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_ADMIN")
    @PostMapping("/kyc/applications/{id}/review")
    public ResponseEntity<KycApplicationResponse> reviewKycApplication(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewKycRequest request,
            Authentication authentication) {
        String reviewerEmail = authentication != null ? authentication.getName() : "ADMIN";
        KycApplicationResponse response = userService.reviewKycApplication(id, request, reviewerEmail);
        return ResponseEntity.ok(response);
    }
}

