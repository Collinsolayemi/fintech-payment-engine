package com.fintech.engine.service;

import com.fintech.engine.dto.OtpResponse;
import com.fintech.engine.model.enums.OtpPurpose;

public interface OtpService {
    OtpResponse sendOtp(String email, OtpPurpose purpose);
    OtpResponse verifyOtp(String email, String otpCode, OtpPurpose purpose);
    void validateResetToken(String email, String resetToken);
    void consumeResetToken(String email, String resetToken);
}

