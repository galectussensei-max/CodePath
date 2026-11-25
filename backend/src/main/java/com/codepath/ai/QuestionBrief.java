package com.codepath.ai;

/**
 * The public facts about a question that the AI is allowed to see. Test cases (public or hidden) and
 * reference solutions are deliberately not part of it.
 */
public record QuestionBrief(
        Long id,
        String title,
        String description,
        String constraints,
        String difficulty,
        String topic,
        String concept) {
}
