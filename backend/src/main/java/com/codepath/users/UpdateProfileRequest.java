package com.codepath.users;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Onboarding goals and study time. PUT replaces all five fields. */
public record UpdateProfileRequest(
        @Size(max = 500, message = "must be at most 500 characters") String programmingBackground,
        @Size(max = 500, message = "must be at most 500 characters") String dsaExperience,
        @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String prepGoal,
        @Size(max = 100, message = "must be at most 100 characters") String targetLevel,
        @NotNull(message = "is required") @Min(value = 10, message = "must be at least 10")
        @Max(value = 720, message = "must be at most 720") Integer dailyStudyMinutes) {
}
