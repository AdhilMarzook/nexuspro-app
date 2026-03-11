package com.nexuspro.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT utility for generating and validating tokens.
 *
 * Security decisions:
 * - HS512 signing (stronger than HS256)
 * - 15-minute access tokens (limits exposure window)
 * - 7-day refresh tokens stored in HttpOnly cookies only
 * - JTI (JWT ID) claim enables token revocation via Redis blocklist
 * - All claims validated: exp, iat, nbf, iss
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry-ms:900000}")   // 15 min
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:604800000}") // 7 days
    private long refreshTokenExpiryMs;

    @Value("${spring.application.name:nexuspro}")
    private String issuer;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String userId, String email, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .id(UUID.randomUUID().toString())               // JTI for revocation
            .issuer(issuer)
            .subject(userId)
            .claim("email", email)
            .claim("username", username)
            .claim("role", role)
            .claim("type", "ACCESS")
            .issuedAt(Date.from(now))
            .notBefore(Date.from(now))
            .expiration(Date.from(now.plusMillis(accessTokenExpiryMs)))
            .signWith(getSigningKey(), Jwts.SIG.HS512)
            .compact();
    }

    public String generateRefreshToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .issuer(issuer)
            .subject(userId)
            .claim("type", "REFRESH")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(refreshTokenExpiryMs)))
            .signWith(getSigningKey(), Jwts.SIG.HS512)
            .compact();
    }

    public Claims validateAndExtract(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractJti(String token) {
        return validateAndExtract(token).getId();
    }

    public String extractUserId(String token) {
        return validateAndExtract(token).getSubject();
    }

    public boolean isRefreshToken(Claims claims) {
        return "REFRESH".equals(claims.get("type", String.class));
    }

    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }
}
