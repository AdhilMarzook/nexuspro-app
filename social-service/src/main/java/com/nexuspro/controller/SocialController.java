package com.nexuspro.controller;

import com.nexuspro.dto.request.PostRequest;
import com.nexuspro.dto.response.ApiResponse;
import com.nexuspro.model.entity.Comment;
import com.nexuspro.model.entity.Connection;
import com.nexuspro.model.entity.Post;
import com.nexuspro.service.SocialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    // ── FEED ────────────────────────────────────────────────────────────────

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<Page<Post>>> getFeed(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            socialService.getFeed(UUID.fromString(userId), pageable)));
    }

    @GetMapping("/explore")
    public ResponseEntity<ApiResponse<Page<Post>>> getExploreFeed(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(socialService.getExploreFeed(pageable)));
    }

    // ── POSTS ────────────────────────────────────────────────────────────────

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<Post>> createPost(
            @Valid @RequestBody PostRequest req,
            @RequestHeader("X-User-Id") String userId) {
        Post post = socialService.createPost(
            UUID.fromString(userId),
            req.getContent(),
            req.getVisibility(),
            req.getPostType(),
            req.getMediaUrl(),
            req.getMediaType()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(post));
    }

    @GetMapping("/users/{targetUserId}/posts")
    public ResponseEntity<ApiResponse<Page<Post>>> getUserPosts(
            @PathVariable UUID targetUserId,
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 15) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            socialService.getUserPosts(targetUserId, UUID.fromString(userId), pageable)));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable UUID postId,
            @RequestHeader("X-User-Id") String userId) {
        socialService.deletePost(postId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(null, "Post deleted"));
    }

    // ── LIKES ────────────────────────────────────────────────────────────────

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLike(
            @PathVariable UUID postId,
            @RequestHeader("X-User-Id") String userId) {
        boolean liked = socialService.toggleLike(postId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("liked", liked)));
    }

    // ── COMMENTS ─────────────────────────────────────────────────────────────

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<Comment>> addComment(
            @PathVariable UUID postId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") String userId) {
        String content  = body.get("content");
        String parentId = body.get("parentCommentId");
        Comment comment = socialService.addComment(
            postId, UUID.fromString(userId), content,
            parentId != null ? UUID.fromString(parentId) : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(comment));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<Page<Comment>>> getComments(
            @PathVariable UUID postId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(socialService.getComments(postId, pageable)));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID commentId,
            @RequestHeader("X-User-Id") String userId) {
        socialService.deleteComment(commentId, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── CONNECTIONS ──────────────────────────────────────────────────────────

    @PostMapping("/connections/request/{targetUserId}")
    public ResponseEntity<ApiResponse<Connection>> sendRequest(
            @PathVariable UUID targetUserId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader("X-User-Id") String userId) {
        String msg = body != null ? body.get("message") : null;
        Connection conn = socialService.sendConnectionRequest(
            UUID.fromString(userId), targetUserId, msg);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(conn));
    }

    @PatchMapping("/connections/{connectionId}/respond")
    public ResponseEntity<ApiResponse<Connection>> respond(
            @PathVariable UUID connectionId,
            @RequestParam boolean accept,
            @RequestHeader("X-User-Id") String userId) {
        Connection conn = socialService.respondToRequest(
            connectionId, UUID.fromString(userId), accept);
        return ResponseEntity.ok(ApiResponse.ok(conn));
    }

    @PostMapping("/connections/block/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> blockUser(
            @PathVariable UUID targetUserId,
            @RequestHeader("X-User-Id") String userId) {
        socialService.blockUser(UUID.fromString(userId), targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(null, "User blocked"));
    }

    @GetMapping("/connections/pending")
    public ResponseEntity<ApiResponse<Page<Connection>>> getPending(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            socialService.getPendingRequests(UUID.fromString(userId), pageable)));
    }

    @GetMapping("/connections")
    public ResponseEntity<ApiResponse<Page<Connection>>> getConnections(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            socialService.getConnections(UUID.fromString(userId), pageable)));
    }

    @GetMapping("/connections/count/{targetUserId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConnectionCount(
            @PathVariable UUID targetUserId) {
        long count = socialService.getConnectionCount(targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
    }
}
