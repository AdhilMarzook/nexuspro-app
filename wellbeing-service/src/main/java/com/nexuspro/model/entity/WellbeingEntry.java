package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "wellbeing_entries",
    indexes = {
        @Index(name = "idx_wb_user_date", columnList = "userId, entryDate"),
        @Index(name = "idx_wb_user_id",   columnList = "userId")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WellbeingEntry {

    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private LocalDate entryDate;

    // Daily metrics (all 0–100)
    @Column private Integer moodScore;              // Mood rating 1–5 converted to 0–100
    @Column private Integer sleepHours;             // Actual hours × 10 for precision
    @Column private Integer sleepQuality;           // 0–100
    @Column private Integer trainingHours;          // Minutes / 6 = 0–100
    @Column private Integer stressLevel;            // 0–100 (higher = worse)
    @Column private Integer energyLevel;            // 0–100
    @Column private Integer focusLevel;             // 0–100
    @Column private Integer physicalPain;           // 0–100 (0 = no pain)

    // Weekly metrics
    @Column private Integer socialInteraction;      // 0–100
    @Column private Integer workLifeBalance;        // 0–100

    // AI-computed scores (updated nightly)
    @Column private Integer burnoutRiskScore;       // 0–100
    @Column private Integer wellbeingIndex;         // Composite 0–100

    // Free-text journal
    @Column(columnDefinition = "TEXT") private String journal;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BurnoutLevel burnoutLevel;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp  private Instant updatedAt;

    public enum BurnoutLevel { LOW, MODERATE, HIGH, CRITICAL }
}
