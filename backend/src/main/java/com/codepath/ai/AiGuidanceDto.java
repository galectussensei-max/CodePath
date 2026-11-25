package com.codepath.ai;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An AI reply. It is always marked unverified so the UI renders it in the separate "AI Assistant
 * (Unverified Guidance - Verify with Judge)" box, never as a Judge0 verdict.
 *
 * @param hintLevel               1-4 for hints, null for reviews
 * @param hintsUsedForQuestion    how many hints this learner has now taken on this question (feeds mastery)
 */
public record AiGuidanceDto(
        Long interactionId,
        String interactionType,
        Integer hintLevel,
        String content,
        @JsonProperty("isVerified") boolean isVerified,
        String label,
        long hintsUsedForQuestion,
        Instant createdAt) {

    public static final String UNVERIFIED_LABEL = "AI Assistant (Unverified Guidance - Verify with Judge)";

    public static AiGuidanceDto of(AiInteraction interaction, Integer hintLevel, long hintsUsedForQuestion) {
        return new AiGuidanceDto(interaction.getId(), interaction.getInteractionType().name(), hintLevel,
                interaction.getAiResponse(), false, UNVERIFIED_LABEL, hintsUsedForQuestion,
                interaction.getCreatedAt());
    }
}
