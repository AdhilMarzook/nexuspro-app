package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts",
    indexes = {
        @Index(name = "idx_posts_author", columnList = "authorId"),
        @Index(name = "idx_posts_created", columnList = "createdAt"),
        @Index(name = "idx_posts_visibility", columnList = "visibility")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Post {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PostType postType;

    @Column(length = 255)
    private String mediaUrl;

    @Column(length = 20)
    private String mediaType;    // IMAGE | VIDEO | DOCUMENT

    // Engagement metrics
    @Column @Builder.Default private int likeCount    = 0;
    @Column @Builder.Default private int commentCount = 0;
    @Column @Builder.Default private int shareCount   = 0;
    @Column @Builder.Default private int viewCount    = 0;

    // Soft delete
    @Column private Instant deletedAt;

    // Moderation
    @Column @Builder.Default private boolean flagged = false;
    @Column private String flagReason;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp  private Instant updatedAt;

    public enum Visibility { PUBLIC, CONNECTIONS, PRIVATE }
    public enum PostType { GENERAL, ACHIEVEMENT, JOB_SEEKING, HIRING, TOURNAMENT_RESULT, MILESTONE }
}
