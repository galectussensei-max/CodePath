package com.codepath.questions;

import java.util.List;

/**
 * Full problem view for the workspace. {@code sampleTestCases} holds PUBLIC cases only: the list is
 * typed as {@link PublicTestCaseDto}, which has no hidden flag and refuses hidden cases.
 */
public record QuestionDetailDto(
        Long id,
        String slug,
        String title,
        Long topicId,
        String topic,
        Long conceptId,
        String concept,
        Difficulty difficulty,
        List<String> placementTags,
        String description,
        String constraints,
        String starterCodeJava,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        List<PublicTestCaseDto> sampleTestCases) {

    public static QuestionDetailDto from(Question question, List<PublicTestCaseDto> sampleTestCases) {
        return new QuestionDetailDto(
                question.getId(),
                question.getSlug(),
                question.getTitle(),
                question.getTopic().getId(),
                question.getTopic().getName(),
                question.getConcept().getId(),
                question.getConcept().getName(),
                question.getDifficulty(),
                QuestionSummaryDto.splitTags(question.getPlacementTags()),
                question.getDescription(),
                question.getConstraints(),
                question.getStarterCodeJava(),
                question.getTimeLimitMs(),
                question.getMemoryLimitKb(),
                List.copyOf(sampleTestCases));
    }
}
