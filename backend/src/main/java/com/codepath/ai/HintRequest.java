package com.codepath.ai;

import com.codepath.submissions.Verdict;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/ai/hint}. {@code sourceCode} may be empty (a learner can ask for a Level 1 nudge
 * before writing anything). {@code verdict} is the last Judge0 verdict the UI showed, if any.
 */
public record HintRequest(
        @NotNull(message = "questionId is required") Long questionId,
        @Size(max = 20000, message = "sourceCode must be at most 20000 characters") String sourceCode,
        Verdict verdict,
        @NotNull(message = "hintLevel is required")
        @Min(value = 1, message = "hintLevel must be between 1 and 4")
        @Max(value = 4, message = "hintLevel must be between 1 and 4") Integer hintLevel) {
}
