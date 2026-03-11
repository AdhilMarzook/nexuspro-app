package com.nexuspro.repository;

import com.nexuspro.model.entity.Post;
import com.nexuspro.model.entity.Post.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    // Feed: posts from connections + own posts, ordered by time
    @Query("""
        SELECT p FROM Post p
        WHERE p.deletedAt IS NULL
          AND (p.authorId = :userId
               OR (p.authorId IN (
                     SELECT CASE WHEN c.requesterId = :userId THEN c.addresseeId ELSE c.requesterId END
                     FROM Connection c
                     WHERE (c.requesterId = :userId OR c.addresseeId = :userId)
                       AND c.status = 'ACCEPTED'
                   )
                   AND p.visibility IN ('PUBLIC', 'CONNECTIONS'))
              )
        ORDER BY p.createdAt DESC
        """)
    Page<Post> findFeedForUser(UUID userId, Pageable pageable);

    // Public feed for discover/explore
    Page<Post> findByVisibilityAndDeletedAtIsNullOrderByCreatedAtDesc(
        Visibility visibility, Pageable pageable);

    Page<Post> findByAuthorIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        UUID authorId, Pageable pageable);

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.id = :id")
    void adjustLikeCount(UUID id, int delta);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :id")
    void adjustCommentCount(UUID id, int delta);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViews(UUID id);
}
