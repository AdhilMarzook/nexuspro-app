package com.nexuspro.controller;

import com.nexuspro.dto.request.AuthRequests.*;
import com.nexuspro.dto.response.ApiResponse;
import com.nexuspro.dto.response.AuthResponses.*;
import com.nexuspro.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * Authentication controller.
 *
 * Security design:
 * - Refresh token delivered via HttpOnly, Secure, SameSite=Strict cookie
 * - Access token returned in response body (stored in memory by client, NOT localStorage)
 * - All endpoints rate-limited at gateway level
 * - IP forwarding via X-Forwarded-For header for audit logging
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private static final String REFRESH_COOKIE = "nexuspro_refresh";

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest httpReq) {
        String ip = getClientIp(httpReq);
        String message = authService.register(req, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(message));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpReq,
            HttpServletResponse httpRes) {
        String ip = getClientIp(httpReq);
        String ua = httpReq.getHeader(HttpHeaders.USER_AGENT);

        AuthResponse auth = authService.login(req, ip, ua);

        // Set refresh token as HttpOnly cookie — never exposed to JavaScript
        setRefreshCookie(httpRes, auth.getAccessToken(), req.getEmail());

        return ResponseEntity.ok(ApiResponse.ok(auth));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest httpReq,
            HttpServletResponse httpRes) {
        String refreshToken = extractRefreshCookie(httpReq);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("No refresh token", "TOKEN_MISSING"));
        }
        String ip = getClientIp(httpReq);
        String ua = httpReq.getHeader(HttpHeaders.USER_AGENT);

        AuthResponse auth = authService.refresh(refreshToken, ip, ua);

        // Rotate cookie
        setRefreshCookie(httpRes, auth.getAccessToken(), null);

        return ResponseEntity.ok(ApiResponse.ok(auth));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpReq,
            HttpServletResponse httpRes) {
        String accessToken   = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        String refreshToken  = extractRefreshCookie(httpReq);

        authService.logout(accessToken, refreshToken);

        // Clear cookie
        clearRefreshCookie(httpRes);

        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok("Email verified successfully. You can now log in."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.getEmail());
        // Always return same message — prevent email enumeration
        return ResponseEntity.ok(ApiResponse.ok("If that email exists, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully. Please log in."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            @RequestHeader("X-User-Id") String userId) {
        authService.changePassword(UUID.fromString(userId), req.getCurrentPassword(), req.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully."));
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> setupMfa(
            @RequestHeader("X-User-Id") String userId) {
        MfaSetupResponse mfa = authService.setupMfa(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok(mfa));
    }

    @PostMapping("/mfa/confirm")
    public ResponseEntity<ApiResponse<String>> confirmMfa(
            @Valid @RequestBody EnableMfaRequest req,
            @RequestHeader("X-User-Id") String userId) {
        authService.confirmMfa(UUID.fromString(userId), req.getTotpCode());
        return ResponseEntity.ok(ApiResponse.ok("MFA enabled successfully."));
    }

    // ── Cookie helpers ──────────────────────────────────────────────────────

    private void setRefreshCookie(HttpServletResponse res, String token, String email) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
            .httpOnly(true)          // Not accessible via JS
            .secure(true)            // HTTPS only
            .sameSite("Strict")      // CSRF protection
            .path("/api/v1/auth")    // Scoped to auth endpoints only
            .maxAge(Duration.ofDays(7))
            .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true).secure(true).sameSite("Strict")
            .path("/api/v1/auth").maxAge(0).build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractRefreshCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        return Arrays.stream(req.getCookies())
            .filter(c -> REFRESH_COOKIE.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
