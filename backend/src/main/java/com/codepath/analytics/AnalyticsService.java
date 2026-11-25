package com.codepath.analytics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.config.DataSeeder;
import com.codepath.learning.MasteryStatus;
import com.codepath.learning.MasteryUpdate;
import com.codepath.learning.TopicMastery;
import com.codepath.learning.TopicMasteryRepository;
import com.codepath.questions.Difficulty;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.Topic;
import com.codepath.questions.TopicRepository;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.TopicVerdictCount;
import com.codepath.submissions.Verdict;

import lombok.RequiredArgsConstructor;

/** Read-only statistics about one learner's submissions and mastery, shaped for charts. */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    static final int MAX_RECURRING_ERRORS = 5;
    static final long MIN_RECURRING_COUNT = 2;

    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final TopicMasteryRepository topicMasteryRepository;
    private final SubmissionRepository submissionRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto summary(Long userId) {
        List<Topic> topics = topicRepository.findAllByOrderByDisplayOrderAsc();
        List<Question> practice = questionRepository.findPracticeQuestions(DataSeeder.DIAGNOSTIC_TAG);
        Map<Long, TopicMastery> mastery = topicMasteryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(row -> row.getTopic().getId(), row -> row, (a, b) -> a));
        Set<Long> solved = new HashSet<>(submissionRepository.findQuestionIdsByVerdict(userId, Verdict.ACCEPTED));
        Set<Long> attempted = new HashSet<>(submissionRepository.findAttemptedQuestionIds(userId));
        List<TopicVerdictCount> verdictRows = submissionRepository.countVerdictsByTopic(userId);

        Map<Verdict, Long> byVerdict = new EnumMap<>(Verdict.class);
        for (TopicVerdictCount row : verdictRows) {
            byVerdict.merge(row.verdict(), row.count(), Long::sum);
        }
        long total = byVerdict.values().stream().mapToLong(Long::longValue).sum();
        long accepted = byVerdict.getOrDefault(Verdict.ACCEPTED, 0L);

        List<AnalyticsSummaryDto.TopicMasteryPoint> topicPoints = new ArrayList<>();
        for (Topic topic : topics) {
            TopicMastery row = mastery.get(topic.getId());
            int topicTotal = (int) practice.stream().filter(q -> q.getTopic().getId().equals(topic.getId())).count();
            topicPoints.add(row == null
                    ? new AnalyticsSummaryDto.TopicMasteryPoint(topic.getId(), topic.getName(), 0.0,
                            MasteryStatus.UNASSESSED, 0, 0, topicTotal, null)
                    : new AnalyticsSummaryDto.TopicMasteryPoint(topic.getId(), topic.getName(), row.getMasteryScore(),
                            row.getStatus(), row.getAttemptsCount(), row.getSolvedCount(), topicTotal,
                            row.getLastPracticedAt()));
        }

        List<AnalyticsSummaryDto.DifficultyProgress> difficulty = new ArrayList<>();
        for (Difficulty level : Difficulty.values()) {
            List<Question> atLevel = practice.stream().filter(q -> q.getDifficulty() == level).toList();
            difficulty.add(new AnalyticsSummaryDto.DifficultyProgress(level, atLevel.size(),
                    (int) atLevel.stream().filter(q -> attempted.contains(q.getId())).count(),
                    (int) atLevel.stream().filter(q -> solved.contains(q.getId())).count()));
        }

        List<AnalyticsSummaryDto.VerdictCount> distribution = new ArrayList<>();
        for (Verdict verdict : Verdict.values()) {
            if (verdict != Verdict.PENDING) {
                distribution.add(new AnalyticsSummaryDto.VerdictCount(verdict, byVerdict.getOrDefault(verdict, 0L)));
            }
        }

        Double averageRuntime = submissionRepository.averageExecutionTimeMs(userId, Verdict.ACCEPTED);
        AnalyticsSummaryDto.Overview overview = new AnalyticsSummaryDto.Overview(total, accepted,
                total == 0 ? 0.0 : Math.round(accepted * 1000.0 / total) / 10.0,
                (int) practice.stream().filter(q -> solved.contains(q.getId())).count(),
                (int) practice.stream().filter(q -> attempted.contains(q.getId())).count(),
                practice.size(),
                averageRuntime == null ? null : Math.round(averageRuntime * 10.0) / 10.0);

        return new AnalyticsSummaryDto(overview, topicPoints, difficulty, distribution,
                recurringErrors(verdictRows, topics), MasteryUpdate.ESTIMATE_NOTE);
    }

    /** Failing verdicts that repeat within one topic, most frequent first. Compile errors are included. */
    static List<AnalyticsSummaryDto.RecurringError> recurringErrors(List<TopicVerdictCount> rows, List<Topic> topics) {
        Map<Long, String> names = topics.stream().collect(Collectors.toMap(Topic::getId, Topic::getName, (a, b) -> a));
        List<TopicVerdictCount> failures = rows.stream()
                .filter(row -> row.verdict() != Verdict.ACCEPTED && row.verdict() != Verdict.PENDING)
                .toList();
        long totalFailures = failures.stream().mapToLong(TopicVerdictCount::count).sum();
        return failures.stream()
                .filter(row -> row.count() >= MIN_RECURRING_COUNT)
                .sorted(Comparator.comparing(TopicVerdictCount::count).reversed()
                        .thenComparing(row -> names.getOrDefault(row.topicId(), "")))
                .limit(MAX_RECURRING_ERRORS)
                .map(row -> {
                    String topic = names.getOrDefault(row.topicId(), "Unknown topic");
                    return new AnalyticsSummaryDto.RecurringError(row.topicId(), topic, row.verdict(), row.count(),
                            Math.round(row.count() * 1000.0 / totalFailures) / 10.0,
                            label(row.verdict()) + " " + row.count() + " times in " + topic);
                })
                .toList();
    }

    private static String label(Verdict verdict) {
        String lower = verdict.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
