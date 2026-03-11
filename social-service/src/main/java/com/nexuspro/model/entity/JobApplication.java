package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_applications",
    uniqueConstraints = @UniqueConstraint(columnNames = {"jobId", "applicantId"}),
    indexes = {
        @Index(name = "idx_app_job",       columnList = "jobId"),
        @Index(name = "idx_app_applicant", columnList = "applicantId"),
        @Index(name = "idx_app_status",    columnList = "status")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class JobApplication {

    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false) private UUID jobId;
    @Column(nullable = false) private UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.APPLIED;

    @Column(columnDefinition = "TEXT") private String coverLetter;
    @Column(length = 500)             private String cvUrl;       // S3 link
    @Column(length = 500)             private String portfolioUrl;

    // Recruiter notes — never exposed to applicant
    @Column(columnDefinition = "TEXT") private String recruiterNotes;

    @CreationTimestamp private Instant appliedAt;
    @UpdateTimestamp  private Instant updatedAt;
    @Column           private Instant reviewedAt;

    public enum Status {
        APPLIED, UNDER_REVIEW, SHORTLISTED, INTERVIEW_SCHEDULED,
        OFFER_MADE, ACCEPTED, REJECTED, WITHDRAWN
    }
}
