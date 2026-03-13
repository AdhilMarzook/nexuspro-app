package com.nexuspro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EmailService — direct email sending stub.
 * In production, all email is dispatched asynchronously via Kafka
 * to the notification-service. This class exists as a fallback
 * for any synchronous email needs.
 */
@Slf4j
@Service
public class EmailService {

    public void sendVerificationEmail(String to, String token) {
        // Handled by notification-service via Kafka topic: user.email.verification
        log.debug("Verification email queued for: {}", to);
    }

    public void sendPasswordResetEmail(String to, String token) {
        // Handled by notification-service via Kafka topic: user.password.reset
        log.debug("Password reset email queued for: {}", to);
    }

    public void sendWelcomeEmail(String to, String username) {
        // Handled by notification-service via Kafka topic: user.verified
        log.debug("Welcome email queued for: {}", to);
    }
}
