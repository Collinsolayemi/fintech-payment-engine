package com.fintech.engine.service.impl;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.DuplicateKycApplicationException;
import com.fintech.engine.exception.InvalidKycTierException;
import com.fintech.engine.exception.KycApplicationNotFoundException;
import com.fintech.engine.exception.UserNotFoundException;
import com.fintech.engine.model.entity.KycApplication;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.KycApplicationStatus;
import com.fintech.engine.model.enums.KycStatus;
import com.fintech.engine.model.enums.KycTierPolicy;
import com.fintech.engine.repository.KycApplicationRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.KycLimitService;
import com.fintech.engine.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final KycApplicationRepository kycApplicationRepository;
    private final KycLimitService kycLimitService;

    public UserServiceImpl(UserRepository userRepository,
                           KycApplicationRepository kycApplicationRepository,
                           KycLimitService kycLimitService) {
        this.userRepository = userRepository;
        this.kycApplicationRepository = kycApplicationRepository;
        this.kycLimitService = kycLimitService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = findUserByEmail(email);
        return mapToProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = findUserByEmail(email);

        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName().trim());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().trim());
        }
        if (request.addressLine() != null) {
            user.setAddressLine(request.addressLine().trim());
        }
        if (request.city() != null) {
            user.setCity(request.city().trim());
        }
        if (request.country() != null) {
            user.setCountry(request.country().trim());
        }
        if (request.postalCode() != null) {
            user.setPostalCode(request.postalCode().trim());
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }

        user = userRepository.save(user);
        log.info("Profile updated successfully for user {}", email);
        return mapToProfileResponse(user);
    }

    @Override
    @Transactional
    public KycApplicationResponse submitKycApplication(String email, SubmitKycRequest request) {
        User user = findUserByEmail(email);

        int currentTier = user.getKycTier();
        int targetTier = request.targetTier();

        if (targetTier <= currentTier) {
            throw new InvalidKycTierException(
                    String.format("User is already at Tier %d. Target tier must be higher than current tier.", currentTier));
        }

        if (targetTier > currentTier + 1) {
            throw new InvalidKycTierException(
                    String.format("Tier progression must be sequential. Current: Tier %d, Requested: Tier %d", currentTier, targetTier));
        }

        if (kycApplicationRepository.existsByUserIdAndStatus(user.getId(), KycApplicationStatus.SUBMITTED)) {
            throw new DuplicateKycApplicationException("An active KYC verification application is already under review");
        }

        KycApplication application = new KycApplication(
                user,
                targetTier,
                request.idType(),
                request.idNumber().trim(),
                request.documentUrl() != null ? request.documentUrl().trim() : null
        );

        user.setKycStatus(KycStatus.PENDING);
        userRepository.save(user);

        application = kycApplicationRepository.save(application);
        log.info("KYC tier upgrade application submitted by user {} for Tier {}", email, targetTier);

        return mapToApplicationResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KycApplicationResponse> getMyKycApplications(String email) {
        User user = findUserByEmail(email);
        return kycApplicationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::mapToApplicationResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> listUsers(String search, Pageable pageable) {
        Page<User> users;
        if (search != null && !search.isBlank()) {
            String s = search.trim();
            users = userRepository.findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                    s, s, s, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(this::mapToProfileResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<KycApplicationResponse> listKycApplications(KycApplicationStatus status, Pageable pageable) {
        Page<KycApplication> page = (status != null)
                ? kycApplicationRepository.findByStatusOrderByCreatedAtAsc(status, pageable)
                : kycApplicationRepository.findAllByOrderByCreatedAtDesc(pageable);
        return page.map(this::mapToApplicationResponse);
    }

    @Override
    @Transactional
    public KycApplicationResponse reviewKycApplication(UUID applicationId, ReviewKycRequest request, String reviewerEmail) {
        KycApplication application = kycApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new KycApplicationNotFoundException(applicationId));

        if (application.getStatus() != KycApplicationStatus.SUBMITTED) {
            throw new IllegalStateException("Application is already finalized with status: " + application.getStatus());
        }

        User user = application.getUser();

        if (request.status() == KycApplicationStatus.APPROVED) {
            application.approve(reviewerEmail);
            user.setKycTier(application.getTargetTier());
            user.setKycStatus(KycStatus.APPROVED);
            log.info("KYC application {} APPROVED by {}. User {} upgraded to Tier {}",
                    applicationId, reviewerEmail, user.getEmail(), application.getTargetTier());
        } else if (request.status() == KycApplicationStatus.REJECTED) {
            application.reject(reviewerEmail, request.rejectionReason());
            user.setKycStatus(KycStatus.REJECTED);
            log.info("KYC application {} REJECTED by {}. Reason: {}",
                    applicationId, reviewerEmail, request.rejectionReason());
        } else {
            throw new IllegalArgumentException("Review status must be either APPROVED or REJECTED");
        }

        userRepository.save(user);
        application = kycApplicationRepository.save(application);
        return mapToApplicationResponse(application);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found for email: " + email));
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        KycTierPolicy policy = KycTierPolicy.fromTier(user.getKycTier());
        BigDecimal usedToday = kycLimitService.getDailyUsedToday(user.getId());
        BigDecimal remaining = kycLimitService.getDailyRemainingToday(user);

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getKycTier(),
                policy.getDescription(),
                policy.getSingleTransactionLimit(),
                policy.getDailyLimit(),
                usedToday,
                remaining,
                user.getKycStatus(),
                user.getPhoneNumber(),
                user.getAddressLine(),
                user.getCity(),
                user.getCountry(),
                user.getPostalCode(),
                user.getDateOfBirth(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private KycApplicationResponse mapToApplicationResponse(KycApplication app) {
        return new KycApplicationResponse(
                app.getId(),
                app.getUser().getId(),
                app.getUser().getEmail(),
                app.getTargetTier(),
                app.getIdType(),
                app.getIdNumber(),
                app.getDocumentUrl(),
                app.getStatus(),
                app.getRejectionReason(),
                app.getReviewedBy(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}

