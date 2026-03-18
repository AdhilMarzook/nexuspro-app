package com.nexuspro.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournaments",
    indexes = {
        @Index(name = "idx_tourney_profile",  columnList = "profileId"),
        @Index(name = "idx_tourney_date",     columnList = "eventDate"),
        @Index(name = "idx_tourney_verified", columnList = "verified")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Tournament {

    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false) private UUID profileId;

    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 50)  private String game;
    @Column(nullable = false)               private LocalDate eventDate;
    @Column(nullable = false, length = 100) private String placement;   // "1st Place", "Top 8"

    @Column private Integer totalTeams;
    @Column private Integer prizeAmountGbp;
    @Column(length = 100) private String organiser;
    @Column(length = 100) private String teamName;
    @Column(length = 50)  private String format;  // Online, LAN, Regional, National

    @Column @Builder.Default private boolean verified = false;
    @Column private String verificationSource; // Battlefy ID, Toornament ID, manual…
    @Column private Instant verifiedAt;

    // Career points awarded for this result
    @Column @Builder.Default private int careerPoints = 0;

    @Column(length = 500) private String notes;
    @Column(length = 500) private String proofUrl;   // Screenshot / external link

    @CreationTimestamp private Instant createdAt;
}
