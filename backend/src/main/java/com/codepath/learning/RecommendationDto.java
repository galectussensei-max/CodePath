package com.codepath.learning;

import java.util.List;

import com.codepath.questions.Difficulty;

/** One recommended problem. {@code reason} always explains, in plain words, why it was ranked here. */
public record RecommendationDto(
        int rank,
        Long questionId,
        String slug,
        String title,
        Long topicId,
        String topic,
        String concept,
        Difficulty difficulty,
        List<String> placementTags,
        double priorityScore,
        String reason) {
}
