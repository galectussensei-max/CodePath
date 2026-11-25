package com.codepath.learning;

import java.util.List;

/**
 * What one submission did to a topic's mastery estimate, with the reasons, so the change is explainable.
 * Mastery is a rule-based estimate, not a measurement of true ability.
 */
public record MasteryUpdate(
        Long topicId,
        String topicName,
        double previousScore,
        double newScore,
        double change,
        MasteryStatus previousStatus,
        MasteryStatus newStatus,
        List<String> factors,
        String note) {

    public static final String ESTIMATE_NOTE =
            "Mastery is a transparent rule-based estimate from your submissions, not a measurement of true ability.";
}
