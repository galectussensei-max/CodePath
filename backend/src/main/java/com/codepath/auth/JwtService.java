package com.codepath.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${codepath.jwt.secret}") String secret,
            @Value("${codepath.jwt.expiration-ms}") long expirationMs) {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "codepath.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (expirationMs <= 0) {
            throw new IllegalStateException("codepath.jwt.expiration-ms must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserPrincipal principal) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + expirationMs);
        return Jwts.builder()
                .subject(principal.getEmail())
                .claim("uid", principal.getId())
                .claim("role", principal.getRole().name())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature and expiry and returns the subject (the user's email).
     *
     * @throws JwtException             if the token is malformed, tampered with, or expired
     * @throws IllegalArgumentException if the token is blank
     */
    public String extractEmail(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtException("Token has no subject");
        }
        return subject;
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
