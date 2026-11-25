package com.codepath.assessment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DiagnosticSubmitRequest(
        @NotEmpty(message = "is required") List<@Valid @NotNull(message = "must not be null") AnswerItem> answers) {

    public record AnswerItem(
            @NotNull(message = "is required") Long questionId,
            @NotNull(message = "is required")
            @Pattern(regexp = "^[A-Da-d]$", message = "must be a single letter A, B, C or D") String answer) {
    }
}
