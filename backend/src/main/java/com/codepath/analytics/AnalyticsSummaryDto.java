package com.codepath.analytics;

import java.time.Instant;
import java.util.List;

import com.codepath.learning.MasteryStatus;
import com.codepath.questions.Difficulty;
import com.codepath.submissions.Verdict;

/** Response of {@code GET /api/analytics/summary}; every part maps to one chart on the analytics page. */
public record AnalyticsSummaryDto(
        Overview overview,
        List<TopicMasteryPoint> topicMastery,
        List<DifficultyProgress> difficultyBreakdown,
        List<VerdictCount> verdictDistribution,
        List<RecurringError> recurringErrors,
        String note) {

    public record Overview(
            long totalSubmissions,
            long acceptedSubmissions,
            double acceptanceRatePercent,
            int problemsSolved,
            int problemsAttempted,
            int totalProblems,
            Double averageAcceptedRuntimeMs) {
    }

    /** One radar/bar point. Topics the learner has not been assessed in appear with score 0 and UNASSESSED. */
    public record TopicMasteryPoint(Long topicId, String topic, double masteryScore, MasteryStatus status,
            int attemptsCount, int solvedCount, int totalProblems, Instant lastPracticedAt) {
    }

    public record DifficultyProgress(Difficulty difficulty, int totalProblems, int attempted, int solved) {
    }

    public record VerdictCount(Verdict verdict, long count) {
    }

    /** A failing verdict that keeps happening in the same topic (at least 2 times). */
    public record RecurringError(Long topicId, String topic, Verdict verdict, long count, double sharePercent,
            String description) {
    }
}
