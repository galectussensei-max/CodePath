package com.codepath.revision;

import java.time.Instant;

import com.codepath.learning.MasteryStatus;

/**
 * One topic that needs revision. {@code errorRate} is the share of graded submissions in the topic that were
 * wrong answers, runtime errors or time-limit failures (0.0 to 1.0); {@code daysOverdue} is 0 when the topic
 * is only in the queue because of its error rate.
 */
public record RevisionItemDto(
        Long topicId,
        String topic,
        double masteryScore,
        MasteryStatus status,
        Instant lastPracticedAt,
        Instant nextRevisionDue,
        long daysOverdue,
        int gradedSubmissions,
        double errorRate,
        RevisionTrigger trigger,
        String reason) {
}
