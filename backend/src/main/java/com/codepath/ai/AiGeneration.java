package com.codepath.ai;

/**
 * A guard-approved model reply plus the prompt that produced it (stored as {@code prompt_context}).
 */
public record AiGeneration(String content, String promptContext) {
}
