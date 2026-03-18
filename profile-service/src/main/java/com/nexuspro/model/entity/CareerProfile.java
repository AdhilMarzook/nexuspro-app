package com.nexuspro.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "career_profiles",
    indexes = {
        @Index(name = "idx_profiles_user_id",  columnList = "userId", unique = true),
        @Index(name = "idx_profiles_username", columnList = "username")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CareerProfile {

    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(length = 100)   private String displayName;
    @Column(columnDefinition = "TEXT") private String bio;
    @Column(length = 100)   private String primaryGame;
    @Column(length = 50)    private String primaryRole;   // IGL, Support, Fragger…
    @Column(length = 50)    private String countryCode;
    @Column(length = 255)   private String avatarUrl;
    @Column(length = 255)   private String bannerUrl;
    @Column(length = 50)    private String currentTeam;
    @Column(length = 50)    private String currentTier;   // Professional, Semi-Pro, Amateur…

    // Social links
    @Column(length = 255) private String twitterUrl;
    @Column(length = 255) private String twitchUrl;
    @Column(length = 255) private String youtubeUrl;
    @Column(length = 255) private String linkedinUrl;
    @Column(length = 255) private String portfolioUrl;

    // NexusPro career score (computed from tournaments, certs, completeness)
    @Column @Builder.Default private int careerScore = 0;

    // Skills stored as JSONB: { "mechanics": 91, "leadership": 65, … }
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, Integer> skills;

    // Verified by NexusPro
    @Column @Builder.Default private boolean passportVerified = false;
    @Column private Instant verifiedAt;

    // Profile completion percentage (0–100)
    @Column @Builder.Default private int completionPct = 0;

    // Plan tier
    @Column(length = 20) @Builder.Default private String plan = "FREE";

    // Visibility
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp  private Instant updatedAt;

    public enum Visibility { PUBLIC, CONNECTIONS, PRIVATE }
}
