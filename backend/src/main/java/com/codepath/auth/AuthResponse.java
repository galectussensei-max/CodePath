package com.codepath.auth;

import com.codepath.users.Role;

public record AuthResponse(String accessToken, String tokenType, long expiresInMs, UserSummary user) {

    public record UserSummary(Long id, String name, String email, Role role, boolean diagnosticCompleted) {
    }
}
