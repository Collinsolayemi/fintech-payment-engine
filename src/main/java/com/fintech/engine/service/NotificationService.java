package com.fintech.engine.service;

import com.fintech.engine.model.enums.OtpPurpose;

public interface NotificationService {
    void sendOtpNotification(String email, String otpCode, OtpPurpose purpose);
}

