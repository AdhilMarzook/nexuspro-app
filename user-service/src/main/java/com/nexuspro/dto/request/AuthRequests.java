package com.nexuspro.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

public class AuthRequests {

    @Data
    public static class RegisterRequest {
        @NotBlank
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        private String email;

        @NotBlank
        @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username may only contain letters, numbers, underscores, dots, and hyphens")
        private String username;

        @NotBlank
        @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
        private String password;

        @NotBlank
        @Size(max = 100)
        private String fullName;

        @NotNull
        private String role; // PLAYER | COACH | ORG_ADMIN
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;

        // Optional: TOTP code if MFA is enabled
        private String totpCode;
    }

    @Data
    public static class RefreshTokenRequest {
        @NotBlank
        private String refreshToken;
    }

    @Data
    public static class ForgotPasswordRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String token;

        @NotBlank
        @Size(min = 8, max = 72)
        private String newPassword;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8, max = 72)
        private String newPassword;
    }

    @Data
    public static class EnableMfaRequest {
        @NotBlank
        @Size(min = 6, max = 6)
        private String totpCode;
    }
}
