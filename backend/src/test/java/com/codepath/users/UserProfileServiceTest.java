package com.codepath.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.codepath.common.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private LearnerProfileRepository learnerProfileRepository;

    @InjectMocks
    private UserProfileService service;

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setName("Asha");
        user.setEmail("asha@example.com");
        user.setRole(Role.STUDENT);
        return user;
    }

    @Test
    void getProfileReturnsExistingProfileWithoutSecrets() {
        User user = user();
        LearnerProfile profile = new LearnerProfile();
        profile.setUser(user);
        profile.setPrepGoal("Placements");
        profile.setDailyStudyMinutes(90);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(learnerProfileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));

        ProfileResponse response = service.getProfile(7L);

        assertEquals(7L, response.userId());
        assertEquals("asha@example.com", response.email());
        assertEquals("Placements", response.prepGoal());
        assertEquals(90, response.dailyStudyMinutes());
        assertFalse(response.diagnosticCompleted());
    }

    @Test
    void getProfileCreatesMissingProfile() {
        User user = user();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(learnerProfileRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(learnerProfileRepository.save(any(LearnerProfile.class))).thenAnswer(i -> i.getArgument(0));

        ProfileResponse response = service.getProfile(7L);

        verify(learnerProfileRepository).save(any(LearnerProfile.class));
        assertFalse(response.diagnosticCompleted());
        assertNull(response.prepGoal());
    }

    @Test
    void updateProfileTrimsTextAndKeepsDiagnosticFlag() {
        User user = user();
        LearnerProfile profile = new LearnerProfile();
        profile.setUser(user);
        profile.setDiagnosticCompleted(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(learnerProfileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(learnerProfileRepository.save(any(LearnerProfile.class))).thenAnswer(i -> i.getArgument(0));

        ProfileResponse response = service.updateProfile(7L,
                new UpdateProfileRequest("  Java basics  ", "   ", "  Crack product-company OAs ", "Intermediate", 120));

        assertEquals("Java basics", response.programmingBackground());
        assertNull(response.dsaExperience());
        assertEquals("Crack product-company OAs", response.prepGoal());
        assertEquals("Intermediate", response.targetLevel());
        assertEquals(120, response.dailyStudyMinutes());
        assertEquals(true, response.diagnosticCompleted());
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getProfile(99L));
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateProfile(99L, new UpdateProfileRequest(null, null, "Goal", null, 60)));
    }
}
