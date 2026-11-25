package com.codepath.users;

/** Client-facing view of a learner profile. Never exposes the password hash or the entity itself. */
public record ProfileResponse(
        Long userId,
        String name,
        String email,
        String programmingBackground,
        String dsaExperience,
        String prepGoal,
        String targetLevel,
        Integer dailyStudyMinutes,
        boolean diagnosticCompleted) {

    public static ProfileResponse from(User user, LearnerProfile profile) {
        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                profile.getProgrammingBackground(),
                profile.getDsaExperience(),
                profile.getPrepGoal(),
                profile.getTargetLevel(),
                profile.getDailyStudyMinutes(),
                profile.isDiagnosticCompleted());
    }
}
