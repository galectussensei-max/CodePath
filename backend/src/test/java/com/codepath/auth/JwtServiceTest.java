package com.codepath.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.codepath.users.Role;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long";

    private static UserPrincipal principal() {
        return new UserPrincipal(7L, "Asha", "asha@example.com", "hash", Role.STUDENT);
    }

    @Test
    void generatedTokenRoundTripsToTheEmail() {
        JwtService service = new JwtService(SECRET, 60_000L);

        String token = service.generateToken(principal());

        assertEquals("asha@example.com", service.extractEmail(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = new JwtService(SECRET, 60_000L);
        String token = service.generateToken(principal());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThrows(JwtException.class, () -> service.extractEmail(tampered));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService issuer = new JwtService("another-secret-that-is-also-at-least-32-bytes", 60_000L);
        JwtService verifier = new JwtService(SECRET, 60_000L);

        String token = issuer.generateToken(principal());

        assertThrows(JwtException.class, () -> verifier.extractEmail(token));
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService service = new JwtService(SECRET, 1L);
        String token = service.generateToken(principal());

        Thread.sleep(50L);

        assertThrows(ExpiredJwtException.class, () -> service.extractEmail(token));
    }

    @Test
    void blankTokenIsRejected() {
        JwtService service = new JwtService(SECRET, 60_000L);

        assertThrows(IllegalArgumentException.class, () -> service.extractEmail(""));
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 60_000L));
    }

    @Test
    void nonPositiveExpirationFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService(SECRET, 0L));
    }
}
