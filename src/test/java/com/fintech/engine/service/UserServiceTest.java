package com.fintech.engine.service;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.DuplicateKycApplicationException;
import com.fintech.engine.exception.InvalidKycTierException;
import com.fintech.engine.model.entity.KycApplication;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.*;
import com.fintech.engine.repository.KycApplicationRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KycApplicationRepository kycApplicationRepository;

    @Mock
    private KycLimitService kycLimitService;

    private UserServiceImpl userService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, kycApplicationRepository, kycLimitService);
        userId = UUID.randomUUID();

        user = new User("customer@fintech.com", "hashedPassword", "John", "Doe");
        user.setId(userId);
        user.setKycTier(1);
        user.setRole("ROLE_USER");
    }

    @Test
    @DisplayName("getProfile - Should return user profile enriched with limits")
    void shouldReturnProfileWithLimits() {
        when(userRepository.findByEmail("customer@fintech.com")).thenReturn(Optional.of(user));
        when(kycLimitService.getDailyUsedToday(userId)).thenReturn(new BigDecimal("150.00"));
        when(kycLimitService.getDailyRemainingToday(user)).thenReturn(new BigDecimal("850.00"));

        UserProfileResponse response = userService.getProfile("customer@fintech.com");

        assertEquals("customer@fintech.com", response.email());
        assertEquals(1, response.kycTier());
        assertEquals(0, new BigDecimal("500.00").compareTo(response.singleTransactionLimit()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(response.dailyLimit()));
        assertEquals(0, new BigDecimal("150.00").compareTo(response.dailyUsedToday()));
        assertEquals(0, new BigDecimal("850.00").compareTo(response.dailyRemainingToday()));
    }

    @Test
    @DisplayName("updateProfile - Should update contact details")
    void shouldUpdateProfile() {
        when(userRepository.findByEmail("customer@fintech.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest(
                "Johnny", "Doe", "+1234567890", "123 Main St", "Metropolis", "USA", "10001", LocalDate.of(1990, 1, 1));

        UserProfileResponse response = userService.updateProfile("customer@fintech.com", request);

        assertEquals("Johnny", response.firstName());
        assertEquals("+1234567890", response.phoneNumber());
        assertEquals("123 Main St", response.addressLine());
        assertEquals("Metropolis", response.city());
    }

    @Test
    @DisplayName("submitKycApplication - Should submit valid Tier 2 application")
    void shouldSubmitValidKycApplication() {
        when(userRepository.findByEmail("customer@fintech.com")).thenReturn(Optional.of(user));
        when(kycApplicationRepository.existsByUserIdAndStatus(userId, KycApplicationStatus.SUBMITTED)).thenReturn(false);
        when(kycApplicationRepository.save(any(KycApplication.class))).thenAnswer(inv -> {
            KycApplication app = inv.getArgument(0);
            app.setId(UUID.randomUUID());
            return app;
        });

        SubmitKycRequest request = new SubmitKycRequest(2, IdType.PASSPORT, "A12345678", "https://s3.example.com/doc.jpg");

        KycApplicationResponse response = userService.submitKycApplication("customer@fintech.com", request);

        assertEquals(2, response.targetTier());
        assertEquals(IdType.PASSPORT, response.idType());
        assertEquals("A12345678", response.idNumber());
        assertEquals(KycApplicationStatus.SUBMITTED, response.status());
        assertEquals(KycStatus.PENDING, user.getKycStatus());
    }

    @Test
    @DisplayName("submitKycApplication - Should reject non-sequential tier jump (Tier 1 -> Tier 3)")
    void shouldRejectNonSequentialTierJump() {
        when(userRepository.findByEmail("customer@fintech.com")).thenReturn(Optional.of(user));

        SubmitKycRequest request = new SubmitKycRequest(3, IdType.PASSPORT, "A12345678", null);

        InvalidKycTierException ex = assertThrows(InvalidKycTierException.class, () ->
                userService.submitKycApplication("customer@fintech.com", request));

        assertTrue(ex.getMessage().contains("Tier progression must be sequential"));
    }

    @Test
    @DisplayName("submitKycApplication - Should reject duplicate active application")
    void shouldRejectDuplicatePendingApplication() {
        when(userRepository.findByEmail("customer@fintech.com")).thenReturn(Optional.of(user));
        when(kycApplicationRepository.existsByUserIdAndStatus(userId, KycApplicationStatus.SUBMITTED)).thenReturn(true);

        SubmitKycRequest request = new SubmitKycRequest(2, IdType.NATIONAL_ID, "NIN-1234", null);

        assertThrows(DuplicateKycApplicationException.class, () ->
                userService.submitKycApplication("customer@fintech.com", request));
    }

    @Test
    @DisplayName("reviewKycApplication - APPROVE should upgrade user KYC tier")
    void shouldApproveAndUpgradeUserTier() {
        UUID appId = UUID.randomUUID();
        KycApplication application = new KycApplication(user, 2, IdType.PASSPORT, "A12345678", null);
        application.setId(appId);

        when(kycApplicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(kycApplicationRepository.save(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewKycRequest review = new ReviewKycRequest(KycApplicationStatus.APPROVED, null);

        KycApplicationResponse response = userService.reviewKycApplication(appId, review, "admin@fintech.com");

        assertEquals(KycApplicationStatus.APPROVED, response.status());
        assertEquals(2, user.getKycTier());
        assertEquals(KycStatus.APPROVED, user.getKycStatus());
        assertEquals("admin@fintech.com", response.reviewedBy());
    }

    @Test
    @DisplayName("reviewKycApplication - REJECT should mark application rejected and preserve tier")
    void shouldRejectApplicationAndPreserveTier() {
        UUID appId = UUID.randomUUID();
        KycApplication application = new KycApplication(user, 2, IdType.PASSPORT, "A12345678", null);
        application.setId(appId);

        when(kycApplicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(kycApplicationRepository.save(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewKycRequest review = new ReviewKycRequest(KycApplicationStatus.REJECTED, "Document blurred");

        KycApplicationResponse response = userService.reviewKycApplication(appId, review, "admin@fintech.com");

        assertEquals(KycApplicationStatus.REJECTED, response.status());
        assertEquals(1, user.getKycTier());
        assertEquals(KycStatus.REJECTED, user.getKycStatus());
        assertEquals("Document blurred", response.rejectionReason());
    }
}

