package com.codepath.users;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.common.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final LearnerProfileRepository learnerProfileRepository;

    /** Not read-only: a profile row is created on the fly if registration somehow left none. */
    @Transactional
    public ProfileResponse getProfile(Long userId) {
        User user = loadUser(userId);
        return ProfileResponse.from(user, loadOrCreateProfile(user));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = loadUser(userId);
        LearnerProfile profile = loadOrCreateProfile(user);
        profile.setProgrammingBackground(clean(request.programmingBackground()));
        profile.setDsaExperience(clean(request.dsaExperience()));
        profile.setPrepGoal(clean(request.prepGoal()));
        profile.setTargetLevel(clean(request.targetLevel()));
        profile.setDailyStudyMinutes(request.dailyStudyMinutes());
        LearnerProfile saved = learnerProfileRepository.save(profile);
        return ProfileResponse.from(user, saved);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private LearnerProfile loadOrCreateProfile(User user) {
        return learnerProfileRepository.findByUserId(user.getId()).orElseGet(() -> {
            LearnerProfile profile = new LearnerProfile();
            profile.setUser(user);
            profile.setDiagnosticCompleted(false);
            return learnerProfileRepository.save(profile);
        });
    }

    /** Trims; blank becomes null so optional fields are stored consistently. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
