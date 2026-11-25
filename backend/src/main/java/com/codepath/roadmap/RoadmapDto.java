package com.codepath.roadmap;

import java.util.List;

import com.codepath.learning.MasteryStatus;
import com.codepath.questions.Difficulty;

/** Response of {@code GET /api/roadmap}: an ordered, optional pathway through topics and problems. */
public record RoadmapDto(
        Goal goal,
        List<Step> steps,
        Integer estimatedDaysRemaining,
        String note) {

    /** The learner's onboarding goal, echoed so the page can show what the pacing is based on. */
    public record Goal(String prepGoal, String targetLevel, Integer dailyStudyMinutes) {
    }

    public record Step(
            int order,
            Long topicId,
            String topic,
            String description,
            RoadmapStepState state,
            double masteryScore,
            MasteryStatus masteryStatus,
            int solvedCount,
            int totalProblems,
            int estimatedMinutesRemaining,
            List<Prerequisite> prerequisites,
            String lockedReason,
            List<Problem> problems) {
    }

    public record Prerequisite(Long topicId, String topic, double masteryScore, boolean met) {
    }

    public record Problem(Long questionId, String slug, String title, String concept, Difficulty difficulty,
            boolean solved) {
    }
}
