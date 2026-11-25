package com.codepath.questions;

import java.util.Arrays;
import java.util.List;

/** One row of the problem bank. Contains no test cases at all. */
public record QuestionSummaryDto(
        Long id,
        String slug,
        String title,
        Long topicId,
        String topic,
        Long conceptId,
        String concept,
        Difficulty difficulty,
        List<String> placementTags) {

    public static QuestionSummaryDto from(Question question) {
        return new QuestionSummaryDto(
                question.getId(),
                question.getSlug(),
                question.getTitle(),
                question.getTopic().getId(),
                question.getTopic().getName(),
                question.getConcept().getId(),
                question.getConcept().getName(),
                question.getDifficulty(),
                splitTags(question.getPlacementTags()));
    }

    static List<String> splitTags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::strip).filter(tag -> !tag.isEmpty()).toList();
    }
}
