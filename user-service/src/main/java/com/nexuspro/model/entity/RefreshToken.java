package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted refresh tokens for rotation and revocation.
 * When a refresh token is used, the old one is deleted and a new one issued.
 * This detects token theft: if an already-used token is presented, revoke ALL tokens for that user.
 */
@Entity
@Table(name = "refresh_tokens",
    indexes = {
        @Index(name = "idx_refresh_token_hash", columnList = "tokenHash", unique = true),
        @Index(name = "idx_refresh_token_user", columnList = "userId")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshToken {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;   // SHA-256 hash of the token — never store raw

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(length = 45)
    private String issuedToIp;

    @Column(length = 255)
    private String userAgent;

    @CreationTimestamp
    private Instant createdAt;
}
