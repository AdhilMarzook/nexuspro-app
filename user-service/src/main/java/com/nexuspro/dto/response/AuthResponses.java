package com.nexuspro.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

public class AuthResponses {

    @Data @Builder
    public static class AuthResponse {
        private String accessToken;
        private String tokenType;        // "Bearer"
        private long expiresIn;          // seconds
        private UserInfo user;
        // Refresh token is set as HttpOnly cookie — NOT returned in body
    }

    @Data @Builder
    public static class UserInfo {
        private String id;
        private String email;
        private String username;
        private String fullName;
        private String role;
        private String plan;
        private boolean mfaEnabled;
        private boolean emailVerified;
        private Instant createdAt;
    }

    @Data @Builder
    public static class MfaSetupResponse {
        private String secretKey;
        private String qrCodeUri;        // otpauth:// URI for authenticator apps
        private String[] backupCodes;    // 8 one-time backup codes
    }
}
