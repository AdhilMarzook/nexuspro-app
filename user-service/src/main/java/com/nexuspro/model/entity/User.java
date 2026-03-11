package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Core user account entity.
 *
 * Security notes:
 * - Password stored as bcrypt hash (cost factor 12)
 * - Email stored lowercase (normalised on save)
 * - Account lockout after 5 failed attempts (lockoutUntil)
 * - MFA support via TOTP secret
 * - Soft-delete via deletedAt for GDPR right-to-erasure audit trail
 * - emailVerified prevents unverified accounts from accessing the platform
 */
@Entity
@Table(name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_username", columnList = "username", unique = true)
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class User {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.PLAYER;

    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(length = 64)
    private String emailVerificationToken;

    @Column
    private Instant emailVerificationExpiry;

    // MFA
    @Column
    @Builder.Default
    private boolean mfaEnabled = false;

    @Column(length = 64)
    private String mfaSecret;

    // Account lockout
    @Column
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column
    private Instant lockoutUntil;

    // Password reset
    @Column(length = 64)
    private String passwordResetToken;

    @Column
    private Instant passwordResetExpiry;

    // OAuth2 social login
    @Column(length = 50)
    private String oauthProvider;

    @Column(length = 255)
    private String oauthProviderId;

    // Subscription
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Plan plan = Plan.FREE;

    @Column
    private Instant planExpiresAt;

    // Timestamps
    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Column
    private Instant lastLoginAt;

    // GDPR soft delete
    @Column
    private Instant deletedAt;

    @Column(length = 45)
    private String lastLoginIp;

    public boolean isLocked() {
        return lockoutUntil != null && Instant.now().isBefore(lockoutUntil);
    }

    public boolean isActive() {
        return deletedAt == null && emailVerified;
    }

    public enum Role { PLAYER, COACH, ORG_ADMIN, PLATFORM_ADMIN }
    public enum Plan { FREE, PRO, ORGANISATION, UNIVERSITY }
}
