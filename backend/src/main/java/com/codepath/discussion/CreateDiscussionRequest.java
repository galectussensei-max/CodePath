package com.codepath.discussion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDiscussionRequest(
        @NotBlank(message = "title is required")
        @Size(min = 3, max = 200, message = "title must be 3 to 200 characters") String title,
        @NotBlank(message = "content is required")
        @Size(max = 5000, message = "content must be at most 5000 characters") String content) {
}
