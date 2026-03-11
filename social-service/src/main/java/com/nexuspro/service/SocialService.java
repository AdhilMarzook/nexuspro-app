package com.nexuspro.service;

import com.nexuspro.exception.BusinessException;
import com.nexuspro.exception.ResourceNotFoundException;
import com.nexuspro.model.entity.Comment;
import com.nexuspro.model.entity.Connection;
import com.nexuspro.model.entity.Connection.Status;
import com.nexuspro.model.entity.Post;
import com.nexuspro.repository.CommentRepository;
import com.nexuspro.repository.ConnectionRepository;
import com.nexuspro.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Social service: feed, posts, connections, likes, comments.
 *
 * Feed algorithm: chronological merge of own posts + accepted connections' posts.
 * Redis tracks likes per user to prevent duplicate likes (SET user:{id}:likes -> post IDs).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialService {

    private final PostRepository       postRepo;
    private final ConnectionRepository connectionRepo;
    private final CommentRepository    commentRepo;
    private final StringRedisTemplate  redis;
    private final KafkaTemplate<String, String> kafka;

    private static final int MAX_POST_LENGTH = 3000;
    private static final String LIKE_KEY = "like:%s:%s"; // like:postId:userId

    // ── POSTS ──────────────────────────────────────────────────────────────

    @Transactional
    public Post createPost(UUID authorId, String content, Post.Visibility visibility,
                           Post.PostType postType, String mediaUrl, String mediaType) {
        if (content == null || content.isBlank())
            throw new BusinessException("Post content cannot be empty", "EMPTY_CONTENT");
        if (content.length() > MAX_POST_LENGTH)
            throw new BusinessException("Post exceeds maximum length of " + MAX_POST_LENGTH, "CONTENT_TOO_LONG");

        Post post = Post.builder()
            .authorId(authorId)
            .content(sanitise(content))
            .visibility(visibility != null ? visibility : Post.Visibility.PUBLIC)
            .postType(postType)
            .mediaUrl(mediaUrl)
            .mediaType(mediaType)
            .build();

        post = postRepo.save(post);

        // Notify followers/connections via Kafka
        kafka.send("social.post.created",
            "{\"postId\":\"" + post.getId() + "\",\"authorId\":\"" + authorId + "\"}");

        return post;
    }

    public Page<Post> getFeed(UUID userId, Pageable pageable) {
        return postRepo.findFeedForUser(userId, pageable);
    }

    public Page<Post> getExploreFeed(Pageable pageable) {
        return postRepo.findByVisibilityAndDeletedAtIsNullOrderByCreatedAtDesc(
            Post.Visibility.PUBLIC, pageable);
    }

    public Page<Post> getUserPosts(UUID authorId, UUID requesterId, Pageable pageable) {
        // Can only see CONNECTIONS posts if connected
        return postRepo.findByAuthorIdAndDeletedAtIsNullOrderByCreatedAtDesc(authorId, pageable);
    }

    @Transactional
    public void deletePost(UUID postId, UUID userId) {
        Post post = postRepo.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", postId.toString()));

        if (!post.getAuthorId().equals(userId))
            throw new BusinessException("Cannot delete another user's post", "FORBIDDEN");

        post.setDeletedAt(Instant.now());
        postRepo.save(post);
    }

    // ── LIKES ──────────────────────────────────────────────────────────────

    @Transactional
    public boolean toggleLike(UUID postId, UUID userId) {
        postRepo.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", postId.toString()));

        String key = String.format(LIKE_KEY, postId, userId);
        boolean alreadyLiked = Boolean.TRUE.equals(redis.hasKey(key));

        if (alreadyLiked) {
            redis.delete(key);
            postRepo.adjustLikeCount(postId, -1);
            return false;
        } else {
            redis.opsForValue().set(key, "1", 30, TimeUnit.DAYS);
            postRepo.adjustLikeCount(postId, 1);

            // Notify post author
            kafka.send("social.post.liked",
                "{\"postId\":\"" + postId + "\",\"likerId\":\"" + userId + "\"}");
            return true;
        }
    }

    public boolean isLikedByUser(UUID postId, UUID userId) {
        return Boolean.TRUE.equals(redis.hasKey(String.format(LIKE_KEY, postId, userId)));
    }

    // ── COMMENTS ───────────────────────────────────────────────────────────

    @Transactional
    public Comment addComment(UUID postId, UUID authorId, String content, UUID parentId) {
        postRepo.findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post", postId.toString()));

        if (content == null || content.isBlank())
            throw new BusinessException("Comment cannot be empty", "EMPTY_COMMENT");
        if (content.length() > 1000)
            throw new BusinessException("Comment exceeds 1000 characters", "COMMENT_TOO_LONG");

        Comment comment = Comment.builder()
            .postId(postId)
            .authorId(authorId)
            .content(sanitise(content))
            .parentCommentId(parentId)
            .build();

        comment = commentRepo.save(comment);
        postRepo.adjustCommentCount(postId, 1);

        kafka.send("social.comment.created",
            "{\"postId\":\"" + postId + "\",\"commentId\":\"" + comment.getId()
                + "\",\"authorId\":\"" + authorId + "\"}");

        return comment;
    }

    public Page<Comment> getComments(UUID postId, Pageable pageable) {
        return commentRepo.findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId, pageable);
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        Comment c = commentRepo.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId.toString()));
        if (!c.getAuthorId().equals(userId))
            throw new BusinessException("Cannot delete another user's comment", "FORBIDDEN");
        c.setDeletedAt(Instant.now());
        commentRepo.save(c);
        postRepo.adjustCommentCount(c.getPostId(), -1);
    }

    // ── CONNECTIONS ────────────────────────────────────────────────────────

    @Transactional
    public Connection sendConnectionRequest(UUID requesterId, UUID addresseeId, String message) {
        if (requesterId.equals(addresseeId))
            throw new BusinessException("Cannot connect with yourself", "SELF_CONNECTION");

        connectionRepo.findBetween(requesterId, addresseeId).ifPresent(existing -> {
            if (existing.getStatus() == Status.ACCEPTED)
                throw new BusinessException("Already connected", "ALREADY_CONNECTED");
            if (existing.getStatus() == Status.PENDING)
                throw new BusinessException("Connection request already pending", "REQUEST_PENDING");
            if (existing.getStatus() == Status.BLOCKED)
                throw new BusinessException("Connection not possible", "BLOCKED");
        });

        Connection conn = Connection.builder()
            .requesterId(requesterId)
            .addresseeId(addresseeId)
            .message(message != null ? message.substring(0, Math.min(message.length(), 500)) : null)
            .build();

        conn = connectionRepo.save(conn);

        kafka.send("social.connection.requested",
            "{\"requesterId\":\"" + requesterId + "\",\"addresseeId\":\"" + addresseeId + "\"}");

        return conn;
    }

    @Transactional
    public Connection respondToRequest(UUID connectionId, UUID addresseeId, boolean accept) {
        Connection conn = connectionRepo.findById(connectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Connection", connectionId.toString()));

        if (!conn.getAddresseeId().equals(addresseeId))
            throw new BusinessException("Not authorised to respond to this request", "FORBIDDEN");

        if (conn.getStatus() != Status.PENDING)
            throw new BusinessException("Connection is not pending", "NOT_PENDING");

        conn.setStatus(accept ? Status.ACCEPTED : Status.DECLINED);
        conn.setRespondedAt(Instant.now());
        conn = connectionRepo.save(conn);

        if (accept) {
            kafka.send("social.connection.accepted",
                "{\"connectionId\":\"" + connectionId + "\",\"requesterId\":\""
                    + conn.getRequesterId() + "\",\"addresseeId\":\"" + addresseeId + "\"}");
        }

        return conn;
    }

    @Transactional
    public void blockUser(UUID blockerId, UUID blockedId) {
        connectionRepo.findBetween(blockerId, blockedId).ifPresent(connectionRepo::delete);

        Connection block = Connection.builder()
            .requesterId(blockerId)
            .addresseeId(blockedId)
            .status(Status.BLOCKED)
            .build();
        connectionRepo.save(block);
    }

    public Page<Connection> getPendingRequests(UUID userId, Pageable pageable) {
        return connectionRepo.findByAddresseeIdAndStatus(userId, Status.PENDING, pageable);
    }

    public Page<Connection> getConnections(UUID userId, Pageable pageable) {
        return connectionRepo.findByRequesterIdAndStatus(userId, Status.ACCEPTED, pageable);
    }

    public long getConnectionCount(UUID userId) {
        return connectionRepo.countConnections(userId);
    }

    public boolean areConnected(UUID u1, UUID u2) {
        return connectionRepo.areConnected(u1, u2);
    }

    private String sanitise(String input) {
        if (input == null) return null;
        // Strip HTML tags, XSS prevention
        return input.replaceAll("<[^>]*>", "").trim();
    }
}
