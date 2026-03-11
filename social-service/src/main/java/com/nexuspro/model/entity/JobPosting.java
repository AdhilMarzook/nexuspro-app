package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_postings",
    indexes = {
        @Index(name = "idx_jobs_poster",   columnList = "posterId"),
        @Index(name = "idx_jobs_status",   columnList = "status"),
        @Index(name = "idx_jobs_type",     columnList = "jobType"),
        @Index(name = "idx_jobs_location", columnList = "location"),
        @Index(name = "idx_jobs_created",  columnList = "createdAt")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class JobPosting {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false) private UUID posterId;      // User ID of poster

    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 100) private String organisation;
    @Column(nullable = false, length = 100) private String location;
    @Column                                 private boolean remote;

    @Column(columnDefinition = "TEXT", nullable = false) private String description;
    @Column(columnDefinition = "TEXT")                  private String requirements;
    @Column(columnDefinition = "TEXT")                  private String benefits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExperienceLevel experienceLevel;

    // Salary range
    @Column private Integer salaryMin;
    @Column private Integer salaryMax;
    @Column(length = 3) @Builder.Default private String currency = "GBP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column private Instant expiresAt;
    @Column @Builder.Default private int applicationCount = 0;
    @Column @Builder.Default private int viewCount = 0;

    // Tags for search (stored as JSON array)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    // Game titles relevant to this role
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> gameTitles;

    @Column(length = 500) private String applicationUrl;  // External ATS link
    @Column(length = 255) private String contactEmail;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp  private Instant updatedAt;

    public enum JobType {
        FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP, VOLUNTEER, TRIAL
    }

    public enum ExperienceLevel {
        ENTRY, JUNIOR, MID, SENIOR, LEAD, EXECUTIVE
    }

    public enum Status { ACTIVE, FILLED, EXPIRED, DRAFT, REMOVED }
}
