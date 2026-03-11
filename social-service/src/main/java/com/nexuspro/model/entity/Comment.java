package com.nexuspro.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments",
    indexes = {
        @Index(name = "idx_comments_post",   columnList = "postId"),
        @Index(name = "idx_comments_author", columnList = "authorId"),
        @Index(name = "idx_comments_parent", columnList = "parentCommentId")
    })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Comment {

    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false)              private UUID postId;
    @Column(nullable = false)              private UUID authorId;
    @Column(columnDefinition = "TEXT", nullable = false) private String content;

    // For nested replies
    @Column private UUID parentCommentId;

    @Column @Builder.Default private int likeCount = 0;
    @Column private Instant deletedAt;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp  private Instant updatedAt;
}
