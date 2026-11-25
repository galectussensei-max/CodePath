package com.codepath.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.codepath.common.ConflictException;
import com.codepath.users.LearnerProfile;
import com.codepath.users.LearnerProfileRepository;
import com.codepath.users.Role;
import com.codepath.users.User;
import com.codepath.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LearnerProfileRepository learnerProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerCreatesStudentWithNormalizedEmailAndEmptyProfile() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(11L);
            return user;
        });
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("token-123");
        when(jwtService.getExpirationMs()).thenReturn(60_000L);

        AuthResponse response = authService.register(
                new RegisterRequest("  Asha Verma ", "  Asha@Example.COM ", "password123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("asha@example.com", saved.getEmail());
        assertEquals("Asha Verma", saved.getName());
        assertEquals("encoded", saved.getPasswordHash());
        assertEquals(Role.STUDENT, saved.getRole());

        ArgumentCaptor<LearnerProfile> profileCaptor = ArgumentCaptor.forClass(LearnerProfile.class);
        verify(learnerProfileRepository).save(profileCaptor.capture());
        assertEquals(saved, profileCaptor.getValue().getUser());
        assertFalse(profileCaptor.getValue().isDiagnosticCompleted());

        assertEquals("token-123", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(60_000L, response.expiresInMs());
        assertEquals(11L, response.user().id());
        assertEquals(Role.STUDENT, response.user().role());
        assertFalse(response.user().diagnosticCompleted());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> authService.register(new RegisterRequest("Asha", "asha@example.com", "password123")));

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(learnerProfileRepository, never()).save(any(LearnerProfile.class));
    }

    @Test
    void loginReturnsTokenAndDiagnosticFlag() {
        UserPrincipal principal = new UserPrincipal(5L, "Asha", "asha@example.com", "encoded", Role.STUDENT);
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        principal.getAuthorities()));
        LearnerProfile profile = new LearnerProfile();
        profile.setDiagnosticCompleted(true);
        when(learnerProfileRepository.findByUserId(5L)).thenReturn(Optional.of(profile));
        when(jwtService.generateToken(principal)).thenReturn("login-token");
        when(jwtService.getExpirationMs()).thenReturn(60_000L);

        AuthResponse response = authService.login(new LoginRequest(" Asha@Example.com ", "password123"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor = ArgumentCaptor
                .forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertEquals("asha@example.com", captor.getValue().getPrincipal());

        assertEquals("login-token", response.accessToken());
        assertEquals("asha@example.com", response.user().email());
        assertTrue(response.user().diagnosticCompleted());
    }

    @Test
    void loginPropagatesBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("asha@example.com", "wrong")));

        verify(jwtService, never()).generateToken(any());
    }
}
