package com.fintech.engine.service;

import com.fintech.engine.dto.*;
import com.fintech.engine.model.enums.KycApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserProfileResponse getProfile(String email);
    UserProfileResponse updateProfile(String email, UpdateProfileRequest request);
    KycApplicationResponse submitKycApplication(String email, SubmitKycRequest request);
    List<KycApplicationResponse> getMyKycApplications(String email);

    // Admin operations
    Page<UserProfileResponse> listUsers(String search, Pageable pageable);
    Page<KycApplicationResponse> listKycApplications(KycApplicationStatus status, Pageable pageable);
    KycApplicationResponse reviewKycApplication(UUID applicationId, ReviewKycRequest request, String reviewerEmail);
}

