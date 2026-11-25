package com.codepath.assessment;

import java.util.List;

import com.codepath.learning.MasteryStatus;

/** Result of grading a diagnostic. Per-question correctness is intentionally not revealed. */
public record DiagnosticResultDto(
        int totalQuestions,
        int correctCount,
        List<TopicResult> topics,
        String note) {

    public record TopicResult(
            Long topicId,
            String topicName,
            int correct,
            int total,
            double masteryScore,
            MasteryStatus status) {
    }
}
