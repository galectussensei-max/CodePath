package com.codepath.auth;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.common.ConflictException;
import com.codepath.users.LearnerProfile;
import com.codepath.users.LearnerProfileRepository;
import com.codepath.users.Role;
import com.codepath.users.User;
import com.codepath.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final LearnerProfileRepository learnerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Self-registration always creates a STUDENT; the role is never taken from the request.
        user.setRole(Role.STUDENT);
        User saved = userRepository.saveAndFlush(user);

        LearnerProfile profile = new LearnerProfile();
        profile.setUser(saved);
        profile.setDiagnosticCompleted(false);
        learnerProfileRepository.save(profile);

        return buildResponse(UserPrincipal.from(saved), false);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        boolean diagnosticCompleted = learnerProfileRepository.findByUserId(principal.getId())
                .map(LearnerProfile::isDiagnosticCompleted)
                .orElse(false);

        return buildResponse(principal, diagnosticCompleted);
    }

    private AuthResponse buildResponse(UserPrincipal principal, boolean diagnosticCompleted) {
        String token = jwtService.generateToken(principal);
        AuthResponse.UserSummary summary = new AuthResponse.UserSummary(
                principal.getId(),
                principal.getName(),
                principal.getEmail(),
                principal.getRole(),
                diagnosticCompleted);
        return new AuthResponse(token, TOKEN_TYPE, jwtService.getExpirationMs(), summary);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
