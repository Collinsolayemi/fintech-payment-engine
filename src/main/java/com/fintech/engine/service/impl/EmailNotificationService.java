package com.fintech.engine.service.impl;

import com.fintech.engine.model.enums.OtpPurpose;
import com.fintech.engine.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    @Override
    public void sendOtpNotification(String email, String otpCode, OtpPurpose purpose) {
        log.info("================================================================================");
        log.info("[NOTIFICATION DISPATCH] Sending Email to: {}", email);
        log.info("[NOTIFICATION DISPATCH] Purpose: {}", purpose);
        log.info("[NOTIFICATION DISPATCH] One-Time Password (OTP): {}", otpCode);
        log.info("[NOTIFICATION DISPATCH] Expiration: 10 minutes");
        log.info("================================================================================");
    }
}

