package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "contracts",
    indexes = {
        @Index(name = "idx_contracts_user_id", columnList = "userId"),
        @Index(name = "idx_contracts_created_at", columnList = "createdAt")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Contract {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 10)
    private String fileType;    // PDF | DOCX

    // Raw extracted text — never stored if user requests deletion (GDPR)
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    // AI analysis results stored as JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ContractAnalysis analysis;

    @Column
    private Integer riskScore;       // 0–100

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RiskLevel riskLevel;     // LOW | MEDIUM | HIGH | CRITICAL

    @Column
    private Integer totalClauses;

    @Column
    private Integer flaggedClauses;

    // Cost tracking for AI usage
    @Column
    private Integer aiInputTokens;

    @Column
    private Integer aiOutputTokens;

    @CreationTimestamp
    private Instant createdAt;

    @Column
    private Instant analysedAt;

    // GDPR: user can request text deletion post-analysis
    @Column
    private Instant textDeletedAt;

    public enum AnalysisStatus { PENDING, PROCESSING, COMPLETED, FAILED }
    public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ContractAnalysis {
        private String summary;
        private List<ClauseFlag> flags;
        private List<String> positiveFindings;
        private String overallRecommendation;
        private String ukLawCompliance;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ClauseFlag {
        private String clauseReference;  // e.g. "Clause 4.2"
        private String severity;         // CRITICAL | WARNING | INFO | OK
        private String title;
        private String description;
        private String legalContext;
        private String recommendedAction;
        private String exactQuote;       // The problematic text from contract
    }
}
