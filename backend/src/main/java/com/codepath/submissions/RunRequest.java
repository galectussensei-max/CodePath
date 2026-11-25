package com.codepath.submissions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/submissions/run}. */
public record RunRequest(
        @NotNull(message = "questionId is required") Long questionId,
        @NotBlank(message = "sourceCode is required")
        @Size(max = SubmitRequest.MAX_SOURCE_CHARS, message = "sourceCode is too long") String sourceCode) {
}
