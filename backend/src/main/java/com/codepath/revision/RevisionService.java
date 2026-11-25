package com.codepath.revision;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.learning.MasteryUpdate;
import com.codepath.learning.TopicMastery;
import com.codepath.learning.TopicMasteryRepository;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.TopicVerdictCount;
import com.codepath.submissions.Verdict;

import lombok.RequiredArgsConstructor;

/**
 * Spaced-revision queue. A topic is queued when
 * <ul>
 *   <li>its {@code next_revision_due} date has passed (the date is set after every submission: WEAK 1 day,
 *       DEVELOPING 3 days, STRONG 7 days), or</li>
 *   <li>its error rate is high: at least {@value #HIGH_ERROR_RATE} of at least {@value #MIN_GRADED}
 *       graded submissions failed. Compilation errors are not graded, matching the mastery rules.</li>
 * </ul>
 * Topics the learner has never practised are never queued. Overdue topics come first, then by error rate.
 */
@Service
@RequiredArgsConstructor
public class RevisionService {

    public static final double HIGH_ERROR_RATE = 0.5;
    public static final int MIN_GRADED = 3;

    private final TopicMasteryRepository topicMasteryRepository;
    private final SubmissionRepository submissionRepository;

    @Transactional(readOnly = true)
    public RevisionQueueDto queue(Long userId) {
        Instant now = Instant.now();
        Map<Long, int[]> gradedByTopic = gradedCounts(submissionRepository.countVerdictsByTopic(userId));

        List<Candidate> due = new ArrayList<>();
        for (TopicMastery row : topicMasteryRepository.findByUserId(userId)) {
            int[] counts = gradedByTopic.getOrDefault(row.getTopic().getId(), new int[] {0, 0});
            int graded = counts[0];
            double errorRate = errorRate(counts[1], graded);

            boolean overdue = row.getNextRevisionDue() != null && row.getNextRevisionDue().isBefore(now);
            boolean highError = graded >= MIN_GRADED && errorRate >= HIGH_ERROR_RATE;
            if (!overdue && !highError) {
                continue;
            }
            Duration lateBy = overdue ? Duration.between(row.getNextRevisionDue(), now) : Duration.ZERO;
            RevisionTrigger trigger = overdue && highError ? RevisionTrigger.BOTH
                    : overdue ? RevisionTrigger.SCHEDULED : RevisionTrigger.HIGH_ERROR_RATE;
            RevisionItemDto item = new RevisionItemDto(row.getTopic().getId(), row.getTopic().getName(),
                    row.getMasteryScore(), row.getStatus(), row.getLastPracticedAt(), row.getNextRevisionDue(),
                    lateBy.toDays(), graded, Math.round(errorRate * 100.0) / 100.0, trigger,
                    reason(row.getTopic().getName(), trigger, lateBy, graded, errorRate));
            due.add(new Candidate(item, lateBy, errorRate));
        }

        due.sort(Comparator.comparing(Candidate::lateBy).reversed()
                .thenComparing(Comparator.comparingDouble(Candidate::errorRate).reversed())
                .thenComparing(c -> c.item().topic()));
        List<RevisionItemDto> items = due.stream().map(Candidate::item).toList();
        return new RevisionQueueDto(items.size(), items, MasteryUpdate.ESTIMATE_NOTE
                + " Revision dates follow fixed intervals after practice (1, 3 or 7 days by mastery level).");
    }

    /** Per topic: [0] graded submissions (accepted + failed), [1] failed submissions (WA, RE, TLE). */
    static Map<Long, int[]> gradedCounts(List<TopicVerdictCount> rows) {
        Map<Long, int[]> byTopic = new HashMap<>();
        for (TopicVerdictCount row : rows) {
            Verdict verdict = row.verdict();
            boolean failed = verdict == Verdict.WRONG_ANSWER || verdict == Verdict.RUNTIME_ERROR
                    || verdict == Verdict.TIME_LIMIT_EXCEEDED;
            if (!failed && verdict != Verdict.ACCEPTED) {
                continue;
            }
            int[] counts = byTopic.computeIfAbsent(row.topicId(), id -> new int[2]);
            counts[0] += row.count().intValue();
            if (failed) {
                counts[1] += row.count().intValue();
            }
        }
        return byTopic;
    }

    static double errorRate(int failed, int graded) {
        return graded == 0 ? 0.0 : failed / (double) graded;
    }

    private static String reason(String topic, RevisionTrigger trigger, Duration lateBy, int graded, double errorRate) {
        String overdue = "revision was due " + describe(lateBy) + " ago";
        String errors = String.format(Locale.ROOT, "%.0f%% of your %d graded submissions in %s failed",
                errorRate * 100.0, graded, topic);
        return switch (trigger) {
            case SCHEDULED -> "Revise " + topic + ": " + overdue + ".";
            case HIGH_ERROR_RATE -> "Revise " + topic + ": " + errors + ".";
            case BOTH -> "Revise " + topic + ": " + overdue + ", and " + errors + ".";
        };
    }

    private static String describe(Duration duration) {
        long days = duration.toDays();
        if (days >= 1) {
            return days == 1 ? "1 day" : days + " days";
        }
        long hours = duration.toHours();
        return hours <= 1 ? "about an hour" : hours + " hours";
    }

    private record Candidate(RevisionItemDto item, Duration lateBy, double errorRate) {
    }
}
