package com.codepath.learning;

import java.util.List;

/** Response of {@code GET /api/learning/recommendations}. */
public record RecommendationsDto(List<RecommendationDto> recommendations, List<LockedTopicDto> lockedTopics,
        String note) {
}
