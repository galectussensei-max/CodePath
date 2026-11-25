package com.codepath.ai;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/ai/review}. The code that gets reviewed is read from the learner's saved
 * ACCEPTED submission, never from the request, so a review cannot be obtained for unverified code.
 * {@code submissionId} is optional; without it the latest ACCEPTED submission for the question is used.
 */
public record ReviewRequest(
        @NotNull(message = "questionId is required") Long questionId,
        Long submissionId) {
}
