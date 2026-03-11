package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "certifications",
    indexes = {
        @Index(name = "idx_cert_profile", columnList = "profileId")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Certification {

    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false) private UUID profileId;

    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 100) private String issuer;
    @Column                                 private LocalDate issueDate;
    @Column                                 private LocalDate expiryDate;
    @Column(length = 200)                   private String credentialId;
    @Column(length = 500)                   private String credentialUrl;

    @Column @Builder.Default private boolean verified = false;
    @Column private Instant verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CertCategory category;

    @CreationTimestamp private Instant createdAt;

    public enum CertCategory {
        COACHING, PERFORMANCE, MENTAL_HEALTH, ESPORTS_MANAGEMENT,
        GAME_SPECIFIC, BROADCAST, TECHNICAL, OTHER
    }
}
