package com.codepath.submissions;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/submissions/submit}. The language is always Java. The hint count is deliberately
 * not accepted from the client: the server counts the learner's AI hints itself.
 *
 * @param timeTakenSeconds optional client-reported time spent on the problem
 */
public record SubmitRequest(
        @NotNull(message = "questionId is required") Long questionId,
        @NotBlank(message = "sourceCode is required")
        @Size(max = MAX_SOURCE_CHARS, message = "sourceCode is too long") String sourceCode,
        @PositiveOrZero(message = "timeTakenSeconds cannot be negative")
        @Max(value = 86_400, message = "timeTakenSeconds is too large") Integer timeTakenSeconds) {

    public static final int MAX_SOURCE_CHARS = 65_536;
}
